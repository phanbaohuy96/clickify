import AppKit
import CoreGraphics
import Foundation

struct RunProgress: Equatable, Sendable {
    /// The current Scenario iteration, counted from 1.
    var iteration: Int
    /// `nil` when the Scenario runs unlimited (UI-14).
    var totalIterations: Int?
    /// The current Step, counted from 1.
    var stepIndex: Int
    var stepCount: Int
}

enum ScenarioRunError: LocalizedError, Equatable {
    case emptyScenario
    case accessibilityDenied
    case missingLockedApplication
    case lockedApplicationNotRunning(String)
    case lockedApplicationTerminated
    case pointOutsideLockedApplication
    case anchorWindowUnavailable(String)
    case activationFailed(String)
    case unknownKey(String)
    case targetNotFound(String)
    case screenCaptureFailed(ScreenCaptureError?)

    var errorDescription: String? {
        switch self {
        case .emptyScenario:
            return localized(.errorEmptyScenario)
        case .accessibilityDenied:
            // SF-10: an app update changes the signature, macOS invalidates the old grant but still
            // shows the toggle as on. Saying only "please grant the permission" makes the user open
            // System Settings, see it already enabled, and conclude the app is broken.
            return localized(.errorAccessibilityDenied)
        case .missingLockedApplication:
            return localized(.errorMissingLockedApplication)
        case let .lockedApplicationNotRunning(name):
            return localized(.errorLockedApplicationNotRunning, name)
        case .lockedApplicationTerminated:
            return localized(.errorLockedApplicationTerminated)
        case .pointOutsideLockedApplication:
            return localized(.errorPointOutsideLockedApplication)
        case let .anchorWindowUnavailable(name):
            // EX-25: the most common cause is the window being minimised under the Dock. Not saying so
            // sends the user looking in the wrong place.
            return localized(.errorAnchorWindowUnavailable, name)
        case let .activationFailed(name):
            return localized(.errorActivationFailed, name)
        case let .unknownKey(key):
            return localized(.errorUnknownKey, key)
        case let .targetNotFound(description):
            return localized(.errorTargetNotFound, description)
        case let .screenCaptureFailed(cause):
            return cause?.errorDescription ?? localized(.errorScreenCaptureFailed)
        }
    }
}

/// The application's one and only Scenario runner.
///
/// Simple mode has no runner of its own: it builds a one-step Scenario and calls in here
/// (ADR-0002, UI-6). That way the most error-prone part — emitting events, checking the Locked application,
/// releasing the mouse button on stop — exists in exactly one copy.
@MainActor
final class ScenarioRunner: ObservableObject {
    static let defaultCountdownSeconds = 3

    @Published private(set) var isRunning = false {
        didSet { onRunningStateChanged?(isRunning) }
    }
    @Published private(set) var countdown: Int?
    @Published private(set) var progress: RunProgress?
    /// LC-6: an enum, so the status line is translated when it is drawn. A stored string would keep the
    /// language the run happened to end in, however many times the user switched afterwards.
    @Published private(set) var message: Message?

    /// How a run ended, or why it could not start.
    enum Message: Equatable {
        case stopped
        case failed(ScenarioRunError)
        /// Anything thrown that is not a `ScenarioRunError`; its text comes from the system, already localised.
        case unexpected(String)
        case completedSteps(Int)
        case completedIterations(Int)

        var text: String {
            switch self {
            case .stopped: return localized(.runStopped)
            case let .failed(error): return error.errorDescription ?? localized(.runStopped)
            case let .unexpected(reason): return localized(.runFailed, reason)
            case let .completedSteps(count): return localized(.runCompletedSteps, count)
            case let .completedIterations(count): return localized(.runCompletedIterations, count)
            }
        }

        var isError: Bool {
            switch self {
            case .failed, .unexpected: return true
            case .stopped, .completedSteps, .completedIterations: return false
            }
        }
    }
    /// UI-16: whether `message` is an error. It used to be just a string, so the interface could not tell
    /// UI-16: an error state has to look like an error, so this is read straight off the message.
    var messageIsError: Bool { message?.isError ?? false }
    @Published private(set) var runningScenarioName: String?

    var onRunningStateChanged: ((Bool) -> Void)?

