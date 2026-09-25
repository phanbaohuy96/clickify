import CoreGraphics
import Foundation
import Testing
@testable import Clickify

// MARK: - Window anchoring

/// DM-18: a window-relative Target cannot be resolved without a Locked application, so block it at validation
/// rather than letting it start and fail.
@MainActor
@Test func aWindowRelativeStepWithoutALockedApplicationIsRefused() {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Missing lock",
        steps: [Step(action: .move, target: .windowRelative(corner: .topLeft, dx: 10, dy: 10))]
    )

    #expect(runner.validate(scenario) == .missingLockedApplication)
    #expect(!runner.start(scenario))
    #expect(recorder.records.isEmpty)
}

@MainActor
@Test func aWindowRelativeStepResolvesAgainstTheAnchorWindow() async {
    let recorder = EventRecorder()
    let system = FakeSystem()
    system.anchorWindowFrame = CGRect(x: 700, y: 300, width: 800, height: 600)
    let runner = makeRunner(recorder: recorder, system: system)

    let scenario = Scenario(
        name: "Neo",
        steps: [
            Step(
                action: .move,
                target: .windowRelative(corner: .topLeft, dx: 120, dy: 88),
                delayMillisecondsAfter: 0
            )
        ],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })
    #expect(recorder.records.first?.location == CGPoint(x: 820, y: 388))
}

/// EX-7: when no window can be read, stop — and the message has to name the application.
@MainActor
@Test func aMissingAnchorWindowStopsTheScenarioWithANamedMessage() async {
    let recorder = EventRecorder()
    let system = FakeSystem()
    system.anchorWindowFrame = nil
    let runner = makeRunner(recorder: recorder, system: system)

    let scenario = Scenario(
        name: "No window",
        steps: [Step(action: .move, target: .windowRelative(corner: .topLeft, dx: 1, dy: 1))],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })
    #expect(recorder.records.isEmpty)
    #expect(runner.statusText.contains("Test App"))
}

// MARK: - Drag

/// EX-20: many applications ignore a drag if the cursor jumps straight from start to end.
@MainActor
@Test func draggingPassesThroughIntermediatePoints() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Drag",
        steps: [
            Step(
                action: .drag(button: .left, destination: .screenPoint(x: 300, y: 100)),
                target: .screenPoint(x: 100, y: 100),
                delayMillisecondsAfter: 0
            )
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.types.first == .leftMouseDown)
    #expect(recorder.types.last == .leftMouseUp)
    #expect(
        recorder.types.filter { $0 == .leftMouseDragged }.count
            == ScenarioLimits.dragIntermediateSteps
    )
    #expect(recorder.records.first?.location == CGPoint(x: 100, y: 100))
    #expect(recorder.records.last?.location == CGPoint(x: 300, y: 100))
    // The intermediate points must advance gradually, not leapfrog.
    let xs = recorder.records.map(\.location.x)
    #expect(zip(xs, xs.dropFirst()).allSatisfy { $0 <= $1 })
}

/// SF-1 applies to drag as well: stopping mid-drag still has to release the button.
@MainActor
@Test func stoppingMidDragStillReleasesTheMouseButton() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Long drag",
        steps: [
            Step(
                action: .drag(button: .left, destination: .screenPoint(x: 2000, y: 100)),
                target: .screenPoint(x: 100, y: 100),
                delayMillisecondsAfter: 0
            )
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { recorder.types.contains(.leftMouseDragged) })
    #expect(!recorder.types.contains(.leftMouseUp))

    runner.stop()

    #expect(recorder.types.contains(.leftMouseUp))
}

// MARK: - Keyboard

/// SF-4: keyboard events carry no coordinates, so `EX-10` cannot protect them.
@MainActor
@Test func aKeyboardStepBringsTheLockedApplicationToFrontFirst() async {
    let recorder = EventRecorder()
    let system = FakeSystem()
    system.frontmostProcessIdentifier = 9999  // another application has just stolen focus
    let runner = makeRunner(recorder: recorder, system: system)

    let scenario = Scenario(
        name: "Type",
        steps: [Step(action: .typeText("hi"), target: .cursor, delayMillisecondsAfter: 0)],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(system.frontmostProcessIdentifier == FakeSystem.applicationProcessIdentifier)
    // EX-26: `"hi"` is ASCII, so it goes key by key — one down/up pair per character.
    #expect(recorder.records.count == 4)
    #expect(recorder.typedText == "hi")
}

@MainActor
@Test func aKeyboardStepStopsWhenTheApplicationCannotBeBroughtToFront() async {
    let recorder = EventRecorder()
    let system = FakeSystem()
    system.frontmostProcessIdentifier = 9999
    system.activationSucceeds = false
    let runner = makeRunner(recorder: recorder, system: system)

    let scenario = Scenario(
        name: "Type fails",
        steps: [Step(action: .typeText("hi"), target: .cursor, delayMillisecondsAfter: 0)],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.isEmpty)
    #expect(runner.statusText.contains("Test App"))
}

/// EX-22: a key combination sends the physical key code together with the modifier flags.
@MainActor
@Test func aKeyStrokeSendsItsKeyCodeAndModifierFlags() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Copy",
        steps: [
            Step(
                action: .pressKey(KeyStroke(key: "c", modifiers: [.command])),
                target: .cursor,
                delayMillisecondsAfter: 0
            )
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.count == 2)
    #expect(recorder.records.allSatisfy { $0.keyCode == Int64(KeyCatalog.keyCode(for: "c")!) })
    #expect(recorder.records.allSatisfy { $0.flags.contains(.maskCommand) })
}

