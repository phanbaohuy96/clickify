import Foundation
import Testing
@testable import Clickify

@MainActor
private struct Fixture {
    let root: URL
    let defaults: UserDefaults

    init() {
        root = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("ClickifyTests-\(UUID().uuidString)", isDirectory: true)
        defaults = UserDefaults(suiteName: "ClickifyTests-\(UUID().uuidString)")!
    }

    func makeStore() -> ScenarioStore {
        ScenarioStore(rootDirectory: root, defaults: defaults)
    }

    func write(_ json: String, into folder: String) throws {
        let directory = root.appendingPathComponent(folder, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data(json.utf8).write(to: directory.appendingPathComponent("scenario.json"))
    }

    func cleanUp() {
        try? FileManager.default.removeItem(at: root)
    }
}

@MainActor
@Test func savedScenariosComeBackOnReload() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let store = fixture.makeStore()
    var scenario = store.create(name: "Bulk delete")
    scenario.steps = [Step(action: .move, target: .screenPoint(x: 3, y: 4))]
    scenario.runCount = .untilStopped
    store.save(scenario)

    let reopened = fixture.makeStore()

    #expect(reopened.scenarios.count == 1)
    #expect(reopened.scenarios[0].name == "Bulk delete")
    #expect(reopened.scenarios[0].runCount == .untilStopped)
    #expect(reopened.scenarios[0].steps.count == 1)
    // ST-4: the active selection lives in UserDefaults, not in the scenario file.
    #expect(reopened.selectedScenarioID == scenario.id)
}

/// ST-3: deleting a Scenario deletes the whole directory, everything inside it included.
@MainActor
@Test func deletingAScenarioRemovesItsWholeFolder() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let store = fixture.makeStore()
    let scenario = store.create(name: "Temporary")
    let folder = fixture.root.appendingPathComponent(scenario.id.uuidString)
    let template = folder.appendingPathComponent("templates", isDirectory: true)
    try FileManager.default.createDirectory(at: template, withIntermediateDirectories: true)

    store.delete(scenario)

    #expect(!FileManager.default.fileExists(atPath: folder.path))
    #expect(store.scenarios.isEmpty)
}

@MainActor
@Test func duplicatingGivesFreshIdentifiersToEveryStep() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let store = fixture.makeStore()
    var original = store.create(name: "Original")
    original.steps = [
        Step(action: .move, target: .cursor),
        Step(action: .click(button: .left, count: 1, holdMilliseconds: 0), target: .cursor)
    ]
    store.save(original)

    Catalogs.pinnedToEnglish
    let copy = try #require(store.duplicate(original))

    #expect(copy.id != original.id)
    // LC-11: the suffix is translated once, here, and then it is data — it does not follow a later
    // language change. The catalogue is pinned to English so the assertion can name the text.
    #expect(copy.name == "Original (copy)")
    #expect(Set(copy.steps.map(\.id)).isDisjoint(with: Set(original.steps.map(\.id))))
    #expect(copy.steps.map(\.action) == original.steps.map(\.action))
}

/// ST-9: one corrupt directory must not cost us the remaining Scenarios.
@MainActor
@Test func aCorruptFolderDoesNotHideTheHealthyOnes() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    try fixture.write("{ not json at all", into: "broken")
    try fixture.write(
        """
        { "schemaVersion": 1, "id": "\(UUID().uuidString)", "name": "Intact", "repeat": 1, "steps": [] }
        """,
        into: "healthy"
    )

    let store = fixture.makeStore()

    #expect(store.scenarios.map(\.name) == ["Intact"])
    #expect(store.loadIssues.count == 1)
}

/// ST-12: a newer format loads its name for display, but cannot be edited or overwritten.
@MainActor
@Test func aNewerSchemaLoadsReadOnlyInsteadOfBeingMisread() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let id = UUID()
    try fixture.write(
        """
        { "schemaVersion": 99, "id": "\(id.uuidString)", "name": "From the future", "steps": [] }
        """,
        into: id.uuidString
    )

    let store = fixture.makeStore()
    let scenario = try #require(store.scenarios.first)

    #expect(scenario.name == "From the future")
    #expect(store.isReadOnly(scenario))

    var edited = scenario
    edited.name = "Edited"
    store.save(edited)

    #expect(fixture.makeStore().scenarios.first?.name == "From the future")
}

