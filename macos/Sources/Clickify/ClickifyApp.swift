import SwiftUI

@main
struct ClickifyApp: App {
    @StateObject private var localization: Localization
    @StateObject private var runner: ScenarioRunner
    @StateObject private var store: ScenarioStore
    @StateObject private var recorder: ScenarioRecorder
    @StateObject private var clicker: SimpleModeModel
    @StateObject private var launchAtLogin: LaunchAtLoginManager
    @StateObject private var runtimeController: RuntimeController
    @StateObject private var editorController: ScenarioEditorWindowController

    init() {
        // Built once and published through `Localization.current`, which is what the non-view code reads
        // (LC-6, [ADR-0010]). Views take it as an `ObservedObject` so they redraw when it changes.
        let localization = Localization()
        Localization.current = localization
        let runner = ScenarioRunner()
        let store = ScenarioStore()
        let recorder = ScenarioRecorder()
        let editorController = ScenarioEditorWindowController(
            store: store,
            runner: runner,
            localization: localization
        )

        // RC-16: when recording finishes, save the Scenario, select it, then open the editor.
        recorder.onFinished = { result in
            store.addRecorded(result.scenario)
            editorController.show()
        }

        _localization = StateObject(wrappedValue: localization)
        _runner = StateObject(wrappedValue: runner)
        _store = StateObject(wrappedValue: store)
        _recorder = StateObject(wrappedValue: recorder)
        _clicker = StateObject(wrappedValue: SimpleModeModel(runner: runner))
        _launchAtLogin = StateObject(wrappedValue: LaunchAtLoginManager())
        _runtimeController = StateObject(
            wrappedValue: RuntimeController(
                runner: runner,
                recorder: recorder,
                localization: localization
            )
        )
        _editorController = StateObject(wrappedValue: editorController)
    }

    var body: some Scene {
        MenuBarExtra {
            MenuBarView(
                clicker: clicker,
                runner: runner,
                store: store,
                recorder: recorder,
                launchAtLogin: launchAtLogin,
                localization: localization,
                onOpenEditor: { editorController.show() }
            )
        } label: {
            Label(menuBarTitle, systemImage: menuBarIcon)
        }
        .menuBarExtraStyle(.window)
    }

    private var menuBarTitle: String {
        if recorder.isRecording { return localized(.panelRecordingTitle) }
        if runner.isRunning { return localized(.panelStopHint) }
        return "Clickify"
    }

    private var menuBarIcon: String {
        if recorder.isRecording { return "record.circle" }
        return runner.isRunning ? "stop.circle.fill" : "cursorarrow.click"
    }
}
