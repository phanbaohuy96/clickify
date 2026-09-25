import Foundation
import Testing
@testable import Clickify

private func roundTrip(_ scenario: Scenario) throws -> Scenario {
    let data = try JSONEncoder().encode(scenario)
    return try JSONDecoder().decode(Scenario.self, from: data)
}

@Test func scenarioSurvivesARoundTrip() throws {
    let scenario = Scenario(
        name: "Bulk delete",
        steps: [
            Step(
                action: .click(button: .right, count: 2, holdMilliseconds: 250),
                target: .screenPoint(x: 820, y: 410),
                repeatCount: 3,
                delayMillisecondsAfter: 200
            ),
            Step(action: .scroll(deltaX: 0, deltaY: -3), target: .cursor),
            Step(action: .move, target: .screenPoint(x: 1, y: 2))
        ],
        runCount: .times(50),
        lockedApplication: LockedApplication(bundleIdentifier: "com.google.Chrome", name: "Chrome")
    )

    #expect(try roundTrip(scenario) == scenario)
}

@Test func unlimitedRunCountIsWrittenAsAToken() throws {
    let scenario = Scenario(name: "Unlimited", steps: [], runCount: .untilStopped)
    let json = String(data: try JSONEncoder().encode(scenario), encoding: .utf8) ?? ""

    #expect(json.contains("\"until-stopped\""))
    #expect(try roundTrip(scenario).runCount == .untilStopped)
}

/// ST-7: coordinates have to be separate `x`/`y` fields. `CGPoint` encodes as `[1,2]` by default.
@Test func screenPointIsWrittenAsNamedFields() throws {
    let scenario = Scenario(
        name: "t",
        steps: [Step(action: .move, target: .screenPoint(x: 820, y: 410))]
    )
    let json = String(data: try JSONEncoder().encode(scenario), encoding: .utf8) ?? ""

    #expect(json.contains("\"x\":820"))
    #expect(json.contains("\"y\":410"))
}

/// DM-20 / ST-10: out-of-range values are clamped on read rather than breaking the whole Scenario.
@Test func outOfRangeValuesAreClampedOnRead() throws {
    let json = """
    {
      "schemaVersion": 1,
      "id": "\(UUID().uuidString)",
      "name": "Corrupt",
      "repeat": 99999999,
      "steps": [
        {
          "id": "\(UUID().uuidString)",
          "action": { "kind": "click", "button": "left", "count": 400, "holdMilliseconds": -5 },
          "target": { "kind": "screenPoint", "x": 1, "y": 2 },
          "repeat": 0,
          "delayMillisecondsAfter": 99999999
        }
      ]
    }
    """
    let scenario = try JSONDecoder().decode(Scenario.self, from: Data(json.utf8))

    #expect(scenario.runCount == .times(ScenarioLimits.runCount.upperBound))
    #expect(scenario.steps[0].repeatCount == ScenarioLimits.stepRepeatCount.lowerBound)
    #expect(scenario.steps[0].delayMillisecondsAfter == ScenarioLimits.delayMilliseconds.upperBound)
    #expect(
        scenario.steps[0].action
            == .click(button: .left, count: ScenarioLimits.clickCount.upperBound, holdMilliseconds: 0)
    )
}

@Test func missingOptionalFieldsFallBackToDefaults() throws {
    let json = """
    { "schemaVersion": 1, "name": "Minimal",
      "steps": [ { "action": { "kind": "move" }, "target": { "kind": "cursor" } } ] }
    """
    let scenario = try JSONDecoder().decode(Scenario.self, from: Data(json.utf8))

    #expect(scenario.runCount == .times(1))
    #expect(scenario.lockedApplication == nil)
    #expect(scenario.steps.count == 1)
    #expect(scenario.steps[0].repeatCount == 1)
}

@Test func newerSchemaVersionsAreRejectedRatherThanMisread() {
    let json = """
    { "schemaVersion": 99, "id": "\(UUID().uuidString)", "name": "From the future", "steps": [] }
    """

    #expect(throws: ScenarioDecodingError.unsupportedSchemaVersion(99)) {
        try JSONDecoder().decode(Scenario.self, from: Data(json.utf8))
    }
}

@Test func unknownActionKindsAreRejected() {
    let json = """
    { "schemaVersion": 1, "name": "t",
      "steps": [ { "action": { "kind": "teleport" }, "target": { "kind": "cursor" } } ] }
    """

    #expect(throws: ScenarioDecodingError.unknownActionKind("teleport")) {
        try JSONDecoder().decode(Scenario.self, from: Data(json.utf8))
    }
}
