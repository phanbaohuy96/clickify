import AppKit
import ApplicationServices
import CoreGraphics
import Foundation

/// Everything the runner needs to ask the operating system, gathered in one place.
///
/// Split out so that tests can verify `SF-4` (bring the Locked application forward before typing)
/// and `EX-7` (stop when there is no Anchor window) without launching a real application.
@MainActor
struct ScenarioSystemBridge {
    var isAccessibilityTrusted: () -> Bool
    /// The process to aim at. `preferredWindowTitle` is the **Anchor window** title at recording
    /// time: with two processes sharing a bundle id (two browser profiles, two copies of a game)
    var processIdentifier: (_ bundleIdentifier: String, _ preferredWindowTitle: String?) -> pid_t?
    var isRunning: (pid_t) -> Bool
    var activate: (pid_t) -> Void
    var frontmostProcessIdentifier: () -> pid_t?
    var anchorWindowFrame: (_ processIdentifier: pid_t, _ preferredWindowTitle: String?) -> CGRect?
    var processIdentifierAtPoint: (CGPoint) -> pid_t?

    static let live = ScenarioSystemBridge(
        isAccessibilityTrusted: ScenarioSystemBridge.requestAccessibilityAccess,
        processIdentifier: { bundleIdentifier, preferredWindowTitle in
            guard !bundleIdentifier.isEmpty else { return nil }
            let candidates = NSWorkspace.shared.runningApplications
                .filter { $0.bundleIdentifier == bundleIdentifier && !$0.isTerminated }
                .map(\.processIdentifier)
            // With a title, pick the process that actually has that window. Without one, or with no
            // process matching, keep the old behaviour — take the first.
            if let title = preferredWindowTitle, !title.isEmpty,
               let owner = candidates.first(where: {
                   WindowAnchor.hasWindow(ofProcess: $0, titled: title)
               }) {
                return owner
            }
            return candidates.first
        },
        isRunning: { NSRunningApplication(processIdentifier: $0) != nil },
        activate: { processIdentifier in
            NSRunningApplication(processIdentifier: processIdentifier)?
                .activate(options: [.activateAllWindows])
        },
        frontmostProcessIdentifier: { NSWorkspace.shared.frontmostApplication?.processIdentifier },
        anchorWindowFrame: WindowAnchor.frame(ofProcess:preferringTitle:),
        processIdentifierAtPoint: ScenarioSystemBridge.processIdentifier(at:)
    )

    private nonisolated static func requestAccessibilityAccess() -> Bool {
        if AXIsProcessTrusted() { return true }

        // The SDK exposes kAXTrustedCheckOptionPrompt as mutable global state, which Swift 6
        // rejects under strict concurrency checking. This is its documented CFString value.
        let options = ["AXTrustedCheckOptionPrompt": true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    private nonisolated static func processIdentifier(at point: CGPoint) -> pid_t? {
        let systemWideElement = AXUIElementCreateSystemWide()
        var element: AXUIElement?
        let copyResult = AXUIElementCopyElementAtPosition(
            systemWideElement,
            Float(point.x),
            Float(point.y),
            &element
        )
        guard copyResult == .success, let element else { return nil }

        var processIdentifier: pid_t = 0
        guard AXUIElementGetPid(element, &processIdentifier) == .success else { return nil }
        return processIdentifier
    }
}