    private var task: Task<Void, Never>?
    private let mouse: MouseEventEmitter
    private let keyboard: KeyboardEventEmitter
    private let resolver: TargetResolver
    private let system: ScenarioSystemBridge
    private let recognizer: TargetRecognizing
    /// Escapes every remaining repetition of a Step (EX-9).
    private struct SkipStep: Error {}

    /// How many seconds to count down before emitting the first event (EX-1). Tests set it to 0.
    let countdownSeconds: Int

    init(
        resolver: TargetResolver = .live,
        mouse: MouseEventEmitter = MouseEventEmitter(),
        keyboard: KeyboardEventEmitter = KeyboardEventEmitter(),
        system: ScenarioSystemBridge = .live,
        recognizer: TargetRecognizing = ScreenTargetRecognizer(),
        countdownSeconds: Int = ScenarioRunner.defaultCountdownSeconds
    ) {
        self.resolver = resolver
        self.mouse = mouse
        self.keyboard = keyboard
        self.system = system
        self.recognizer = recognizer
        self.countdownSeconds = countdownSeconds
    }

    /// The Templates directory of the Scenario about to run; set before calling `start`.
    var templatesDirectory: URL? {
        didSet { recognizer.prepare(templatesDirectory: templatesDirectory) }
    }

    var statusText: String {
        if let countdown {
            return localized(.runCountdown, countdown)
        }
        if let progress {
            let step = localized(.runProgressStep, progress.stepIndex, progress.stepCount)
            if let total = progress.totalIterations {
                return total == 1
                    ? step
                    : localized(.runProgressIterationOfTotal, progress.iteration, total, step)
            }
            return localized(.runProgressIteration, progress.iteration, step)
        }
        return message?.text ?? localized(.runReady)
    }

    /// Checks everything that has to be true *before* the first event is emitted (EX-2).
    func validate(_ scenario: Scenario) -> ScenarioRunError? {
        guard !scenario.steps.isEmpty else { return .emptyScenario }

        guard let locked = scenario.lockedApplication else {
            // DM-18: a window-relative Target cannot be resolved without a Locked application.
            return scenario.requiresLockedApplication ? .missingLockedApplication : nil
        }
        guard system.processIdentifier(locked.bundleIdentifier, locked.windowTitle) != nil else {
            return .lockedApplicationNotRunning(locked.name)
        }
        return nil
    }

    @discardableResult
    func start(_ scenario: Scenario) -> Bool {
        guard !isRunning else { return false }

        if let error = validate(scenario) {
            message = .failed(error)
            return false
        }

        guard system.isAccessibilityTrusted() else {
            message = .failed(.accessibilityDenied)
            return false
        }

        var lockedProcessIdentifier: pid_t?
        if let locked = scenario.lockedApplication,
           let processIdentifier = system.processIdentifier(
               locked.bundleIdentifier,
               locked.windowTitle
           ) {
            system.activate(processIdentifier)
            lockedProcessIdentifier = processIdentifier
        }

        message = nil
        progress = nil
        runningScenarioName = scenario.name
        isRunning = true

        task = Task { [weak self] in
            await self?.run(scenario, lockedProcessIdentifier: lockedProcessIdentifier)
        }
        return true
    }

    /// EX-27: whether an input-source override is still in force. Only a test has any business asking — the
    /// obligation itself is discharged in `finish`, unconditionally.
    var keyboardInputSourceIsOverridden: Bool { keyboard.inputSource.isActive }

    /// Every path that stops a run goes through here (EX-13).
    func stop() {
        task?.cancel()
        task = nil
        finish(with: .stopped)
    }

    private func finish(with message: Message?) {
        // Runs unconditionally, even once the task has been cancelled (SF-1, SF-2).
        mouse.releaseAllHeld()
        // EX-27: the same obligation for the input source. `⌥⌘S` can cut a key-by-key string part-way through,
        // and leaving the user on ABC afterwards is the same class of mistake as leaving a button held down.
        keyboard.inputSource.restore()
        countdown = nil
        progress = nil
        runningScenarioName = nil
        isRunning = false
        if let message {
            self.message = message
        }
    }

