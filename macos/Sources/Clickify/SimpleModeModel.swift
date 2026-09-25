import AppKit
import Foundation

/// The form state of **Simple mode**.
///
/// This is not a runner. Pressing Start here builds a one-step Scenario and hands it to `ScenarioRunner` — the
/// same runner the editor window uses (ADR-0002, UI-6). That Scenario is a temporary object and is never saved
/// to disk (UI-7).
@MainActor
final class SimpleModeModel: ObservableObject {
    /// A piece of feedback chosen when it happens and translated when it is drawn (LC-6).
    enum Message: Equatable {
        case validationFailed
        case pickingPoint
        case pointCancelled
        case pointSaved(CGPoint)
    }

    @Published var intervalText: String {
        didSet { saveValidSettings() }
    }

    @Published var repeatText: String {
        didSet { saveValidSettings() }
    }

    @Published var targetMode: ClickTargetMode {
        didSet { defaults.set(targetMode.rawValue, forKey: "targetMode") }
    }

    @Published var applicationLockEnabled: Bool {
        didSet { defaults.set(applicationLockEnabled, forKey: "applicationLockEnabled") }
    }

    @Published var selectedApplicationIdentifier: String {
        didSet { saveSelectedApplication() }
    }

    @Published private(set) var fixedPoint: CGPoint?
    @Published private(set) var selectedApplicationName: String
    @Published private(set) var runningApplications: [RunningApplicationOption] = []
    /// Feedback for point picking. The *running* state lives in `ScenarioRunner`.
    ///
    /// LC-6: an **enum**, not a rendered string. A string stored here would be frozen in whichever language
    /// was active when the event happened, and would still be in it after the user switched.
    @Published private(set) var message: Message?

    /// What `message` says, in the language that is active **now**.
    var messageText: String? {
        switch message {
        case nil: return nil
        case .validationFailed: return validationMessage
        case .pickingPoint: return localized(.simplePickingPoint)
        case .pointCancelled: return localized(.simplePointCancelled)
        case let .pointSaved(point): return localized(.simplePointSaved, Self.describe(point))
        }
    }

    private var pointSelector: ClickPointSelector?
    private let defaults: UserDefaults
    private let runner: ScenarioRunner

    init(runner: ScenarioRunner, defaults: UserDefaults = .standard) {
        self.runner = runner
        self.defaults = defaults
        let interval = defaults.object(forKey: "intervalMilliseconds") as? Int ?? 100
        let repeatCount = defaults.object(forKey: "repeatCount") as? Int ?? 10
        let storedMode = defaults.string(forKey: "targetMode")
            .flatMap(ClickTargetMode.init(rawValue:)) ?? .cursor

        intervalText = String(interval)
        repeatText = String(repeatCount)
        targetMode = storedMode
        applicationLockEnabled = defaults.bool(forKey: "applicationLockEnabled")
        selectedApplicationIdentifier = defaults.string(forKey: "selectedApplicationIdentifier") ?? ""
        selectedApplicationName = defaults.string(forKey: "selectedApplicationName") ?? ""

        if defaults.object(forKey: "fixedPointX") != nil,
           defaults.object(forKey: "fixedPointY") != nil {
            fixedPoint = CGPoint(
                x: defaults.double(forKey: "fixedPointX"),
                y: defaults.double(forKey: "fixedPointY")
            )
        } else {
            fixedPoint = nil
        }

        refreshRunningApplications()
    }

    var validationMessage: String? {
        switch SettingsValidator.validate(intervalText: intervalText, repeatText: repeatText) {
        case .success:
            if targetMode == .fixedPoint, fixedPoint == nil {
                return localized(.errorFixedPointMissing)
            }

            if let error = ApplicationLockValidator.validate(
                isEnabled: applicationLockEnabled,
                selectedBundleIdentifier: selectedApplicationIdentifier,
                isApplicationRunning: selectedRunningApplication != nil
            ) {
                return error.errorDescription
            }
            return nil
        case let .failure(error):
            return error.errorDescription
        }
    }

