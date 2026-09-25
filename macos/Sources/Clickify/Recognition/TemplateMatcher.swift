import CoreGraphics
import Foundation

/// Finds a **Template** inside a larger image with pyramid normalised cross-correlation (RG-8).
///
/// Apple has no template-matching API — no `matchTemplate`, MetalPerformanceShaders has no
/// cross-correlation, Vision can only localise text (RG-12). The SDK has been checked; do not go looking again.
///
/// Why a pyramid is needed: a 120×48 template on a 3024×1964 screen is roughly 5.56 million positions ×
/// 5,760 pixels ≈ **32 billion comparisons** for *one* Target resolution. Scanning coarsely at a
/// downscaled level and then refining around the best spot brings that down to about 9.5 million.
enum TemplateMatcher {
    struct Match: Equatable, Sendable {
        /// Top-left corner of the matched area, in pixels of the larger image.
        var origin: CGPoint
        /// `0…1`; 1 is an exact match.
        var score: Double
    }

    /// Refinement radius around each coarse candidate, in pixels at native resolution.
    static let refinementRadius = 8

    /// How many coarse candidates are kept for refinement.
    ///
    /// Keeping exactly one candidate is not enough: real interfaces are full of repeated detail (a row of
    /// identical buttons, table rules), so the best spot at the coarse level is often not the right one at native resolution.
    static let coarseCandidateLimit = 8

    /// The minimum contrast the downscaled template has to retain relative to the original.
    ///
    /// Downscaling too far **wipes out** high-frequency detail — text, 1px borders, small checker patterns — turning
    /// a coarse template into a nearly flat array. The coarse scan then points at a random spot and the refinement
    /// window does not even contain the right position.
    static let contrastRetention: Float = 0.5

    /// Ceiling on the number of comparisons for one coarse scan.
    ///
    /// This constraint pulls against `contrastRetention`: keeping contrast wants less downscaling,
    /// while scanning wants more. Without this ceiling, a detailed template on a full screen would fall
    /// back to a full scan — about 32 billion comparisons, the very thing the pyramid exists to avoid.
    static let coarseScanBudget = 40_000_000

    static func bestMatch(of template: GrayImage, in haystack: GrayImage) -> Match? {
        guard !template.isEmpty, !haystack.isEmpty,
              template.width <= haystack.width, template.height <= haystack.height else {
            return nil
        }

        let factor = downsampleFactor(for: template, in: haystack)
        guard factor > 1 else { return scan(template, in: haystack, region: nil) }

        let coarseTemplate = template.downsampled(by: factor)
        let coarseHaystack = haystack.downsampled(by: factor)
        guard coarseTemplate.width <= coarseHaystack.width,
              coarseTemplate.height <= coarseHaystack.height else {
            return scan(template, in: haystack, region: nil)
        }

        let candidates = topCandidates(
            coarseTemplate,
            in: coarseHaystack,
            limit: coarseCandidateLimit
        )
        guard !candidates.isEmpty else { return scan(template, in: haystack, region: nil) }

        let radius = refinementRadius + factor
        var best: Match?
        for candidate in candidates {
            let centerX = Int(candidate.origin.x) * factor
            let centerY = Int(candidate.origin.y) * factor
            let region = Region(
                minX: max(0, centerX - radius),
                minY: max(0, centerY - radius),
                maxX: min(haystack.width - template.width, centerX + radius),
                maxY: min(haystack.height - template.height, centerY + radius)
            )
            guard let refined = scan(template, in: haystack, region: region) else { continue }
            if refined.score > (best?.score ?? -.infinity) { best = refined }
        }
        return best
    }

    /// The cheapest downscale level that still keeps the template's contrast, within `coarseScanBudget`.
    ///
    /// Not chosen by size as intuition first suggested: a 32×32 template can still be a 3px checker pattern,
    /// and downscaling 8× would average it into a flat array.
    ///
    /// RG-18: when no level both keeps contrast and fits under the ceiling, the ceiling wins — a less accurate
    /// match beats freezing the interface for seconds on one Step. Narrowing the **Search region** is how the
    /// user buys that accuracy back.
    static func downsampleFactor(for template: GrayImage, in haystack: GrayImage) -> Int {
        let fullContrast = standardDeviation(template.pixels)
        guard fullContrast > 0 else { return 1 }

        // Descending: the largest downscale factor is the cheapest one.
        let usable = [8, 4, 2, 1].filter { factor in
            factor == 1
                || (template.width / factor >= 4 && template.height / factor >= 4)
        }
        let affordable = usable.filter { coarseScanCost(template, haystack, factor: $0) <= coarseScanBudget }

        let preserving = affordable.first { factor in
            factor == 1
                || standardDeviation(template.downsampled(by: factor).pixels)
                    >= fullContrast * contrastRetention
        }
        return preserving ?? affordable.first ?? usable.last ?? 1
    }

    private static func coarseScanCost(
        _ template: GrayImage,
        _ haystack: GrayImage,
        factor: Int
    ) -> Int {
        let positions = max(0, haystack.width / factor - template.width / factor + 1)
            * max(0, haystack.height / factor - template.height / factor + 1)
        let perPosition = max(1, (template.width / factor) * (template.height / factor))
        return positions * perPosition
    }

