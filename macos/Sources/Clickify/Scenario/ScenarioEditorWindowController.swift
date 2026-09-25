import AppKit
import Combine
import SwiftUI

/// Owns the editor window (UI-2).
///
/// SwiftUI's `Window` scene is not used because `openWindow` can only be called from inside a view that is on
/// screen, whereas a **Recording session** ends once the popover has closed (RC-16).
@MainActor
final class ScenarioEditorWindowController: ObservableObject {
    private var window: NSWindow?
    private let store: ScenarioStore
    private let runner: ScenarioRunner
    private let localization: Localization
    private var languageObserver: AnyCancellable?

    init(store: ScenarioStore, runner: ScenarioRunner, localization: Localization) {
        self.store = store
        self.runner = runner
        self.localization = localization
        // LC-6: the window **title** is not drawn by SwiftUI, so nothing redraws it on its own. Without this
        // the title would keep the language the window was first opened in.
        languageObserver = localization.$code
            .sink { [weak self] _ in self?.window?.title = localization(.editorTitle) }
    }

    func show() {
        // The app is an agent (LSUIElement), so without this line the window opens but takes no keyboard input (UI-3).
        NSApp.activate(ignoringOtherApps: true)

        if window == nil {
            let window = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 860, height: 560),
                styleMask: [.titled, .closable, .miniaturizable, .resizable],
                backing: .buffered,
                defer: false
            )
            window.title = localization(.editorTitle)
            window.isReleasedWhenClosed = false
            window.center()
            window.contentView = NSHostingView(
                rootView: ScenarioEditorView(
                    store: store,
                    runner: runner,
                    localization: localization
                )
            )
            self.window = window
        }

        window?.makeKeyAndOrderFront(nil)
    }
}
