import AppKit
import CoreGraphics

/// The Screen Recording permission, entirely separate from Accessibility (SF-5, SF-7).
///
/// The app now needs two different permissions and it is very easy to grant one and forget the
/// other, so this state has to be visible rather than living only in a run-time error message.
@MainActor
enum ScreenRecordingPermission {
    static var isGranted: Bool {
        CGPreflightScreenCaptureAccess()
    }

    /// Raise the system permission dialog. Only call this when the user actually reaches for the
    /// recognition feature — never at launch (SF-5).
    @discardableResult
    static func request() -> Bool {
        CGRequestScreenCaptureAccess()
    }

    static func openSettings() {
        guard let url = URL(
            string: "x-apple.systempreferences:com.apple.preference.security?Privacy_ScreenCapture"
        ) else { return }
        NSWorkspace.shared.open(url)
    }
}
