import AppKit
import Foundation
import Testing
@testable import Clickify

/// The `.lproj` directories as they sit in the repository.
///
/// `Bundle.main` is the app in production and the `xctest` runner here, so the container has to be injected.
/// Note what is **not** used: `Bundle.module`. `LC-4` and [ADR-0010] record that SwiftPM's accessor falls back
/// to an absolute path into the build machine's `.build/`, which works here and crashes everywhere else.
@MainActor
enum Catalogs {
    static let directory = URL(filePath: #filePath)
        .deletingLastPathComponent()   // ClickifyTests
        .deletingLastPathComponent()   // Tests
        .deletingLastPathComponent()   // repository root
        .appending(path: "Resources")

    static func container() throws -> Bundle {
        try #require(Bundle(path: directory.path()), "no bundle at \(directory.path())")
    }

    /// Pins the global `Localization.current` to English, once, for the whole test run.
    ///
    /// Tests run in parallel, so the global is never *switched* during a run — a test that needs another
    /// language builds its own instance instead. Without pinning, `Localization.current` would be reading
    /// `Bundle.main`, which under `swift test` is the xctest runner and carries no catalogue at all.
    static let pinnedToEnglish: Void = {
        // Deliberately no `#require` here: this runs inside a lazy global initialiser that the first test to
        // touch it triggers, which is not a place an expectation belongs.
        guard let container = Bundle(path: directory.path()) else {
            fatalError("no catalogue directory at \(directory.path())")
        }
        let localization = Localization(
            container: container,
            defaults: UserDefaults(suiteName: "ClickifyPinned-\(UUID().uuidString)")!
        )
        localization.select("en")
        Localization.current = localization
    }()

    static func localization(preference: String? = nil) throws -> Localization {
        let defaults = UserDefaults(suiteName: "ClickifyLocalization-\(UUID().uuidString)")!
        let localization = Localization(container: try container(), defaults: defaults)
        if let preference { localization.select(preference) }
        return localization
    }
}

/// `LC-15`: the completeness rule is **asymmetric** on purpose.
@MainActor
struct CatalogCompletenessTests {
    /// `en` is the source of truth and the target every other language falls back to, so a gap here is a gap
    /// with nothing behind it.
    @Test func everyKeyIsTranslatedIntoEnglish() throws {
        let localization = try Catalogs.localization()
        let missing = StringKey.allCases.filter { !localization.hasOwnTranslation(of: $0, in: "en") }
        #expect(missing.isEmpty, "en.lproj is missing: \(missing.map(\.rawValue).sorted())")
    }

    /// `vi` is the only language besides `en` with a reviewer, so it is held to the same standard.
    @Test func everyKeyIsTranslatedIntoVietnamese() throws {
        let localization = try Catalogs.localization()
        let missing = StringKey.allCases.filter { !localization.hasOwnTranslation(of: $0, in: "vi") }
        #expect(missing.isEmpty, "vi.lproj is missing: \(missing.map(\.rawValue).sorted())")
    }

    /// The machine-translated languages **report** their coverage and never fail.
    ///
    /// Holding them to the same standard would turn the build red every time a label is added, until it had
    /// been machine-translated into three languages nobody here can check — and it would contradict `LC-7`,
    /// which exists so that a partial translation is a safe thing to ship.
    @Test func theMachineTranslatedLanguagesOnlyReportCoverage() throws {
        let localization = try Catalogs.localization()
        for code in ["zh-Hans", "ja", "es"] {
            let covered = StringKey.allCases.count { localization.hasOwnTranslation(of: $0, in: code) }
            print("localisation coverage — \(code): \(covered)/\(StringKey.allCases.count)")
        }
    }

    /// `LC-16`: a key left in the catalogue after its last use is dead weight translators keep paying for.
    @Test func englishDeclaresNoKeyTheInterfaceNoLongerUses() throws {
        let localization = try Catalogs.localization()
        let used = Set(StringKey.allCases.map(\.rawValue))
        let orphans = localization.declaredKeys(in: "en").subtracting(used)
        #expect(orphans.isEmpty, "en.lproj declares unused keys: \(orphans.sorted())")
    }
}

/// `LC-5`…`LC-7`: choosing a language, and what happens where a translation runs out.
@MainActor
struct LanguageSelectionTests {
    @Test func switchingLanguageChangesTheStringsWithNoRelaunch() throws {
        let localization = try Catalogs.localization(preference: "en")
        #expect(localization(.menuQuit) == "Quit")

        localization.select("vi")

        #expect(localization.code == "vi")
        #expect(localization(.menuQuit) == "Thoát")
    }

    /// The whole reason this type holds its own bundle. Asking `Bundle.main` instead would answer with the
    /// language chosen when the process started, however many times the preference changed since.
    @Test func switchingBackAgainIsAlsoImmediate() throws {
        let localization = try Catalogs.localization(preference: "vi")
        localization.select("en")
        #expect(localization(.menuQuit) == "Quit")
    }

    @Test func anUnsupportedPreferenceFallsBackToTheDevelopmentLanguage() throws {
        let localization = try Catalogs.localization(preference: "kl")
        #expect(localization.code == Localization.developmentCode)
    }

    /// `LC-5`: an unset preference is not "English", it is "whatever the system negotiated".
    @Test func anUnsetPreferenceIsNotStored() throws {
        let localization = try Catalogs.localization(preference: "vi")
        #expect(localization.preference == "vi")

        localization.select(nil)

        #expect(localization.preference == nil)
        #expect(Localization.supportedCodes.contains(localization.code))
    }

