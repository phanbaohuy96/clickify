import CoreGraphics
import Foundation
@testable import Clickify

/// A fake system so the tests can verify `SF-4`, `EX-7`, `EX-10` without opening a real application.
@MainActor
final class FakeSystem {
    static let applicationBundleIdentifier = "com.test.App"
    static let applicationProcessIdentifier: pid_t = 1234

    /// LC-15 fallout: anything that renders a message reads the **global** `Localization.current`, and under
    /// `swift test` that would be `Bundle.main` — the xctest runner, which carries no catalogue, so every
    /// lookup would come back as its own raw key. Pinning here rather than per-test is deliberate: relying on
    /// some *other* test to have pinned it first made the suite pass in parallel and fail when serialised.
    init() { Catalogs.pinnedToEnglish }

    var isAccessibilityTrusted = true
    var runningApplications: [String: pid_t] = [
        FakeSystem.applicationBundleIdentifier: FakeSystem.applicationProcessIdentifier
    ]
    var terminatedProcesses: Set<pid_t> = []
    var frontmostProcessIdentifier: pid_t? = FakeSystem.applicationProcessIdentifier
    var anchorWindowFrame: CGRect? = CGRect(x: 100, y: 200, width: 800, height: 600)
    var processIdentifierAtPoint: pid_t? = FakeSystem.applicationProcessIdentifier
    /// Each process's windows, by title. Sets up the "two processes sharing one bundle id" scene.
    var windowTitles: [pid_t: [String]] = [:]
    /// The frame of one specific titled window, when a test needs to tell two windows apart.
    var frameForWindowTitle: [String: CGRect] = [:]
    /// Which process was asked about, and with which title.
    private(set) var anchorLookups: [(processIdentifier: pid_t, title: String?)] = []
    /// `false` simulates a modal dialog blocking the application from being brought forward.
    var activationSucceeds = true
    private(set) var activations: [pid_t] = []

    var bridge: ScenarioSystemBridge {
        ScenarioSystemBridge(
            isAccessibilityTrusted: { [self] in isAccessibilityTrusted },
            processIdentifier: { [self] bundleIdentifier, preferredWindowTitle in
                let candidates = runningApplications
                    .filter { $0.key == bundleIdentifier }
                    .map(\.value)
                    .sorted()
                if let title = preferredWindowTitle, !title.isEmpty,
                   let owner = candidates.first(where: {
                       windowTitles[$0]?.contains(title) ?? false
                   }) {
                    return owner
                }
                return candidates.first
            },
            isRunning: { [self] in !terminatedProcesses.contains($0) },
            activate: { [self] processIdentifier in
                activations.append(processIdentifier)
                if activationSucceeds { frontmostProcessIdentifier = processIdentifier }
            },
            frontmostProcessIdentifier: { [self] in frontmostProcessIdentifier },
            anchorWindowFrame: { [self] processIdentifier, preferredWindowTitle in
                anchorLookups.append((processIdentifier, preferredWindowTitle))
                if let title = preferredWindowTitle, let frame = frameForWindowTitle[title] {
                    return frame
                }
                return anchorWindowFrame
            },
            processIdentifierAtPoint: { [self] _ in processIdentifierAtPoint }
        )
    }

    static var lockedApplication: LockedApplication {
        LockedApplication(bundleIdentifier: applicationBundleIdentifier, name: "Test App")
    }
}

@MainActor
final class EventRecorder {
    struct Record: Equatable {
        var type: CGEventType
        var clickState: Int64
        var location: CGPoint
        var keyCode: Int64
        var flags: CGEventFlags
        /// The Unicode string attached to a keyboard event — what `EX-24` splits into chunks.
        var unicodeString: String = ""
    }

    private(set) var records: [Record] = []

    var mouseSink: MouseEventEmitter.EventSink {
        { [self] event in append(event) }
    }

    var keyboardSink: KeyboardEventEmitter.EventSink {
        { [self] event in append(event) }
    }

    private func append(_ event: CGEvent) {
        records.append(
            Record(
                type: event.type,
                clickState: event.getIntegerValueField(.mouseEventClickState),
                location: event.location,
                keyCode: event.getIntegerValueField(.keyboardEventKeycode),
                flags: event.flags,
                unicodeString: EventRecorder.unicodeString(of: event)
            )
        )
    }

    var types: [CGEventType] { records.map(\.type) }

    /// The string a `typeText` Step actually sent, reassembled from every chunk.
    var typedText: String {
        records.filter { $0.type == .keyDown }.map(\.unicodeString).joined()
    }

    private static func unicodeString(of event: CGEvent) -> String {
        var length = 0
        var buffer = [UniChar](repeating: 0, count: 256)
        event.keyboardGetUnicodeString(
            maxStringLength: buffer.count,
            actualStringLength: &length,
            unicodeString: &buffer
        )
        guard length > 0 else { return "" }
        return String(utf16CodeUnits: buffer, count: length)
    }
}

/// A fake recogniser: verifies the retry semantics of `EX-8`/`EX-9` without a real screen capture.
@MainActor
final class FakeRecognizer: TargetRecognizing {
    /// On which attempt the target is "seen"; `nil` means never.
    var foundOnAttempt: Int? = 1
    var point = CGPoint(x: 500, y: 400)
    var error: Error?

