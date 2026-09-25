import AppKit
import Carbon.HIToolbox
import SwiftUI

@MainActor
final class RuntimeController: ObservableObject {
    private let runner: ScenarioRunner
    private let recorder: ScenarioRecorder
    private let localization: Localization
    private let activityPanel: RunningActivityPanelController
    private var hotKeys: [EventHotKeyRef?] = []
    private var hotKeyHandler: EventHandlerRef?

    private enum Shortcut: UInt32 {
        case stop = 1
        case toggleRecording = 2
    }

    init(runner: ScenarioRunner, recorder: ScenarioRecorder, localization: Localization) {
        self.runner = runner
        self.recorder = recorder
        self.localization = localization
        activityPanel = RunningActivityPanelController(
            runner: runner,
            recorder: recorder,
            localization: localization
        )

        runner.onRunningStateChanged = { [weak self] isRunning in
            self?.activityPanel.setVisible(isRunning || (self?.recorder.isRecording ?? false))
        }
        recorder.onRecordingStateChanged = { [weak self] isRecording in
            self?.activityPanel.setVisible(isRecording || (self?.runner.isRunning ?? false))
        }

        registerHotKeys()
    }

    private func handle(_ shortcut: Shortcut) {
        switch shortcut {
        case .stop:
            guard runner.isRunning else { return }
            runner.stop()
        case .toggleRecording:
            // RC-1: finish with the shortcut, because clicking a button would land in the recording.
            guard !runner.isRunning else { return }
            if recorder.isRecording {
                recorder.stop()
            } else {
                recorder.start()
            }
        }
    }

    private func registerHotKeys() {
        var eventType = EventTypeSpec(
            eventClass: OSType(kEventClassKeyboard),
            eventKind: UInt32(kEventHotKeyPressed)
        )
        let context = Unmanaged.passUnretained(self).toOpaque()

        InstallEventHandler(
            GetApplicationEventTarget(),
            { _, event, context in
                guard let context else { return OSStatus(eventNotHandledErr) }
                var identifier = EventHotKeyID()
                GetEventParameter(
                    event,
                    EventParamName(kEventParamDirectObject),
                    EventParamType(typeEventHotKeyID),
                    nil,
                    MemoryLayout<EventHotKeyID>.size,
                    nil,
                    &identifier
                )
                guard let shortcut = Shortcut(rawValue: identifier.id) else {
                    return OSStatus(eventNotHandledErr)
                }
                let controller = Unmanaged<RuntimeController>
                    .fromOpaque(context)
                    .takeUnretainedValue()
                Task { @MainActor in
                    controller.handle(shortcut)
                }
                return noErr
            },
            1,
            &eventType,
            context,
            &hotKeyHandler
        )

        register(.stop, keyCode: UInt32(kVK_ANSI_S))
        register(.toggleRecording, keyCode: UInt32(kVK_ANSI_R))
    }

    private func register(_ shortcut: Shortcut, keyCode: UInt32) {
        var hotKey: EventHotKeyRef?
        RegisterEventHotKey(
            keyCode,
            UInt32(cmdKey | optionKey),
            EventHotKeyID(signature: OSType(0x4154_434B), id: shortcut.rawValue), // "ATCK"
            GetApplicationEventTarget(),
            0,
            &hotKey
        )
        hotKeys.append(hotKey)
    }
}

@MainActor
private final class RunningActivityPanelController {
    private let panel: NSPanel

    init(runner: ScenarioRunner, recorder: ScenarioRecorder, localization: Localization) {
        let panelSize = NSSize(width: 390, height: 86)
        panel = NSPanel(
            contentRect: NSRect(origin: .zero, size: panelSize),
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]
        panel.backgroundColor = .clear
        panel.isOpaque = false
        panel.hasShadow = true
        panel.hidesOnDeactivate = false
        panel.isReleasedWhenClosed = false
        panel.isMovableByWindowBackground = true
        panel.contentView = NSHostingView(
            rootView: RunningActivityView(
                runner: runner,
                recorder: recorder,
                localization: localization
            )
        )
    }

    func setVisible(_ isVisible: Bool) {
        if isVisible {
            positionNearTopCenter()
            panel.orderFrontRegardless()
        } else {
            panel.orderOut(nil)
        }
    }

    private func positionNearTopCenter() {
        guard let screen = NSScreen.main ?? NSScreen.screens.first else { return }
        let visibleFrame = screen.visibleFrame
        let origin = NSPoint(
            x: visibleFrame.midX - panel.frame.width / 2,
            y: visibleFrame.maxY - panel.frame.height - 12
        )
        panel.setFrameOrigin(origin)
    }
}

private struct RunningActivityView: View {
    @ObservedObject var runner: ScenarioRunner
    @ObservedObject var recorder: ScenarioRecorder
    @ObservedObject var localization: Localization

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(recorder.isRecording ? Color.red : Color.accentColor)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 3) {
                Text(recorder.isRecording
                    ? localization(.panelRecordingTitle)
                    : (runner.runningScenarioName ?? "Clickify"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text(title)
                    .font(.headline)
                    .lineLimit(1)
                // The only way out while a click sequence has taken over the cursor (UI-15).
                Text(recorder.isRecording
                    ? localization(.panelFinishHint)
                    : localization(.panelStopHint))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            if recorder.isRecording {
                Button {
                    recorder.stop()
                } label: {
                    Label(localization(.panelFinish), systemImage: "stop.circle")
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
            } else {
                Button {
                    runner.stop()
                } label: {
                    Label(localization(.panelStop), systemImage: "stop.fill")
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .keyboardShortcut("s", modifiers: [.command, .option])
            }
        }
        .padding(.horizontal, 16)
        .frame(width: 390, height: 86)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(.white.opacity(0.16))
        }
    }

    private var icon: String {
        if recorder.isRecording { return "record.circle" }
        return runner.countdown == nil ? "cursorarrow.rays" : "timer"
    }

    private var title: String {
        if recorder.isRecording {
            return localization(.panelGestures, recorder.recordedGestureCount)
        }
        return runner.statusText
    }
}