    /// `LC-7`. The failure this guards against only ever appears in a language the author cannot read, which
    /// is why it is a test and not an eyeball check.
    @Test func aMissingTranslationFallsBackToEnglishRatherThanShowingTheKey() throws {
        let root = URL(filePath: NSTemporaryDirectory())
            .appending(path: "ClickifyCatalog-\(UUID().uuidString)")
        defer { try? FileManager.default.removeItem(at: root) }

        for (code, body) in [
            ("en", "\"menu.quit\" = \"Quit\";\n\"menu.mode.label\" = \"Mode\";\n"),
            ("ja", "\"menu.quit\" = \"終了\";\n")   // deliberately missing menu.mode.label
        ] {
            let directory = root.appending(path: "\(code).lproj")
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try Data(body.utf8).write(to: directory.appending(path: "Localizable.strings"))
        }

        let container = try #require(Bundle(path: root.path()))
        let localization = Localization(
            container: container,
            defaults: UserDefaults(suiteName: "ClickifyGap-\(UUID().uuidString)")!
        )
        localization.select("ja")

        #expect(localization(.menuQuit) == "終了")
        #expect(localization(.menuModeLabel) == "Mode", "a gap must reach en, not the raw key")
        #expect(localization(.menuModeLabel) != "menu.mode.label")
    }
}

/// Does a translated row still fit the box it is drawn in? (`UI-24`, `UI-25`)
///
/// This is the half of `F2` a machine can answer. The eye is still needed for wrapping and for whether a
/// sentence reads well, but "is it wider than the container" is arithmetic, and arithmetic does not have to
/// wait for someone to notice. It has caught one real defect already: at a hard-coded 30 points the unit
/// column cut `times` down to `tim…` in the **default** language.
@MainActor
@Suite("Row widths")
struct RowWidthTests {
    /// SwiftUI's `.body` on macOS, which is what both rows use.
    private static let font = NSFont.preferredFont(forTextStyle: .body)

    private static func width(_ text: String) -> CGFloat {
        ceil((text as NSString).size(withAttributes: [.font: font]).width)
    }

    /// The popover is pinned to `340` points with `16` of padding on each side (`UI-22`).
    private static let popoverContentWidth: CGFloat = 340 - 32

    /// The editor's detail column can be dragged wider, but never narrower than `340` (`UI-18`); measured
    /// inset is about `28` a side.
    private static let detailContentWidth: CGFloat = 340 - 56

    /// Default `HStack` spacing, counted twice: title | field | unit.
    private static let spacing: CGFloat = 8 * 2

    @Test("Simple mode's interval and repeat rows fit the popover", arguments: Localization.supportedCodes)
    func simpleRowsFit(code: String) throws {
        let localization = try Catalogs.localization(preference: code)
        let unit = UnitColumn.width(of: "ms", localization(.simpleRepeatUnit))
        let titles = [localization(.simpleInterval), localization(.simpleRepeat)]
        let needed = (titles.map(Self.width).max() ?? 0) + Self.spacing + 110 + unit

        #expect(
            needed <= Self.popoverContentWidth,
            """
            \(code): the number rows need \(needed) points of \(Self.popoverContentWidth). \
            Widest title \(titles.max(by: { Self.width($0) < Self.width($1) }) ?? ""), unit column \(unit).
            """
        )
    }

    @Test("The Step detail panel's number rows fit the narrowest column", arguments: Localization.supportedCodes)
    func detailRowsFit(code: String) throws {
        let localization = try Catalogs.localization(preference: code)
        let unit = UnitColumn.width(
            of: "ms", localization(.stepRepeatUnit), localization(.stepScrollUnit)
        )
        let titles: [String] = [
            .stepRepeatCount, .stepDelayAfter, .stepClickCount, .stepHold,
            .stepScrollHorizontal, .stepScrollVertical, .stepWaitAtMost
        ].map { localization($0) }
        let needed = (titles.map(Self.width).max() ?? 0) + Self.spacing + 90 + unit

        #expect(
            needed <= Self.detailContentWidth,
            """
            \(code): the number rows need \(needed) points of \(Self.detailContentWidth). \
            Widest title \(titles.max(by: { Self.width($0) < Self.width($1) }) ?? ""), unit column \(unit).
            """
        )
    }

    @Test("A unit label is never cut off by its own column", arguments: Localization.supportedCodes)
    func unitsFitTheirColumn(code: String) throws {
        let localization = try Catalogs.localization(preference: code)
        for key in [StringKey.simpleRepeatUnit, .stepRepeatUnit, .stepScrollUnit] {
            let unit = localization(key)
            #expect(
                Self.width(unit) <= UnitColumn.width(of: unit),
                "\(code): \(key.rawValue) = \(unit.debugDescription) is wider than its column"
            )
        }
    }

    /// Measured from Accessibility on the real button: a 116-point label sat in a 140-point button.
    private static let buttonChrome: CGFloat = 24

    /// The floor `UI-25` puts under the Template thumbnail.
    private static let thumbnailFloor: CGFloat = 64

    @Test("The Template row fits beside a thumbnail", arguments: Localization.supportedCodes)
    func templateRowFits(code: String) throws {
        let localization = try Catalogs.localization(preference: code)
        for key in [StringKey.stepTargetCaptureRegion, .stepTargetCaptureAgain] {
            let button = Self.width(localization(key)) + Self.buttonChrome
            let needed = Self.width(localization(.stepTargetTemplate))
                + Self.spacing + Self.thumbnailFloor + button

            #expect(
                needed <= Self.detailContentWidth,
                """
                \(code): \(key.rawValue) = \(localization(key).debugDescription) makes the row \(needed) \
                points wide, past \(Self.detailContentWidth). The button keeps its label (`UI-25`), so what \
                gives way is the picture of the Template.
                """
            )
        }
    }
}
