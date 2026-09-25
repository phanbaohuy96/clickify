import Foundation
import os

/// One language's loaded string tables, and the lookup over them (`LC-7`, `LC-9`).
///
/// Immutable, and readable from **any thread**. `Bundle.localizedString(forKey:value:table:)` reads a table
/// that was parsed when the bundle was loaded and never changes afterwards, so concurrent reads are safe;
/// what changes when the user picks another language is *which* `Catalogue` is installed, and that swap
/// happens under a lock.
final class Catalogue: @unchecked Sendable {
    let code: String
    private let active: Bundle
    private let fallback: Bundle
    private let locale: Locale

    /// A value no translation can equal, used to tell "missing" apart from "translated to this".
    private static let missing = "\u{0}Clickify.missing"

    init(code: String, active: Bundle, fallback: Bundle) {
        self.code = code
        self.active = active
        self.fallback = fallback
        self.locale = Locale(identifier: code)
    }

    func string(_ key: StringKey) -> String {
        let value = active.localizedString(forKey: key.rawValue, value: Self.missing, table: nil)
        guard value == Self.missing else { return value }
        // LC-7: Foundation's automatic fallback chain belongs to `Bundle.main`. A sub-bundle asked for a key
        // it does not have hands back the key itself, so `editor.step.delay` would appear on screen.
        return fallback.localizedString(forKey: key.rawValue, value: key.rawValue, table: nil)
    }

    func format(_ key: StringKey, _ arguments: [any CVarArg]) -> String {
        guard !arguments.isEmpty else { return string(key) }
        // LC-9: the locale is the **active language**, not the process locale. It picks the plural rule out of
        // `Localizable.stringsdict` and shapes the numbers.
        return String(format: string(key), locale: locale, arguments: arguments)
    }

    func number(_ value: Int) -> String {
        value.formatted(.number.locale(locale))
    }

    func decimal(_ value: Double, fractionDigits: Int = 2) -> String {
        value.formatted(.number.precision(.fractionLength(fractionDigits)).locale(locale))
    }

    /// Whether this language has its own translation of `key`, rather than reaching the fallback (`LC-15`).
    func hasOwnTranslation(of key: StringKey) -> Bool {
        active.localizedString(forKey: key.rawValue, value: Self.missing, table: nil) != Self.missing
    }
}

/// The `Catalogue` every `localized(_:)` call reads.
///
/// It is held behind a lock rather than on the main actor because the readers are not all on it. Globals with
/// lazy initialisers are the sharp edge: `KeyCatalog.entries` builds its table the first time **any** thread
/// touches it, and a `MainActor.assumeIsolated` inside that initialiser traps outright when that thread is not
/// the main one. Measured — it crashed the parallel test run with `SIGTRAP`, and in the app it was only ever
/// working by luck. See [ADR-0010].
enum InstalledCatalogue {
    private static let storage = OSAllocatedUnfairLock<Catalogue?>(initialState: nil)

    static func install(_ catalogue: Catalogue) {
        storage.withLock { $0 = catalogue }
    }

    /// The installed catalogue, or one built from `Bundle.main` if nothing has been installed yet.
    ///
    /// The fallback matters for order-of-initialisation only: `ClickifyApp` installs the real one while it is
    /// building its objects, and a global initialiser that runs even earlier must still get strings rather
    /// than crash.
    static var current: Catalogue {
        if let installed = storage.withLock({ $0 }) { return installed }
        let built = build(code: Localization.developmentCode, container: .main)
        install(built)
        return built
    }

    static func build(code: String, container: Bundle) -> Catalogue {
        let fallback = bundle(for: Localization.developmentCode, in: container) ?? container
        return Catalogue(
            code: code,
            active: bundle(for: code, in: container) ?? fallback,
            fallback: fallback
        )
    }

    static func bundle(for code: String, in container: Bundle) -> Bundle? {
        guard let path = container.path(forResource: code, ofType: "lproj") else { return nil }
        return Bundle(path: path)
    }
}

/// The translation of `key` in the active **Interface language**, readable from any thread.
func localized(_ key: StringKey, _ arguments: any CVarArg...) -> String {
    InstalledCatalogue.current.format(key, arguments)
}

/// A number in the active language's shape (`LC-9`).
func localizedNumber(_ value: Int) -> String {
    InstalledCatalogue.current.number(value)
}

/// A decimal in the active language's shape (`LC-9`): `0.85` in `en`, `0,85` in `vi` and `es`.
func localizedDecimal(_ value: Double) -> String {
    InstalledCatalogue.current.decimal(value)
}
