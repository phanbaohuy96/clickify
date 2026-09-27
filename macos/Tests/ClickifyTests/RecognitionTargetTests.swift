import CoreGraphics
import Foundation
import Testing
@testable import Clickify

private func templateStep(
    wait: Int,
    onTimeout: TimeoutBehaviour,
    region: SearchRegion? = nil,
    repeatCount: Int = 1
) -> Step {
    Step(
        action: .move,
        target: .template(
            name: "ok-button.png",
            settings: RecognitionSettings(
                threshold: 0.9,
                searchRegion: region,
                waitMilliseconds: wait,
                onTimeout: onTimeout
            )
        ),
        repeatCount: repeatCount,
        delayMillisecondsAfter: 0
    )
}

@MainActor
@Test func aTemplateTargetMovesToWhereItWasFound() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    recognizer.point = CGPoint(x: 640, y: 480)
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    #expect(runner.start(Scenario(name: "Image", steps: [templateStep(wait: 0, onTimeout: .stopScenario)])))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.map(\.location) == [CGPoint(x: 640, y: 480)])
}

/// EX-8: this is where "wait for the Save button to appear, then press it" is expressed — as a timeout, not as
/// a conditional loop.
@MainActor
@Test func aTemplateTargetRetriesUntilTheTargetAppears() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    recognizer.foundOnAttempt = 3
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    #expect(runner.start(Scenario(name: "Waiting", steps: [templateStep(wait: 5_000, onTimeout: .stopScenario)])))
    #expect(await waitUntil(timeout: .seconds(5)) { !runner.isRunning })

    #expect(recognizer.attempts == 3)
    #expect(recorder.records.count == 1)
}

@MainActor
@Test func aTemplateTargetTriesExactlyOnceWhenItIsNotAskedToWait() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    recognizer.foundOnAttempt = nil
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    #expect(runner.start(Scenario(name: "No wait", steps: [templateStep(wait: 0, onTimeout: .skipStep)])))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recognizer.attempts == 1)
}

@MainActor
@Test func aMissingTemplateStopsTheScenarioWhenAskedTo() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    recognizer.foundOnAttempt = nil
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    let scenario = Scenario(
        name: "Stop",
        steps: [
            templateStep(wait: 0, onTimeout: .stopScenario),
            Step(action: .move, target: .screenPoint(x: 1, y: 1), delayMillisecondsAfter: 0)
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recorder.records.isEmpty)
    #expect(runner.statusText.contains("ok-button.png"))
}

/// EX-9: "skip the step" skips **all** the remaining repetitions of that Step, not just this one.
@MainActor
@Test func skipOnTimeoutAbandonsEveryRemainingRepeatOfThatStep() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    recognizer.foundOnAttempt = nil
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    let scenario = Scenario(
        name: "Skip",
        steps: [
            templateStep(wait: 0, onTimeout: .skipStep, repeatCount: 5),
            Step(action: .move, target: .screenPoint(x: 9, y: 9), delayMillisecondsAfter: 0)
        ]
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    // Exactly one attempt, then all 5 repetitions are skipped — but the next Step still runs.
    #expect(recognizer.attempts == 1)
    #expect(recorder.records.map(\.location) == [CGPoint(x: 9, y: 9)])
}

// MARK: - Search region

/// RG-7: with no region drawn, the scope narrows to the Anchor window when there is a Locked application.
@MainActor
@Test func withoutASearchRegionTheAnchorWindowIsUsed() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    let system = FakeSystem()
    system.anchorWindowFrame = CGRect(x: 100, y: 100, width: 800, height: 600)
    let runner = makeRunner(recorder: recorder, system: system, recognizer: recognizer)

    let scenario = Scenario(
        name: "Narrowed",
        steps: [templateStep(wait: 0, onTimeout: .stopScenario)],
        lockedApplication: FakeSystem.lockedApplication
    )

    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recognizer.regions == [CGRect(x: 100, y: 100, width: 800, height: 600)])
}

/// [ADR-0006]: image Targets work without a Locked application — the scope is then all displays.
@MainActor
@Test func aTemplateTargetWorksWithNoLockedApplication() async {
    let recorder = EventRecorder()
    let recognizer = FakeRecognizer()
    let runner = makeRunner(recorder: recorder, recognizer: recognizer)

    let scenario = Scenario(name: "Unlocked", steps: [templateStep(wait: 0, onTimeout: .stopScenario)])

    #expect(runner.validate(scenario) == nil)
    #expect(runner.start(scenario))
    #expect(await waitUntil { !runner.isRunning })

    #expect(recognizer.regions == [nil])
    #expect(recorder.records.count == 1)
}

