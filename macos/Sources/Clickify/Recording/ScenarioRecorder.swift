import AppKit
import CoreGraphics
import Foundation
import os

/// Records the user's real mouse operations into a **Scenario** (RC-1…RC-17).
///
/// Watches the mouse only. Recording the keyboard would require the Input Monitoring permission and turn the
/// application into a system-wide keylogger — see [ADR-0003].
@MainActor
final class ScenarioRecorder: ObservableObject {
    @Published private(set) var isRecording = false {
        didSet { onRecordingStateChanged?(isRecording) }
    }
    @Published private(set) var recordedGestureCount = 0
    /// LC-6: an enum, so the line is translated when it is drawn rather than when recording ended.
    @Published private(set) var message: Message?

    /// A piece of feedback from a Recording session.
    enum Message: Equatable {
        case accessibilityDenied
        case eventTapFailed
        case nothingCaptured
        case recorded(Int)
        case warning(RecordingAssembler.Warning)

        var text: String {
            switch self {
            case .accessibilityDenied: return localized(.errorAccessibilityDenied)
            case .eventTapFailed: return localized(.recordTapFailed)
            case .nothingCaptured: return localized(.recordNothingCaptured)
            case let .recorded(count): return localized(.recordFinished, count)
            case let .warning(warning): return warning.text
            }
        }

        /// Whether this is something the user has to act on.
        ///
        /// `"Recorded 3 steps."` is a **success**. Poured into the same line as the reasons a Scenario cannot
        /// run, it appeared with an orange triangle and looked exactly like a failure — the same bug as
        /// `UI-16` on the status line.
        var needsAttention: Bool {
            if case .recorded = self { return false }
            return true
        }
    }

    var messageText: String? { message?.text }
    var messageNeedsAttention: Bool { message?.needsAttention ?? false }

    var onRecordingStateChanged: ((Bool) -> Void)?
    /// Called when a recording session ends with at least one Step (RC-16, RC-17).
    var onFinished: ((RecordingAssembler.Result) -> Void)?

    private static let logger = Logger(subsystem: "com.local.Clickify", category: "Recorder")

    private let environment: RecordingEnvironment
    private var eventTap: CFMachPort?
    private var runLoopSource: CFRunLoopSource?
    private var events: [RecordedEvent] = []
    /// Whether the current gesture is being skipped because it landed on Clickify itself (`RC-2`).
    private var ignoringGesture = false

    init(environment: RecordingEnvironment = .live) {
        self.environment = environment
    }

    // MARK: - Lifecycle

    @discardableResult
    func start() -> Bool {
        guard !isRecording else { return false }
        guard AXIsProcessTrusted() else {
            message = .accessibilityDenied
            return false
        }

        guard let tap = CGEvent.tapCreate(
            tap: .cgSessionEventTap,
            place: .headInsertEventTap,
            options: .listenOnly,
            eventsOfInterest: Self.eventMask,
            callback: { _, type, event, context in
                guard let context else { return Unmanaged.passUnretained(event) }
                let recorder = Unmanaged<ScenarioRecorder>.fromOpaque(context).takeUnretainedValue()
                MainActor.assumeIsolated { recorder.handle(type: type, event: event) }
                return Unmanaged.passUnretained(event)
            },
            userInfo: Unmanaged.passUnretained(self).toOpaque()
        ) else {
            message = .eventTapFailed
            return false
        }

        let source = CFMachPortCreateRunLoopSource(kCFAllocatorDefault, tap, 0)
        // Attached to the main run loop so that asking Accessibility inside the callback is legal (RC-18).
        CFRunLoopAddSource(CFRunLoopGetMain(), source, .commonModes)
        CGEvent.tapEnable(tap: tap, enable: true)

        eventTap = tap
        runLoopSource = source
        events.removeAll()
        recordedGestureCount = 0
        message = nil
        isRecording = true
        return true
    }

    func stop() {
        guard isRecording else { return }
        teardown()
        isRecording = false
        finishSession()
    }

    /// Split out of `stop()` so tests can exercise the whole recording path without a real `CGEventTap`.
    func finishSession() {
        let recorded = RecordingInterpreter.steps(
            from: events,
            doubleClickInterval: environment.doubleClickInterval()
        )
        events.removeAll()

        guard !recorded.isEmpty else {
            // RC-17: no Step recorded means no Scenario is created.
            message = .nothingCaptured
            return
        }

        let result = RecordingAssembler.scenario(
            named: defaultName(for: recorded),
            from: recorded,
            lockedApplication: lockedApplication(for: recorded)
        )
        message = result.warning.map(Message.warning) ?? .recorded(recorded.count)
        onFinished?(result)
    }

    private func teardown() {
        if let eventTap {
            CGEvent.tapEnable(tap: eventTap, enable: false)
        }
        if let runLoopSource {
            CFRunLoopRemoveSource(CFRunLoopGetMain(), runLoopSource, .commonModes)
        }
        eventTap = nil
        runLoopSource = nil
    }

    // MARK: - What is listened to

    /// The event types the recorder listens to. **Mouse and scroll only.**
    ///
    /// `SF-6` / [ADR-0003]: adding `keyDown`, `keyUp` or `flagsChanged` here turns Clickify into a system-wide
    /// keylogger — it would see every key pressed in **every** application, drag in the Input Monitoring
    /// permission, and a password the user types while recording would sit verbatim in `scenario.json`.
    /// Emitting keys is fine (Accessibility alone); **capturing** them is not.
    ///
    /// Pulled out into a constant so `theEventMaskContainsNoKeyboardEvent` can guard it — it used to live inside
    /// the body of `startSession`, where only a real `CGEventTap` reached it, which means no test held it.
    static let recordedEventTypes: [CGEventType] = [
        .leftMouseDown, .leftMouseUp, .leftMouseDragged,
        .rightMouseDown, .rightMouseUp, .rightMouseDragged,
        .otherMouseDown, .otherMouseUp, .otherMouseDragged,
        .scrollWheel
    ]

