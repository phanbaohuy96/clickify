import CoreGraphics
import Foundation

/// Emits keyboard events (EX-21, EX-22).
///
/// There is no held-key ledger like `MouseEventEmitter`'s: modifiers are attached straight to each event's flags
/// rather than emitted separately, so cancelling part-way leaves no key stuck down.
@MainActor
final class KeyboardEventEmitter {
    typealias EventSink = @MainActor (CGEvent) -> Void

    private let source = CGEventSource(stateID: .hidSystemState)
    private let sink: EventSink

    /// EX-27. Held here because only this class knows when typing begins; putting it back is the runner's job.
    let inputSource = InputSourceOverride()

    init(sink: @escaping EventSink = KeyboardEventEmitter.postToSystem) {
        self.sink = sink
    }

    static func postToSystem(_ event: CGEvent) {
        event.post(tap: .cghidEventTap)
    }

    /// Types an arbitrary string by whichever of the two routes its contents call for (EX-21).
    ///
    /// An all-ASCII string goes key by key (`EX-26`): it needs no Unicode payload, so it avoids the loss behind
    /// `EX-24` altogether, and the destination sees one key per character — what a game expects. Anything else
    /// goes in chunks (`EX-24`), the only way to send `ằ` or an emoji.
    func type(_ text: String) async throws {
        guard !text.isEmpty else { return }

        if let keystrokes = Self.asciiKeystrokes(for: text) {
            try await typeKeyByKey(keystrokes)
        } else {
            try await typeInChunks(text)
        }
    }

    /// Whether a string's **contents** allow the key-by-key route (EX-26) — printable ASCII throughout.
    ///
    /// Control characters are excluded on purpose: tab and return are `pressKey`'s business (`EX-22`), not
    /// something a key-code lookup should quietly invent a key for.
    ///
    /// Separate from `asciiKeystrokes` because this half is pure. The other half has to ask the system what the
    /// current layout is, and that can only be done on the main thread.
    nonisolated static func isTypableKeyByKey(_ text: String) -> Bool {
        text.allSatisfy { character in
            character.isASCII
                && character.unicodeScalars.allSatisfy { $0.value >= 0x20 && $0.value != 0x7F }
        }
    }

    /// The per-character keystrokes for an all-ASCII string, or `nil` when this string must go by `EX-24`.
    ///
    /// EX-26: `nil` covers both "not ASCII" and "this layout cannot produce that character with Shift and
    /// Option alone". Both mean the same thing to the caller — use the payload route rather than type the wrong
    /// character, which is the failure `EX-24` exists to stop.
    static func asciiKeystrokes(for text: String) -> [KeyboardLayout.Keystroke]? {
        guard isTypableKeyByKey(text) else { return nil }
        guard !text.isEmpty else { return [] }
        guard let table = KeyboardLayout.currentTable() else { return nil }

        var keystrokes: [KeyboardLayout.Keystroke] = []
        keystrokes.reserveCapacity(text.count)
        for character in text {
            guard let keystroke = KeyboardLayout.keystroke(for: character, in: table) else { return nil }
            keystrokes.append(keystroke)
        }
        return keystrokes
    }

    /// EX-26: one key down/up pair per character, carrying the character's real key code.
    ///
    /// EX-27: the input source is switched to ABC first, because these characters pass **through** the active
    /// input method — Telex would fold `"pass"` into `"pá"`. Restoring is the runner's job on the cleanup path,
    /// not this method's: `⌥⌘S` can cut a string part-way through.
    private func typeKeyByKey(_ keystrokes: [KeyboardLayout.Keystroke]) async throws {
        inputSource.engage()

        for (index, keystroke) in keystrokes.enumerated() {
            for isKeyDown in [true, false] {
                guard let event = CGEvent(
                    keyboardEventSource: source,
                    virtualKey: keystroke.keyCode,
                    keyDown: isKeyDown
                ) else { continue }
                event.flags = keystroke.flags
                sink(event)
            }

            if index < keystrokes.count - 1 {
                try await Task.sleep(for: .milliseconds(ScenarioLimits.perKeyGapMilliseconds))
            }
        }
    }

    /// Uses `keyboardSetUnicodeString` rather than looking up key codes, so it can type Vietnamese and emoji
    /// alike — which a keycode mapping cannot.
    ///
    /// EX-24: the string is split into **chunks**, one key pair per chunk — not one pair per character. Measured
    /// on a real machine: the character-by-character version typed `"Xin chào 123 — ăn"` as `"Aa chào 123 — ăn"`,
    /// reproducibly, because the Unicode payload is lost and the system falls back to `virtualKey` (0 = the `a`
    /// key) **with no error at all**. After chunking: 8 of 8 correct, measured the same way. No failure *rate*
    /// is quoted here on purpose — every one previously measured was withdrawn, see [ADR-0007].
    private func typeInChunks(_ text: String) async throws {
        let units = Array(text.utf16)
        guard !units.isEmpty else { return }

        var index = 0
        while index < units.count {
            var end = min(index + ScenarioLimits.typingChunkUTF16Units, units.count)
            // Never cut inside a surrogate pair, otherwise an emoji breaks into two pieces of garbage.
            if end < units.count, end - 1 > index, UTF16.isLeadSurrogate(units[end - 1]) {
                end -= 1
            }
            let chunk = Array(units[index..<end])

            // The text is attached to `keyDown` only; key-up carries no characters, just like real typing.
            if let down = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true) {
                down.keyboardSetUnicodeString(stringLength: chunk.count, unicodeString: chunk)
                sink(down)
            }
            // Key-up carries no text. The character of `virtualKey` cannot be removed from it — it still reads
            // as `a` — but applications only insert text on key-down, so it does no harm. Switching to a key
            // code that produces no character (F13, fn) was measured to type **nothing at all**, so 0 is
            // mandatory rather than a choice.
            if let up = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false) {
                sink(up)
            }

            index = end
            if index < units.count {
                try await Task.sleep(
                    for: .milliseconds(ScenarioLimits.minimumEventGapMilliseconds)
                )
            }
        }
    }

    /// Presses a key combination. Returns `false` if the key name is not recognised.
    @discardableResult
    func press(_ stroke: KeyStroke) -> Bool {
        guard let keyCode = KeyCatalog.keyCode(for: stroke.key) else { return false }
        let flags = stroke.modifiers.eventFlags

        for isKeyDown in [true, false] {
            guard let event = CGEvent(
                keyboardEventSource: source,
                virtualKey: keyCode,
                keyDown: isKeyDown
            ) else { continue }
            event.flags = flags
            sink(event)
        }
        return true
    }
}
