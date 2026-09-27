import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// Builds a deterministically noisy background image with one patch pasted at exactly one spot.
private func haystack(
    width: Int,
    height: Int,
    patch: GrayImage,
    at origin: (x: Int, y: Int)
) -> GrayImage {
    var pixels = [Float](repeating: 0, count: width * height)
    for index in pixels.indices {
        // Deterministic noise, not random, so the test can never flake.
        pixels[index] = Float((index * 37) % 100) / 400
    }
    for y in 0..<patch.height {
        for x in 0..<patch.width {
            pixels[(origin.y + y) * width + origin.x + x] = patch[x, y]
        }
    }
    return GrayImage(width: width, height: height, pixels: pixels)
}

private func checkerboard(width: Int, height: Int) -> GrayImage {
    var pixels = [Float](repeating: 0, count: width * height)
    for y in 0..<height {
        for x in 0..<width {
            pixels[y * width + x] = (x / 3 + y / 3) % 2 == 0 ? 0.95 : 0.05
        }
    }
    return GrayImage(width: width, height: height, pixels: pixels)
}

@Test func aTemplateIsFoundAtItsExactLocation() throws {
    let patch = checkerboard(width: 24, height: 18)
    let image = haystack(width: 300, height: 200, patch: patch, at: (x: 137, y: 88))

    let match = try #require(TemplateMatcher.bestMatch(of: patch, in: image))

    #expect(match.origin == CGPoint(x: 137, y: 88))
    #expect(match.score > 0.99)
}

/// RG-8: the coarse-to-fine path must give the same result as an exhaustive scan, otherwise the optimisation
/// has silently changed the behaviour.
@Test func theCoarseToFinePathAgreesWithAnExhaustiveScan() throws {
    let patch = checkerboard(width: 32, height: 32)
    let image = haystack(width: 400, height: 300, patch: patch, at: (x: 201, y: 154))

    #expect(TemplateMatcher.downsampleFactor(for: patch, in: image) > 1)
    let match = try #require(TemplateMatcher.bestMatch(of: patch, in: image))

    #expect(match.origin == CGPoint(x: 201, y: 154))
}

/// A small template must not be downsampled until it shrinks to a meaningless handful of pixels.
@Test func aTinyTemplateIsNotDownsampledAway() {
    let tiny = checkerboard(width: 6, height: 6)
    let image = checkerboard(width: 200, height: 200)

    #expect(TemplateMatcher.downsampleFactor(for: tiny, in: image) == 1)
}

/// The opposing constraint: a fine-grained template on a large image still has to stay within the cost ceiling,
/// even when that means losing contrast (RG-18). Without the ceiling one Step would hang for seconds.
@Test func aFineGrainedTemplateOnALargeImageStaysWithinTheScanBudget() {
    let patch = checkerboard(width: 120, height: 48)
    let screen = checkerboard(width: 3024, height: 1964)

    let factor = TemplateMatcher.downsampleFactor(for: patch, in: screen)

    #expect(factor > 1)
    let positions = (3024 / factor - 120 / factor + 1) * (1964 / factor - 48 / factor + 1)
    let cost = positions * (120 / factor) * (48 / factor)
    #expect(cost <= TemplateMatcher.coarseScanBudget)
}

@Test func aTemplateLargerThanTheImageHasNoMatch() {
    let patch = checkerboard(width: 40, height: 40)
    let image = checkerboard(width: 20, height: 20)

    #expect(TemplateMatcher.bestMatch(of: patch, in: image) == nil)
}

/// A flat single-colour template has nothing to match — normalised correlation is undefined there.
/// Return `nil` rather than a made-up number.
@Test func aFlatTemplateHasNoMatch() {
    let flat = GrayImage(width: 10, height: 10, pixels: [Float](repeating: 0.5, count: 100))
    let image = checkerboard(width: 100, height: 100)

    #expect(TemplateMatcher.bestMatch(of: flat, in: image) == nil)
}

/// An image that does not contain the template still returns a best position, but the score has to be well below
/// the default threshold — that is how the caller tells "seen" from "not seen".
@Test func anAbsentTemplateScoresWellBelowTheDefaultThreshold() throws {
    let patch = checkerboard(width: 24, height: 24)
    var pixels = [Float](repeating: 0, count: 300 * 200)
    for index in pixels.indices {
        pixels[index] = Float((index * 37) % 100) / 400
    }
    let image = GrayImage(width: 300, height: 200, pixels: pixels)

    let match = try #require(TemplateMatcher.bestMatch(of: patch, in: image))

    #expect(match.score < 0.9)
}

