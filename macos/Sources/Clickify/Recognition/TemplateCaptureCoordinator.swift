import AppKit
import CoreGraphics
import Foundation

/// Runs the region-drawing overlay, then captures that region into a **Template** (RG-4, RG-5).
@MainActor
final class TemplateCaptureCoordinator {
    /// How long to wait for the window server to actually take the overlay off the screen before capturing.
    ///
    /// RG-5: the overlay paints a translucent black layer over the whole screen. Capturing while it is still up
    /// produces a darkened Template that can never match again at run time. The capture already excludes Auto
    /// Click's own windows (RG-20), but `orderOut` is only a request — this delay is what actually guarantees it.
    static let overlayDismissDelayMilliseconds = 120

    private var selector: ScreenRegionSelector?

    /// The Search region suggested around the area just cropped as a **Template** (`RG-23`).
    ///
    /// The old default was to search the whole screen, and that is a bad default for the primary use case: in
    /// games, what you need to aim at almost always stays where it was cropped, while scanning the whole screen
    /// is both slower and more likely to grab an identical patch somewhere else.
    ///
    /// The padding is **half the Template's longer side** — enough for a target that shifts by about half its own
    /// size — but never below 48 points (a tiny template still needs room to breathe) and never above 160 points
    /// (too much padding is no different from searching the whole screen).
    static func suggestedSearchRect(around rect: CGRect, within bounds: CGRect) -> CGRect {
        let padding = min(160, max(48, max(rect.width, rect.height) / 2))
        return rect.insetBy(dx: -padding, dy: -padding).intersection(bounds)
    }

    func selectRegion(prompt: String) async -> CGRect? {
        await withCheckedContinuation { continuation in
            let selector = ScreenRegionSelector()
            self.selector = selector
            selector.start(prompt: prompt) { rect in
                self.selector = nil
                NSApp.activate(ignoringOtherApps: true)
                continuation.resume(returning: rect)
            }
        }
    }

    /// The result of one capture: the Template file name, and **the area drawn**, to derive the default Search region from.
    struct Capture {
        let templateName: String
        let rect: CGRect
    }

    /// Draws a region, captures it, saves it to the library and returns the file name together with the area drawn.
    func captureTemplate(into library: TemplateLibrary) async throws -> Capture? {
        guard let rect = await selectRegion(
            prompt: localized(.overlayTemplateRegion)
        ) else { return nil }

        try? await Task.sleep(for: .milliseconds(Self.overlayDismissDelayMilliseconds))

        let captured = try await ScreenCapture().capture(within: rect)
        guard let image = captured.first?.image else {
            throw ScreenCaptureError.permissionDenied
        }
        return Capture(templateName: try library.save(image), rect: rect)
    }
}