    static let eventMask: CGEventMask = recordedEventTypes
        .reduce(into: CGEventMask(0)) { $0 |= 1 << $1.rawValue }

    // MARK: - Event capture

    func handle(type: CGEventType, event: CGEvent) {
        if type == .tapDisabledByTimeout || type == .tapDisabledByUserInput {
            // The system disables the tap when the callback runs too long; re-enable it rather than silently ceasing to record.
            if let eventTap { CGEvent.tapEnable(tap: eventTap, enable: true) }
            Self.logger.warning("Event tap was disabled; re-enabled")
            return
        }

        guard let kind = Self.kind(of: type, event: event) else { return }

        let isGestureStart: Bool
        switch kind {
        case .mouseDown, .scroll: isGestureStart = true
        case .mouseUp, .mouseDragged: isGestureStart = false
        }

        var processIdentifier: pid_t?
        var windowFrame: CGRect?
        var windowTitle: String?
        if isGestureStart {
            // RC-2: skip exactly the clicks that **land in Clickify's own windows**, and only those.
            ignoringGesture = environment.pointIsInOwnWindow(event.location)
            if ignoringGesture { return }

            // The application owning the gesture. Usually the frontmost one — but right after the user presses
            // the "Record" button, the frontmost application is **Clickify**, so it has to be asked by
            // coordinate. Otherwise the first click is attributed to ourselves, the whole session looks like it
            // spans two applications, and window-relative Targets are lost.
            let owner = environment.frontmostProcessIdentifier()
            processIdentifier = owner == environment.ownProcessIdentifier()
                ? environment.processIdentifierAtPoint(event.location)
                : owner
            if let processIdentifier {
                windowFrame = environment.anchorWindowFrame(processIdentifier)
                windowTitle = environment.anchorWindowTitle(processIdentifier)
            }
            recordedGestureCount += 1
        } else if ignoringGesture {
            // Drop the tail of a skipped gesture too, otherwise the recording gets an orphan `mouseUp`.
            return
        }

        events.append(
            RecordedEvent(
                kind: kind,
                location: event.location,
                timestamp: environment.now(),
                processIdentifier: processIdentifier ?? events.last?.processIdentifier,
                windowFrame: windowFrame ?? events.last?.windowFrame,
                windowTitle: windowTitle ?? events.last?.windowTitle
            )
        )
    }

    private static func kind(of type: CGEventType, event: CGEvent) -> RecordedEvent.Kind? {
        switch type {
        case .leftMouseDown: return .mouseDown(.left)
        case .leftMouseUp: return .mouseUp(.left)
        case .leftMouseDragged: return .mouseDragged(.left)
        case .rightMouseDown: return .mouseDown(.right)
        case .rightMouseUp: return .mouseUp(.right)
        case .rightMouseDragged: return .mouseDragged(.right)
        case .otherMouseDown: return .mouseDown(.center)
        case .otherMouseUp: return .mouseUp(.center)
        case .otherMouseDragged: return .mouseDragged(.center)
        case .scrollWheel:
            return .scroll(
                deltaX: Int(event.getIntegerValueField(.scrollWheelEventDeltaAxis2)),
                deltaY: Int(event.getIntegerValueField(.scrollWheelEventDeltaAxis1))
            )
        default:
            return nil
        }
    }

    // MARK: - Naming and locking the application

    private func lockedApplication(for recorded: [RecordedStep]) -> LockedApplication? {
        guard let processIdentifier = RecordingAssembler.singleProcessIdentifier(in: recorded)
        else { return nil }
        guard var application = environment.application(processIdentifier) else { return nil }
        // Remember which **window** too, not just which application: a bundle id alone cannot name a window.
        // Take the title at the moment of the first gesture, not at the end — part-way through, the user may
        // switch tabs or open a different file.
        application.windowTitle = recorded.first?.windowTitle
        return application
    }

    /// RC-16: the default name comes from the application and the time of recording.
    private func defaultName(for recorded: [RecordedStep]) -> String {
        let timestamp = Self.timestampFormatter
            .string(from: Date(timeIntervalSinceReferenceDate: environment.now()))

        guard let processIdentifier = RecordingAssembler.singleProcessIdentifier(in: recorded),
              let application = environment.application(processIdentifier)
        else {
            return "\(localized(.recordDefaultName)) \(timestamp)"
        }
        return "\(application.name) \(timestamp)"
    }

    /// RC-20: a **sortable** timestamp, frozen to `en_US_POSIX`.
    ///
    /// `ScenarioStore` orders the list with `name.localizedCaseInsensitiveCompare`, so the name **is** the sort
    /// key. The previous `"dd/MM HH:mm"` sorts by day-of-month first, which puts a recording made on `01/10`
    /// ahead of one made on `17/09` — the list of recordings was in no useful order at all.
    ///
    /// The format is deliberately **not** taken from the user's locale. A name goes into `scenario.json` and
    /// stays there; deriving it from an interface setting would mean the same recording is named differently on
    /// two machines, and would reintroduce the same wrong ordering in every locale that puts the day first.
    private static let timestampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter
    }()
}
