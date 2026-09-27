import AppKit
import CoreGraphics
import SwiftUI

/// One Step's detail: two separate pickers for **Action** and **Target**, matching the orthogonal model
/// (UI-10, ADR-0002).
struct StepDetailView: View {
    @Binding var step: Step
    @ObservedObject var localization: Localization
    /// The Locked application's name, or `nil` when the Scenario is not locked to any application.
    let lockedApplicationName: String?
    let onPickScreenPoint: (@escaping (CGPoint) -> Void) -> Void
    let onPickWindowOffset: (@escaping (WindowAnchor.Offset) -> Void) -> Void
    let onCaptureTemplate: (@escaping (String, SearchRegion?) -> Void) -> Void
    let onPickSearchRegion: (@escaping (SearchRegion) -> Void) -> Void
    /// Saved Templates, so a thumbnail can be shown instead of a file name — `3f2a91c0.png` tells you nothing.
    let templateImage: (String) -> NSImage?

    var body: some View {
        Form {
            Section(localization(.stepActionSection)) {
                Picker(localization(.stepActionKind), selection: actionKind) {
                    ForEach(ActionKind.allCases) { Text($0.title).tag($0) }
                }
                actionParameters
            }

            Section(localization(.stepPosition)) {
                targetEditor(target)
            }

            if case .drag = step.action {
                Section(localization(.stepReleasePoint)) {
                    targetEditor(dragDestination)
                }
            }

            Section(localization(.stepRepeatSection)) {
                labelledField(
                    localization(.stepRepeatCount),
                    value: $step.repeatCount,
                    range: ScenarioLimits.stepRepeatCount,
                    suffix: localization(.stepRepeatUnit)
                )
                labelledField(
                    localization(.stepDelayAfter),
                    value: $step.delayMillisecondsAfter,
                    range: ScenarioLimits.delayMilliseconds,
                    suffix: "ms"
                )
                if step.delayMillisecondsAfter < ScenarioLimits.minimumEventGapMilliseconds {
                    footnote(localization(.stepMinimumGapHint, ScenarioLimits.minimumEventGapMilliseconds))
                }
            }
        }
        .formStyle(.grouped)
    }

    // MARK: - Action parameters

    @ViewBuilder
    private var actionParameters: some View {
        switch step.action {
        case .click(_, let count, let hold):
            Picker(localization(.stepButton), selection: mouseButton) {
                ForEach(MouseButton.allCases) { Text(StepSummary.name(of: $0)).tag($0) }
            }
            labelledField(localization(.stepClickCount), value: clickCount, range: ScenarioLimits.clickCount)
            labelledField(localization(.stepHold), value: clickHold, range: ScenarioLimits.holdMilliseconds, suffix: "ms")
            if count == 2 {
                footnote(localization(.stepDoubleClickHint))
            }
            if hold > 0 {
                footnote(localization(.stepHoldHint))
            }

        case .scroll:
            labelledField(
                localization(.stepScrollHorizontal),
                value: scrollDeltaX,
                range: ScenarioLimits.scrollDelta,
                suffix: localization(.stepScrollUnit)
            )
            labelledField(
                localization(.stepScrollVertical),
                value: scrollDeltaY,
                range: ScenarioLimits.scrollDelta,
                suffix: localization(.stepScrollUnit)
            )
            footnote(localization(.stepScrollHint))

        case .move:
            footnote(localization(.stepMoveHint))

        case .drag:
            Picker(localization(.stepButton), selection: mouseButton) {
                ForEach(MouseButton.allCases) { Text(StepSummary.name(of: $0)).tag($0) }
            }
            footnote(localization(.stepDragHint, ScenarioLimits.dragIntermediateSteps))

        case let .typeText(text):
            TextField(localization(.stepTypeContent), text: typedText, axis: .vertical)
                .lineLimit(1...4)
            // UI-21: the route is chosen from the string's contents (EX-21), so two strings in the same Step
            // behave differently. That is only acceptable if the panel says which one this string takes.
            if KeyboardEventEmitter.isTypableKeyByKey(text) {
                footnote(
                    localization(.stepTypePerKeyHint)
                )
            } else {
                footnote(
                    localization(.stepTypeChunkedHint)
                )
            }

        case let .pressKey(stroke):
            Picker(localization(.stepKey), selection: keyName) {
                ForEach(KeyCatalog.entries) { Text($0.title).tag($0.name) }
            }
            HStack {
                Text(localization(.stepModifiers))
                Spacer()
                ForEach(KeyModifier.allCases) { modifier in
                    Toggle(modifier.symbol, isOn: modifierBinding(modifier))
                        .toggleStyle(.button)
                }
            }
            footnote(localization(.stepKeyPreview, KeyCatalog.describe(stroke)))
        }

        if step.action.isKeyboard {
            if let lockedApplicationName {
                footnote(localization(.stepKeyActivationHint, lockedApplicationName))
            } else {
                warning(localization(.stepKeyNoLockHint))
            }
        }
    }

