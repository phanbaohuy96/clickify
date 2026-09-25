package com.pbh.clickify.overlay

import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.overlay.ui.ScenarioPanelActions
import com.pbh.clickify.overlay.ui.StepPanelActions

/** What the panel's two faces do, wired to the service and to the Overlay's own state. */
internal fun stepPanelActions(
    editing: EditingStep,
    callbacks: OverlayCallbacks,
    update: ((OverlayUiState) -> OverlayUiState) -> Unit,
): StepPanelActions =
    StepPanelActions(
        onDraftChanged = { draft -> update { it.withStep { open -> open.copy(step = open.step.copy(draft = draft)) } } },
        onTypingChanged = { typing -> update { it.withPanelTyping(typing) } },
        onSave = { callbacks.onStepSaved(editing.draft) },
        // OV-36: both exits go through the same question, and neither asks it when there is
        // nothing to lose.
        onBack = { update { it.leaving(PanelExit.TO_SCENARIO) } },
        onCancel = { update { it.leaving(PanelExit.CLOSED) } },
        onDiscard = { update { it.leftBehind() } },
        onKeepEditing = { update { it.stayed() } },
        onDelete = { callbacks.onStepDeleted(editing.draft.stepId) },
        onGo = { by -> callbacks.onStepNavigated(editing.draft.stepId, by) },
        onCropTemplate = callbacks::onCropTemplate,
        onTry = { callbacks.onTryStep(editing.draft) },
    )

/** FS-15: every one of these is the edit and the save at once — there is no Save for a Scenario. */
internal fun scenarioPanelActions(
    scenario: Scenario,
    callbacks: OverlayCallbacks,
    update: ((OverlayUiState) -> OverlayUiState) -> Unit,
): ScenarioPanelActions =
    ScenarioPanelActions(
        onRenamed = { callbacks.onScenarioChanged(scenario.copy(name = it)) },
        onRunCountChanged = { callbacks.onScenarioChanged(scenario.copy(runCount = it)) },
        onCountdownChanged = { callbacks.onScenarioChanged(scenario.copy(countdownMilliseconds = it)) },
        onTypingChanged = { typing -> update { it.withPanelTyping(typing) } },
        onAddStep = callbacks::onAddStep,
        onOpenStep = callbacks::onStepOpened,
        onMoveStep = callbacks::onStepMoved,
        onDeleteStep = callbacks::onStepDeleted,
        // SM-18: asked about before it happens, because it is the one edit here that cannot be
        // undone by doing the opposite — a clamped point has forgotten where it used to be.
        onAskRebuild = { update { it.withScenarioPanel { open -> open.copy(confirmingRebuild = true) } } },
        onKeepScreen = { update { it.withScenarioPanel { open -> open.copy(confirmingRebuild = false) } } },
        onRebuild = callbacks::onRebuildForThisScreen,
        onRecordSilently = { callbacks.onRecord(passThrough = false) },
        onFreeTheTouch = callbacks::onFreeTheTouch,
        onOpenApp = callbacks::onOpenApp,
        onCloseOverlay = callbacks::onCloseOverlay,
        onDismiss = { update { it.copy(panel = null) } },
    )

internal fun OverlayUiState.withStep(edit: (PanelState.StepEditor) -> PanelState.StepEditor): OverlayUiState =
    copy(panel = (panel as? PanelState.StepEditor)?.let(edit) ?: panel)

internal fun OverlayUiState.withScenarioPanel(edit: (PanelState.ScenarioEditor) -> PanelState.ScenarioEditor): OverlayUiState =
    copy(panel = (panel as? PanelState.ScenarioEditor)?.let(edit) ?: panel)

internal fun OverlayUiState.withPanelTyping(typing: Boolean): OverlayUiState =
    copy(
        panel =
            when (val open = panel) {
                is PanelState.StepEditor -> open.copy(typing = typing)
                is PanelState.ScenarioEditor -> open.copy(typing = typing)
                null -> null
            },
    )
