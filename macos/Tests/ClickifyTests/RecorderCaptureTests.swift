import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// The event-capture half of `ScenarioRecorder`: decoding a `CGEvent`, excluding our own events,
/// and sampling the Anchor window at the right moment.
///
/// This is the path that used to be reachable only through a real `CGEventTap`. The tests inject prebuilt
/// `CGEvent`s straight into `handle`, so they prove `RC-2`, `RC-12`, `RC-13` on a machine with no permission.
@MainActor
struct RecorderCaptureTests {
    private func makeRecorder(
        _ fake: FakeRecordingEnvironment
    ) -> (ScenarioRecorder, () -> RecordingAssembler.Result?) {
        let recorder = ScenarioRecorder(environment: fake.environment)
        var captured: RecordingAssembler.Result?
        recorder.onFinished = { captured = $0 }
        return (recorder, { captured })
    }

    private func click(
        _ recorder: ScenarioRecorder,
        _ fake: FakeRecordingEnvironment,
        at point: CGPoint,
        downAt downTime: TimeInterval,
        upAt upTime: TimeInterval
    ) {
        fake.now = downTime
        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: point))
        fake.now = upTime
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: point))
    }

    /// RC-2: pressing "Finish" on the floating panel while recording must not become a Step.
    ///
    /// The floating panel is an `NSPanel` of kind `.nonactivatingPanel`: clicking it does **not** make Clickify
    /// the frontmost application, so the "who is in front" test still returns the application being recorded and
    /// the click lands straight in the recording. The project owner hit exactly this: finish recording, press
    /// Finish, and the recording has a spurious click Step right where the button is.
    @Test func aClickOnTheFloatingPanelIsNotRecordedEvenThoughItDoesNotActivateTheApp() throws {
        let fake = FakeRecordingEnvironment()
        // Just like the real thing: the frontmost app is still the one being recorded, not Clickify.
        #expect(fake.frontmostProcessIdentifier == FakeRecordingEnvironment.otherProcessIdentifier)
        fake.ownWindowRects = [CGRect(x: 500, y: 40, width: 390, height: 86)]
        let (recorder, result) = makeRecorder(fake)

        // A real click on the application being recorded…
        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        // …then pressing "Finish" on the floating panel.
        click(recorder, fake, at: CGPoint(x: 690, y: 83), downAt: 1_001, upAt: 1_001.05)
        recorder.finishSession()

        #expect(recorder.recordedGestureCount == 1)
        let scenario = try #require(result()?.scenario)
        #expect(scenario.steps.count == 1)
        #expect(scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 50, dy: 40))
    }

    /// RC-2: Clickify being the **frontmost** application must not lose a click on another application.
    ///
    /// The real user flow: open the popover, press "Record". The popover closes but Clickify is still the
    /// frontmost application, so the old test ("who is in front") swallowed **the first operation of every
    /// recording**, silently. Measured on the real app: three clicks produced two Steps, and the missing one was always the first.
    ///
    /// And the Locked application has to be the one **under the cursor**, not Clickify — attributing it wrongly
    /// makes the whole session look like it spans two applications and loses window-relative Targets entirely.
    @Test func theFirstClickIsKeptEvenWhenClickifyIsStillTheFrontmostApp() throws {
        let fake = FakeRecordingEnvironment()
        fake.frontmostProcessIdentifier = FakeRecordingEnvironment.ownProcessIdentifier
        fake.processIdentifierAtPoint = FakeRecordingEnvironment.otherProcessIdentifier
        let (recorder, result) = makeRecorder(fake)

        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        // By the second click the other application has come forward, as it would in reality.
        fake.frontmostProcessIdentifier = FakeRecordingEnvironment.otherProcessIdentifier
        click(recorder, fake, at: CGPoint(x: 250, y: 140), downAt: 1_001, upAt: 1_001.05)
        recorder.finishSession()

        #expect(recorder.recordedGestureCount == 2)
        let outcome = try #require(result())
        #expect(outcome.scenario.steps.count == 2)
        #expect(outcome.scenario.lockedApplication?.bundleIdentifier == "com.test.Ghi")
        // A single application, so no warning, and the Target is raised to window-relative.
        #expect(outcome.warning == nil)
        #expect(outcome.scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 50, dy: 40))
    }

    @Test func aPlainClickBecomesOneStepAnchoredToTheWindow() {
        let fake = FakeRecordingEnvironment()
        let (recorder, result) = makeRecorder(fake)

        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        recorder.finishSession()

        let scenario = try! #require(result()?.scenario)
        #expect(scenario.steps.count == 1)
        #expect(scenario.steps[0].action == .click(button: .left, count: 1, holdMilliseconds: 0))
        // RC-13: there is a Locked application and a window frame, so the Target is raised to relative.
        #expect(scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 50, dy: 40))
        #expect(scenario.lockedApplication?.bundleIdentifier == "com.test.Ghi")
        #expect(result()?.warning == nil)
    }

    @Test func eventsBelongingToClickifyItselfAreNeverRecorded() {
        let fake = FakeRecordingEnvironment()
        // The right condition is **the click point falling inside one of our own windows**, not "we are in front".
        fake.ownWindowRects = [CGRect(x: 100, y: 100, width: 200, height: 120)]
        let (recorder, result) = makeRecorder(fake)

        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        recorder.finishSession()

        // RC-2: a click on Clickify's own window must not get into the recording.
        #expect(recorder.recordedGestureCount == 0)
        #expect(result() == nil)
        #expect(recorder.message == .nothingCaptured)
    }

    @Test func theScrollAxesAreNotSwapped() {
        let fake = FakeRecordingEnvironment()
        let (recorder, result) = makeRecorder(fake)

        recorder.handle(
            type: .scrollWheel,
            event: TestEvent.scroll(deltaX: 3, deltaY: -7, at: CGPoint(x: 200, y: 200))
        )
        recorder.finishSession()

        // Swapping the two axes is a silent bug: the scenario still runs, it just scrolls the wrong way.
        let scenario = try! #require(result()?.scenario)
        #expect(scenario.steps[0].action == .scroll(deltaX: 3, deltaY: -7))
    }

    @Test func theAnchorWindowIsSampledAtGestureStartNotAtRelease() {
        let fake = FakeRecordingEnvironment()
        let (recorder, result) = makeRecorder(fake)

        fake.now = 1_000
        recorder.handle(
            type: .leftMouseDown,
            event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 150, y: 140))
        )
        // The window moves while the button is held; the Target must be computed from the frame at the start.
        fake.anchorWindowFrame = CGRect(x: 700, y: 700, width: 400, height: 300)
        fake.now = 1_000.05
        recorder.handle(
            type: .leftMouseUp,
            event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 150, y: 140))
        )
        recorder.finishSession()

        let scenario = try! #require(result()?.scenario)
        #expect(scenario.steps[0].target == .windowRelative(corner: .topLeft, dx: 50, dy: 40))
    }

    @Test func aSessionSpanningTwoApplicationsFallsBackToAbsolutePointsAndSaysSo() {
        let fake = FakeRecordingEnvironment()
        fake.applications[7] = LockedApplication(bundleIdentifier: "com.test.Other", name: "Other App")
        let (recorder, result) = makeRecorder(fake)

        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        fake.frontmostProcessIdentifier = 7
        click(recorder, fake, at: CGPoint(x: 160, y: 150), downAt: 1_002, upAt: 1_002.05)
        recorder.finishSession()

        // RC-14: no Locked application is right for the whole session, so it has to say so rather than stay silent.
        let unwrapped = try! #require(result())
        #expect(unwrapped.scenario.lockedApplication == nil)
        #expect(unwrapped.scenario.steps.allSatisfy { !$0.target.needsAnchorWindow })
        #expect(unwrapped.warning == .spansSeveralApplications(2))
    }

    @Test func aDisabledTapIsReEnabledWithoutPollutingTheRecording() {
        let fake = FakeRecordingEnvironment()
        let (recorder, result) = makeRecorder(fake)

        recorder.handle(
            type: .tapDisabledByTimeout,
            event: TestEvent.mouse(.leftMouseDown, at: .zero)
        )
        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        recorder.finishSession()

        // An event reporting the tap was disabled is not a user operation.
        #expect(result()?.scenario.steps.count == 1)
    }

    @Test func theRealGapBetweenGesturesSurvivesIntoTheScenario() {
        let fake = FakeRecordingEnvironment()
        let (recorder, result) = makeRecorder(fake)

        click(recorder, fake, at: CGPoint(x: 150, y: 140), downAt: 1_000, upAt: 1_000.05)
        click(recorder, fake, at: CGPoint(x: 300, y: 250), downAt: 1_002.55, upAt: 1_002.6)
        recorder.finishSession()

        // ADR-0004: keep the 2.5 seconds the user really waited, uncut.
        let steps = try! #require(result()?.scenario.steps)
        #expect(steps[0].delayMillisecondsAfter == 2_500)
        #expect(steps[1].delayMillisecondsAfter == 0)
    }
}