    // MARK: - Target editor

    @ViewBuilder
    private func targetEditor(_ target: Binding<StepTarget>) -> some View {
        Picker(localization(.stepTargetKind), selection: targetKind(target)) {
            ForEach(TargetKind.allCases) { Text($0.title).tag($0) }
        }

        switch target.wrappedValue {
        case .cursor:
            footnote(localization(.stepTargetCursorHint))

        case let .screenPoint(x, y):
            HStack {
                Text(localization(.stepTargetCoordinates))
                Spacer()
                Text("X: \(Int(x.rounded()))  Y: \(Int(y.rounded()))")
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                Button(localization(.stepTargetChoosePoint)) {
                    onPickScreenPoint { point in
                        target.wrappedValue = .screenPoint(x: point.x, y: point.y)
                    }
                }
            }

        case let .windowRelative(corner, dx, dy):
            if let lockedApplicationName {
                HStack {
                    Text(localization(.stepTargetCornerOffset, corner.title))
                    Spacer()
                    Text("\(Int(dx.rounded())), \(Int(dy.rounded()))")
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                    Button(localization(.stepTargetChoosePoint)) {
                        onPickWindowOffset { offset in
                            target.wrappedValue = .windowRelative(
                                corner: offset.corner,
                                dx: offset.dx,
                                dy: offset.dy
                            )
                        }
                    }
                }
                footnote(localization(.stepTargetAnchorHint, lockedApplicationName))
            } else {
                warning(localization(.stepTargetAnchorNeedsLock))
            }

        case let .template(name, settings):
            // UI-25: the button keeps its whole label and the thumbnail gives up the width, down to a floor.
            // In Spanish the row does not fit a 340-point column and it was the button that gave way, so
            // `Capturar otra vez…` read `Capturar otra…`. A picture that is 40 points narrower is still a
            // picture; a verb that has lost its object is a guess.
            //
            // `ViewThatFits` was tried first and does not work here: an `HStack` holding a `Spacer()` reports
            // that it fits at any width, so the one-line layout always won and the label was still cut.
            HStack(alignment: .top) {
                Text(localization(.stepTargetTemplate))
                Spacer(minLength: 8)
                templateThumbnail(name)
                captureButton(name, target, settings)
                    .fixedSize(horizontal: true, vertical: false)
            }
            footnote(localization(.stepTargetTemplateHint))
            thresholdField(target, settings: settings)
            recognitionFields(target, settings: settings)

        case let .text(text, settings):
            TextField(localization(.stepTargetText), text: Binding(
                get: { text },
                set: { target.wrappedValue = .text($0, settings: settings) }
            ))
            footnote(localization(.stepTargetTextHint))
            recognitionFields(target, settings: settings)
        }
    }

    private func captureButton(
        _ name: String,
        _ target: Binding<StepTarget>,
        _ settings: RecognitionSettings
    ) -> some View {
        Button(localization(
            name.isEmpty ? .stepTargetCaptureRegion : .stepTargetCaptureAgain
        )) {
            onCaptureTemplate { captured, suggestedRegion in
                target.wrappedValue = .template(
                    name: captured,
                    settings: RecognitionSettings(
                        threshold: settings.threshold,
                        // RG-23: the search region hugs the area just captured. On a recapture the region
                        // follows the new image, since the old one was derived from the old image.
                        searchRegion: suggestedRegion ?? settings.searchRegion,
                        waitMilliseconds: settings.waitMilliseconds,
                        onTimeout: settings.onTimeout
                    )
                )
            }
        }
    }

