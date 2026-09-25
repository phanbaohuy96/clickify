import Carbon.HIToolbox
import CoreGraphics
import Foundation

/// The key table used by the `pressKey` Action.
///
/// A Scenario stores the **key name**, not the numeric code (DM-21): `"c"` rather than `8`. The
/// code is the physical position on the keyboard — accurate, but unreadable in `scenario.json`.
///
/// The table holds only keys usable in a combination. To enter text, use `typeText`, which does
/// not depend on the keyboard layout.
enum KeyCatalog {
    struct Entry: Identifiable, Sendable {
        let name: String
        let keyCode: CGKeyCode
        /// The key-cap name, used as it is. `Return`, `Tab`, `F1` and `←` read the same in every language.
        private let capName: String
        /// The one entry that is a **description** rather than a key cap, so the one that is translated.
        private let titleKey: StringKey?

        init(name: String, title: String, keyCode: CGKeyCode) {
            self.name = name
            self.capName = title
            self.titleKey = nil
            self.keyCode = keyCode
        }

        init(name: String, titleKey: StringKey, keyCode: CGKeyCode) {
            self.name = name
            self.capName = name
            self.titleKey = titleKey
            self.keyCode = keyCode
        }

        var id: String { name }

        /// LC-6: **computed**, not stored. `entries` is a `static let` built once, so a stored title would be
        /// frozen in whichever language happened to be active the first time anything touched the table — and
        /// would stay in it after the user switched.
        var title: String {
            guard let titleKey else { return capName }
            return localized(titleKey)
        }
    }

    static let entries: [Entry] = {
        var result: [Entry] = [
            Entry(name: "return", title: "Return", keyCode: CGKeyCode(kVK_Return)),
            Entry(name: "enter", titleKey: .keyNumpadEnter, keyCode: CGKeyCode(kVK_ANSI_KeypadEnter)),
            Entry(name: "tab", title: "Tab", keyCode: CGKeyCode(kVK_Tab)),
            Entry(name: "space", title: "Space", keyCode: CGKeyCode(kVK_Space)),
            Entry(name: "delete", title: "Delete", keyCode: CGKeyCode(kVK_Delete)),
            Entry(name: "forwardDelete", title: "Forward Delete", keyCode: CGKeyCode(kVK_ForwardDelete)),
            Entry(name: "escape", title: "Escape", keyCode: CGKeyCode(kVK_Escape)),
            Entry(name: "home", title: "Home", keyCode: CGKeyCode(kVK_Home)),
            Entry(name: "end", title: "End", keyCode: CGKeyCode(kVK_End)),
            Entry(name: "pageUp", title: "Page Up", keyCode: CGKeyCode(kVK_PageUp)),
            Entry(name: "pageDown", title: "Page Down", keyCode: CGKeyCode(kVK_PageDown)),
            Entry(name: "left", title: "←", keyCode: CGKeyCode(kVK_LeftArrow)),
            Entry(name: "right", title: "→", keyCode: CGKeyCode(kVK_RightArrow)),
            Entry(name: "up", title: "↑", keyCode: CGKeyCode(kVK_UpArrow)),
            Entry(name: "down", title: "↓", keyCode: CGKeyCode(kVK_DownArrow)),
            Entry(name: "minus", title: "-", keyCode: CGKeyCode(kVK_ANSI_Minus)),
            Entry(name: "equal", title: "=", keyCode: CGKeyCode(kVK_ANSI_Equal)),
            Entry(name: "comma", title: ",", keyCode: CGKeyCode(kVK_ANSI_Comma)),
            Entry(name: "period", title: ".", keyCode: CGKeyCode(kVK_ANSI_Period)),
            Entry(name: "slash", title: "/", keyCode: CGKeyCode(kVK_ANSI_Slash))
        ]

        let letters: [(String, Int)] = [
            ("a", kVK_ANSI_A), ("b", kVK_ANSI_B), ("c", kVK_ANSI_C), ("d", kVK_ANSI_D),
            ("e", kVK_ANSI_E), ("f", kVK_ANSI_F), ("g", kVK_ANSI_G), ("h", kVK_ANSI_H),
            ("i", kVK_ANSI_I), ("j", kVK_ANSI_J), ("k", kVK_ANSI_K), ("l", kVK_ANSI_L),
            ("m", kVK_ANSI_M), ("n", kVK_ANSI_N), ("o", kVK_ANSI_O), ("p", kVK_ANSI_P),
            ("q", kVK_ANSI_Q), ("r", kVK_ANSI_R), ("s", kVK_ANSI_S), ("t", kVK_ANSI_T),
            ("u", kVK_ANSI_U), ("v", kVK_ANSI_V), ("w", kVK_ANSI_W), ("x", kVK_ANSI_X),
            ("y", kVK_ANSI_Y), ("z", kVK_ANSI_Z)
        ]
        result += letters.map { Entry(name: $0.0, title: $0.0.uppercased(), keyCode: CGKeyCode($0.1)) }

        let digits: [(String, Int)] = [
            ("0", kVK_ANSI_0), ("1", kVK_ANSI_1), ("2", kVK_ANSI_2), ("3", kVK_ANSI_3),
            ("4", kVK_ANSI_4), ("5", kVK_ANSI_5), ("6", kVK_ANSI_6), ("7", kVK_ANSI_7),
            ("8", kVK_ANSI_8), ("9", kVK_ANSI_9)
        ]
        result += digits.map { Entry(name: $0.0, title: $0.0, keyCode: CGKeyCode($0.1)) }

        let functionKeys: [(String, Int)] = [
            ("f1", kVK_F1), ("f2", kVK_F2), ("f3", kVK_F3), ("f4", kVK_F4),
            ("f5", kVK_F5), ("f6", kVK_F6), ("f7", kVK_F7), ("f8", kVK_F8),
            ("f9", kVK_F9), ("f10", kVK_F10), ("f11", kVK_F11), ("f12", kVK_F12)
        ]
        result += functionKeys.map {
            Entry(name: $0.0, title: $0.0.uppercased(), keyCode: CGKeyCode($0.1))
        }

        return result
    }()

    private static let byName: [String: Entry] = Dictionary(
        uniqueKeysWithValues: entries.map { ($0.name, $0) }
    )

    static func keyCode(for name: String) -> CGKeyCode? {
        byName[name.lowercased()]?.keyCode
    }

    static func title(for name: String) -> String {
        byName[name.lowercased()]?.title ?? name
    }

    static func describe(_ stroke: KeyStroke) -> String {
        let symbols = stroke.modifiers.map(\.symbol).joined()
        return symbols + title(for: stroke.key)
    }
}

extension KeyModifier {
    var symbol: String {
        switch self {
        case .command: return "⌘"
        case .option: return "⌥"
        case .control: return "⌃"
        case .shift: return "⇧"
        }
    }

    var flag: CGEventFlags {
        switch self {
        case .command: return .maskCommand
        case .option: return .maskAlternate
        case .control: return .maskControl
        case .shift: return .maskShift
        }
    }
}

extension Array where Element == KeyModifier {
    var eventFlags: CGEventFlags {
        reduce(into: CGEventFlags()) { $0.insert($1.flag) }
    }
}
