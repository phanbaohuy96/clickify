import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// SF-1 / EX-14: this is the one behaviour in Clickify that can lock the user's machine — stopping while a
/// button is held without releasing it makes the operating system believe the mouse button is still down.
@MainActor
@Test func stoppingDuringAHoldStillReleasesTheMouseButton() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Long press",
        steps: [
            Step(
                action: .click(button: .left, count: 1, holdMilliseconds: 30_000),
                target: .screenPoint(x: 100, y: 200),
                delayMillisecondsAfter: 0
            )
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { recorder.types.contains(.leftMouseDown) })
    #expect(!recorder.types.contains(.leftMouseUp))

    runner.stop()

    #expect(recorder.types.contains(.leftMouseUp))
    #expect(recorder.records.last?.location == CGPoint(x: 100, y: 200))
    #expect(!runner.isRunning)
}

/// EX-16: without `mouseEventClickState` AppKit treats it as two separate clicks, not a double click.
@MainActor
@Test func doubleClickNumbersItsClickState() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Double",
        steps: [
            Step(
                action: .click(button: .left, count: 2, holdMilliseconds: 0),
                target: .screenPoint(x: 5, y: 6),
                delayMillisecondsAfter: 0
            )
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { recorder.records.count >= 4 })

    #expect(recorder.types == [.leftMouseDown, .leftMouseUp, .leftMouseDown, .leftMouseUp])
    #expect(recorder.records.map(\.clickState) == [1, 1, 2, 2])
}

/// EX-4: two nested counters — the Scenario loop wrapping the Step loop.
@MainActor
@Test func stepsRunInOrderInsideEachScenarioIteration() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Nested",
        steps: [
            Step(action: .move, target: .screenPoint(x: 1, y: 1), delayMillisecondsAfter: 0),
            Step(
                action: .move,
                target: .screenPoint(x: 2, y: 2),
                repeatCount: 2,
                delayMillisecondsAfter: 0
            )
        ],
        runCount: .times(2)
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.map(\.location.x) == [1, 2, 2, 1, 2, 2])
}

/// DM-19 / EX-6: an at-cursor Target is resolved again on every repetition.
@MainActor
@Test func cursorTargetIsResolvedForEveryRepeat() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Cursor",
        steps: [
            Step(action: .move, target: .cursor, repeatCount: 3, delayMillisecondsAfter: 0)
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.count == 3)
    #expect(recorder.records.allSatisfy { $0.location == CGPoint(x: 7, y: 8) })
}

@MainActor
@Test func anEmptyScenarioIsRefusedBeforeAnyEventIsPosted() {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    #expect(runner.validate(Scenario(name: "Empty")) == .emptyScenario)
    #expect(!runner.start(Scenario(name: "Empty")))
    #expect(recorder.records.isEmpty)
    #expect(!runner.isRunning)
}

/// EX-3: cancelling during the countdown emits no event at all.
@MainActor
@Test func stoppingDuringTheCountdownPostsNothing() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder, countdownSeconds: 3)
    let scenario = Scenario(
        name: "Countdown",
        steps: [Step(action: .move, target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { runner.countdown != nil })
    runner.stop()
    try await Task.sleep(for: .milliseconds(50))

    #expect(recorder.records.isEmpty)
}

/// SF-9: an unlimited Scenario must still check for cancellation at every Step, so that ⌥⌘S takes effect.
@MainActor
@Test func anUnlimitedScenarioStopsWithinOneStep() async throws {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Unlimited",
        steps: [Step(action: .move, target: .cursor, delayMillisecondsAfter: 0)],
        runCount: .untilStopped
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { recorder.records.count > 3 })
    #expect(runner.isRunning)

    runner.stop()
    let countAtStop = recorder.records.count
    try await Task.sleep(for: .milliseconds(80))

    #expect(!runner.isRunning)
    #expect(recorder.records.count == countAtStop)
}