    private func run(_ scenario: Scenario, lockedProcessIdentifier: pid_t?) async {
        var outcome: Message?
        defer {
            task = nil
            finish(with: outcome)
        }

        let context = RunContext(
            lockedProcessIdentifier: lockedProcessIdentifier,
            lockedApplicationName: scenario.lockedApplication?.name
                ?? localized(.editorLockApplication),
            lockedWindowTitle: scenario.lockedApplication?.windowTitle
        )

        do {
            for second in stride(from: countdownSeconds, through: 1, by: -1) {
                countdown = second
                try await Task.sleep(for: .seconds(1))
            }
            countdown = nil

            var iteration = 0
            while true {
                if let total = scenario.runCount.totalIterations, iteration >= total { break }
                try Task.checkCancellation()
                iteration += 1

                for (index, step) in scenario.steps.enumerated() {
                    try Task.checkCancellation()
                    progress = RunProgress(
                        iteration: iteration,
                        totalIterations: scenario.runCount.totalIterations,
                        stepIndex: index + 1,
                        stepCount: scenario.steps.count
                    )
                    try await perform(step, in: context)
                }
            }

            outcome = completionMessage(for: scenario)
        } catch is CancellationError {
            outcome = .stopped
        } catch {
            outcome = .unexpected(error.localizedDescription)
        }
    }

    private struct RunContext {
        let lockedProcessIdentifier: pid_t?
        let lockedApplicationName: String
        /// The **Anchor window** title at recording time, if the recording remembers one. Used to pick the right
        /// window in an application with several open.
        let lockedWindowTitle: String?
    }

    private func completionMessage(for scenario: Scenario) -> Message {
        switch scenario.runCount {
        case .times(1):
            return .completedSteps(scenario.steps.count)
        case let .times(count):
            return .completedIterations(count)
        case .untilStopped:
            return .stopped
        }
    }

    private func perform(_ step: Step, in context: RunContext) async throws {
        for _ in 0..<step.repeatCount {
            try Task.checkCancellation()

            if let processIdentifier = context.lockedProcessIdentifier,
               !system.isRunning(processIdentifier) {
                throw ScenarioRunError.lockedApplicationTerminated
            }

            do {
                if step.action.isKeyboard {
                    try await bringLockedApplicationToFront(in: context)
                    try await applyKeyboard(step.action)
                } else {
                    try await applyPointer(step.action, step: step, in: context)
                }
            } catch is SkipStep {
                // EX-9: skip **all** the remaining repetitions of the Step, not just this one.
                return
            }

            try await sleepBetweenEvents(step.delayMillisecondsAfter)
        }
    }

    // MARK: - Events carrying coordinates

    private func applyPointer(
        _ action: StepAction,
        step: Step,
        in context: RunContext
    ) async throws {
        let anchorFrame = context.lockedProcessIdentifier.flatMap {
            system.anchorWindowFrame($0, context.lockedWindowTitle)
        }
        let point = try await resolve(step.target, step: step, anchorFrame: anchorFrame, in: context)
        try authorize(point, in: context)

        switch action {
        case .move:
            mouse.move(to: point)

        case let .scroll(deltaX, deltaY):
            mouse.scroll(deltaX: deltaX, deltaY: deltaY, at: point)

        case let .click(button, count, holdMilliseconds):
            for clickState in 1...count {
                mouse.press(button, at: point, clickState: clickState)
                if holdMilliseconds > 0 {
                    try await Task.sleep(for: .milliseconds(holdMilliseconds))
                }
                mouse.release(button, at: point, clickState: clickState)
                if clickState < count {
                    try await Task.sleep(
                        for: .milliseconds(ScenarioLimits.interClickGapMilliseconds)
                    )
                }
            }

        case let .drag(button, destination):
            let end = try await resolve(destination, step: step, anchorFrame: anchorFrame, in: context)
            try authorize(end, in: context)
            try await drag(button, from: point, to: end)

        case .typeText, .pressKey:
            // The keyboard branch was split off in `perform`.
            break
        }
    }

    /// EX-20: many applications ignore a drag if the cursor jumps straight from start to end.
    ///
    /// EX-23: only the first and last points are checked against `EX-10`. Asking Accessibility at every
    /// intermediate point would make the drag stutter and could stop it part-way.
    private func drag(_ button: MouseButton, from start: CGPoint, to end: CGPoint) async throws {
        mouse.press(button, at: start, clickState: 1)
        defer { mouse.releaseAllHeld() }

        let steps = ScenarioLimits.dragIntermediateSteps
        for index in 1...steps {
            try Task.checkCancellation()
            let progress = Double(index) / Double(steps)
            mouse.dragMove(
                button,
                to: CGPoint(
                    x: start.x + (end.x - start.x) * progress,
                    y: start.y + (end.y - start.y) * progress
                )
            )
            try await Task.sleep(for: .milliseconds(ScenarioLimits.dragStepGapMilliseconds))
        }

        mouse.release(button, at: end, clickState: 1)
    }

