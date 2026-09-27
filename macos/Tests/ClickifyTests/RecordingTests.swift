import CoreGraphics
import Foundation
import Testing
@testable import Clickify

private func down(_ x: Double, _ y: Double, at time: TimeInterval, button: MouseButton = .left, pid: pid_t? = 1, frame: CGRect? = nil) -> RecordedEvent {
    RecordedEvent(kind: .mouseDown(button), location: CGPoint(x: x, y: y), timestamp: time, processIdentifier: pid, windowFrame: frame)
}

private func up(_ x: Double, _ y: Double, at time: TimeInterval, button: MouseButton = .left, pid: pid_t? = 1, frame: CGRect? = nil) -> RecordedEvent {
    RecordedEvent(kind: .mouseUp(button), location: CGPoint(x: x, y: y), timestamp: time, processIdentifier: pid, windowFrame: frame)
}

private func dragged(_ x: Double, _ y: Double, at time: TimeInterval, pid: pid_t? = 1) -> RecordedEvent {
    RecordedEvent(kind: .mouseDragged(.left), location: CGPoint(x: x, y: y), timestamp: time, processIdentifier: pid, windowFrame: nil)
}

private func scroll(_ dy: Int, at time: TimeInterval, pid: pid_t? = 1) -> RecordedEvent {
    RecordedEvent(kind: .scroll(deltaX: 0, deltaY: dy), location: CGPoint(x: 50, y: 50), timestamp: time, processIdentifier: pid, windowFrame: nil)
}

// MARK: - Inferring Actions

/// RC-5: a quick press and release in place is an ordinary click.
@Test func aQuickPressBecomesAPlainClick() {
    let steps = RecordingInterpreter.steps(from: [down(100, 200, at: 0), up(100, 200, at: 0.08)])

    #expect(steps.count == 1)
    #expect(steps[0].action == .click(button: .left, count: 1, holdMilliseconds: 0))
    #expect(steps[0].location == CGPoint(x: 100, y: 200))
}

/// RC-6: held longer than the threshold is a long press, and the real hold time is recorded.
@Test func aSlowPressBecomesALongPressCarryingItsRealDuration() {
    let steps = RecordingInterpreter.steps(from: [down(100, 200, at: 0), up(100, 200, at: 0.7)])

    #expect(steps[0].action == .click(button: .left, count: 1, holdMilliseconds: 700))
}

/// RC-7: two clicks close together fold into a double click rather than two separate steps.
@Test func twoQuickClicksMergeIntoADoubleClick() {
    let steps = RecordingInterpreter.steps(
        from: [
            down(100, 200, at: 0), up(100, 200, at: 0.08),
            down(101, 201, at: 0.19), up(101, 201, at: 0.26)
        ],
        doubleClickInterval: 0.5
    )

    #expect(steps.count == 1)
    #expect(steps[0].action == .click(button: .left, count: 2, holdMilliseconds: 0))
}

@Test func clicksTooFarApartInTimeStaySeparate() {
    let steps = RecordingInterpreter.steps(
        from: [
            down(100, 200, at: 0), up(100, 200, at: 0.08),
            down(100, 200, at: 2.0), up(100, 200, at: 2.08)
        ],
        doubleClickInterval: 0.5
    )

    #expect(steps.count == 2)
}

@Test func clicksTooFarApartInSpaceStaySeparate() {
    let steps = RecordingInterpreter.steps(
        from: [
            down(100, 200, at: 0), up(100, 200, at: 0.08),
            down(400, 200, at: 0.19), up(400, 200, at: 0.26)
        ],
        doubleClickInterval: 0.5
    )

    #expect(steps.count == 2)
}

/// RC-8: moving beyond the threshold makes it a drag, and only the start and end points are kept.
@Test func movingWhileHeldDownBecomesADrag() {
    let steps = RecordingInterpreter.steps(
        from: [
            down(100, 100, at: 0),
            dragged(150, 100, at: 0.05),
            dragged(250, 100, at: 0.10),
            up(300, 100, at: 0.15)
        ]
    )

    #expect(steps.count == 1)
    #expect(
        steps[0].action == .drag(button: .left, destination: .screenPoint(x: 300, y: 100))
    )
    #expect(steps[0].location == CGPoint(x: 100, y: 100))
}

/// RC-9: one trackpad scroll emits about 100 events. Without folding, the recording becomes 100 Steps.
@Test func aBurstOfScrollEventsBecomesOneStep() {
    var events: [RecordedEvent] = []
    for index in 0..<100 {
        events.append(scroll(-3, at: Double(index) * 0.01))
    }

    let steps = RecordingInterpreter.steps(from: events)

    #expect(steps.count == 1)
    #expect(steps[0].action == .scroll(deltaX: 0, deltaY: -300))
}

@Test func scrollBurstsSeparatedByAPauseStaySeparate() {
    let steps = RecordingInterpreter.steps(
        from: [scroll(-3, at: 0), scroll(-3, at: 0.01), scroll(-3, at: 5.0)]
    )

    #expect(steps.count == 2)
    #expect(steps[0].action == .scroll(deltaX: 0, deltaY: -6))
    #expect(steps[1].action == .scroll(deltaX: 0, deltaY: -3))
}