    private(set) var attempts = 0
    private(set) var regions: [CGRect?] = []
    private(set) var preparedDirectories: [URL?] = []

    func prepare(templatesDirectory: URL?) {
        preparedDirectories.append(templatesDirectory)
    }

    func locate(_ target: StepTarget, within region: CGRect?) async throws -> CGPoint? {
        attempts += 1
        regions.append(region)
        if let error { throw error }
        guard let foundOnAttempt, attempts >= foundOnAttempt else { return nil }
        return point
    }
}

@MainActor
func makeRunner(
    recorder: EventRecorder,
    system: FakeSystem = FakeSystem(),
    recognizer: TargetRecognizing = FakeRecognizer(),
    cursor: CGPoint = CGPoint(x: 7, y: 8),
    countdownSeconds: Int = 0
) -> ScenarioRunner {
    ScenarioRunner(
        resolver: TargetResolver(currentCursorPoint: { cursor }),
        mouse: MouseEventEmitter(sink: recorder.mouseSink),
        keyboard: KeyboardEventEmitter(sink: recorder.keyboardSink),
        system: system.bridge,
        recognizer: recognizer,
        countdownSeconds: countdownSeconds
    )
}

/// Waits until `condition` holds, at most `timeout`. The runner works on the MainActor, so the test has to
/// yield rather than block.
@MainActor
func waitUntil(
    timeout: Duration = .seconds(3),
    _ condition: () -> Bool
) async -> Bool {
    let deadline = ContinuousClock.now + timeout
    while ContinuousClock.now < deadline {
        if condition() { return true }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return false
}

/// A fake recording environment: lets prebuilt `CGEvent`s be injected into `ScenarioRecorder` without a real
/// `CGEventTap`, which means without the Accessibility permission and without manual operation.
@MainActor
final class FakeRecordingEnvironment {
    static let ownProcessIdentifier: pid_t = 99
    static let otherProcessIdentifier: pid_t = 42

    /// See `FakeSystem.init`.
    init() { Catalogs.pinnedToEnglish }

    var frontmostProcessIdentifier: pid_t? = FakeRecordingEnvironment.otherProcessIdentifier
    var anchorWindowFrame: CGRect? = CGRect(x: 100, y: 100, width: 400, height: 300)
    /// The anchor window title the recorder will read.
    var anchorWindowTitle: String? = "Test Window"
    var applications: [pid_t: LockedApplication] = [
        FakeRecordingEnvironment.otherProcessIdentifier:
            LockedApplication(bundleIdentifier: "com.test.Ghi", name: "App Ghi")
    ]
    var doubleClickInterval: TimeInterval = 0.5
    /// Clickify's own windows, in top-left-origin screen coordinates like `CGEvent.location`.
    var ownWindowRects: [CGRect] = []
    /// The process owning the window under the cursor; by default the application being recorded.
    var processIdentifierAtPoint: pid_t? = FakeRecordingEnvironment.otherProcessIdentifier
    /// A clock driven by the test: every event picks its own moment.
    var now: TimeInterval = 1_000

    var environment: RecordingEnvironment {
        RecordingEnvironment(
            ownProcessIdentifier: { FakeRecordingEnvironment.ownProcessIdentifier },
            frontmostProcessIdentifier: { [self] in frontmostProcessIdentifier },
            pointIsInOwnWindow: { [self] (point: CGPoint) in
                ownWindowRects.contains { $0.contains(point) }
            },
            processIdentifierAtPoint: { [self] _ in processIdentifierAtPoint },
            anchorWindowFrame: { [self] _ in anchorWindowFrame },
            anchorWindowTitle: { [self] _ in anchorWindowTitle },
            application: { [self] in applications[$0] },
            doubleClickInterval: { [self] in doubleClickInterval },
            now: { [self] in now }
        )
    }
}

/// Builds real `CGEvent`s (creating an event needs no permission, only posting one does) so the tests go through
/// the same decoding path `ScenarioRecorder` uses for events from the tap.
enum TestEvent {
    static func mouse(_ type: CGEventType, at point: CGPoint, button: CGMouseButton = .left) -> CGEvent {
        CGEvent(
            mouseEventSource: nil,
            mouseType: type,
            mouseCursorPosition: point,
            mouseButton: button
        )!
    }

    /// `kCGScrollWheelEventDeltaAxis1` is the vertical axis, `Axis2` the horizontal one — per `CGEventTypes.h`.
    static func scroll(deltaX: Int, deltaY: Int, at point: CGPoint) -> CGEvent {
        let event = CGEvent(
            scrollWheelEvent2Source: nil,
            units: .line,
            wheelCount: 2,
            wheel1: Int32(deltaY),
            wheel2: Int32(deltaX),
            wheel3: 0
        )!
        event.setIntegerValueField(.scrollWheelEventDeltaAxis1, value: Int64(deltaY))
        event.setIntegerValueField(.scrollWheelEventDeltaAxis2, value: Int64(deltaX))
        event.location = point
        return event
    }
}
