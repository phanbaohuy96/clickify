import Foundation
import Testing
@testable import Clickify

@MainActor
private func makeClicker(
    interval: Int = 250,
    repeatCount: Int = 12
) -> SimpleModeModel {
    Catalogs.pinnedToEnglish
    let defaults = UserDefaults(suiteName: "ClickifySimple-\(UUID().uuidString)")!
    defaults.set(interval, forKey: "intervalMilliseconds")
    defaults.set(repeatCount, forKey: "repeatCount")
    return SimpleModeModel(
        runner: makeRunner(recorder: EventRecorder()),
        defaults: defaults
    )
}

/// UI-6: Simple mode is not a second runner — it builds a one-step Scenario.
@MainActor
@Test func simpleModeBuildsASingleStepScenario() throws {
    let clicker = makeClicker(interval: 250, repeatCount: 12)
    clicker.targetMode = .cursor

    let scenario = try #require(clicker.makeScenario())

    #expect(scenario.steps.count == 1)
    #expect(scenario.runCount == .times(1))
    #expect(scenario.steps[0].action == .click(button: .left, count: 1, holdMilliseconds: 0))
    #expect(scenario.steps[0].target == .cursor)
    // The repeat count lives on the Step, not on the Scenario: that is what makes "click 200 times, then save" expressible.
    #expect(scenario.steps[0].repeatCount == 12)
    #expect(scenario.steps[0].delayMillisecondsAfter == 250)
}

@MainActor
@Test func simpleModeRefusesFixedPointModeWithNoPoint() {
    let clicker = makeClicker()
    clicker.targetMode = .fixedPoint

    #expect(clicker.makeScenario() == nil)
    #expect(clicker.validationMessage == "Choose a fixed click point.")
}

@MainActor
@Test func simpleModeCarriesTheLockedApplicationIntoTheScenario() throws {
    let clicker = makeClicker()
    clicker.targetMode = .cursor
    clicker.applicationLockEnabled = true
    clicker.selectedApplicationIdentifier = "com.google.Chrome"

    let scenario = try #require(clicker.makeScenario())

    #expect(scenario.lockedApplication?.bundleIdentifier == "com.google.Chrome")
}

@MainActor
@Test func simpleModeDropsTheLockWhenItIsTurnedOff() throws {
    let clicker = makeClicker()
    clicker.targetMode = .cursor
    clicker.selectedApplicationIdentifier = "com.google.Chrome"
    clicker.applicationLockEnabled = false

    #expect(try #require(clicker.makeScenario()).lockedApplication == nil)
}