@MainActor
@Test func anUnknownKeyNameStopsTheScenario() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)
    let scenario = Scenario(
        name: "Unknown key",
        steps: [Step(action: .pressKey(KeyStroke(key: "khongton")), target: .cursor)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.isEmpty)
    #expect(runner.statusText.contains("khongton"))
}

@Test func keyStrokeModifiersAreNormalisedSoEqualStrokesCompareEqual() {
    let a = KeyStroke(key: "c", modifiers: [.command, .shift])
    let b = KeyStroke(key: "c", modifiers: [.shift, .command, .command])

    #expect(a == b)
    #expect(KeyCatalog.describe(a) == "⌘⇧C")
}


// MARK: - EX-24: typing a string in chunks

/// Proves the string is split in the right places and reassembles without losing a character.
///
/// The character-by-character version was measured on a real machine to type `"Xin chào 123 — ăn"` as
/// `"Aa chào 123 — ăn"`: the Unicode payload is dropped and the system falls back to `virtualKey` (0 = the `a`
/// key). This test pins down the chunking. The string is deliberately **not** ASCII, because an ASCII one would
/// take the `EX-26` route and never reach the code under test here.
@MainActor
@Test func aLongStringIsSentInChunksThatReassembleExactly() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    let text = String(repeating: "àbcde", count: 13)  // 65 UTF-16 units → 4 chunks
    let scenario = Scenario(
        name: "Long typing",
        steps: [Step(action: .typeText(text), target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.typedText == text)
    #expect(recorder.records.count == 8)  // 4 chunks × (down + up)

    // The text rides on key-down only; key-up carries no characters, just like real typing.
    let downs = recorder.records.filter { $0.type == .keyDown }
    #expect(downs.count == 4)
    #expect(downs.allSatisfy { $0.unicodeString.utf16.count <= ScenarioLimits.typingChunkUTF16Units })

    // Key-up reads as "a" — the character of `virtualKey: 0` — because that attribute cannot be removed from
    // the event. Harmless: applications only insert text on key-down. But this is exactly the character that
    // shows up when the Unicode payload is lost, so `typedText` must only be read from key-down.
    #expect(recorder.records.filter { $0.type == .keyUp }.allSatisfy { $0.unicodeString == "a" })
}

/// Splitting by UTF-16 units carelessly would tear an emoji straddling a chunk boundary into two pieces of
/// garbage — a bug that only shows up at exactly position 20.
@MainActor
@Test func aSurrogatePairIsNeverSplitAcrossChunks() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    // 19 lowercase units then an emoji: the chunk boundary falls in the middle of the surrogate pair.
    let text = String(repeating: "x", count: 19) + "😀" + "yz"
    let scenario = Scenario(
        name: "Typing emoji",
        steps: [Step(action: .typeText(text), target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.typedText == text)
    // No chunk may end with the leading half of a surrogate pair.
    #expect(recorder.records.allSatisfy { record in
        guard let last = record.unicodeString.utf16.last else { return true }
        return !UTF16.isLeadSurrogate(last)
    })
}

// MARK: - UI-16: an error state has to look like an error

@MainActor
@Test func aFailedRunMarksItsMessageAsAnError() async {
    let recorder = EventRecorder()
    let system = FakeSystem()
    system.frontmostProcessIdentifier = 9999
    system.activationSucceeds = false
    let runner = makeRunner(recorder: recorder, system: system)

    let scenario = Scenario(
        name: "Type fails",
        steps: [Step(action: .typeText("hi"), target: .cursor, delayMillisecondsAfter: 0)],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })
    #expect(runner.messageIsError)
}

@MainActor
@Test func aRunThatFinishesCleanlyIsNotMarkedAsAnError() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    let scenario = Scenario(
        name: "Typing done",
        steps: [Step(action: .typeText("hi"), target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })
    #expect(!runner.messageIsError)
}