    private func resolve(
        _ target: StepTarget,
        step: Step,
        anchorFrame: CGRect?,
        in context: RunContext
    ) async throws -> CGPoint {
        do {
            return try resolver.resolve(target, anchorWindowFrame: anchorFrame)
        } catch TargetResolutionError.anchorWindowUnavailable {
            throw ScenarioRunError.anchorWindowUnavailable(context.lockedApplicationName)
        } catch TargetResolutionError.requiresRecognition {
            return try await locate(target, step: step, anchorFrame: anchorFrame)
        }
    }

    /// EX-8: retry at a cadence equal to the Step's delay, at least 150 ms, until the timeout expires. This is
    /// where "wait for the Save button to appear, then press it" is expressed — no control flow needed.
    private func locate(
        _ target: StepTarget,
        step: Step,
        anchorFrame: CGRect?
    ) async throws -> CGPoint {
        guard let settings = target.recognitionSettings else {
            throw ScenarioRunError.targetNotFound(StepSummary.target(target))
        }
        let region = resolver.resolve(settings.searchRegion, anchorWindowFrame: anchorFrame)
        let deadline = ContinuousClock.now + .milliseconds(settings.waitMilliseconds)
        let retryGap = max(
            step.delayMillisecondsAfter,
            ScenarioLimits.recognitionRetryFloorMilliseconds
        )

        while true {
            try Task.checkCancellation()
            do {
                if let point = try await recognizer.locate(target, within: region) { return point }
            } catch let error as ScreenCaptureError {
                throw ScenarioRunError.screenCaptureFailed(error)
            }

            if ContinuousClock.now >= deadline { break }
            try await Task.sleep(for: .milliseconds(retryGap))
        }

        switch settings.onTimeout {
        case .stopScenario:
            throw ScenarioRunError.targetNotFound(StepSummary.target(target))
        case .skipStep:
            throw SkipStep()
        }
    }

    private func authorize(_ point: CGPoint, in context: RunContext) throws {
        let route = ClickRoutingPolicy.route(
            targetProcessIdentifier: context.lockedProcessIdentifier,
            processIdentifierAtPoint: context.lockedProcessIdentifier == nil
                ? nil
                : system.processIdentifierAtPoint(point)
        )
        guard route != nil else { throw ScenarioRunError.pointOutsideLockedApplication }
    }

    // MARK: - Keyboard events

    private func applyKeyboard(_ action: StepAction) async throws {
        switch action {
        case let .typeText(text):
            try await keyboard.type(text)
        case let .pressKey(stroke):
            guard keyboard.press(stroke) else {
                throw ScenarioRunError.unknownKey(stroke.key)
            }
        default:
            break
        }
    }

    /// SF-4: keyboard events carry no coordinates, so `EX-10` cannot protect them. If a notification steals
    /// focus part-way through, the scenario types into the wrong application — so bring the Locked application
    /// to the front first, and stop outright if it cannot be brought forward.
    private func bringLockedApplicationToFront(in context: RunContext) async throws {
        guard let processIdentifier = context.lockedProcessIdentifier else { return }
        if system.frontmostProcessIdentifier() == processIdentifier { return }

        system.activate(processIdentifier)

        let deadline = ContinuousClock.now
            + .milliseconds(ScenarioLimits.activationTimeoutMilliseconds)
        while ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(20))
            if system.frontmostProcessIdentifier() == processIdentifier { return }
        }

        throw ScenarioRunError.activationFailed(context.lockedApplicationName)
    }

    /// Yield at least `minimumEventGapMilliseconds` even when the Step has no delay (SF-8).
    private func sleepBetweenEvents(_ milliseconds: Int) async throws {
        try await Task.sleep(
            for: .milliseconds(max(milliseconds, ScenarioLimits.minimumEventGapMilliseconds))
        )
    }
}
