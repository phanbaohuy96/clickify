import AppKit
import CoreGraphics
import Foundation

/// Everything recording needs to ask the operating system, gathered in one place — the same way as `ScenarioSystemBridge`.
///
/// Split out because the event-capture path only runs with a real `CGEventTap`, and a real tap requires the
/// Accessibility permission and manual operation by the user. With this seam, tests inject prebuilt `CGEvent`s
/// straight into `ScenarioRecorder.handle` and verify `RC-2`, `RC-12`, `RC-13` on a machine with no permission.
@MainActor
struct RecordingEnvironment {
    var ownProcessIdentifier: () -> pid_t
    var frontmostProcessIdentifier: () -> pid_t?
    /// Whether this point falls inside one of Clickify's own windows (`RC-2`).
    ///
    /// It cannot be replaced by `frontmostProcessIdentifier`: the floating panel shown while recording is an
    /// `NSPanel` of kind `.nonactivatingPanel`, so clicking it does **not** make Clickify the frontmost
    /// application, and asking "who is in front" still returns the other application — the "Finish" click then lands straight in the recording.
    var pointIsInOwnWindow: (CGPoint) -> Bool
    /// The process owning the window under a point. Needed while Clickify is the frontmost application —
    /// right after the user presses the "Record" button — where asking "who is in front" returns ourselves
    /// rather than the application the user is operating.
    var processIdentifierAtPoint: (CGPoint) -> pid_t?
    var anchorWindowFrame: (pid_t) -> CGRect?
    /// The anchor window's title, so a recording can name a **window** and not just an application.
    var anchorWindowTitle: (pid_t) -> String?
    var application: (pid_t) -> LockedApplication?
    var doubleClickInterval: () -> TimeInterval
    /// The clock, in seconds. Injectable so tests can build real gaps without really waiting.
    var now: () -> TimeInterval

    static let live = RecordingEnvironment(
        ownProcessIdentifier: { ProcessInfo.processInfo.processIdentifier },
        frontmostProcessIdentifier: { NSWorkspace.shared.frontmostApplication?.processIdentifier },
        pointIsInOwnWindow: { point in
            // `CGEvent.location` has its origin at the top-left of the main screen, `NSWindow` at the bottom-left.
            guard let mainScreen = NSScreen.screens.first else { return false }
            let flipped = NSPoint(x: point.x, y: mainScreen.frame.maxY - point.y)
            // Ask the system which window is **topmost** at that point, so one of our windows covered by another
            // application's does not count as ours.
            let number = NSWindow.windowNumber(at: flipped, belowWindowWithWindowNumber: 0)
            return NSApp.windows.contains { $0.isVisible && $0.windowNumber == number }
        },
        processIdentifierAtPoint: ScenarioSystemBridge.live.processIdentifierAtPoint,
        anchorWindowFrame: WindowAnchor.focusedWindowFrame(ofProcess:),
        anchorWindowTitle: WindowAnchor.focusedWindowTitle(ofProcess:),
        application: { processIdentifier in
            guard let application = NSRunningApplication(processIdentifier: processIdentifier),
                  let bundleIdentifier = application.bundleIdentifier else { return nil }
            return LockedApplication(
                bundleIdentifier: bundleIdentifier,
                name: application.localizedName ?? bundleIdentifier
            )
        },
        doubleClickInterval: { NSEvent.doubleClickInterval },
        now: { Date().timeIntervalSinceReferenceDate }
    )
}