/// ST-12: duplicating a read-only Scenario must **do nothing**, rather than produce an empty copy.
///
/// A read-only Scenario loads with only its `id` and `name` — the Steps live in the part of the JSON this app
/// cannot decode. Duplicating it used to write a Scenario to disk carrying the original's name, the current
/// `schemaVersion` and **no Steps at all**, looking entirely genuine. A user who believed the data had been
/// rescued and then deleted the original would lose everything. Found in `E2` of the manual tests.
@MainActor
@Test func aReadOnlyScenarioCannotBeDuplicatedIntoAnEmptyOne() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let id = UUID()
    try fixture.write(
        """
        { "schemaVersion": 99, "id": "\(id.uuidString)", "name": "From the future", "steps": [] }
        """,
        into: id.uuidString
    )

    let store = fixture.makeStore()
    let scenario = try #require(store.scenarios.first)

    #expect(store.duplicate(scenario) == nil)
    #expect(store.scenarios.count == 1)
    // And no second directory appeared on disk.
    let folders = try FileManager.default.contentsOfDirectory(atPath: fixture.root.path)
    #expect(folders == [id.uuidString])
}

/// ST-3 / ADR-0005: duplicating a Scenario has to copy `templates/` too, not just `scenario.json`.
///
/// ADR-0005 chose "one directory per Scenario, Templates duplicated" precisely so that duplicating is a directory
/// copy. Copying only `scenario.json` leaves the copy pointing at files that do not exist: its recognition Steps
/// are broken immediately, while the interface still shows it as a perfectly ordinary Scenario.
@MainActor
@Test func duplicatingAScenarioAlsoCopiesItsTemplateFiles() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let store = fixture.makeStore()
    var original = store.create(name: "With templates")

    let library = store.templateLibrary(for: original.id)
    try FileManager.default.createDirectory(at: library.url(for: "x").deletingLastPathComponent(), withIntermediateDirectories: true)
    try Data("pretend-image".utf8).write(to: library.url(for: "button.png"))

    original.steps = [
        Step(
            action: .click(button: .left, count: 1, holdMilliseconds: 0),
            target: .template(name: "button.png", settings: RecognitionSettings())
        )
    ]
    store.save(original)
    #expect(FileManager.default.fileExists(atPath: library.url(for: "button.png").path))

    let copy = try #require(store.duplicate(original))

    let copiedFile = store.templateLibrary(for: copy.id).url(for: "button.png")
    #expect(FileManager.default.fileExists(atPath: copiedFile.path))
    #expect(try Data(contentsOf: copiedFile) == Data("pretend-image".utf8))
    // And the original is left untouched.
    #expect(FileManager.default.fileExists(atPath: library.url(for: "button.png").path))
}

/// RC-16: two recordings in the same minute produce the same name, so the name has to be made unique.
///
/// `save` leaves the name alone — rightly, since it is also the path that saves every character the user types
/// while renaming. But the **recording** path needs this, otherwise the picker shows two identical rows.
@MainActor
@Test func twoRecordingsInTheSameMinuteDoNotEndUpWithTheSameName() throws {
    let fixture = Fixture()
    defer { fixture.cleanUp() }

    let store = fixture.makeStore()
    let first = store.addRecorded(Scenario(name: "Google Chrome 12/09 01:09"))
    let second = store.addRecorded(Scenario(name: "Google Chrome 12/09 01:09"))

    #expect(first.name == "Google Chrome 12/09 01:09")
    #expect(second.name == "Google Chrome 12/09 01:09 2")
    #expect(store.scenarios.count == 2)
    // And the one just recorded is the one selected.
    #expect(store.selectedScenarioID == second.id)
}