@Test func aWindowRelativeSearchRegionResolvesAgainstTheAnchorWindow() {
    let resolver = TargetResolver(currentCursorPoint: { .zero })
    let frame = CGRect(x: 700, y: 300, width: 800, height: 600)

    let region = resolver.resolve(
        .windowRelative(corner: .topLeft, dx: 20, dy: 30, width: 400, height: 120),
        anchorWindowFrame: frame
    )

    #expect(region == CGRect(x: 720, y: 330, width: 400, height: 120))
}

/// [ADR-0006]: a window-relative region that cannot be resolved falls back to the default scope rather than
/// reporting an error — a Search region never forces a Scenario to have a Locked application.
@Test func aWindowRelativeSearchRegionFallsBackInsteadOfFailing() {
    let resolver = TargetResolver(currentCursorPoint: { .zero })

    let region = resolver.resolve(
        .windowRelative(corner: .topLeft, dx: 20, dy: 30, width: 400, height: 120),
        anchorWindowFrame: nil
    )

    #expect(region == nil)
}

// MARK: - Storage

@Test func recognitionTargetsSurviveARoundTrip() throws {
    let scenario = Scenario(
        name: "Recognition",
        steps: [
            Step(
                action: .click(button: .left, count: 1, holdMilliseconds: 0),
                target: .template(
                    name: "ok-button.png",
                    settings: RecognitionSettings(
                        threshold: 0.85,
                        searchRegion: .windowRelative(
                            corner: .bottomRight, dx: -300, dy: -120, width: 280, height: 100
                        ),
                        waitMilliseconds: 10_000,
                        onTimeout: .skipStep
                    )
                )
            ),
            Step(
                action: .move,
                target: .text(
                    "Confirm",
                    settings: RecognitionSettings(
                        searchRegion: .screenRect(x: 10, y: 20, width: 300, height: 200)
                    )
                )
            )
        ]
    )

    let data = try JSONEncoder().encode(scenario)
    #expect(try JSONDecoder().decode(Scenario.self, from: data) == scenario)
}

@Test func aTemplateTargetNamesTheFileItNeeds() {
    let scenario = Scenario(
        name: "t",
        steps: [
            Step(action: .move, target: .template(name: "a.png", settings: RecognitionSettings())),
            Step(
                action: .drag(
                    button: .left,
                    destination: .template(name: "b.png", settings: RecognitionSettings())
                ),
                target: .cursor
            )
        ]
    )

    #expect(scenario.templateNames == ["a.png", "b.png"])
}

/// RG-23: after cropping a Template, the default Search region hugs the area just captured, not the whole screen.
@MainActor
struct SuggestedSearchRegionTests {
    private let screen = CGRect(x: 0, y: 0, width: 1512, height: 982)

    @Test func theSuggestedRegionSurroundsTheCapturedRect() {
        let captured = CGRect(x: 400, y: 300, width: 200, height: 100)
        let suggested = TemplateCaptureCoordinator.suggestedSearchRect(around: captured, within: screen)

        // It has to cover the Template completely, otherwise the very first run misses.
        #expect(suggested.contains(captured))
        // And it has to be well smaller than the whole screen, otherwise it suggests nothing at all.
        #expect(suggested.width * suggested.height < screen.width * screen.height / 4)
    }

    /// A tiny Template — a game icon — still needs room to breathe, not a region hugging its edges.
    @Test func aTinyTemplateStillGetsRoomToMove() {
        let tiny = CGRect(x: 700, y: 500, width: 26, height: 26)
        let suggested = TemplateCaptureCoordinator.suggestedSearchRect(around: tiny, within: screen)

        #expect(suggested.width >= tiny.width + 96)
        #expect(suggested.height >= tiny.height + 96)
    }

    /// A capture at the screen edge has its suggested region clipped rather than spilling into negative coordinates.
    @Test func aCaptureAtTheEdgeIsClampedToTheScreen() {
        let corner = CGRect(x: 0, y: 0, width: 120, height: 60)
        let suggested = TemplateCaptureCoordinator.suggestedSearchRect(around: corner, within: screen)

        #expect(suggested.minX == 0)
        #expect(suggested.minY == 0)
        #expect(screen.contains(suggested))
        #expect(suggested.contains(corner))
    }

    /// The padding is capped: a very large template must not stretch the search region to the whole screen.
    @Test func thePaddingIsCappedSoALargeTemplateDoesNotSelectTheWholeScreen() {
        let large = CGRect(x: 300, y: 200, width: 800, height: 500)
        let suggested = TemplateCaptureCoordinator.suggestedSearchRect(around: large, within: screen)

        #expect(suggested.width <= large.width + 320)
        #expect(suggested.height <= large.height + 320)
    }
}
