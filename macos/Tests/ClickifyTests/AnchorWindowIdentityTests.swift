import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// A recording has to name a **window**, not just an application.
///
/// Found while preparing `D10` of the manual tests: the machine had two Chrome processes open — the user's
/// everyday Chrome and the measurement rig's isolated Chrome session. Both processes share a
/// `bundleIdentifier`, so `runningApplications.first` returned the user's process and a recording made on the
/// scratch window would replay onto the logged-in window. The same hole exists in a milder form with **one**
/// process and several windows: whichever window is focused at run time is the one that gets hit.
@MainActor
@Suite struct AnchorWindowIdentityTests {
    /// The recorder has to remember the window title, otherwise there is nothing to pick the right window with at run time.
    @Test func aRecordingRemembersWhichWindowItWasMadeOn() {
        let environment = FakeRecordingEnvironment()
        environment.anchorWindowTitle = "Draft — unsaved"
        var finished: RecordingAssembler.Result?
        let recorder = ScenarioRecorder(environment: environment.environment)
        recorder.onFinished = { finished = $0 }

        let point = CGPoint(x: 150, y: 150)
        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: point))
        environment.now += 0.05
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: point))
        recorder.finishSession()

        #expect(finished?.scenario.lockedApplication?.windowTitle == "Draft — unsaved")
    }

    /// Between two processes sharing a bundle id, pick the one that has the recorded window open.
    @Test func theProcessOwningTheRecordedWindowWinsOverTheFirstOneListed() async {
        let events = EventRecorder()
        let system = FakeSystem()
        let intruder: pid_t = 1          // started first, so it heads the list
        let recorded: pid_t = 2
        system.runningApplications = [FakeSystem.applicationBundleIdentifier: recorded]
        system.windowTitles = [intruder: ["Inbox — signed in"], recorded: ["Draft"]]
        system.frameForWindowTitle = ["Draft": CGRect(x: 500, y: 400, width: 300, height: 200)]
        // SF-3 requires the click to land in the locked application itself, so the target under the cursor is it too.
        system.processIdentifierAtPoint = recorded

        let runner = makeRunner(recorder: events, system: system)
        var locked = FakeSystem.lockedApplication
        locked.windowTitle = "Draft"

        let scenario = Scenario(
            name: "Anchored to a window",
            steps: [
                Step(
                    action: .move,
                    target: .windowRelative(corner: .topLeft, dx: 10, dy: 20),
                    delayMillisecondsAfter: 0
                )
            ],
            lockedApplication: locked
        )

        #expect(runner.start(scenario))
        #expect(await waitUntil { !runner.isRunning })

        // The frame has to come from the recorded window, not from the focused one.
        #expect(events.records.first?.location == CGPoint(x: 510, y: 420))
        #expect(system.anchorLookups.contains { $0.title == "Draft" })
    }

    /// An old recording with no title still runs exactly as before; it must not be refused.
    @Test func aRecordingWithoutAWindowTitleStillResolvesTheOldWay() async {
        let events = EventRecorder()
        let system = FakeSystem()
        system.anchorWindowFrame = CGRect(x: 700, y: 300, width: 800, height: 600)
        let runner = makeRunner(recorder: events, system: system)

        let scenario = Scenario(
            name: "Old recording",
            steps: [
                Step(
                    action: .move,
                    target: .windowRelative(corner: .topLeft, dx: 120, dy: 88),
                    delayMillisecondsAfter: 0
                )
            ],
            lockedApplication: FakeSystem.lockedApplication   // windowTitle = nil
        )

        #expect(runner.start(scenario))
        #expect(await waitUntil { !runner.isRunning })
        #expect(events.records.first?.location == CGPoint(x: 820, y: 388))
        #expect(system.anchorLookups.allSatisfy { $0.title == nil })
    }

    /// The title is only a **preference**: a window that has been renamed must still run, not stall.
    @Test func aTitleThatNoLongerMatchesFallsBackInsteadOfRefusingToRun() async {
        let events = EventRecorder()
        let system = FakeSystem()
        system.anchorWindowFrame = CGRect(x: 700, y: 300, width: 800, height: 600)
        system.windowTitles = [FakeSystem.applicationProcessIdentifier: ["A completely new title"]]
        let runner = makeRunner(recorder: events, system: system)

        var locked = FakeSystem.lockedApplication
        locked.windowTitle = "Title at recording time"

        let scenario = Scenario(
            name: "Title changed",
            steps: [
                Step(
                    action: .move,
                    target: .windowRelative(corner: .topLeft, dx: 120, dy: 88),
                    delayMillisecondsAfter: 0
                )
            ],
            lockedApplication: locked
        )

        #expect(runner.start(scenario))
        #expect(await waitUntil { !runner.isRunning })
        #expect(events.records.first?.location == CGPoint(x: 820, y: 388))
    }

    /// The title has to survive the coding round trip, otherwise it is lost as soon as it is saved.
    @Test func theWindowTitleSurvivesASaveAndLoad() throws {
        var locked = FakeSystem.lockedApplication
        locked.windowTitle = "Draft — unsaved"
        let scenario = Scenario(name: "Round trip", steps: [], lockedApplication: locked)

        let data = try JSONEncoder().encode(scenario)
        let decoded = try JSONDecoder().decode(Scenario.self, from: data)

        #expect(decoded.lockedApplication?.windowTitle == "Draft — unsaved")
    }

    /// An old file **without** the `windowTitle` key still has to load (ST-10).
    @Test func aScenarioFileFromBeforeThisFieldStillLoads() throws {
        let json = """
        {
          "schemaVersion": 1,
          "id": "11111111-1111-4111-8111-111111111111",
          "name": "Old file",
          "repeat": 1,
          "steps": [],
          "lockedApplication": { "bundleIdentifier": "com.test.App", "name": "Test App" }
        }
        """
        let decoded = try JSONDecoder().decode(Scenario.self, from: Data(json.utf8))
        #expect(decoded.lockedApplication?.bundleIdentifier == "com.test.App")
        #expect(decoded.lockedApplication?.windowTitle == nil)
    }
}
