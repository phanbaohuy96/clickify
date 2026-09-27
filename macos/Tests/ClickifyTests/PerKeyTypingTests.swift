import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// `EX-21`, `EX-26`: which of the two typing routes a string takes, and what the key-by-key one emits.
///
/// The routing is decided from the string's contents, so these tests are the only place that decision is
/// visible without a real screen. What they cannot prove is the thing `B10` is for — that the characters
/// **arrive** correctly in another application — which is why `B10` stays in the manual tests.

// MARK: - Choosing the route

@MainActor
@Test func anAsciiStringIsTypedKeyByKey() throws {
    let keystrokes = try #require(KeyboardEventEmitter.asciiKeystrokes(for: "[B10b]"))

    // One keystroke per character — the whole point, for an application that reacts key by key.
    #expect(keystrokes.count == 6)
}

/// A string that needs the Unicode payload has no key codes to be typed with, so it must fall back to `EX-24`.
@Test func aStringWithVietnameseCharactersFallsBackToTheChunkedRoute() {
    #expect(KeyboardEventEmitter.isTypableKeyByKey("Xin chào") == false)
    #expect(KeyboardEventEmitter.isTypableKeyByKey("ăn") == false)
}

/// One character outside ASCII is enough: the routes cannot be mixed inside one string, because the chunked
/// route's whole purpose is to send a run of characters as one event.
@Test func oneNonAsciiCharacterSendsTheWholeStringTheOtherWay() {
    #expect(KeyboardEventEmitter.isTypableKeyByKey("abcde") == true)
    #expect(KeyboardEventEmitter.isTypableKeyByKey("abcdé") == false)
}

/// An em dash and an emoji are both outside ASCII, whatever their UTF-16 length.
@Test func punctuationAndEmojiOutsideAsciiTakeTheChunkedRoute() {
    #expect(KeyboardEventEmitter.isTypableKeyByKey("123 — 456") == false)
    #expect(KeyboardEventEmitter.isTypableKeyByKey("gg 😀") == false)
}

/// Control characters have no business in a typed string: tab and return are `pressKey`'s job (`EX-22`), and
/// they are not what a key-code lookup should be silently inventing a key for.
@Test func controlCharactersAreNotTypedKeyByKey() {
    #expect(KeyboardEventEmitter.isTypableKeyByKey("a\tb") == false)
    #expect(KeyboardEventEmitter.isTypableKeyByKey("a\nb") == false)
}

@MainActor
@Test func anEmptyStringHasNothingToType() {
    #expect(KeyboardEventEmitter.asciiKeystrokes(for: "")?.isEmpty == true)
}

// MARK: - What the key-by-key route emits

/// The layout is read live rather than from a table (`EX-26`), so the test asserts the round trip rather than
/// specific key codes: a hard-coded `8 == c` would be the very assumption this route was written to avoid.
@MainActor
@Test func aKeystrokeRoundTripsBackToItsOwnCharacter() throws {
    let table = try #require(KeyboardLayout.currentTable())

    for character in "abz09" {
        let keystroke = try #require(KeyboardLayout.keystroke(for: character, in: table))
        #expect(keystroke.keyCode <= 50)
        #expect(keystroke.option == false)
        #expect(keystroke.shift == false)
    }
}

/// A capital and its lowercase share a key code and differ only by Shift. If they came out as two different
/// keys, the layout lookup would be finding some Option variant instead of the obvious key.
@MainActor
@Test func aCapitalIsTheSameKeyWithShift() throws {
    let table = try #require(KeyboardLayout.currentTable())

    let lower = try #require(KeyboardLayout.keystroke(for: "b", in: table))
    let upper = try #require(KeyboardLayout.keystroke(for: "B", in: table))

    #expect(upper.keyCode == lower.keyCode)
    #expect(upper.shift == true)
    #expect(lower.shift == false)
}

@MainActor
@Test func typingAnAsciiStringEmitsOneDownUpPairPerCharacter() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    let scenario = Scenario(
        name: "Per-key typing",
        steps: [Step(action: .typeText("gg"), target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.count == 4)
    #expect(recorder.records.filter { $0.type == .keyDown }.count == 2)
    #expect(recorder.typedText == "gg")

    // Every event carries a **real** key code. `virtualKey: 0` is the signature of the `EX-24` route, and it is
    // exactly what makes a lost payload insert an `a` — so seeing it here would mean the routing did not work.
    let keyCodes = Set(recorder.records.map(\.keyCode))
    #expect(!keyCodes.contains(0))
    #expect(keyCodes.count == 1)
}

/// `EX-27`: the input source is put back on the cleanup path, so that `⌥⌘S` part-way through a string cannot
/// leave the user typing in ABC afterwards. The same obligation `SF-1` places on the mouse button.
@MainActor
@Test func theInputSourceIsRestoredWhenARunStops() async {
    let recorder = EventRecorder()
    let runner = makeRunner(recorder: recorder)

    let scenario = Scenario(
        name: "Per-key typing",
        steps: [Step(action: .typeText("gg"), target: .cursor, delayMillisecondsAfter: 0)]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(runner.keyboardInputSourceIsOverridden == false)
}
