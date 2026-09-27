import CoreGraphics
import Foundation

/// Goes looking on screen for the **Target**s that need recognition (DM-14, DM-15).
///
/// It is a protocol so the runner can verify the retry semantics of `EX-8`/`EX-9` without a real screen
/// capture.
@MainActor
protocol TargetRecognizing: AnyObject {
    /// Points at the Templates directory of the Scenario about to run.
    func prepare(templatesDirectory: URL?)

    /// The target's `CGEvent` coordinates, or `nil` if this attempt did not find it.
    func locate(_ target: StepTarget, within region: CGRect?) async throws -> CGPoint?
}

@MainActor
final class ScreenTargetRecognizer: TargetRecognizing {
    private let capture = ScreenCapture()
    private var templates: TemplateLibrary?

    func prepare(templatesDirectory: URL?) {
        templates = templatesDirectory.map(TemplateLibrary.init(directory:))
    }

    func locate(_ target: StepTarget, within region: CGRect?) async throws -> CGPoint? {
        switch target {
        case let .template(name, settings):
            return try await locateTemplate(name, settings: settings, within: region)
        case let .text(text, settings):
            return try await locateText(text, settings: settings, within: region)
        case .cursor, .screenPoint, .windowRelative:
            return nil
        }
    }

    private func locateTemplate(
        _ name: String,
        settings: RecognitionSettings,
        within region: CGRect?
    ) async throws -> CGPoint? {
        guard let template = templates?.loadGray(name) else { return nil }

        // RG-3: capture again on every attempt. The point of retrying is to see an interface that has changed.
        // Both passes below share this one capture: they have to be judging the same screen.
        let captured = try await capture.capture(within: region)

        // RG-25: native size first. Cropping and running on the same display — every time on a single-display
        // machine — is answered here and never pays for the second pass.
        if let point = Self.search(template, in: captured, threshold: settings.threshold, crossScale: false) {
            return point
        }
        return Self.search(template, in: captured, threshold: settings.threshold, crossScale: true)
    }

    /// The best point clearing `threshold` across every captured display, or `nil`.
    ///
    /// RG-25: with `crossScale` the Template is treated as having been cropped on a display of the *other*
    /// scale. Both directions are a downsample, never an upsample — scaling a 1x Template up to 2x would
    /// invent detail that was never captured, and score worse than shrinking the image that really has it.
    nonisolated static func search(
        _ template: GrayImage,
        in captured: [CapturedImage],
        threshold: Double,
        crossScale: Bool
    ) -> CGPoint? {
        var best: (point: CGPoint, score: Double)?

        for image in captured {
            guard var haystack = GrayImage(cgImage: image.image) else { continue }
            var needle = template
            // How many native pixels one pixel of the scanned haystack is worth.
            var haystackFactor = 1.0

            if crossScale {
                if image.scale >= 2 {
                    let shrunk = haystack.downsampled(by: 2)
                    guard shrunk.width < haystack.width else { continue }
                    haystack = shrunk
                    haystackFactor = 2
                } else {
                    let shrunk = template.downsampled(by: 2)
                    guard shrunk.width < template.width else { continue }
                    needle = shrunk
                }
            }

            guard let match = TemplateMatcher.bestMatch(of: needle, in: haystack),
                  match.score >= threshold else { continue }

            // RG-11: the Target returned is the centre of the matched area.
            let centre = CGPoint(
                x: match.origin.x + Double(needle.width) / 2,
                y: match.origin.y + Double(needle.height) / 2
            )
            let point = image.screenPoint(
                fromPixel: CGPoint(x: centre.x * haystackFactor, y: centre.y * haystackFactor)
            )
            // RG-10: several places clearing the threshold means the highest score wins.
            if match.score > (best?.score ?? -.infinity) {
                best = (point, match.score)
            }
        }
        return best?.point
    }

    private func locateText(
        _ text: String,
        settings: RecognitionSettings,
        within region: CGRect?
    ) async throws -> CGPoint? {
        var best: (point: CGPoint, confidence: Double)?
        for captured in try await capture.capture(within: region) {
            guard let match = TextFinder.find(text, in: captured.image) else { continue }

            // RG-15: the centre of the matched piece of text's bounding box.
            let point = captured.screenPoint(
                fromPixel: CGPoint(x: match.boundingBox.midX, y: match.boundingBox.midY)
            )
            if match.confidence > (best?.confidence ?? -.infinity) {
                best = (point, match.confidence)
            }
        }
        return best?.point
    }
}
