import Carbon.HIToolbox
import Foundation
import os

/// Switches the input source to ABC while typing key by key, and puts the old one back (EX-27).
///
/// Key-by-key characters pass **through** the active input method — the `EX-24` payload route bypassed it, this
/// one cannot. Telex folds `aa` into `â` and `as` into `á`, so `"pass"` would be typed `"pá"`. That is observed,
/// not theoretical: it is why `B10` produced `"â"`.
///
/// The same idea as `SF-4`, which brings the Locked application to the front before typing: prepare the
/// environment, then type into it.
///
/// `restore()` belongs on the **stop and cleanup path**, beside `SF-1`'s mouse-button release — never only at
/// the end of typing. A `⌥⌘S` part-way through a string must not leave the user's input source changed.
@MainActor
final class InputSourceOverride {
    private static let logger = Logger(subsystem: "com.local.Clickify", category: "InputSource")

    /// The source to put back, held only while an override is in force.
    private var previous: TISInputSource?

    /// Whether an override is currently in force. `restore()` on a clean state does nothing.
    var isActive: Bool { previous != nil }

    /// Switches to ABC if something else is active. Safe to call repeatedly: the first call is the one that
    /// records what to go back to.
    func engage() {
        guard previous == nil,
              let current = TISCopyCurrentKeyboardInputSource()?.takeRetainedValue() else { return }

        // Already a plain Latin source with no input method in the way — nothing to do, and nothing to undo.
        if Self.identifier(of: current) == Self.asciiCapableIdentifier { return }

        guard let ascii = Self.abcSource() else {
            // Not fatal: typing still happens, it may just be transformed by the input method. Refusing to run
            // would be worse — that is the friction ADR-0009 rejected.
            Self.logger.warning("No ABC input source found; typing key by key through the active input method")
            return
        }

        guard TISSelectInputSource(ascii) == noErr else {
            Self.logger.warning("Could not select the ABC input source")
            return
        }
        previous = current
    }

    /// Puts back whatever was active before `engage()`.
    func restore() {
        guard let previous else { return }
        self.previous = nil
        if TISSelectInputSource(previous) != noErr {
            Self.logger.warning("Could not restore the previous input source")
        }
    }

    private static let asciiCapableIdentifier = "com.apple.keylayout.ABC"

    private static func identifier(of source: TISInputSource) -> String? {
        guard let value = TISGetInputSourceProperty(source, kTISPropertyInputSourceID) else { return nil }
        return Unmanaged<CFString>.fromOpaque(value).takeUnretainedValue() as String
    }

    private static func abcSource() -> TISInputSource? {
        let properties = [kTISPropertyInputSourceID as String: asciiCapableIdentifier] as CFDictionary
        guard let list = TISCreateInputSourceList(properties, true)?.takeRetainedValue() as? [TISInputSource]
        else { return nil }
        return list.first
    }
}
