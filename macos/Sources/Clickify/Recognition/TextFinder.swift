import CoreGraphics
import Foundation
import Vision

/// Finds a piece of text in an image with Vision OCR (RG-13…RG-16).
///
/// Quite unlike **Template** matching in robustness: switching between light and dark appearance, changing the
/// system font size, or plugging in a display of a different resolution all throw a template off but leave
/// reading text untouched. In exchange, it can only aim at things that **have text** — icons, plain squares and game elements are out of reach.
enum TextFinder {
    struct Match: Equatable, Sendable {
        /// The bounding box of the piece of text, in pixels of the input image, origin at the top-left.
        var boundingBox: CGRect
        var confidence: Double
    }

    static func find(_ needle: String, in image: CGImage) -> Match? {
        let target = normalise(needle)
        guard !target.isEmpty else { return nil }

        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        request.recognitionLanguages = recognitionLanguages

        let handler = VNImageRequestHandler(cgImage: image, options: [:])
        guard (try? handler.perform([request])) != nil,
              let observations = request.results else { return nil }

        let width = Double(image.width)
        let height = Double(image.height)
        var best: Match?

        for observation in observations {
            guard let candidate = observation.topCandidates(1).first,
                  let range = candidate.string.range(of: target, options: .caseInsensitive)
            else { continue }

            // RG-15: the bounding box of the **matched piece of text**, not of the whole line Vision read.
            // Vision folds a whole line into one observation, so taking `observation.boundingBox` clicks the
            // middle of the line: looking for "Lưu" in the line "Lưu   ⌘S" fires into the gap.
            let matchBox = (try? candidate.boundingBox(for: range))??.boundingBox

            // Vision returns normalised coordinates with the origin at the **bottom-left**; images and `CGEvent`
            // use a **top-left** origin, so the vertical axis has to be flipped.
            let box = matchBox ?? observation.boundingBox
            let match = Match(
                boundingBox: CGRect(
                    x: box.minX * width,
                    y: (1 - box.maxY) * height,
                    width: box.width * width,
                    height: box.height * height
                ),
                confidence: Double(candidate.confidence)
            )

            // RG-16: highest confidence wins; on a tie keep the piece found first (RG-10).
            if match.confidence > (best?.confidence ?? -.infinity) {
                best = match
            }
        }

        return best
    }

    /// RG-14: trim leading and trailing whitespace. Case-insensitivity is handled by
    /// `String.range(of:options:.caseInsensitive)`, which also gives the **position** of the matched piece —
    /// something a `contains` comparison on a lowercased string does not.
    static func normalise(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static var recognitionLanguages: [String] {
        var languages = Locale.preferredLanguages
        if !languages.contains(where: { $0.hasPrefix("en") }) {
            languages.append("en-US")
        }
        return languages
    }
}
