import ApplicationServices
import CoreGraphics
import Foundation

/// Anchors a point to the **Anchor window** (DM-13, EX-7).
///
/// The corner it hangs off is chosen automatically when the point is recorded — the corner nearest the point —
/// rather than always the top-left. That way a button in the bottom-right still lands correctly when the user
/// enlarges the window; anchored to the top-left it would drift into the middle of the screen.
///
/// Every coordinate here is in `CGEvent` space: origin at the top-left, measured in points.
/// Accessibility uses that same space, so no conversion is needed.
enum WindowAnchor {
    struct Offset: Equatable, Sendable {
        var corner: WindowCorner
        var dx: Double
        var dy: Double
    }

    /// The coordinates of one corner of a window.
    static func cornerPoint(_ corner: WindowCorner, in frame: CGRect) -> CGPoint {
        switch corner {
        case .topLeft: return CGPoint(x: frame.minX, y: frame.minY)
        case .topRight: return CGPoint(x: frame.maxX, y: frame.minY)
        case .bottomLeft: return CGPoint(x: frame.minX, y: frame.maxY)
        case .bottomRight: return CGPoint(x: frame.maxX, y: frame.maxY)
        }
    }

    /// Picks the corner nearest `point` and returns the offset from that corner.
    static func offset(for point: CGPoint, in frame: CGRect) -> Offset {
        let nearest = WindowCorner.allCases.min { lhs, rhs in
            squaredDistance(from: point, to: cornerPoint(lhs, in: frame))
                < squaredDistance(from: point, to: cornerPoint(rhs, in: frame))
        } ?? .topLeft

        let origin = cornerPoint(nearest, in: frame)
        return Offset(corner: nearest, dx: point.x - origin.x, dy: point.y - origin.y)
    }

    /// Resolves an offset back into real coordinates.
    static func resolve(_ offset: Offset, in frame: CGRect) -> CGPoint {
        let origin = cornerPoint(offset.corner, in: frame)
        return CGPoint(x: origin.x + offset.dx, y: origin.y + offset.dy)
    }

    private static func squaredDistance(from lhs: CGPoint, to rhs: CGPoint) -> Double {
        let dx = lhs.x - rhs.x
        let dy = lhs.y - rhs.y
        return dx * dx + dy * dy
    }

    /// The frame of the frontmost window belonging to the given process.
    ///
    /// Prefers `AXFocusedWindow`; if the application declares no focused window, take the first in the list,
    /// which is the topmost one.
    ///
    /// EX-25: **a window minimised under the Dock is skipped**. Accessibility still reports its old position
    /// and size as though it were on screen, so trusting that would have the Scenario resolve a Target to
    /// coordinates pointing at empty space — or worse, at another application's window.
    static func focusedWindowFrame(ofProcess processIdentifier: pid_t) -> CGRect? {
        frame(ofProcess: processIdentifier, preferringTitle: nil)
    }

    /// As above, but **preferring the window whose title matches** `title`.
    ///
    /// A recording could only remember the application, not the window, so the Scenario used to bind to whichever
    /// window happened to be focused. With a title it picks that exact window. Titles change often, so a title
    /// that no longer matches does **not** refuse: it falls back to the focused window as before.
    static func frame(ofProcess processIdentifier: pid_t, preferringTitle title: String?) -> CGRect? {
        let application = AXUIElementCreateApplication(processIdentifier)
        let preferred = title.flatMap { window(of: application, titled: $0) }
        let focused = copyElement(application, attribute: kAXFocusedWindowAttribute)
        guard let window = preferred
            ?? (focused.flatMap { isMinimised($0) ? nil : $0 })
            ?? firstVisibleWindow(of: application) else { return nil }

        guard let position = copyPoint(window, attribute: kAXPositionAttribute),
              let size = copySize(window, attribute: kAXSizeAttribute) else { return nil }
        return CGRect(origin: position, size: size)
    }

    /// The title of a process's focused window. This is what the recorder stores.
    static func focusedWindowTitle(ofProcess processIdentifier: pid_t) -> String? {
        let application = AXUIElementCreateApplication(processIdentifier)
        let focused = copyElement(application, attribute: kAXFocusedWindowAttribute)
        guard let window = (focused.flatMap { isMinimised($0) ? nil : $0 })
            ?? firstVisibleWindow(of: application) else { return nil }
        return copyString(window, attribute: kAXTitleAttribute)
    }

    /// Whether this process has a window with exactly that title.
    static func hasWindow(ofProcess processIdentifier: pid_t, titled title: String) -> Bool {
        window(of: AXUIElementCreateApplication(processIdentifier), titled: title) != nil
    }

    private static func window(of application: AXUIElement, titled title: String) -> AXUIElement? {
        guard !title.isEmpty else { return nil }
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            application,
            kAXWindowsAttribute as CFString,
            &value
        ) == .success else { return nil }
        return (value as? [AXUIElement])?.first {
            !isMinimised($0) && copyString($0, attribute: kAXTitleAttribute) == title
        }
    }

    private static func copyString(_ element: AXUIElement, attribute: String) -> String? {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, attribute as CFString, &value) == .success
        else { return nil }
        return value as? String
    }

    private static func firstVisibleWindow(of application: AXUIElement) -> AXUIElement? {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            application,
            kAXWindowsAttribute as CFString,
            &value
        ) == .success else { return nil }
        return (value as? [AXUIElement])?.first { !isMinimised($0) }
    }

    private static func isMinimised(_ window: AXUIElement) -> Bool {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            window,
            kAXMinimizedAttribute as CFString,
            &value
        ) == .success else { return false }
        return (value as? Bool) ?? false
    }

    private static func copyElement(_ element: AXUIElement, attribute: String) -> AXUIElement? {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, attribute as CFString, &value) == .success,
              let value else { return nil }
        guard CFGetTypeID(value) == AXUIElementGetTypeID() else { return nil }
        return (value as! AXUIElement)
    }

    private static func copyPoint(_ element: AXUIElement, attribute: String) -> CGPoint? {
        guard let value = copyAXValue(element, attribute: attribute) else { return nil }
        var point = CGPoint.zero
        guard AXValueGetValue(value, .cgPoint, &point) else { return nil }
        return point
    }

    private static func copySize(_ element: AXUIElement, attribute: String) -> CGSize? {
        guard let value = copyAXValue(element, attribute: attribute) else { return nil }
        var size = CGSize.zero
        guard AXValueGetValue(value, .cgSize, &size) else { return nil }
        return size
    }

    private static func copyAXValue(_ element: AXUIElement, attribute: String) -> AXValue? {
        var value: CFTypeRef?
        guard AXUIElementCopyAttributeValue(element, attribute as CFString, &value) == .success,
              let value, CFGetTypeID(value) == AXValueGetTypeID() else { return nil }
        return (value as! AXValue)
    }
}
