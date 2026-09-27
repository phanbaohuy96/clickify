import Foundation

/// Encodes a Scenario into JSON that can be read and edited by hand (ST-5, ST-6, ST-7).
///
/// Synthesised Codable is not used for enums with associated values: it produces JSON nested under the Swift
/// `case` names, which means renaming a `case` in the code would silently break every saved file.
/// The `kind` discriminator keeps the on-disk format separate from the names in the code.
enum ScenarioSchema {
    static let currentVersion = 1
}

enum ScenarioDecodingError: LocalizedError, Equatable {
    case unsupportedSchemaVersion(Int)
    case unknownActionKind(String)
    case unknownTargetKind(String)

    var errorDescription: String? {
        switch self {
        case let .unsupportedSchemaVersion(version):
            return localized(.errorSchemaTooNew, version, ScenarioSchema.currentVersion)
        case let .unknownActionKind(kind):
            return localized(.errorUnknownAction, kind)
        case let .unknownTargetKind(kind):
            return localized(.errorUnknownTarget, kind)
        }
    }
}

/// The head of `scenario.json`, readable even when the rest is in a newer format (ST-12).
struct ScenarioHeader: Decodable {
    let schemaVersion: Int
    let id: UUID
    let name: String
}

// MARK: - RunCount

extension RunCount: Codable {
    private static let untilStoppedToken = "until-stopped"

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let count = try? container.decode(Int.self) {
            self = .times(count.clamped(to: ScenarioLimits.runCount))
            return
        }
        let token = try container.decode(String.self)
        guard token == Self.untilStoppedToken else {
            self = .times(1)
            return
        }
        self = .untilStopped
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .times(count):
            try container.encode(count)
        case .untilStopped:
            try container.encode(Self.untilStoppedToken)
        }
    }
}

// MARK: - StepAction

extension StepAction: Codable {
    private enum CodingKeys: String, CodingKey {
        case kind, button, count, holdMilliseconds, deltaX, deltaY
        case destination, text, key, modifiers
    }

    private enum Kind: String {
        case click, scroll, move, drag, typeText, pressKey
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawKind = try container.decode(String.self, forKey: .kind)
        guard let kind = Kind(rawValue: rawKind) else {
            throw ScenarioDecodingError.unknownActionKind(rawKind)
        }

        switch kind {
        case .click:
            self = .click(
                button: try container.decodeIfPresent(MouseButton.self, forKey: .button) ?? .left,
                count: (try container.decodeIfPresent(Int.self, forKey: .count) ?? 1)
                    .clamped(to: ScenarioLimits.clickCount),
                holdMilliseconds: (try container.decodeIfPresent(Int.self, forKey: .holdMilliseconds) ?? 0)
                    .clamped(to: ScenarioLimits.holdMilliseconds)
            )
        case .scroll:
            self = .scroll(
                deltaX: (try container.decodeIfPresent(Int.self, forKey: .deltaX) ?? 0)
                    .clamped(to: ScenarioLimits.scrollDelta),
                deltaY: (try container.decodeIfPresent(Int.self, forKey: .deltaY) ?? 0)
                    .clamped(to: ScenarioLimits.scrollDelta)
            )
        case .move:
            self = .move
        case .drag:
            self = .drag(
                button: try container.decodeIfPresent(MouseButton.self, forKey: .button) ?? .left,
                destination: try container.decode(StepTarget.self, forKey: .destination)
            )
        case .typeText:
            self = .typeText(try container.decodeIfPresent(String.self, forKey: .text) ?? "")
        case .pressKey:
            self = .pressKey(
                KeyStroke(
                    key: try container.decodeIfPresent(String.self, forKey: .key) ?? "return",
                    modifiers: try container.decodeIfPresent([KeyModifier].self, forKey: .modifiers) ?? []
                )
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .drag(button, destination):
            try container.encode(Kind.drag.rawValue, forKey: .kind)
            try container.encode(button, forKey: .button)
            try container.encode(destination, forKey: .destination)
        case let .typeText(text):
            try container.encode(Kind.typeText.rawValue, forKey: .kind)
            try container.encode(text, forKey: .text)
        case let .pressKey(stroke):
            try container.encode(Kind.pressKey.rawValue, forKey: .kind)
            try container.encode(stroke.key, forKey: .key)
            try container.encode(stroke.modifiers, forKey: .modifiers)
        case let .click(button, count, holdMilliseconds):
            try container.encode(Kind.click.rawValue, forKey: .kind)
            try container.encode(button, forKey: .button)
            try container.encode(count, forKey: .count)
            try container.encode(holdMilliseconds, forKey: .holdMilliseconds)
        case let .scroll(deltaX, deltaY):
            try container.encode(Kind.scroll.rawValue, forKey: .kind)
            try container.encode(deltaX, forKey: .deltaX)
            try container.encode(deltaY, forKey: .deltaY)
        case .move:
            try container.encode(Kind.move.rawValue, forKey: .kind)
        }
    }
}

// MARK: - StepTarget

extension StepTarget: Codable {
    private enum CodingKeys: String, CodingKey {
        case kind, x, y, corner, dx, dy
        case name, text, threshold, searchRegion, waitMilliseconds, onTimeout
    }

