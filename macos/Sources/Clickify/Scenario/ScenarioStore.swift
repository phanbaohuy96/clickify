import Foundation
import SwiftUI
import os

/// The Scenario store on disk: one directory per Scenario (ADR-0005, ST-1…ST-3).
///
/// There is no reference counting anywhere — deleting a Scenario deletes its whole directory, Templates
/// included. Two Scenarios using the same button keep two copies of the same Template, deliberately.
@MainActor
final class ScenarioStore: ObservableObject {
    @Published private(set) var scenarios: [Scenario] = []
    /// Scenarios in a newer format: the name loads, but they cannot be edited or run (ST-12).
    @Published private(set) var readOnlyScenarioIDs: Set<UUID> = []
    /// LC-6: enums, so the list is translated when it is drawn rather than when the directory was scanned.
    @Published private(set) var loadIssues: [LoadIssue] = []

    /// Why one Scenario directory could not be loaded normally (ST-9, ST-12).
    enum LoadIssue: Equatable {
        case readOnlySchema(name: String, version: Int)
        case unreadable(directory: String)

        var text: String {
            switch self {
            case let .readOnlySchema(name, version):
                return localized(.issueSchemaReadOnly, name, version)
            case let .unreadable(directory):
                return localized(.issueUnreadable, directory)
            }
        }
    }

    @Published var selectedScenarioID: UUID? {
        didSet {
            defaults.set(selectedScenarioID?.uuidString, forKey: Self.selectedKey)
        }
    }

    private static let selectedKey = "selectedScenarioID"
    private static let logger = Logger(subsystem: "com.local.Clickify", category: "ScenarioStore")

    private let defaults: UserDefaults
    private let fileManager: FileManager
    private let rootDirectory: URL

    init(defaults: UserDefaults = .standard, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
        self.rootDirectory = Self.defaultRootDirectory(using: fileManager)
        self.selectedScenarioID = defaults.string(forKey: Self.selectedKey).flatMap(UUID.init)
        reload()
    }

    /// Test-only: point the store at a temporary directory.
    init(rootDirectory: URL, defaults: UserDefaults, fileManager: FileManager = .default) {
        self.defaults = defaults
        self.fileManager = fileManager
        self.rootDirectory = rootDirectory
        self.selectedScenarioID = defaults.string(forKey: Self.selectedKey).flatMap(UUID.init)
        reload()
    }

    var selectedScenario: Scenario? {
        guard let selectedScenarioID else { return nil }
        return scenarios.first { $0.id == selectedScenarioID }
    }

    func isReadOnly(_ scenario: Scenario) -> Bool {
        readOnlyScenarioIDs.contains(scenario.id)
    }

    /// A Scenario's Templates directory (ST-2). It lives inside the Scenario directory, so deleting the
    /// Scenario deletes the images too and no reference counting is needed ([ADR-0005]).
    func templatesDirectory(for scenarioID: UUID) -> URL {
        rootDirectory
            .appendingPathComponent(scenarioID.uuidString, isDirectory: true)
            .appendingPathComponent("templates", isDirectory: true)
    }

    func templateLibrary(for scenarioID: UUID) -> TemplateLibrary {
        TemplateLibrary(directory: templatesDirectory(for: scenarioID))
    }

    /// A binding that writes straight to disk: the editor has no Save button (UI-11).
    func binding(for id: UUID) -> Binding<Scenario> {
        Binding(
            get: { self.scenarios.first { $0.id == id } ?? Scenario(id: id, name: "") },
            set: { self.save($0) }
        )
    }

    // MARK: - Reading

    func reload() {
        var loaded: [Scenario] = []
        var readOnly: Set<UUID> = []
        var issues: [LoadIssue] = []

        let directories = (try? fileManager.contentsOfDirectory(
            at: rootDirectory,
            includingPropertiesForKeys: [.isDirectoryKey]
        )) ?? []

        for directory in directories {
            let file = directory.appendingPathComponent("scenario.json")
            guard let data = try? Data(contentsOf: file) else { continue }

            do {
                loaded.append(try JSONDecoder().decode(Scenario.self, from: data))
            } catch {
                // One corrupt directory must not cost us the remaining Scenarios (ST-9).
                if let header = try? JSONDecoder().decode(ScenarioHeader.self, from: data),
                   header.schemaVersion > ScenarioSchema.currentVersion {
                    loaded.append(Scenario(id: header.id, name: header.name))
                    readOnly.insert(header.id)
                    issues.append(.readOnlySchema(name: header.name, version: header.schemaVersion))
                } else {
                    Self.logger.error(
                        "Skipping \(file.path, privacy: .public): \(error.localizedDescription, privacy: .public)"
                    )
                    issues.append(.unreadable(directory: directory.lastPathComponent))
                }
            }
        }

        scenarios = loaded.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        readOnlyScenarioIDs = readOnly
        loadIssues = issues

        if let selectedScenarioID, !scenarios.contains(where: { $0.id == selectedScenarioID }) {
            self.selectedScenarioID = scenarios.first?.id
        } else if selectedScenarioID == nil {
            selectedScenarioID = scenarios.first?.id
        }
    }

