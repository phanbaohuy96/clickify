import CoreGraphics
import Foundation

enum RecordingLimits {
    /// Held longer than this is a long press, shorter is an ordinary click (RC-5, RC-6).
    static let longPressThresholdMilliseconds = 400
    /// Movement within this tolerance still counts as standing still — a human hand is never truly motionless.
    static let movementTolerancePoints = 3.0
    /// Two clicks further apart than this are not folded into a double click (RC-7).
    static let doubleClickTolerancePoints = 5.0
    /// Two scroll events less than this apart belong to the same burst (RC-9).
    static let scrollCoalesceGapMilliseconds = 150
}

/// One raw mouse event taken from `CGEventTap`.
struct RecordedEvent: Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        case mouseDown(MouseButton)
        case mouseUp(MouseButton)
        case mouseDragged(MouseButton)
        case scroll(deltaX: Int, deltaY: Int)
    }

    var kind: Kind
    var location: CGPoint
    var timestamp: TimeInterval
    var processIdentifier: pid_t?
    var windowFrame: CGRect?
    /// The window title at the moment of **this gesture**, so replay knows which window to aim at.
    var windowTitle: String?
}

/// A Step that has been inferred but is still in absolute coordinates.
///
/// Raising it to a **Target** relative to the **Anchor window** (RC-13) happens later, in `ScenarioRecorder`,
/// because that needs to know whether the whole session lies inside one application or several.
struct RecordedStep: Equatable, Sendable {
    var action: StepAction
    var location: CGPoint
    var endLocation: CGPoint?
    var delayMillisecondsAfter: Int
    var processIdentifier: pid_t?
    var windowFrame: CGRect?
    /// The window title at the moment of **this gesture**, so replay knows which window to aim at.
    var windowTitle: String?
}

/// Turns a sequence of raw events into high-level **Action**s (RC-5…RC-11).
///
/// A pure function: it touches no `CGEventTap` and no system clock. All the rules for recognising a double
/// click / long press / drag live here and can be verified against prebuilt data.
enum RecordingInterpreter {
    static func steps(
        from events: [RecordedEvent],
        doubleClickInterval: TimeInterval = 0.5
    ) -> [RecordedStep] {
        let merged = merge(gestures(from: events), doubleClickInterval: doubleClickInterval)
        return withDelays(merged)
    }

    // MARK: - Grouping events into gestures

    private struct Gesture {
        var action: StepAction
        var location: CGPoint
        var endLocation: CGPoint?
        var startTime: TimeInterval
        var endTime: TimeInterval
        var processIdentifier: pid_t?
        var windowFrame: CGRect?
        var windowTitle: String?
        /// Only present on a short click gesture; used to fold into a double click.
        var clickButton: MouseButton?
    }

    private static func gestures(from events: [RecordedEvent]) -> [Gesture] {
        var result: [Gesture] = []
        var index = 0

        while index < events.count {
            switch events[index].kind {
            case let .mouseDown(button):
                guard let upIndex = indexOfMouseUp(for: button, in: events, after: index) else {
                    // A press with no release — the recording session ended part-way. Skip it.
                    index += 1
                    continue
                }
                result.append(pressGesture(events, downIndex: index, upIndex: upIndex, button: button))
                index = upIndex + 1

            case .scroll:
                let (gesture, nextIndex) = scrollGesture(events, from: index)
                result.append(gesture)
                index = nextIndex

            case .mouseUp, .mouseDragged:
                // An orphan release or drag: recording started while the user was already holding the button.
                index += 1
            }
        }

        return result
    }

    private static func indexOfMouseUp(
        for button: MouseButton,
        in events: [RecordedEvent],
        after downIndex: Int
    ) -> Int? {
        events[(downIndex + 1)...].firstIndex { $0.kind == .mouseUp(button) }
    }

