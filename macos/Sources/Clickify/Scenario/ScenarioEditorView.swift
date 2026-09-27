import AppKit
import SwiftUI

/// The **configuration** surface (UI-2). The menu-bar popover contains no Step editor.
struct ScenarioEditorView: View {
    @ObservedObject var store: ScenarioStore
    @ObservedObject var runner: ScenarioRunner
    @ObservedObject var localization: Localization

    @State private var selectedStepID: UUID?
    @State private var pointSelector: ClickPointSelector?
    @State private var pickError: String?
    @State private var runningApplications: [RunningApplicationOption] = []
    @State private var captureCoordinator = TemplateCaptureCoordinator()

    var body: some View {
        NavigationSplitView {
            scenarioSidebar
                .navigationSplitViewColumnWidth(min: 200, ideal: 240, max: 340)
        } content: {
            Group {
                if let scenario = store.selectedScenario {
                    if store.isReadOnly(scenario) {
                        readOnlyNotice(scenario)
                    } else {
                        scenarioPane(for: store.binding(for: scenario.id))
                    }
                } else {
                    emptyState
                }
            }
            .navigationSplitViewColumnWidth(min: 320, ideal: 380)
        } detail: {
            Group {
                if let scenario = store.selectedScenario, !store.isReadOnly(scenario) {
                    stepDetail(store.binding(for: scenario.id))
                } else {
                    placeholder(localization(.editorSelectAStep))
                }
            }
            .navigationSplitViewColumnWidth(min: 340, ideal: 400)
        }
        .navigationTitle(localization(.editorTitle))
        .frame(minWidth: 940, minHeight: 520)
        .onAppear { runningApplications = RunningApplicationOption.current() }
        .disabled(runner.isRunning)
        .overlay(alignment: .top) {
            if runner.isRunning {
                Label(localization(.editorRunningNotice), systemImage: "lock.fill")
                    .font(.caption)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.top, 8)
            }
        }
    }

    // MARK: - Left pane: every Scenario

    private var scenarioSidebar: some View {
        VStack(spacing: 0) {
            List(selection: $store.selectedScenarioID) {
                ForEach(store.scenarios) { scenario in
                    scenarioRow(scenario).tag(UUID?.some(scenario.id))
                }
            }
            .listStyle(.sidebar)

            Divider()

            HStack(spacing: 6) {
                iconButton("plus", help: localization(.editorNewScenario)) { store.create() }
                iconButton(
                    "doc.on.doc",
                    help: localization(.editorDuplicateScenario),
                    isDisabled: store.selectedScenario.map(store.isReadOnly) ?? true
                ) {
                    if let scenario = store.selectedScenario { store.duplicate(scenario) }
                }
                iconButton(
                    "trash",
                    help: localization(.editorDeleteScenario),
                    isDisabled: store.selectedScenario == nil
                ) {
                    if let scenario = store.selectedScenario { store.delete(scenario) }
                }

                Spacer()

                if !store.loadIssues.isEmpty {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                        .help(store.loadIssues.map(\.text).joined(separator: "\n"))
                        .accessibilityLabel(localization(.editorLoadIssues, store.loadIssues.count))
                }
            }
            .padding(8)
        }
    }

    private func scenarioRow(_ scenario: Scenario) -> some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(scenario.name.isEmpty ? localization(.editorUnnamed) : scenario.name)
                    .lineLimit(1)
                Text(localization(.scenarioSummarySteps, scenario.steps.count))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if store.isReadOnly(scenario) {
                Image(systemName: "lock.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .help(localization(.editorReadOnlyBadge))
            }
        }
        .padding(.vertical, 2)
    }

    /// UI-17: icon buttons sharing a row must be the same size.
    ///
    /// Each SF Symbol has its own natural width, so at default sizing `plus`, `doc.on.doc` and `trash` came out
    /// at three different sizes standing next to each other — measured on the AX tree: 37×20, 40×26, 38×24.
    private func iconButton(
        _ symbol: String,
        help: String,
        isDisabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .frame(width: 20, height: 20)
        }
        .buttonStyle(.bordered)
        .disabled(isDisabled)
        .help(help)
    }

    private func placeholder(_ text: String) -> some View {
        VStack {
            Spacer()
            Text(text).foregroundStyle(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "list.bullet.rectangle")
                .font(.system(size: 34))
                .foregroundStyle(.secondary)
            Text(localization(.editorEmptyTitle))
                .font(.headline)
            Button(localization(.editorEmptyAction)) { store.create() }
                .buttonStyle(.borderedProminent)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func readOnlyNotice(_ scenario: Scenario) -> some View {
        VStack(spacing: 10) {
            Spacer()
            Image(systemName: "lock.doc")
                .font(.system(size: 34))
                .foregroundStyle(.secondary)
            Text(localization(.editorNewerFormatTitle, scenario.name))
                .font(.headline)
            Text(localization(.editorNewerFormatDetail))
                .font(.callout)
                .foregroundStyle(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Editor

    private func scenarioPane(for scenario: Binding<Scenario>) -> some View {
        VStack(spacing: 0) {
            scenarioSettings(scenario)
                .padding(12)
            Divider()
            stepList(scenario)
        }
    }

    /// Scenario-level configuration. Laid out vertically rather than in one long horizontal row: the middle column
    /// is only about 380 points wide, and packing it horizontally squeezed a label to 0 points and stretched a checkbox into a stripe.
    private func scenarioSettings(_ scenario: Binding<Scenario>) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField(localization(.editorScenarioName), text: scenario.name)
                .textFieldStyle(.roundedBorder)

            HStack(spacing: 10) {
                Toggle(localization(.editorRepeatUntilStopped), isOn: Binding(
                    get: { scenario.wrappedValue.runCount == .untilStopped },
                    set: { scenario.wrappedValue.runCount = $0 ? .untilStopped : .times(1) }
                ))
                .fixedSize()

                if case let .times(count) = scenario.wrappedValue.runCount {
                    Text(localization(.editorIterationCount)).fixedSize()
                    integerField(
                        value: Binding(
                            get: { count },
                            set: { scenario.wrappedValue.runCount = .times($0) }
                        ),
                        range: ScenarioLimits.runCount
                    )
                }

                Spacer(minLength: 0)
            }

            Toggle(localization(.editorLockToggle), isOn: lockToggle(scenario))
                .fixedSize()

            if scenario.wrappedValue.lockedApplication != nil {
                HStack(spacing: 8) {
                    Picker(localization(.editorLockApplication), selection: lockedBundleIdentifier(scenario)) {
                        Text(localization(.editorLockChoose)).tag("")
                        ForEach(runningApplications) { Text($0.name).tag($0.bundleIdentifier) }
                    }
                    .labelsHidden()

                    iconButton("arrow.clockwise", help: localization(.editorLockRefresh)) {
                        runningApplications = RunningApplicationOption.current()
                    }
                }
            }

            if let error = runner.validate(scenario.wrappedValue)?.errorDescription {
                Label(error, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let pickError {
                Label(pickError, systemImage: "exclamationmark.triangle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func lockToggle(_ scenario: Binding<Scenario>) -> Binding<Bool> {
        Binding(
            get: { scenario.wrappedValue.lockedApplication != nil },
            set: { isOn in
                scenario.wrappedValue.lockedApplication = isOn
                    ? LockedApplication(bundleIdentifier: "", name: localization(.editorNotChosen))
                    : nil
            }
        )
    }

    private func lockedBundleIdentifier(_ scenario: Binding<Scenario>) -> Binding<String> {
        Binding(
            get: { scenario.wrappedValue.lockedApplication?.bundleIdentifier ?? "" },
            set: { bundleIdentifier in
                let name = runningApplications
                    .first { $0.bundleIdentifier == bundleIdentifier }?.name ?? localization(.editorNotChosen)
                scenario.wrappedValue.lockedApplication = LockedApplication(
                    bundleIdentifier: bundleIdentifier,
                    name: name
                )
            }
        )
    }

    private func stepList(_ scenario: Binding<Scenario>) -> some View {
        VStack(spacing: 0) {
            List(selection: $selectedStepID) {
                ForEach(Array(scenario.wrappedValue.steps.enumerated()), id: \.element.id) { index, step in
                    stepRow(index: index, step: step)
                        .tag(step.id)
                }
                .onMove { offsets, destination in
                    scenario.wrappedValue.steps.move(fromOffsets: offsets, toOffset: destination)
                }
                .onDelete { offsets in
                    scenario.wrappedValue.steps.remove(atOffsets: offsets)
                }
            }

            Divider()

            HStack(spacing: 6) {
                iconButton("plus", help: localization(.editorAddStep)) { addStep(to: scenario) }
                iconButton(
                    "doc.on.doc",
                    help: localization(.editorDuplicateStep),
                    isDisabled: selectedStepIndex(in: scenario.wrappedValue) == nil
                ) { duplicateSelectedStep(in: scenario) }
                iconButton(
                    "minus",
                    help: localization(.editorDeleteStep),
                    isDisabled: selectedStepIndex(in: scenario.wrappedValue) == nil
                ) { deleteSelectedStep(in: scenario) }

                Spacer()
                Text(localization(.editorDragToReorder))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(8)
        }
    }

    /// Summarises a Step on exactly one line (UI-8).
    private func stepRow(index: Int, step: Step) -> some View {
        HStack(spacing: 10) {
            Text("\(index + 1)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 22, alignment: .trailing)

            VStack(alignment: .leading, spacing: 2) {
                Text(StepSummary.action(step.action))
                    .lineLimit(1)
                Text(StepSummary.target(step.target))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                if step.repeatCount > 1 {
                    Text("×\(step.repeatCount)").font(.caption.monospacedDigit())
                }
                if step.delayMillisecondsAfter > 0 {
                    Text("\(step.delayMillisecondsAfter) ms")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func stepDetail(_ scenario: Binding<Scenario>) -> some View {
        if let index = selectedStepIndex(in: scenario.wrappedValue) {
            StepDetailView(
                step: scenario.steps[index],
                localization: localization,
                lockedApplicationName: lockedApplicationName(of: scenario.wrappedValue),
                onPickScreenPoint: { apply in pickPoint { point, _ in apply(point) } },
                onPickWindowOffset: { apply in
                    pickWindowOffset(in: scenario.wrappedValue, apply: apply)
                },
                onCaptureTemplate: { apply in
                    captureTemplate(for: scenario.wrappedValue, apply: apply)
                },
                onPickSearchRegion: { apply in
                    pickSearchRegion(in: scenario.wrappedValue, apply: apply)
                },
                templateImage: { [scenarioID = scenario.wrappedValue.id] name in
                    store.templateLibrary(for: scenarioID).loadImage(name).map {
                        NSImage(cgImage: $0, size: NSSize(width: $0.width, height: $0.height))
                    }
                }
            )
        } else {
            placeholder(localization(.editorSelectAStep))
        }
    }

    // MARK: - Operations

    private func selectedStepIndex(in scenario: Scenario) -> Int? {
        guard let selectedStepID else { return nil }
        return scenario.steps.firstIndex { $0.id == selectedStepID }
    }

    private func addStep(to scenario: Binding<Scenario>) {
        let step = Step(
            action: .click(button: .left, count: 1, holdMilliseconds: 0),
            target: .cursor,
            repeatCount: 1,
            delayMillisecondsAfter: 100
        )
        scenario.wrappedValue.steps.append(step)
        selectedStepID = step.id
    }

    private func duplicateSelectedStep(in scenario: Binding<Scenario>) {
        guard let index = selectedStepIndex(in: scenario.wrappedValue) else { return }
        var copy = scenario.wrappedValue.steps[index]
        copy.id = UUID()
        scenario.wrappedValue.steps.insert(copy, at: index + 1)
        selectedStepID = copy.id
    }

    private func deleteSelectedStep(in scenario: Binding<Scenario>) {
        guard let index = selectedStepIndex(in: scenario.wrappedValue) else { return }
        scenario.wrappedValue.steps.remove(at: index)
        selectedStepID = scenario.wrappedValue.steps.indices.contains(index)
            ? scenario.wrappedValue.steps[index].id
            : scenario.wrappedValue.steps.last?.id
    }

    private func lockedApplicationName(of scenario: Scenario) -> String? {
        guard let locked = scenario.lockedApplication, !locked.bundleIdentifier.isEmpty else {
            return nil
        }
        return locked.name
    }

    /// The point-picking overlay has to run from this window, not from the popover (UI-4).
    private func pickPoint(_ completion: @escaping (CGPoint, NSWindow?) -> Void) {
        let window = NSApp.keyWindow
        window?.orderOut(nil)
        pickError = nil

        let selector = ClickPointSelector()
        pointSelector = selector
        selector.start { point in
            pointSelector = nil
            NSApp.activate(ignoringOtherApps: true)
            window?.makeKeyAndOrderFront(nil)
            guard let point else { return }
            completion(point, window)
        }
    }

    /// Converts the point just picked into an offset from the nearest corner of the Anchor window (DM-13).
    private func pickWindowOffset(
        in scenario: Scenario,
        apply: @escaping (WindowAnchor.Offset) -> Void
    ) {
        guard let locked = scenario.lockedApplication, !locked.bundleIdentifier.isEmpty else {
            pickError = localization(.editorAnchorNeedsLock)
            return
        }

        pickPoint { point, _ in
            guard let processIdentifier = RunningApplicationOption.processIdentifier(
                forBundleIdentifier: locked.bundleIdentifier
            ) else {
                pickError = localization(.editorLockedNotRunning, locked.name)
                return
            }
            guard let frame = WindowAnchor.focusedWindowFrame(ofProcess: processIdentifier) else {
                pickError = localization(.editorNoWindowForLocked, locked.name)
                return
            }
            apply(WindowAnchor.offset(for: point, in: frame))
        }
    }

    /// RG-4: cropping a Template is entirely independent of the Locked application.
    /// RG-23: after cropping, attach a default Search region hugging the area just drawn.
    private func captureTemplate(
        for scenario: Scenario,
        apply: @escaping (String, SearchRegion?) -> Void
    ) {
        let window = NSApp.keyWindow
        window?.orderOut(nil)
        pickError = nil

        Task {
            defer {
                NSApp.activate(ignoringOtherApps: true)
                window?.makeKeyAndOrderFront(nil)
            }
            do {
                let library = store.templateLibrary(for: scenario.id)
                guard let capture = try await captureCoordinator.captureTemplate(into: library)
                else { return }

                let bounds = NSScreen.screens
                    .map(\.frame)
                    .reduce(CGRect.null) { $0.union($1) }
                let padded = TemplateCaptureCoordinator.suggestedSearchRect(
                    around: capture.rect,
                    within: bounds.isNull ? capture.rect : bounds
                )
                apply(capture.templateName, searchRegion(for: padded, in: scenario))
            } catch {
                pickError = error.localizedDescription
            }
        }
    }

    /// RG-6: the search region is stored relative to the Anchor window when one is available, absolute otherwise.
    /// Shared between hand-drawn regions and the one suggested after cropping, so the two paths cannot drift apart.
    private func searchRegion(for rect: CGRect, in scenario: Scenario) -> SearchRegion {
        if let locked = scenario.lockedApplication,
           let processIdentifier = RunningApplicationOption.processIdentifier(
               forBundleIdentifier: locked.bundleIdentifier
           ),
           let frame = WindowAnchor.focusedWindowFrame(ofProcess: processIdentifier) {
            let offset = WindowAnchor.offset(for: rect.origin, in: frame)
            return .windowRelative(
                corner: offset.corner,
                dx: offset.dx,
                dy: offset.dy,
                width: rect.width,
                height: rect.height
            )
        }
        return .screenRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height)
    }

    /// RG-6: the search region is stored relative to the Anchor window when one is available, absolute otherwise —
    /// and the interface says so plainly. Locking an application is never a requirement ([ADR-0006]).
    private func pickSearchRegion(in scenario: Scenario, apply: @escaping (SearchRegion) -> Void) {
        let window = NSApp.keyWindow
        window?.orderOut(nil)
        pickError = nil

        Task {
            defer {
                NSApp.activate(ignoringOtherApps: true)
                window?.makeKeyAndOrderFront(nil)
            }
            guard let rect = await captureCoordinator.selectRegion(
                prompt: localized(.overlaySearchRegion)
            ) else { return }

            apply(searchRegion(for: rect, in: scenario))
        }
    }

    private func integerField(value: Binding<Int>, range: ClosedRange<Int>) -> some View {
        TextField(
            "",
            value: Binding(
                get: { value.wrappedValue },
                set: { value.wrappedValue = $0.clamped(to: range) }
            ),
            format: .number
        )
        .textFieldStyle(.roundedBorder)
        .multilineTextAlignment(.trailing)
        .frame(width: 90)
    }
}

/// Summary strings shared between the Step list and the messages.
enum StepSummary {
    static func action(_ action: StepAction) -> String {
        switch action {
        case let .click(button, count, hold):
            var text = "Click \(name(of: button))"
            if count > 1 { text += " ×\(count)" }
            if hold > 0 { text += localized(.summaryHold, hold) }
            return text
        case let .scroll(deltaX, deltaY):
            return localized(.summaryScroll, deltaX, deltaY)
        case .move:
            return localized(.summaryMove)
        case let .drag(button, destination):
            return localized(.summaryDrag, name(of: button), target(destination))
        case let .typeText(text):
            return text.isEmpty ? localized(.summaryTypeEmpty) : localized(.summaryType, text)
        case let .pressKey(stroke):
            return localized(.summaryKey, KeyCatalog.describe(stroke))
        }
    }

    static func target(_ target: StepTarget) -> String {
        switch target {
        case .cursor:
            return localized(.summaryTargetCursor)
        case let .screenPoint(x, y):
            return localized(.summaryTargetPoint, Int(x.rounded()), Int(y.rounded()))
        case let .windowRelative(corner, dx, dy):
            return localized(
                .summaryTargetCorner, corner.title, Int(dx.rounded()), Int(dy.rounded())
            )
        case let .template(name, settings):
            let label = name.isEmpty ? localized(.summaryTargetTemplateMissing) : name
            return localized(.summaryTargetTemplate, label, localizedDecimal(settings.threshold))
        case let .text(text, _):
            return text.isEmpty ? localized(.summaryTargetTextEmpty) : localized(.summaryTargetText, text)
        }
    }

    static func name(of button: MouseButton) -> String {
        switch button {
        case .left: return localized(.buttonLeft)
        case .right: return localized(.buttonRight)
        case .center: return localized(.buttonMiddle)
        }
    }
}
