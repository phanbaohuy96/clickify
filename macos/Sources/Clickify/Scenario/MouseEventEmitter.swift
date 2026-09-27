import CoreGraphics
import Foundation

/// Emits mouse events and remembers which buttons are being held.
///
/// The ledger exists because of `SF-1`: the `click` Action has `holdMilliseconds`, and since Slice 2 there is
/// drag — both leave a `mouseDown` with no `mouseUp` if cut off part-way. The operating system then believes the
/// button is held down and the user loses the ability to interact. `releaseAllHeld()` is synchronous, not
/// `async`, so it can be called from a `defer` after the task has been cancelled (`SF-2`).
@MainActor
final class MouseEventEmitter {
    /// Where events go. Separable so tests can verify `SF-1` without really clicking on the machine running
    /// the tests.
    typealias EventSink = @MainActor (CGEvent) -> Void

    private var heldButtons: [MouseButton: CGPoint] = [:]
    private let source = CGEventSource(stateID: .hidSystemState)
    private let sink: EventSink

    init(sink: @escaping EventSink = MouseEventEmitter.postToSystem) {
        self.sink = sink
    }

    /// Post into the system so the destination application handles it as a real operation (EX-19).
    static func postToSystem(_ event: CGEvent) {
        event.post(tap: .cghidEventTap)
    }

    var hasHeldButtons: Bool { !heldButtons.isEmpty }

    func press(_ button: MouseButton, at point: CGPoint, clickState: Int) {
        heldButtons[button] = point
        post(type: button.downEventType, button: button, at: point, clickState: clickState)
    }

    func release(_ button: MouseButton, at point: CGPoint, clickState: Int) {
        heldButtons[button] = nil
        post(type: button.upEventType, button: button, at: point, clickState: clickState)
    }

    func move(to point: CGPoint) {
        post(type: .mouseMoved, button: .left, at: point, clickState: 0)
    }

    /// One leg of a drag. The button must already be held (EX-20).
    func dragMove(_ button: MouseButton, to point: CGPoint) {
        heldButtons[button] = point
        post(type: button.draggedEventType, button: button, at: point, clickState: 0)
    }

    /// Scrolls at `point`. The unit is lines; a positive `deltaY` scrolls up.
    func scroll(deltaX: Int, deltaY: Int, at point: CGPoint) {
        guard let event = CGEvent(
            scrollWheelEvent2Source: source,
            units: .line,
            wheelCount: 2,
            wheel1: Int32(deltaY),
            wheel2: Int32(deltaX),
            wheel3: 0
        ) else { return }

        // A scroll event follows the position recorded in the event itself, so setting the coordinates here is
        // enough; the user's real cursor need not be moved.
        event.location = point
        sink(event)
    }

    /// Releases every button still held (SF-1). Callable after the task has been cancelled (SF-2).
    func releaseAllHeld() {
        let held = heldButtons
        heldButtons.removeAll()
        for (button, point) in held {
            post(type: button.upEventType, button: button, at: point, clickState: 1)
        }
    }

    private func post(
        type: CGEventType,
        button: MouseButton,
        at point: CGPoint,
        clickState: Int
    ) {
        guard let event = CGEvent(
            mouseEventSource: source,
            mouseType: type,
            mouseCursorPosition: point,
            mouseButton: button.cgButton
        ) else { return }

        if clickState > 0 {
            // Without this field AppKit treats n clicks as n separate operations rather than one double click
            // (EX-16).
            event.setIntegerValueField(.mouseEventClickState, value: Int64(clickState))
        }
        sink(event)
    }
}

private extension MouseButton {
    var cgButton: CGMouseButton {
        switch self {
        case .left: return .left
        case .right: return .right
        case .center: return .center
        }
    }

    var downEventType: CGEventType {
        switch self {
        case .left: return .leftMouseDown
        case .right: return .rightMouseDown
        case .center: return .otherMouseDown
        }
    }

    var upEventType: CGEventType {
        switch self {
        case .left: return .leftMouseUp
        case .right: return .rightMouseUp
        case .center: return .otherMouseUp
        }
    }

    var draggedEventType: CGEventType {
        switch self {
        case .left: return .leftMouseDragged
        case .right: return .rightMouseDragged
        case .center: return .otherMouseDragged
        }
    }
}