// MARK: - Timing

/// RC-10 / ADR-0004: the delay keeps its real duration, even a very long one. Capping would guess wrong at
/// exactly the moment that matters — "wait for the page to load" and "go make coffee" look identical.
@Test func gapsKeepTheirRealDurationEvenWhenLong() {
    let steps = RecordingInterpreter.steps(
        from: [
            down(1, 1, at: 0), up(1, 1, at: 0.05),
            down(2, 2, at: 8.05), up(2, 2, at: 8.10)
        ]
    )

    #expect(steps[0].delayMillisecondsAfter == 8000)
}

/// RC-11: the last Step has no dangling trailing delay.
@Test func theLastStepHasNoTrailingDelay() {
    let steps = RecordingInterpreter.steps(from: [down(1, 1, at: 0), up(1, 1, at: 0.05)])

    #expect(steps[0].delayMillisecondsAfter == 0)
}

@Test func anUnmatchedPressIsDroppedRatherThanGuessed() {
    let steps = RecordingInterpreter.steps(from: [down(1, 1, at: 0)])

    #expect(steps.isEmpty)
}

// MARK: - Building the Scenario

/// RC-13: a session lying inside one application locks it automatically and raises Targets to window-relative.
@Test func aSingleApplicationRecordingIsUpgradedToWindowRelativeTargets() {
    let frame = CGRect(x: 700, y: 300, width: 800, height: 600)
    let recorded = RecordingInterpreter.steps(
        from: [down(820, 388, at: 0, pid: 42, frame: frame), up(820, 388, at: 0.05, pid: 42, frame: frame)]
    )

    let result = RecordingAssembler.scenario(
        named: "Chrome",
        from: recorded,
        lockedApplication: LockedApplication(bundleIdentifier: "com.google.Chrome", name: "Chrome")
    )

    #expect(result.scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 120, dy: 88))
    #expect(result.scenario.lockedApplication?.bundleIdentifier == "com.google.Chrome")
    #expect(result.warning == nil)
}

/// RC-14: spanning several applications keeps absolute coordinates and says so plainly, rather than letting the
/// user find out when the scenario fires wide.
@Test func aMultiApplicationRecordingStaysAbsoluteAndWarns() {
    let recorded = RecordingInterpreter.steps(
        from: [
            down(10, 10, at: 0, pid: 1), up(10, 10, at: 0.05, pid: 1),
            down(20, 20, at: 1, pid: 2), up(20, 20, at: 1.05, pid: 2)
        ]
    )

    #expect(RecordingAssembler.singleProcessIdentifier(in: recorded) == nil)

    let result = RecordingAssembler.scenario(named: "Mixed", from: recorded, lockedApplication: nil)

    #expect(result.scenario.steps.allSatisfy { !$0.target.needsAnchorWindow })
    #expect(result.scenario.lockedApplication == nil)
    #expect(result.warning == .spansSeveralApplications(2))
}

@Test func aDragDestinationIsUpgradedTogetherWithItsStart() {
    let frame = CGRect(x: 0, y: 0, width: 1000, height: 1000)
    let recorded = RecordingInterpreter.steps(
        from: [
            down(100, 100, at: 0, pid: 7, frame: frame),
            dragged(500, 500, at: 0.05, pid: 7),
            up(900, 900, at: 0.1, pid: 7, frame: frame)
        ]
    )

    let result = RecordingAssembler.scenario(
        named: "Drag",
        from: recorded,
        lockedApplication: LockedApplication(bundleIdentifier: "x", name: "X")
    )

    guard case let .drag(_, destination) = result.scenario.steps[0].action else {
        Issue.record("The Step is not a drag")
        return
    }
    #expect(destination == .windowRelative(corner: .bottomRight, dx: -100, dy: -100))
    #expect(result.scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 100, dy: 100))
}

/// RC-15: a recording session never invents a Template Target on its own.
@Test func recordingNeverInventsImageTargets() {
    let recorded = RecordingInterpreter.steps(from: [down(1, 1, at: 0), up(1, 1, at: 0.05)])
    let result = RecordingAssembler.scenario(named: "t", from: recorded, lockedApplication: nil)

    #expect(result.scenario.steps.allSatisfy { $0.target == .screenPoint(x: 1, y: 1) })
}

/// A recorded Step repeats exactly once — the user adjusts it afterwards.
@Test func recordedStepsRepeatExactlyOnce() {
    let recorded = RecordingInterpreter.steps(
        from: [down(1, 1, at: 0), up(1, 1, at: 0.05), down(2, 2, at: 1), up(2, 2, at: 1.05)]
    )
    let result = RecordingAssembler.scenario(named: "t", from: recorded, lockedApplication: nil)

    #expect(result.scenario.steps.allSatisfy { $0.repeatCount == 1 })
    #expect(result.scenario.runCount == .times(1))
}
