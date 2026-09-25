import AppKit
import CoreGraphics
import Foundation
// `@preconcurrency`: ScreenCaptureKit is an Objective-C framework that has not been audited for
// `Sendable`, and `SCShareableContent` is a class it returns across an `await`. Swift 6.0 rejects
// that outright; later toolchains accept it. Without this the package builds on the machine it was
// written on and nowhere else, including CI — which is the one place that has to agree.
@preconcurrency import ScreenCaptureKit

/// One captured piece of the screen, carrying enough information to convert pixel coordinates into `CGEvent` coordinates.
struct CapturedImage: Sendable {
    var image: CGImage
    /// The screen region this image covers, in **points**, origin at the global top-left.
    var frame: CGRect
    /// How many pixels per point the display holding it has.
    var scale: Double

    /// Converts a point inside the image (in **pixels**) into `CGEvent` coordinates (in **points**).
    ///
    /// RG-2: this is the classic source of bugs in this feature. A capture comes back in pixels, `CGEvent` works
    /// in points, and a machine with a retina display and an external display has two different factors inside
    /// the same coordinate space.
    func screenPoint(fromPixel pixel: CGPoint) -> CGPoint {
        CGPoint(x: frame.minX + pixel.x / scale, y: frame.minY + pixel.y / scale)
    }
}

enum ScreenCaptureError: LocalizedError, Equatable {
    case permissionDenied
    case noDisplays
    case unknownDisplayScale

    var errorDescription: String? {
        switch self {
        case .permissionDenied:
            // SF-7: if the user has just enabled the permission and it still fails, it is almost certainly an
            // ad-hoc signed build that macOS treats as a different application after an update. The fix is to
            // restart the app, and that has to be said — the system cannot tell the two cases apart.
            return localized(.errorScreenRecordingDenied)
        case .noDisplays:
            return localized(.errorNoDisplay)
        case .unknownDisplayScale:
            // RG-24: its own case rather than falling through to `permissionDenied`. Reporting the wrong
            // cause is the mistake `B5` of the manual tests exists to catch.
            return localized(.errorPixelScaleUnreadable)
        }
    }
}

/// Captures the screen with ScreenCaptureKit (RG-1, [ADR-0001]).
@MainActor
struct ScreenCapture {
    /// Captures the part of the screen intersecting `rect` (points, origin at the global top-left); `nil` captures everything.
    ///
    /// Returns one image **per display** rather than stitching them together: each display has its own scale
    /// factor, and stitching would lose the information `RG-2` needs.
    func capture(within rect: CGRect? = nil) async throws -> [CapturedImage] {
        let content: SCShareableContent
        do {
            content = try await SCShareableContent.excludingDesktopWindows(
                false,
                onScreenWindowsOnly: true
            )
        } catch {
            throw ScreenCaptureError.permissionDenied
        }

        guard !content.displays.isEmpty else { throw ScreenCaptureError.noDisplays }

        // RG-20: exclude Clickify itself from the capture. The floating panel shown while running sits in the
        // middle-top of the screen and can perfectly well cover the target being looked for.
        let ownApplications = content.applications.filter {
            $0.processID == ProcessInfo.processInfo.processIdentifier
        }

        var results: [CapturedImage] = []
        var skippedForUnknownScale = false
        for display in content.displays {
            let displayFrame = display.frame
            let region = rect.map { displayFrame.intersection($0) } ?? displayFrame
            guard !region.isNull, region.width >= 1, region.height >= 1 else { continue }

            // RG-24: a display whose scale cannot be established is skipped, not guessed at.
            guard let scale = self.scale(of: display) else {
                skippedForUnknownScale = true
                continue
            }
            let configuration = SCStreamConfiguration()
            configuration.sourceRect = CGRect(
                x: region.minX - displayFrame.minX,
                y: region.minY - displayFrame.minY,
                width: region.width,
                height: region.height
            )
            configuration.width = Int((region.width * scale).rounded())
            configuration.height = Int((region.height * scale).rounded())
            configuration.captureResolution = .best
            configuration.showsCursor = false

            let filter = SCContentFilter(
                display: display,
                excludingApplications: ownApplications,
                exceptingWindows: []
            )

            guard let image = try? await SCScreenshotManager.captureImage(
                contentFilter: filter,
                configuration: configuration
            ) else { continue }

            results.append(CapturedImage(image: image, frame: region, scale: scale))
        }

        guard !results.isEmpty else {
            // Empty now has two causes, and they call for opposite actions from the user.
            throw skippedForUnknownScale
                ? ScreenCaptureError.unknownDisplayScale
                : ScreenCaptureError.permissionDenied
        }
        return results
    }

    /// How many pixels per point `display` has, or `nil` when it cannot be established.
    ///
    /// RG-24: this used to end in `?? 2`. On a 1x display that guess halves every coordinate and the click
    /// lands between the display's origin and the target — silently wrong, which is worse than not clicking
    /// at all. `CGDisplayCopyDisplayMode` is the second opinion because it comes from CoreGraphics and does
    /// not depend on `NSScreen` agreeing that the display exists.
    private func scale(of display: SCDisplay) -> Double? {
        let screen = NSScreen.screens.first {
            ($0.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber)?.uint32Value
                == display.displayID
        }
        if let screen { return Double(screen.backingScaleFactor) }

        if let mode = CGDisplayCopyDisplayMode(display.displayID), mode.width > 0 {
            return Double(mode.pixelWidth) / Double(mode.width)
        }
        return nil
    }
}