    private static func pressGesture(
        _ events: [RecordedEvent],
        downIndex: Int,
        upIndex: Int,
        button: MouseButton
    ) -> Gesture {
        let down = events[downIndex]
        let up = events[upIndex]
        let holdMilliseconds = milliseconds(from: down.timestamp, to: up.timestamp)
        let travelled = distance(down.location, up.location) > RecordingLimits.movementTolerancePoints

        if travelled {
            // RC-8: keep only the start and end points; the path is reconstructed at run time (EX-20).
            return Gesture(
                action: .drag(
                    button: button,
                    destination: .screenPoint(x: up.location.x, y: up.location.y)
                ),
                location: down.location,
                endLocation: up.location,
                startTime: down.timestamp,
                endTime: up.timestamp,
                processIdentifier: down.processIdentifier,
                windowFrame: down.windowFrame,
                windowTitle: down.windowTitle
            )
        }

        let isLongPress = holdMilliseconds > RecordingLimits.longPressThresholdMilliseconds
        return Gesture(
            action: .click(
                button: button,
                count: 1,
                holdMilliseconds: isLongPress ? holdMilliseconds : 0
            ),
            location: down.location,
            endLocation: nil,
            startTime: down.timestamp,
            endTime: up.timestamp,
            processIdentifier: down.processIdentifier,
            windowFrame: down.windowFrame,
            windowTitle: down.windowTitle,
            clickButton: isLongPress ? nil : button
        )
    }

    /// RC-9: one trackpad scroll emits about 100 events. Without folding, the recording becomes 100 Steps.
    private static func scrollGesture(
        _ events: [RecordedEvent],
        from startIndex: Int
    ) -> (Gesture, Int) {
        var totalX = 0
        var totalY = 0
        var index = startIndex
        var lastTimestamp = events[startIndex].timestamp

        while index < events.count,
              case let .scroll(deltaX, deltaY) = events[index].kind,
              milliseconds(from: lastTimestamp, to: events[index].timestamp)
                <= RecordingLimits.scrollCoalesceGapMilliseconds {
            totalX += deltaX
            totalY += deltaY
            lastTimestamp = events[index].timestamp
            index += 1
        }

        let first = events[startIndex]
        return (
            Gesture(
                action: .scroll(deltaX: totalX, deltaY: totalY),
                location: first.location,
                endLocation: nil,
                startTime: first.timestamp,
                endTime: lastTimestamp,
                processIdentifier: first.processIdentifier,
                windowFrame: first.windowFrame,
                windowTitle: first.windowTitle
            ),
            index
        )
    }

    // MARK: - Folding double clicks

    private static func merge(
        _ gestures: [Gesture],
        doubleClickInterval: TimeInterval
    ) -> [Gesture] {
        var result: [Gesture] = []

        for gesture in gestures {
            guard let button = gesture.clickButton,
                  var previous = result.last,
                  previous.clickButton == button,
                  case let .click(_, count, _) = previous.action,
                  gesture.startTime - previous.endTime < doubleClickInterval,
                  distance(previous.location, gesture.location)
                    <= RecordingLimits.doubleClickTolerancePoints else {
                result.append(gesture)
                continue
            }

            previous.action = .click(button: button, count: count + 1, holdMilliseconds: 0)
            previous.endTime = gesture.endTime
            result[result.count - 1] = previous
        }

        return result
    }

    // MARK: - Timing

    /// RC-10: the delay is the real gap between two gestures, uncapped and unrounded
    /// (ADR-0004). RC-11: the last Step has a delay of 0.
    private static func withDelays(_ gestures: [Gesture]) -> [RecordedStep] {
        gestures.enumerated().map { index, gesture in
            let delay = index + 1 < gestures.count
                ? milliseconds(from: gesture.endTime, to: gestures[index + 1].startTime)
                : 0

            return RecordedStep(
                action: gesture.action,
                location: gesture.location,
                endLocation: gesture.endLocation,
                delayMillisecondsAfter: delay.clamped(to: ScenarioLimits.delayMilliseconds),
                processIdentifier: gesture.processIdentifier,
                windowFrame: gesture.windowFrame,
                windowTitle: gesture.windowTitle
            )
        }
    }

    private static func milliseconds(from start: TimeInterval, to end: TimeInterval) -> Int {
        max(0, Int(((end - start) * 1000).rounded()))
    }

    private static func distance(_ lhs: CGPoint, _ rhs: CGPoint) -> Double {
        ((lhs.x - rhs.x) * (lhs.x - rhs.x) + (lhs.y - rhs.y) * (lhs.y - rhs.y)).squareRoot()
    }
}
