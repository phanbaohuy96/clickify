import Foundation
import Testing

/// The product name is a proper noun and is never translated ([ADR-0017]), so it sits as a plain literal
/// inside every catalogue instead of being interpolated from one place. That is the cheap choice — a
/// translator reads a whole sentence rather than `%@ has stopped` — and this test is what pays for it: a
/// rename that misses a catalogue, or a new string that arrives carrying the retired spelling, fails here
/// instead of shipping.
///
/// Deliberately narrow — **catalogues and `Info.plist`, never prose**. Documentation has to be able to say
/// the old name in order to explain it, and [ADR-0017] and `CONTEXT.md` both do. Scanning documentation
/// would mean maintaining an allow-list of the files permitted to tell the truth.
@MainActor
struct BrandNameTests {
    /// The spellings **Clickify** replaced. Case-sensitive on purpose: the *category* is ordinary lowercase
    /// English ("an auto clicker"), and `CONTEXT.md` leaves a string free to use it.
    private static let retired = ["Auto Click", "AutoClick"]

    /// Every catalogue in the repository, plus the bundle description that names the app to the system.
    private static func scanned() throws -> [URL] {
        let resources = Catalogs.directory
        let catalogues = try FileManager.default
            .contentsOfDirectory(at: resources, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "lproj" }
            .map { $0.appending(path: "Localizable.strings") }
            .sorted { $0.path() < $1.path() }
        return catalogues + [resources.appending(path: "Info.plist")]
    }

    @Test func noCatalogueCarriesTheRetiredName() throws {
        let files = try Self.scanned()

        // Asserted before anything is read. The trap this closes: a wrong path finds no files at all, every
        // expectation below then passes vacuously, and the test reports success for a scan it never ran.
        // LC-1 ships five languages, so fewer than six files means the walk, not the repository, is wrong.
        #expect(files.count >= 6, "expected five catalogues and Info.plist, found \(files.count)")

        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            let name = "\(file.deletingLastPathComponent().lastPathComponent)/\(file.lastPathComponent)"
            for spelling in Self.retired {
                #expect(!text.contains(spelling), "\(name) still says \"\(spelling)\"")
            }
        }
    }

    /// The identifier is what macOS keys a permission grant to, so a typo here costs every user their
    /// Accessibility and Screen Recording grants ([ADR-0017]).
    @Test func theBundleIsIdentifiedAsClickify() throws {
        let plist = try String(contentsOf: Catalogs.directory.appending(path: "Info.plist"), encoding: .utf8)

        #expect(plist.contains("<string>com.pbh.clickify</string>"))
        #expect(plist.contains("<key>CFBundleDisplayName</key>\n    <string>Clickify</string>"))
    }
}
