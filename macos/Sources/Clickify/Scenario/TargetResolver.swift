import CoreGraphics
import Foundation

enum TargetResolutionError: LocalizedError, Equatable {
    case anchorWindowUnavailable
    /// This Target has to go looking for its target on screen, so it cannot be resolved synchronously.
    case requiresRecognition
}

/// Resolves a Target into real coordinates (EX-6, EX-7).
///
/// Resolution happens just before **each repetition** of a Step, not once for the whole Step — that is what
/// makes `atCursor` with a repeat count above 1 follow the user's hand (DM-19).
///
/// The Anchor window frame is passed in rather than asked of Accessibility here: that keeps resolving a Target
/// a pure transformation, verifiable without a real window.
struct TargetResolver: Sendable {
    let currentCursorPoint: @Sendable () -> CGPoint

    func resolve(_ target: StepTarget, anchorWindowFrame: CGRect?) throws -> CGPoint {
        switch target {
        case .cursor:
            return currentCursorPoint()
        case let .screenPoint(x, y):
            return CGPoint(x: x, y: y)
        case let .windowRelative(corner, dx, dy):
            guard let anchorWindowFrame else {
                throw TargetResolutionError.anchorWindowUnavailable
            }
            return WindowAnchor.resolve(
                WindowAnchor.Offset(corner: corner, dx: dx, dy: dy),
                in: anchorWindowFrame
            )
        case .template, .text:
            throw TargetResolutionError.requiresRecognition
        }
    }

    /// Resolves a **Search region** into a real rectangle.
    ///
    /// [ADR-0006]: a window-relative region that cannot be resolved **falls back to the default scope**
    /// (`RG-7`) rather than reporting an error — a Search region must never force a Scenario to have a
    /// Locked application.
    func resolve(_ region: SearchRegion?, anchorWindowFrame: CGRect?) -> CGRect? {
        switch region {
        case nil:
            return anchorWindowFrame
        case let .screenRect(x, y, width, height):
            return CGRect(x: x, y: y, width: width, height: height)
        case let .windowRelative(corner, dx, dy, width, height):
            guard let anchorWindowFrame else { return nil }
            let origin = WindowAnchor.resolve(
                WindowAnchor.Offset(corner: corner, dx: dx, dy: dy),
                in: anchorWindowFrame
            )
            return CGRect(x: origin.x, y: origin.y, width: width, height: height)
        }
    }
}

extension TargetResolver {
    /// The resolver used at run time: reads the cursor position in `CGEvent` coordinate space
    /// (origin at the top-left of the main screen, measured in points — DM-12).
    static let live = TargetResolver(
        currentCursorPoint: { CGEvent(source: nil)?.location ?? .zero }
    )
}