/// `SF-6` / ADR-0003: the recorder must **never** listen to the keyboard.
///
/// This is the project's strongest safety constraint and it used to have no test holding it: the event mask lived
/// inside the body of `startSession`, where only a real `CGEventTap` ever reached it. Someone adding `.keyDown`
/// to "record keys too, for convenience" would turn Clickify into a system-wide keylogger, drag in the Input
/// Monitoring permission, and a password typed while recording would sit verbatim in `scenario.json`.
@MainActor
struct RecorderNeverListensToTheKeyboardTests {
    private func listens(to type: CGEventType) -> Bool {
        ScenarioRecorder.eventMask & (1 << type.rawValue) != 0
    }

    @Test func theEventMaskContainsNoKeyboardEvent() {
        #expect(!listens(to: .keyDown))
        #expect(!listens(to: .keyUp))
        #expect(!listens(to: .flagsChanged))

        // And it must really listen to the mouse, otherwise the assertion above holds vacuously.
        #expect(listens(to: .leftMouseDown))
        #expect(listens(to: .scrollWheel))
    }

    /// The second layer: even if the mask were widened, the decoding path still would not turn keys into Steps.
    @Test func aKeyboardEventFedStraightIntoTheRecorderProducesNothing() {
        let fake = FakeRecordingEnvironment()
        let recorder = ScenarioRecorder(environment: fake.environment)
        var captured: RecordingAssembler.Result?
        recorder.onFinished = { captured = $0 }

        let key = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true)!
        key.keyboardSetUnicodeString(stringLength: 6, unicodeString: Array("matkhau".utf16))
        recorder.handle(type: .keyDown, event: key)
        recorder.handle(type: .keyUp, event: CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false)!)
        recorder.finishSession()

        #expect(recorder.recordedGestureCount == 0)
        #expect(captured == nil)
    }
}

