import Foundation

/// The active **Interface language** (`LC-1`…`LC-8`), and the object views observe so they redraw when it
/// changes.
///
/// The lookup itself lives in `Catalogue`; this type decides **which** catalogue is installed. That split is
/// deliberate: views need an `ObservableObject` on the main actor, while the readers of a translation are not
/// all on the main actor and must not be forced onto it.
///
/// Reading goes through an explicitly loaded `<code>.lproj` sub-bundle rather than through `Bundle.main`, and
/// that is the whole reason this type exists. `Bundle.main` resolves its language **once per process and
/// caches it**: writing `AppleLanguages` into our own defaults domain mid-process leaves
/// `preferredLocalizations` at its launch value and every string unchanged. Holding the bundle ourselves is
/// what makes `LC-6` — switching with no relaunch — possible at all. See [ADR-0010].
@MainActor
final class Localization: ObservableObject {
    /// `LC-1`. `en` is first because it is the development language and the fallback target.
    ///
    /// `nonisolated` because `Catalogue` is built off the main actor too — see `InstalledCatalogue`.
    nonisolated static let supportedCodes = ["en", "vi", "zh-Hans", "ja", "es"]
    nonisolated static let developmentCode = "en"

    static let preferenceKey = "interfaceLanguage"

    /// `LC-8`: each language written **in its own language**, and deliberately not a `StringKey`.
    ///
    /// Someone who picked `日本語` by accident has to find the way back while unable to read anything else on
    /// screen. Translating this list would show them *"ベトナム語"* where they are looking for *"Tiếng Việt"*.
    static func nativeName(of code: String) -> String {
        switch code {
        case "en": return "English"
        case "vi": return "Tiếng Việt"
        case "zh-Hans": return "中文（简体）"
        case "ja": return "日本語"
        case "es": return "Español"
        default: return code
        }
    }

    /// The instance that owns the installed `Catalogue`.
    ///
    /// Assigning it installs that instance's catalogue, which is what every `localized(_:)` call then reads.
    /// A mutable global, in a codebase that otherwise injects its seams: `LocalizedError.errorDescription` is
    /// a protocol property taking no arguments, so a validator has no way to be handed a bundle.
    static var current = Localization() {
        didSet { current.install() }
    }

    /// The active language code — always one of `supportedCodes`.
    @Published private(set) var code: String
    /// The stored preference: `nil` means **follow the system** (`LC-5`).
    @Published private(set) var preference: String?

    private let container: Bundle
    private let defaults: UserDefaults
    private(set) var catalogue: Catalogue

    /// - Parameter container: the bundle holding the `.lproj` directories. `Bundle.main` in the app, where
    ///   `build-app.sh` puts them in `Contents/Resources`; a plain directory in tests. Never a SwiftPM
    ///   resource bundle — `LC-4` explains why `Bundle.module` must not appear in this module.
    init(container: Bundle = .main, defaults: UserDefaults = .standard) {
        self.container = container
        self.defaults = defaults
        let stored = defaults.string(forKey: Self.preferenceKey)
        let resolved = Self.resolve(preference: stored, container: container)
        self.preference = stored
        self.code = resolved
        self.catalogue = InstalledCatalogue.build(code: resolved, container: container)
    }

    // MARK: - Choosing

    /// `LC-5`, `LC-6`. `nil` follows the system. Takes effect immediately; nothing is relaunched.
    func select(_ preference: String?) {
        let resolved = Self.resolve(preference: preference, container: container)
        if let preference {
            defaults.set(preference, forKey: Self.preferenceKey)
        } else {
            defaults.removeObject(forKey: Self.preferenceKey)
        }
        self.preference = preference
        catalogue = InstalledCatalogue.build(code: resolved, container: container)
        if Localization.current === self { install() }
        // Published last: SwiftUI redraws on this, and by then the catalogue is already in place.
        code = resolved
    }

    /// Makes this instance's catalogue the one every `localized(_:)` call reads.
    func install() {
        InstalledCatalogue.install(catalogue)
    }

    /// `LC-5`: an unset preference goes through `Bundle.main.preferredLocalizations`, which has already done
    /// Apple's own negotiation — on a machine set to `en-VN` it answers `en`.
    private static func resolve(preference: String?, container: Bundle) -> String {
        if let preference, supportedCodes.contains(preference) { return preference }
        for candidate in container.preferredLocalizations where supportedCodes.contains(candidate) {
            return candidate
        }
        return developmentCode
    }

    // MARK: - Reading

    /// The translation of `key`, falling back to `en` and never to the raw key (`LC-7`).
    func callAsFunction(_ key: StringKey) -> String {
        catalogue.string(key)
    }

    /// A translation with arguments (`LC-9`, `LC-10`).
    func callAsFunction(_ key: StringKey, _ arguments: any CVarArg...) -> String {
        catalogue.format(key, arguments)
    }

    /// A number in the active language's shape (`LC-9`).
    func number(_ value: Int) -> String { catalogue.number(value) }

    /// A decimal in the active language's shape (`LC-9`).
    func decimal(_ value: Double, fractionDigits: Int = 2) -> String {
        catalogue.decimal(value, fractionDigits: fractionDigits)
    }

    // MARK: - For the completeness tests (LC-15, LC-16)

    /// Whether `code` has its own translation of `key`, rather than reaching the `en` fallback.
    func hasOwnTranslation(of key: StringKey, in code: String) -> Bool {
        InstalledCatalogue.build(code: code, container: container).hasOwnTranslation(of: key)
    }

    /// Every key `<code>.lproj` declares, whether or not `StringKey` still uses it.
    ///
    /// Both files count: Foundation merges `Localizable.strings` and `Localizable.stringsdict` into one
    /// table, so a key living in either of them is a key the catalogue declares.
    func declaredKeys(in code: String) -> Set<String> {
        guard let path = container.path(forResource: code, ofType: "lproj") else { return [] }
        let directory = URL(filePath: path)
        var keys = Set<String>()
        for file in ["Localizable.strings", "Localizable.stringsdict"] {
            guard let table = NSDictionary(contentsOf: directory.appending(path: file)) as? [String: Any]
            else { continue }
            keys.formUnion(table.keys)
        }
        return keys
    }
}