/// Matching is correlation-based, so it is unchanged when the whole region gets uniformly brighter or darker —
/// exactly what is needed when screen brightness changes.
@Test func matchingSurvivesAUniformBrightnessShift() throws {
    let patch = checkerboard(width: 20, height: 20)
    var brighter = patch
    brighter.pixels = patch.pixels.map { min(1, $0 * 0.6 + 0.3) }
    let image = haystack(width: 200, height: 160, patch: brighter, at: (x: 90, y: 60))

    let match = try #require(TemplateMatcher.bestMatch(of: patch, in: image))

    #expect(match.origin == CGPoint(x: 90, y: 60))
    #expect(match.score > 0.99)
}

@Test func downsamplingAveragesEachBlock() {
    let image = GrayImage(width: 4, height: 2, pixels: [0, 1, 0, 1, 1, 0, 1, 0])

    let smaller = image.downsampled(by: 2)

    #expect(smaller.width == 2)
    #expect(smaller.height == 1)
    #expect(smaller.pixels == [0.5, 0.5])
}

// MARK: - Numerical accuracy

/// A deterministic patch, **bright and low-contrast**.
///
/// These are precisely the conditions under which `Σh² − n·h̄²` loses all its significant digits: two large
/// nearly equal numbers subtracted. Real interfaces are full of such places — bright buttons, flat backgrounds, pale icons.
private func speckle(width: Int, height: Int, seed: UInt64 = 12_345,
                     base: Float = 0.90, spread: Float = 0.02) -> [Float] {
    var state = seed
    return (0..<(width * height)).map { _ in
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return base + Float((state >> 33) % 1000) / 1000 * spread
    }
}

/// Normalised correlation **cannot** exceed 1: that is its definition.
///
/// A score above 1 is the signature of catastrophic cancellation in `Σh² − n·h̄²`. Accumulating in `Float` for a
/// template of some 15,000 pixels produces an error up to ~2·10⁻³ and turns this test red.
@Test func anExactMatchNeverScoresAboveOne() throws {
    // The real case's template size (a 62pt icon on a retina display). The Float error grows with the pixel
    // count, so a small template cannot reproduce the bug. The image frame is kept tight so the scan stays fast.
    let side = 124
    let template = GrayImage(width: side, height: side, pixels: speckle(width: side, height: side))

    var canvas = [Float](repeating: 0.91, count: 260 * 130)
    for y in 0..<side {
        for x in 0..<side {
            canvas[(3 + y) * 260 + 40 + x] = template.pixels[y * side + x]
        }
    }
    let image = GrayImage(width: 260, height: 130, pixels: canvas)

    let match = try #require(TemplateMatcher.bestMatch(of: template, in: image))
    #expect(match.origin == CGPoint(x: 40, y: 3))
    // Loosened by exactly the unavoidable floating-point error (~10⁻¹³), no more. Accumulating in
    // `Float` gives 1.0017 and turns this test red.
    #expect(match.score <= 1.000_000_1)
    #expect(match.score > 0.999_99)
}

/// Two nearly identical targets — an everyday thing in games — must still pick the one that really matches.
///
/// The real difference between the two regions here is about 10⁻⁴. Accumulating in `Float` produces noise an order
/// of magnitude larger, so the matcher picks at random; on a real machine it picked wrong.
@Test func aNearIdenticalNeighbourDoesNotStealTheMatch() throws {
    let side = 124
    let pattern = speckle(width: side, height: side)
    let template = GrayImage(width: side, height: side, pixels: pattern)

    // A near-identical copy: a very small and **uneven** offset, so not the linear transform that normalised
    // correlation ignores by construction.
    var neighbour = pattern
    for i in stride(from: 0, to: neighbour.count, by: 37) {
        neighbour[i] = min(1, neighbour[i] + 0.0008)
    }

    var canvas = [Float](repeating: 0.91, count: 300 * 130)
    for y in 0..<side {
        for x in 0..<side {
            canvas[(3 + y) * 300 + 10 + x] = pattern[y * side + x]         // the real match
            canvas[(3 + y) * 300 + 160 + x] = neighbour[y * side + x]      // the near-identical copy
        }
    }
    let image = GrayImage(width: 300, height: 130, pixels: canvas)

    let match = try #require(TemplateMatcher.bestMatch(of: template, in: image))
    #expect(match.origin == CGPoint(x: 10, y: 3))
}