/// UI-16 (same family): `"Recorded N steps."` is a **success**, and must not carry the error icon.
///
/// The recorder's message was poured into the same line as the reasons a Scenario cannot run, and that line was
/// always drawn with an orange `exclamationmark.triangle.fill`. So a successful recording still looked like a failure.
@MainActor
struct RecorderMessageSeverityTests {
    @Test func aSuccessfulRecordingDoesNotAskForAttention() throws {
        let fake = FakeRecordingEnvironment()
        let recorder = ScenarioRecorder(environment: fake.environment)

        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 150, y: 140)))
        fake.now += 0.05
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 150, y: 140)))
        recorder.finishSession()

        // LC-6: the assertion is on the message's **identity**, not on its words. Before Slice 6 this
        // compared Vietnamese prose, so correcting a typo in the interface broke a test about icons.
        #expect(recorder.message == .recorded(1))
        #expect(recorder.messageNeedsAttention == false)
    }

    @Test func recordingNothingDoesAskForAttention() {
        let fake = FakeRecordingEnvironment()
        fake.ownWindowRects = [CGRect(x: 100, y: 100, width: 200, height: 120)]
        let recorder = ScenarioRecorder(environment: fake.environment)

        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 150, y: 140)))
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 150, y: 140)))
        recorder.finishSession()

        #expect(recorder.message == .nothingCaptured)
        #expect(recorder.messageNeedsAttention)
    }

    /// A recording spanning two applications really does deserve attention: Targets fall back to absolute coordinates.
    @Test func aRecordingSpanningTwoApplicationsDoesAskForAttention() {
        let fake = FakeRecordingEnvironment()
        fake.applications[7] = LockedApplication(bundleIdentifier: "com.test.Hai", name: "App Hai")
        let recorder = ScenarioRecorder(environment: fake.environment)

        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 150, y: 140)))
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 150, y: 140)))
        fake.now += 1
        fake.frontmostProcessIdentifier = 7
        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 400, y: 400)))
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 400, y: 400)))
        recorder.finishSession()

        #expect(recorder.messageNeedsAttention)
    }
}

/// RC-20: the default name **is** the sort key, so its timestamp has to sort chronologically.
///
/// `ScenarioStore` orders the list with `name.localizedCaseInsensitiveCompare`. Under the previous
/// `"dd/MM HH:mm"` the day came first, so a recording made in October sorted ahead of one made in September.
@MainActor
struct RecordingNameOrderingTests {
    private func recordedName(at components: DateComponents) throws -> String {
        let fake = FakeRecordingEnvironment()
        fake.now = try #require(Calendar(identifier: .gregorian).date(from: components))
            .timeIntervalSinceReferenceDate
        let recorder = ScenarioRecorder(environment: fake.environment)
        var captured: RecordingAssembler.Result?
        recorder.onFinished = { captured = $0 }

        recorder.handle(type: .leftMouseDown, event: TestEvent.mouse(.leftMouseDown, at: CGPoint(x: 150, y: 140)))
        recorder.handle(type: .leftMouseUp, event: TestEvent.mouse(.leftMouseUp, at: CGPoint(x: 150, y: 140)))
        recorder.finishSession()

        return try #require(captured).scenario.name
    }

    @Test func aLaterRecordingSortsAfterAnEarlierOneAcrossAMonthBoundary() throws {
        let september = try recordedName(
            at: DateComponents(year: 2026, month: 9, day: 17, hour: 14, minute: 30)
        )
        let october = try recordedName(
            at: DateComponents(year: 2026, month: 10, day: 1, hour: 9, minute: 5)
        )

        #expect(september.hasSuffix("2026-09-17 14:30"))
        #expect(october.hasSuffix("2026-10-01 09:05"))
        #expect(september.localizedCaseInsensitiveCompare(october) == .orderedAscending)
    }
}