    /// Shows the Template itself rather than its file name: the name is `3f2a91c0.png`, which tells you nothing.
    @ViewBuilder
    private func templateThumbnail(_ name: String) -> some View {
        if name.isEmpty {
            Text(localization(.stepTemplateNotCaptured))
                .foregroundStyle(.secondary)
        } else if let image = templateImage(name) {
            Image(nsImage: image)
                .resizable()
                .interpolation(.high)
                .aspectRatio(contentMode: .fit)
                // UI-25: a floor, so a long button label cannot squeeze the picture out of existence — the
                // way a fixed-width column once squeezed a label to nothing (`UI-18`).
                .frame(minWidth: 64, maxWidth: 160, maxHeight: 64)
                .background(.quaternary)
                .clipShape(RoundedRectangle(cornerRadius: 4))
                .overlay(
                    RoundedRectangle(cornerRadius: 4).strokeBorder(.separator)
                )
                // The real pixel size is still needed: it determines the scan area and the matching speed.
                .help("\(name) — \(Int(image.size.width))×\(Int(image.size.height)) pixel")
        } else {
            Label(localization(.stepTemplateFileMissing), systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
                .font(.caption)
        }
    }

    // MARK: - Recognition settings

    @ViewBuilder
    private func thresholdField(
        _ target: Binding<StepTarget>,
        settings: RecognitionSettings
    ) -> some View {
        HStack {
            Text(localization(.stepThreshold))
            Slider(
                value: Binding(
                    get: { settings.threshold },
                    set: { update(target, settings, threshold: $0) }
                ),
                in: ScenarioLimits.recognitionThreshold
            )
            Text(localization.decimal(settings.threshold))
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .frame(width: 44, alignment: .trailing)
        }
    }

    @ViewBuilder
    private func recognitionFields(
        _ target: Binding<StepTarget>,
        settings: RecognitionSettings
    ) -> some View {
        labelledField(
            localization(.stepWaitAtMost),
            value: Binding(
                get: { settings.waitMilliseconds },
                set: { update(target, settings, wait: $0) }
            ),
            range: ScenarioLimits.recognitionWaitMilliseconds,
            suffix: "ms"
        )

        Picker(
            localization(.stepOnTimeout),
            selection: Binding(
                get: { settings.onTimeout },
                set: { update(target, settings, onTimeout: $0) }
            )
        ) {
            Text(localization(.stepOnTimeoutStop)).tag(TimeoutBehaviour.stopScenario)
            Text(localization(.stepOnTimeoutSkip)).tag(TimeoutBehaviour.skipStep)
        }

        HStack {
            Text(localization(.stepSearchRegion))
            Spacer()
            Text(searchRegionDescription(settings.searchRegion))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            if settings.searchRegion != nil {
                Button(localization(.stepSearchRegionClear)) { update(target, settings, searchRegion: .some(nil)) }
            }
            Button(localization(.stepSearchRegionPick)) {
                onPickSearchRegion { region in
                    update(target, settings, searchRegion: .some(region))
                }
            }
        }
        if settings.waitMilliseconds > 0 {
            footnote(localization(.stepWaitHint, settings.waitMilliseconds))
        }
    }

    private func searchRegionDescription(_ region: SearchRegion?) -> String {
        switch region {
        case nil:
            return localization(.stepRegionWholeScreen)
        case let .screenRect(_, _, width, height):
            return localization(.stepRegionAbsolute, Int(width), Int(height))
        case let .windowRelative(_, _, _, width, height):
            return localization(.stepRegionWindowRelative, Int(width), Int(height))
        }
    }

    private func update(
        _ target: Binding<StepTarget>,
        _ settings: RecognitionSettings,
        threshold: Double? = nil,
        wait: Int? = nil,
        onTimeout: TimeoutBehaviour? = nil,
        searchRegion: SearchRegion?? = nil
    ) {
        let updated = RecognitionSettings(
            threshold: threshold ?? settings.threshold,
            searchRegion: searchRegion ?? settings.searchRegion,
            waitMilliseconds: wait ?? settings.waitMilliseconds,
            onTimeout: onTimeout ?? settings.onTimeout
        )
        switch target.wrappedValue {
        case let .template(name, _):
            target.wrappedValue = .template(name: name, settings: updated)
        case let .text(text, _):
            target.wrappedValue = .text(text, settings: updated)
        default:
            break
        }
    }

    // MARK: - Bridge between the pickers and the model

    private enum ActionKind: String, CaseIterable, Identifiable {
        case click, scroll, move, drag, typeText, pressKey
        var id: String { rawValue }
        var title: String {
            switch self {
            case .click: return localized(.actionClick)
            case .scroll: return localized(.actionScroll)
            case .move: return localized(.actionMove)
            case .drag: return localized(.actionDrag)
            case .typeText: return localized(.actionType)
            case .pressKey: return localized(.actionPressKey)
            }
        }
    }

    private enum TargetKind: String, CaseIterable, Identifiable {
        case cursor, screenPoint, windowRelative, template, text
        var id: String { rawValue }
        var title: String {
            switch self {
            case .cursor: return localized(.targetCursor)
            case .screenPoint: return localized(.targetFixedPoint)
            case .windowRelative: return localized(.targetWindowRelative)
            case .template: return localized(.targetTemplate)
            case .text: return localized(.targetText)
            }
        }
    }

    private var target: Binding<StepTarget> { $step.target }

    private var dragDestination: Binding<StepTarget> {
        Binding(
            get: {
                guard case let .drag(_, destination) = step.action else { return .cursor }
                return destination
            },
            set: { newValue in
                guard case let .drag(button, _) = step.action else { return }
                step.action = .drag(button: button, destination: newValue)
            }
        )
    }

    private var actionKind: Binding<ActionKind> {
        Binding(
            get: {
                switch step.action {
                case .click: return .click
                case .scroll: return .scroll
                case .move: return .move
                case .drag: return .drag
                case .typeText: return .typeText
                case .pressKey: return .pressKey
                }
            },
            set: { kind in
                switch kind {
                case .click:
                    if case .click = step.action { return }
                    step.action = .click(button: .left, count: 1, holdMilliseconds: 0)
                case .scroll:
                    if case .scroll = step.action { return }
                    step.action = .scroll(deltaX: 0, deltaY: -3)
                case .move:
                    step.action = .move
                case .drag:
                    if case .drag = step.action { return }
                    step.action = .drag(button: .left, destination: .cursor)
                case .typeText:
                    if case .typeText = step.action { return }
                    step.action = .typeText("")
                case .pressKey:
                    if case .pressKey = step.action { return }
                    step.action = .pressKey(KeyStroke(key: "return"))
                }
            }
        )
    }

    private func targetKind(_ target: Binding<StepTarget>) -> Binding<TargetKind> {
        Binding(
            get: {
                switch target.wrappedValue {
                case .cursor: return .cursor
                case .screenPoint: return .screenPoint
                case .windowRelative: return .windowRelative
                case .template: return .template
                case .text: return .text
                }
            },
            set: { kind in
                switch kind {
                case .cursor:
                    target.wrappedValue = .cursor
                case .screenPoint:
                    if case .screenPoint = target.wrappedValue { return }
                    target.wrappedValue = .screenPoint(x: 0, y: 0)
                case .windowRelative:
                    if case .windowRelative = target.wrappedValue { return }
                    target.wrappedValue = .windowRelative(corner: .topLeft, dx: 0, dy: 0)
                case .template:
                    if case .template = target.wrappedValue { return }
                    target.wrappedValue = .template(
                        name: "",
                        settings: target.wrappedValue.recognitionSettings ?? RecognitionSettings()
                    )
                case .text:
                    if case .text = target.wrappedValue { return }
                    target.wrappedValue = .text(
                        "",
                        settings: target.wrappedValue.recognitionSettings ?? RecognitionSettings()
                    )
                }
            }
        )
    }

    private var mouseButton: Binding<MouseButton> {
        Binding(
            get: {
                switch step.action {
                case let .click(button, _, _): return button
                case let .drag(button, _): return button
                default: return .left
                }
            },
            set: { newValue in
                switch step.action {
                case let .click(_, count, hold):
                    step.action = .click(button: newValue, count: count, holdMilliseconds: hold)
                case let .drag(_, destination):
                    step.action = .drag(button: newValue, destination: destination)
                default:
                    break
                }
            }
        )
    }

    private var clickCount: Binding<Int> {
        Binding(
            get: {
                guard case let .click(_, count, _) = step.action else { return 1 }
                return count
            },
            set: { newValue in
                guard case let .click(button, _, hold) = step.action else { return }
                step.action = .click(button: button, count: newValue, holdMilliseconds: hold)
            }
        )
    }

    private var clickHold: Binding<Int> {
        Binding(
            get: {
                guard case let .click(_, _, hold) = step.action else { return 0 }
                return hold
            },
            set: { newValue in
                guard case let .click(button, count, _) = step.action else { return }
                step.action = .click(button: button, count: count, holdMilliseconds: newValue)
            }
        )
    }

    private var scrollDeltaX: Binding<Int> {
        Binding(
            get: {
                guard case let .scroll(deltaX, _) = step.action else { return 0 }
                return deltaX
            },
            set: { newValue in
                guard case let .scroll(_, deltaY) = step.action else { return }
                step.action = .scroll(deltaX: newValue, deltaY: deltaY)
            }
        )
    }

    private var scrollDeltaY: Binding<Int> {
        Binding(
            get: {
                guard case let .scroll(_, deltaY) = step.action else { return 0 }
                return deltaY
            },
            set: { newValue in
                guard case let .scroll(deltaX, _) = step.action else { return }
                step.action = .scroll(deltaX: deltaX, deltaY: newValue)
            }
        )
    }

    private var typedText: Binding<String> {
        Binding(
            get: {
                guard case let .typeText(text) = step.action else { return "" }
                return text
            },
            set: { step.action = .typeText($0) }
        )
    }

    private var keyName: Binding<String> {
        Binding(
            get: {
                guard case let .pressKey(stroke) = step.action else { return "return" }
                return stroke.key
            },
            set: { newValue in
                guard case let .pressKey(stroke) = step.action else { return }
                step.action = .pressKey(KeyStroke(key: newValue, modifiers: stroke.modifiers))
            }
        )
    }

    private func modifierBinding(_ modifier: KeyModifier) -> Binding<Bool> {
        Binding(
            get: {
                guard case let .pressKey(stroke) = step.action else { return false }
                return stroke.modifiers.contains(modifier)
            },
            set: { isOn in
                guard case let .pressKey(stroke) = step.action else { return }
                var modifiers = stroke.modifiers
                if isOn {
                    modifiers.append(modifier)
                } else {
                    modifiers.removeAll { $0 == modifier }
                }
                step.action = .pressKey(KeyStroke(key: stroke.key, modifiers: modifiers))
            }
        )
    }

    // MARK: - Reusable components

    /// Wide enough for every unit the panel can put beside a number, in the active language (`UI-24`).
    private var unitColumnWidth: CGFloat {
        UnitColumn.width(of: "ms", localization(.stepRepeatUnit), localization(.stepScrollUnit))
    }

    private func labelledField(
        _ title: String,
        value: Binding<Int>,
        range: ClosedRange<Int>,
        suffix: String? = nil
    ) -> some View {
        HStack {
            Text(title)
            Spacer()
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
            if let suffix {
                Text(suffix)
                    .foregroundStyle(.secondary)
                    // UI-24: the width covers every unit this panel can show, so the fields line up down
                    // the column whichever kind of Step is selected.
                    .frame(width: unitColumnWidth, alignment: .leading)
            }
        }
    }

    private func footnote(_ text: String) -> some View {
        Text(text)
            .font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func warning(_ text: String) -> some View {
        Label(text, systemImage: "exclamationmark.triangle.fill")
            .font(.caption)
            .foregroundStyle(.orange)
            .fixedSize(horizontal: false, vertical: true)
    }
}

extension WindowCorner {
    var title: String {
        switch self {
        case .topLeft: return localized(.cornerTopLeft)
        case .topRight: return localized(.cornerTopRight)
        case .bottomLeft: return localized(.cornerBottomLeft)
        case .bottomRight: return localized(.cornerBottomRight)
        }
    }
}