    // MARK: - Ghi

    @discardableResult
    func create(name: String? = nil) -> Scenario {
        // LC-11: translated once, here, and then it is data like any other name.
        let name = name ?? localized(.dataNewScenarioName)
        let scenario = Scenario(name: uniqueName(from: name))
        save(scenario)
        selectedScenarioID = scenario.id
        return scenario
    }

    func save(_ scenario: Scenario) {
        guard !readOnlyScenarioIDs.contains(scenario.id) else { return }

        let directory = rootDirectory.appendingPathComponent(scenario.id.uuidString)
        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .withoutEscapingSlashes, .sortedKeys]
            let data = try encoder.encode(scenario)
            // Written atomically so a mid-write shutdown cannot leave a truncated scenario.json (ST-11).
            try data.write(to: directory.appendingPathComponent("scenario.json"), options: .atomic)
        } catch {
            Self.logger.error("Could not save scenario: \(error.localizedDescription, privacy: .public)")
            return
        }

        // A Template no Step uses any more is deleted, so the Scenario directory does not grow with every
        // recapture the user makes.
        templateLibrary(for: scenario.id).removeUnused(keeping: scenario.templateNames)

        if let index = scenarios.firstIndex(where: { $0.id == scenario.id }) {
            // Deliberately not re-sorted here: a rename is saved character by character, and re-sorting on
            // each one makes the row being edited jump away. The order is normalised again in `reload()`.
            scenarios[index] = scenario
        } else {
            scenarios.append(scenario)
            scenarios.sort { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    /// Saves a freshly recorded **Scenario** and selects it.
    ///
    /// Unlike `save`: the name is made unique. `RC-16` names by the minute, so two recordings in the same
    /// minute produce **exactly the same name** — and the picker cannot tell one from the other. Measured
    /// while running session D: six recordings, three colliding pairs.
    @discardableResult
    func addRecorded(_ scenario: Scenario) -> Scenario {
        var stored = scenario
        stored.name = uniqueName(from: scenario.name)
        save(stored)
        selectedScenarioID = stored.id
        return stored
    }

    /// Returns `nil` for a read-only Scenario: the copy that was loaded **has no Steps** — they live in the
    /// part of the JSON this build cannot decode. Duplicating it would write out an empty Scenario carrying the
    /// original's name and the current `schemaVersion`, which is exactly what `ST-12` exists to prevent: a bad
    /// read turned into a Scenario that looks genuine. Found in `E2` of the manual tests.
    @discardableResult
    func duplicate(_ scenario: Scenario) -> Scenario? {
        guard !readOnlyScenarioIDs.contains(scenario.id) else { return nil }
        var copy = scenario
        copy.id = UUID()
        copy.name = uniqueName(from: localized(.dataDuplicateSuffix, scenario.name))
        copy.steps = scenario.steps.map { step in
            var duplicated = step
            duplicated.id = UUID()
            return duplicated
        }
        // ADR-0005 chose "one directory per Scenario, Templates duplicated" precisely so that duplicating is a
        // directory copy. Copying only `scenario.json` leaves the copy pointing at files that do not exist and
        // every recognition Step in it broken, even though the interface looks perfectly normal.
        copyTemplates(from: scenario.id, to: copy.id, names: copy.templateNames)
        save(copy)
        selectedScenarioID = copy.id
        return copy
    }

    private func copyTemplates(from source: UUID, to destination: UUID, names: Set<String>) {
        guard !names.isEmpty else { return }
        let sourceDirectory = templatesDirectory(for: source)
        let destinationDirectory = templatesDirectory(for: destination)
        do {
            try fileManager.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)
            for name in names {
                try fileManager.copyItem(
                    at: sourceDirectory.appendingPathComponent(name),
                    to: destinationDirectory.appendingPathComponent(name)
                )
            }
        } catch {
            Self.logger.error("Could not copy templates while duplicating: \(error.localizedDescription, privacy: .public)")
        }
    }

    func delete(_ scenario: Scenario) {
        let directory = rootDirectory.appendingPathComponent(scenario.id.uuidString)
        try? fileManager.removeItem(at: directory)
        scenarios.removeAll { $0.id == scenario.id }
        readOnlyScenarioIDs.remove(scenario.id)
        if selectedScenarioID == scenario.id {
            selectedScenarioID = scenarios.first?.id
        }
    }

    private func uniqueName(from name: String) -> String {
        let existing = Set(scenarios.map(\.name))
        guard existing.contains(name) else { return name }
        var index = 2
        while existing.contains("\(name) \(index)") { index += 1 }
        return "\(name) \(index)"
    }

    private static func defaultRootDirectory(using fileManager: FileManager) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.homeDirectoryForCurrentUser.appendingPathComponent("Library/Application Support")
        return base
            .appendingPathComponent("Clickify", isDirectory: true)
            .appendingPathComponent("Scenarios", isDirectory: true)
    }
}
