import Carbon.HIToolbox
import CoreGraphics
import Foundation

/// Maps a character to the key code that produces it **on the keyboard layout in use right now** (EX-26).
///
/// Resolved live rather than from a table because `KeyCatalog` promises that typing "does not depend on the
/// keyboard layout": key code 8 is `c` on QWERTY and something else entirely on AZERTY or Dvorak. A hard-coded
/// table would be that dependency, reintroduced.
///
/// The map is built by asking each key code what it produces — the inverse of the question, because that is the
/// direction `UCKeyTranslate` answers.
enum KeyboardLayout {
    struct Keystroke: Equatable, Sendable {
        var keyCode: CGKeyCode
        var shift: Bool
        var option: Bool

        var flags: CGEventFlags {
            var flags: CGEventFlags = []
            if shift { flags.insert(.maskShift) }
            if option { flags.insert(.maskAlternate) }
            return flags
        }
    }

    /// Key codes above this are the keypad, arrows and function keys — never text.
    private static let highestTextKeyCode: CGKeyCode = 50

    /// The modifier combinations worth trying, in the order a typist would reach for them.
    ///
    /// Control is deliberately absent: it produces control characters, not text.
    private static let combinations: [(shift: Bool, option: Bool)] = [
        (false, false), (true, false), (false, true), (true, true),
    ]

    /// The keystroke that types `character`, or `nil` if this layout cannot produce it with Shift and Option
    /// alone — in which case the caller falls back to `EX-24`.
    static func keystroke(for character: Character, in table: [Character: Keystroke]) -> Keystroke? {
        table[character]
    }

    /// Builds the character → keystroke map for the layout currently in use.
    ///
    /// Returns `nil` when the layout cannot be read at all, which the caller treats the same as a character it
    /// cannot type: fall back to `EX-24` rather than guess.
    ///
    /// `@MainActor` is not decoration. The Text Input Services are **main-thread only**, and HIToolbox does not
    /// return an error when they are not — `TISCopyCurrentKeyboardLayoutInputSource` calls `abort()` from inside
    /// `islGetInputSourceListWithAdditions`, taking the whole process with it. The annotation is what makes the
    /// compiler refuse the call instead.
    @MainActor
    static func currentTable() -> [Character: Keystroke]? {
        guard let source = TISCopyCurrentKeyboardLayoutInputSource()?.takeRetainedValue(),
              let data = TISGetInputSourceProperty(source, kTISPropertyUnicodeKeyLayoutData)
        else { return nil }

        // `TISGetInputSourceProperty` hands back a pointer **borrowed** from `source`, and the `Data` below
        // keeps referencing that same memory rather than copying it. Without `withExtendedLifetime`, ARC is
        // free to release `source` after this line — `source` is never mentioned again — and the bytes are read
        // after they have been freed. It survives single-threaded runs and aborts once tests run in parallel
        // and something else claims the memory.
        return withExtendedLifetime(source) {
            buildTable(from: Unmanaged<CFData>.fromOpaque(data).takeUnretainedValue() as Data)
        }
    }

    private static func buildTable(from layoutData: Data) -> [Character: Keystroke]? {
        var table: [Character: Keystroke] = [:]
        layoutData.withUnsafeBytes { raw in
            guard let layout = raw.baseAddress?.assumingMemoryBound(to: UCKeyboardLayout.self) else { return }

            for code in 0...highestTextKeyCode {
                for combination in combinations {
                    var modifiers: UInt32 = 0
                    if combination.shift { modifiers |= UInt32(shiftKey >> 8) }
                    if combination.option { modifiers |= UInt32(optionKey >> 8) }

                    // Carried between calls so that a dead key (`´` on a European layout) is not mistaken for a
                    // character; a dead key produces nothing here and is simply never added to the table.
                    var deadKeyState: UInt32 = 0
                    var length = 0
                    var characters = [UniChar](repeating: 0, count: 4)

                    let status = UCKeyTranslate(
                        layout,
                        UInt16(code),
                        UInt16(kUCKeyActionDown),
                        modifiers,
                        UInt32(LMGetKbdType()),
                        OptionBits(kUCKeyTranslateNoDeadKeysBit),
                        &deadKeyState,
                        characters.count,
                        &length,
                        &characters
                    )

                    guard status == noErr, length == 1 else { continue }
                    let scalar = characters[0]
                    // Text only: control characters, tab and return are `pressKey`'s business, not typing's.
                    guard scalar >= 0x20, scalar != 0x7F,
                          let unicode = Unicode.Scalar(scalar) else { continue }

                    let character = Character(unicode)
                    // First combination to reach a character wins, so `a` is the plain key rather than some
                    // Option variant on another key that happens to produce the same letter.
                    if table[character] == nil {
                        table[character] = Keystroke(
                            keyCode: CGKeyCode(code),
                            shift: combination.shift,
                            option: combination.option
                        )
                    }
                }
            }
        }

        return table.isEmpty ? nil : table
    }
}