    /// Separated correlation peaks at the coarse level.
    ///
    /// Non-maximum suppression at half the template size; without it all `limit` candidates would pile up
    /// around a single peak and keeping several of them would be pointless.
    private static func topCandidates(
        _ template: GrayImage,
        in haystack: GrayImage,
        limit: Int
    ) -> [Match] {
        let templateStatistics = statistics(of: template.pixels)
        guard templateStatistics.deviation > 0 else { return [] }

        let suppressionX = max(1, template.width / 2)
        let suppressionY = max(1, template.height / 2)
        var candidates: [Match] = []

        for originY in 0...(haystack.height - template.height) {
            for originX in 0...(haystack.width - template.width) {
                let score = correlation(
                    template: template,
                    templateStatistics: templateStatistics,
                    haystack: haystack,
                    originX: originX,
                    originY: originY
                )
                let match = Match(origin: CGPoint(x: originX, y: originY), score: score)

                if let index = candidates.firstIndex(where: {
                    abs(Int($0.origin.x) - originX) < suppressionX
                        && abs(Int($0.origin.y) - originY) < suppressionY
                }) {
                    if score > candidates[index].score { candidates[index] = match }
                    continue
                }

                candidates.append(match)
                candidates.sort { $0.score > $1.score }
                if candidates.count > limit { candidates.removeLast() }
            }
        }

        return candidates
    }

    private struct Region {
        var minX: Int
        var minY: Int
        var maxX: Int
        var maxY: Int
    }

    private static func scan(
        _ template: GrayImage,
        in haystack: GrayImage,
        region: Region?
    ) -> Match? {
        let templateStatistics = statistics(of: template.pixels)
        guard templateStatistics.deviation > 0 else {
            // A flat single-colour template has nothing to match; correlation is undefined.
            return nil
        }

        let bounds = region ?? Region(
            minX: 0,
            minY: 0,
            maxX: haystack.width - template.width,
            maxY: haystack.height - template.height
        )
        guard bounds.maxX >= bounds.minX, bounds.maxY >= bounds.minY else { return nil }

        var best: Match?
        for originY in bounds.minY...bounds.maxY {
            for originX in bounds.minX...bounds.maxX {
                let score = correlation(
                    template: template,
                    templateStatistics: templateStatistics,
                    haystack: haystack,
                    originX: originX,
                    originY: originY
                )
                // RG-10: on a tie keep the spot found first — topmost, leftmost — so the result is
                // deterministic across runs.
                if score > (best?.score ?? -.infinity) {
                    best = Match(origin: CGPoint(x: originX, y: originY), score: score)
                }
            }
        }
        return best
    }

    private struct Statistics {
        var mean: Double
        var deviation: Double
    }

    static func standardDeviation(_ pixels: [Float]) -> Float {
        Float(statistics(of: pixels).deviation / Double(max(1, pixels.count)).squareRoot())
    }

    private static func statistics(of pixels: [Float]) -> Statistics {
        let count = Double(pixels.count)
        let mean = pixels.reduce(0.0) { $0 + Double($1) } / count
        let variance = pixels.reduce(0.0) { $0 + (Double($1) - mean) * (Double($1) - mean) }
        return Statistics(mean: mean, deviation: variance.squareRoot())
    }

    /// Normalised cross-correlation at one position.
    ///
    /// Accumulating in `Double` rather than `Float` is **not** micro-tuning.
    /// The form `Σh² − n·h̄²` suffers **catastrophic cancellation**: two large nearly equal numbers are
    /// subtracted and the small remaining difference loses most of its significant digits. In `Float`
    /// (23 bits of mantissa) with a template of some 15,000 pixels the error reaches ~2·10⁻³ — enough for
    /// a score to exceed 1.0, which is mathematically impossible.
    ///
    /// Why it matters: two nearly identical buttons — an everyday thing in games — really differ by about
    /// 4·10⁻⁵. The Float noise is 40 times that gap, so the matcher **picks at random** between right and
    /// wrong. Measured while running `I2` of the manual tests: the template of tile A matched tile B.
    private static func correlation(
        template: GrayImage,
        templateStatistics: Statistics,
        haystack: GrayImage,
        originX: Int,
        originY: Int
    ) -> Double {
        var windowTotal = 0.0
        var windowSquareTotal = 0.0
        var crossTotal = 0.0

        for y in 0..<template.height {
            let haystackRow = (originY + y) * haystack.width + originX
            let templateRow = y * template.width
            for x in 0..<template.width {
                let haystackValue = Double(haystack.pixels[haystackRow + x])
                windowTotal += haystackValue
                windowSquareTotal += haystackValue * haystackValue
                crossTotal += haystackValue * Double(template.pixels[templateRow + x])
            }
        }

        let count = Double(template.width * template.height)
        let windowMean = windowTotal / count
        let windowVariance = windowSquareTotal - count * windowMean * windowMean
        guard windowVariance > 0 else { return 0 }

        let numerator = crossTotal - count * windowMean * templateStatistics.mean
        let denominator = windowVariance.squareRoot() * templateStatistics.deviation
        guard denominator > 0 else { return 0 }

        return numerator / denominator
    }
}