    private enum Kind: String {
        case cursor
        case screenPoint
        case windowRelative
        case template
        case text
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawKind = try container.decode(String.self, forKey: .kind)
        guard let kind = Kind(rawValue: rawKind) else {
            throw ScenarioDecodingError.unknownTargetKind(rawKind)
        }

        switch kind {
        case .cursor:
            self = .cursor
        case .screenPoint:
            self = .screenPoint(
                x: try container.decodeIfPresent(Double.self, forKey: .x) ?? 0,
                y: try container.decodeIfPresent(Double.self, forKey: .y) ?? 0
            )
        case .windowRelative:
            self = .windowRelative(
                corner: try container.decodeIfPresent(WindowCorner.self, forKey: .corner) ?? .topLeft,
                dx: try container.decodeIfPresent(Double.self, forKey: .dx) ?? 0,
                dy: try container.decodeIfPresent(Double.self, forKey: .dy) ?? 0
            )
        case .template:
            self = .template(
                name: try container.decodeIfPresent(String.self, forKey: .name) ?? "",
                settings: try Self.decodeSettings(from: container)
            )
        case .text:
            self = .text(
                try container.decodeIfPresent(String.self, forKey: .text) ?? "",
                settings: try Self.decodeSettings(from: container)
            )
        }
    }

    private static func decodeSettings(
        from container: KeyedDecodingContainer<CodingKeys>
    ) throws -> RecognitionSettings {
        RecognitionSettings(
            threshold: try container.decodeIfPresent(Double.self, forKey: .threshold)
                ?? ScenarioLimits.defaultRecognitionThreshold,
            searchRegion: try container.decodeIfPresent(SearchRegion.self, forKey: .searchRegion),
            waitMilliseconds: try container.decodeIfPresent(Int.self, forKey: .waitMilliseconds) ?? 0,
            onTimeout: try container.decodeIfPresent(TimeoutBehaviour.self, forKey: .onTimeout)
                ?? .stopScenario
        )
    }

    private func encodeSettings(
        _ settings: RecognitionSettings,
        into container: inout KeyedEncodingContainer<CodingKeys>
    ) throws {
        try container.encode(settings.threshold, forKey: .threshold)
        try container.encodeIfPresent(settings.searchRegion, forKey: .searchRegion)
        try container.encode(settings.waitMilliseconds, forKey: .waitMilliseconds)
        try container.encode(settings.onTimeout, forKey: .onTimeout)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .cursor:
            try container.encode(Kind.cursor.rawValue, forKey: .kind)
        case let .screenPoint(x, y):
            try container.encode(Kind.screenPoint.rawValue, forKey: .kind)
            try container.encode(x, forKey: .x)
            try container.encode(y, forKey: .y)
        case let .windowRelative(corner, dx, dy):
            try container.encode(Kind.windowRelative.rawValue, forKey: .kind)
            try container.encode(corner, forKey: .corner)
            try container.encode(dx, forKey: .dx)
            try container.encode(dy, forKey: .dy)
        case let .template(name, settings):
            try container.encode(Kind.template.rawValue, forKey: .kind)
            try container.encode(name, forKey: .name)
            try encodeSettings(settings, into: &container)
        case let .text(text, settings):
            try container.encode(Kind.text.rawValue, forKey: .kind)
            try container.encode(text, forKey: .text)
            try encodeSettings(settings, into: &container)
        }
    }
}