    var fixedPointDescription: String {
        guard let fixedPoint else { return localized(.simplePointNone) }
        return Self.describe(fixedPoint)
    }

    /// The application shown in the lock picker, or a placeholder when none has been chosen yet.
    var selectedApplicationDisplayName: String {
        selectedApplicationName.isEmpty
            ? localized(.simpleApplicationFallbackName)
            : selectedApplicationName
    }

    private static func describe(_ point: CGPoint) -> String {
        localized(.simplePointCoordinates, Int(point.x.rounded()), Int(point.y.rounded()))
    }

    var selectedApplicationIsRunning: Bool {
        selectedRunningApplication != nil
    }

    func refreshRunningApplications() {
        runningApplications = RunningApplicationOption.current()
    }

    /// Builds the one-step Scenario equivalent to the current form (UI-6).
    func makeScenario() -> Scenario? {
        guard case let .success(settings) = SettingsValidator.validate(
            intervalText: intervalText,
            repeatText: repeatText
        ) else { return nil }

        let target: StepTarget
        switch targetMode {
        case .cursor:
            target = .cursor
        case .fixedPoint:
            guard let fixedPoint else { return nil }
            target = .screenPoint(x: fixedPoint.x, y: fixedPoint.y)
        }

        var lockedApplication: LockedApplication?
        if applicationLockEnabled, !selectedApplicationIdentifier.isEmpty {
            lockedApplication = LockedApplication(
                bundleIdentifier: selectedApplicationIdentifier,
                name: selectedApplicationName
            )
        }

        return Scenario(
            name: localized(.simpleScenarioName),
            steps: [
                Step(
                    action: .click(button: .left, count: 1, holdMilliseconds: 0),
                    target: target,
                    repeatCount: settings.repeatCount,
                    delayMillisecondsAfter: settings.intervalMilliseconds
                )
            ],
            runCount: .times(1),
            lockedApplication: lockedApplication
        )
    }

    @discardableResult
    func start() -> Bool {
        guard let scenario = makeScenario() else {
            message = .validationFailed
            return false
        }
        message = nil
        return runner.start(scenario)
    }

    func chooseFixedPoint(returningTo returnWindow: NSWindow?) {
        guard !runner.isRunning else { return }

        message = .pickingPoint
        let selector = ClickPointSelector()
        pointSelector = selector

        selector.start { [weak self] point in
            guard let self else { return }
            self.pointSelector = nil
            NSApp.activate(ignoringOtherApps: true)
            returnWindow?.makeKeyAndOrderFront(nil)

            guard let point else {
                self.message = .pointCancelled
                return
            }

            self.fixedPoint = point
            self.targetMode = .fixedPoint
            self.defaults.set(Double(point.x), forKey: "fixedPointX")
            self.defaults.set(Double(point.y), forKey: "fixedPointY")
            self.message = .pointSaved(point)
        }
    }

    func openAccessibilitySettings() {
        guard let url = URL(
            string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
        ) else { return }
        NSWorkspace.shared.open(url)
    }

    private func saveValidSettings() {
        guard case let .success(settings) = SettingsValidator.validate(
            intervalText: intervalText,
            repeatText: repeatText
        ) else { return }

        defaults.set(settings.intervalMilliseconds, forKey: "intervalMilliseconds")
        defaults.set(settings.repeatCount, forKey: "repeatCount")
    }

    private var selectedRunningApplication: NSRunningApplication? {
        guard !selectedApplicationIdentifier.isEmpty else { return nil }
        return NSWorkspace.shared.runningApplications.first {
            $0.bundleIdentifier == selectedApplicationIdentifier && !$0.isTerminated
        }
    }

    private func saveSelectedApplication() {
        defaults.set(selectedApplicationIdentifier, forKey: "selectedApplicationIdentifier")
        if let selectedOption = runningApplications.first(where: {
            $0.bundleIdentifier == selectedApplicationIdentifier
        }) {
            selectedApplicationName = selectedOption.name
            defaults.set(selectedOption.name, forKey: "selectedApplicationName")
        }
    }
}