// MARK: - Step

extension Step: Codable {
    private enum CodingKeys: String, CodingKey {
        case id, action, target
        case repeatCount = "repeat"
        case delayMillisecondsAfter
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            id: try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID(),
            action: try container.decode(StepAction.self, forKey: .action),
            target: try container.decode(StepTarget.self, forKey: .target),
            repeatCount: try container.decodeIfPresent(Int.self, forKey: .repeatCount) ?? 1,
            delayMillisecondsAfter: try container.decodeIfPresent(
                Int.self,
                forKey: .delayMillisecondsAfter
            ) ?? 0
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(action, forKey: .action)
        try container.encode(target, forKey: .target)
        try container.encode(repeatCount, forKey: .repeatCount)
        try container.encode(delayMillisecondsAfter, forKey: .delayMillisecondsAfter)
    }
}

// MARK: - Scenario

extension Scenario: Codable {
    private enum CodingKeys: String, CodingKey {
        case schemaVersion, id, name, steps, lockedApplication
        case runCount = "repeat"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let version = try container.decodeIfPresent(Int.self, forKey: .schemaVersion)
            ?? ScenarioSchema.currentVersion
        guard version <= ScenarioSchema.currentVersion else {
            throw ScenarioDecodingError.unsupportedSchemaVersion(version)
        }

        self.init(
            id: try container.decodeIfPresent(UUID.self, forKey: .id) ?? UUID(),
            name: try container.decodeIfPresent(String.self, forKey: .name) ?? Scenario.repairedName,
            steps: try container.decodeIfPresent([Step].self, forKey: .steps) ?? [],
            runCount: try container.decodeIfPresent(RunCount.self, forKey: .runCount) ?? .times(1),
            lockedApplication: try container.decodeIfPresent(
                LockedApplication.self,
                forKey: .lockedApplication
            )
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(ScenarioSchema.currentVersion, forKey: .schemaVersion)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(runCount, forKey: .runCount)
        try container.encodeIfPresent(lockedApplication, forKey: .lockedApplication)
        try container.encode(steps, forKey: .steps)
    }
}


// MARK: - SearchRegion

extension SearchRegion: Codable {
    private enum CodingKeys: String, CodingKey {
        case kind, x, y, width, height, corner, dx, dy
    }

    private enum Kind: String {
        case screenRect
        case windowRelative
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let rawKind = try container.decode(String.self, forKey: .kind)
        guard let kind = Kind(rawValue: rawKind) else {
            throw ScenarioDecodingError.unknownTargetKind(rawKind)
        }

        let width = try container.decodeIfPresent(Double.self, forKey: .width) ?? 0
        let height = try container.decodeIfPresent(Double.self, forKey: .height) ?? 0

        switch kind {
        case .screenRect:
            self = .screenRect(
                x: try container.decodeIfPresent(Double.self, forKey: .x) ?? 0,
                y: try container.decodeIfPresent(Double.self, forKey: .y) ?? 0,
                width: width,
                height: height
            )
        case .windowRelative:
            self = .windowRelative(
                corner: try container.decodeIfPresent(WindowCorner.self, forKey: .corner) ?? .topLeft,
                dx: try container.decodeIfPresent(Double.self, forKey: .dx) ?? 0,
                dy: try container.decodeIfPresent(Double.self, forKey: .dy) ?? 0,
                width: width,
                height: height
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case let .screenRect(x, y, width, height):
            try container.encode(Kind.screenRect.rawValue, forKey: .kind)
            try container.encode(x, forKey: .x)
            try container.encode(y, forKey: .y)
            try container.encode(width, forKey: .width)
            try container.encode(height, forKey: .height)
        case let .windowRelative(corner, dx, dy, width, height):
            try container.encode(Kind.windowRelative.rawValue, forKey: .kind)
            try container.encode(corner, forKey: .corner)
            try container.encode(dx, forKey: .dx)
            try container.encode(dy, forKey: .dy)
            try container.encode(width, forKey: .width)
            try container.encode(height, forKey: .height)
        }
    }
}
