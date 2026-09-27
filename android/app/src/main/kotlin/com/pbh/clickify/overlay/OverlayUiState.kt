package com.pbh.clickify.overlay

import androidx.compose.ui.graphics.ImageBitmap
import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.editor.toStep
import com.pbh.clickify.domain.editor.violations
import com.pbh.clickify.domain.overlay.Marker
import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepViolation
import java.util.UUID

/**
 * What the Overlay is showing, in one value.
 *
 * The floating control has exactly two shapes (`OV-12`), and which one it wears is decided here
 * rather than by whoever last called a setter.
 */
data class OverlayUiState(
    /**
     * The Scenario as last saved, or null before one is open.
     *
     * Held here rather than beside the state because four things read it — the Markers, both
     * halves of the panel, the control's caption and the notification — and two copies of the
     * same truth is how a Marker ends up somewhere the Scenario does not agree with.
     */
    val scenario: Scenario? = null,
    val markers: List<Marker> = emptyList(),
    val run: RunState = RunState.Stopped,
    /** OV-17: why the last run ended, shown where the user is already looking. */
    val lastFinish: FinishReason? = null,
    val collapsed: Boolean = false,
    /** OV-21, OV-28: what the one panel window is showing, or null when it is closed. */
    val panel: PanelState? = null,
    /** RD-1: the session in progress, or null when nothing is being recorded. */
    val recording: RecordingSession? = null,
    /** PK-1: true while the Overlay is hidden and one gesture is being waited for. */
    val picking: Boolean = false,
    /** TP-7: cropping a Template, or null when nothing is being cropped. */
    val crop: CropState? = null,
    /** TP-21: how many Steps this run has skipped because a wait ran out. */
    val skippedSteps: Int = 0,
    /**
     * The Screen profile this Scenario's coordinates are measured against (`SM-14`), once it has
     * one.
     *
     * Markers are clamped into **this** rather than into the screen in front of the user, so that
     * dragging and `SM-17` agree about where the edge is. A phone rotated after the Scenario was
     * built would otherwise let a Marker be dragged to a point the Scenario itself calls
     * off-screen.
     */
    val authoringProfile: ScreenProfile? = null,
    /** OV-37: the screen in front of the user, re-read whenever the phone is rotated. */
    val screen: OverlayScreen? = null,
) {
    val scenarioName: String get() = scenario?.name.orEmpty()

    val stepCount: Int get() = scenario?.steps?.size ?: 0

    /** Anything at all in flight: counting down, walking Steps, or finishing a stroke. */
    val running: Boolean get() = run !is RunState.Stopped

    /** RD-1: recording, like running, is a state in which the editor has to be out of the way. */
    val isRecording: Boolean get() = recording != null

    /** PK-1: the user is aiming one Step at the screen underneath, and nothing may be in front of it. */
    val isPicking: Boolean get() = picking

    /** TP-7: true from the moment the Overlay steps aside until the crop is taken or abandoned. */
    val isCropping: Boolean get() = crop != null

    /**
     * TP-7: whether the floating control is on the screen at all.
     *
     * It is the one window that is otherwise always up — Stop has to be reachable (`OV-13`) — and
     * cropping is the one moment it must not be. The frame is taken with nothing of Clickify's
     * on the screen, and afterwards the user needs every pixel of it to drag on. Nothing can be
     * running while this is true, so no Stop is being hidden.
     */
    val showControl: Boolean get() = !isCropping

    /**
     * OV-11, RD-1, PK-1: Markers would be tapped by the very Gestures they describe; while
     * recording or picking they would swallow the touches meant for the application underneath.
     *
     * [collapsed] is in here too, and that is `OV-33`. Collapsing the control means "get out of my
     * way", and a dozen handles left scattered over the screen is not out of the way.
     */
    val showMarkers: Boolean get() = !running && !isRecording && !isPicking && !isCropping && !collapsed

    /**
     * OV-20: the panel is open only while nothing is running.
     *
     * Derived rather than set, and that is the point. The panel is the one window allowed to take
     * input focus, so "it is closed before a run starts" has to be a property of the state and not
     * a call somebody remembers to make. A `setText` Step therefore never has this window to find.
     */
    val showPanel: Boolean
        get() = panel != null && !running && !isRecording && !isPicking && !isCropping && !collapsed

    /** OV-20: the window drops FLAG_NOT_FOCUSABLE only while a field in it holds the caret. */
    val typing: Boolean get() = showPanel && panel?.typing == true

    /** The Step open in the panel, if the panel is showing a Step at all. */
    val editing: EditingStep? get() = (panel as? PanelState.StepEditor)?.step

    /** Which Marker the panel is about, so the Marker layer can say which one it is (`OV-21`). */
    val editedStepId: UUID? get() = editing?.draft?.stepId.takeIf { showPanel }

    sealed interface RunState {
        data object Stopped : RunState

        /** OV-16: shown in the control, counting down, cancelled by the same Stop. */
        data class CountingDown(
            val remainingMilliseconds: Int,
        ) : RunState

        data class Running(
            val stepNumber: Int,
            val stepCount: Int,
        ) : RunState

        /** GX-8: a stroke is still in flight and is allowed to finish. */
        data object Stopping : RunState
    }
}

/**
 * Cropping a Template out of a still frame (`TP-7`).
 *
 * [frame] is null for the moment in between: the Overlay has taken itself off the screen and the
 * frame has not come back yet. Nothing of Clickify's is drawn in that moment, which is the whole
 * reason for it — macOS had to hide a window and wait 120 ms for the window server to believe it
 * (`RG-5`), and this is the same wait with the same reason.
 */
data class CropState(
    val purpose: CropPurpose,
    val frame: ImageBitmap? = null,
)

/**
 * What the Template being cropped is for.
 *
 * The two are the same gesture and different destinations: one becomes the thing the Step aims at
 * (`TP-19`), the other the condition it waits for (`TP-24`).
 */
enum class CropPurpose {
    TARGET,
    GUARD,
}

/**
 * A recording session in progress (`RD-1`).
 *
 * [listening] is false only for the moment a recorded touch is being handed back to the
 * application underneath (`RD-5`). The layer has to stop taking touches for exactly that long, or
 * it records its own re-emission and the session never ends.
 */
data class RecordingSession(
    val touches: Int = 0,
    val listening: Boolean = true,
    /**
     * RD-9: false for a *silent* session, which swallows the touch and hands nothing back.
     *
     * The two modes are the same window with different promises, and the user has to be able to
     * tell which one is in force — in pass-through the application underneath is advancing under
     * synthetic touches (`RD-5`), and silently it is not moving at all. `RD-6`'s border says so.
     */
    val passThrough: Boolean = true,
)

/**
 * The two things the panel window can be showing (OV-21, OV-28).
 *
 * One window rather than two, because they want the same place on the screen, they want the same
 * one exception to `OV-3`, and two of them open at once has no meaning. Making that a sealed type
 * rather than two nullable fields is what stops the pair drifting apart.
 */
sealed interface PanelState {
    /** OV-20: true only while a field in the panel holds the caret. */
    val typing: Boolean

    /** OV-28: the Scenario as a whole — its name, how often it runs, and its Steps in order. */
    data class ScenarioEditor(
        override val typing: Boolean = false,
        /** SM-18: true while the user is being asked whether to re-measure against this screen. */
        val confirmingRebuild: Boolean = false,
    ) : PanelState

    data class StepEditor(
        val step: EditingStep,
        override val typing: Boolean = false,
        /** OV-36: the way out that is waiting on an answer, or null when none is. */
        val leaving: PanelExit? = null,
    ) : PanelState
}

/**
 * OV-36: the two ways out of the Step panel, which differ only in where they leave the user.
 *
 * Both discard the draft, so both are asked about — see [OverlayUiState.leaving]. Keeping them as
 * one type rather than two booleans is what stops a confirmation being answered for the wrong
 * exit, which is a dialogue that closes the panel when the user asked to go up a level.
 */
enum class PanelExit {
    /** Up to the Scenario, which is the list of every Step. */
    TO_SCENARIO,

    /** Out of the editor entirely, leaving the Markers and the control. */
    CLOSED,
}

/**
 * One Step open in the Step panel, with everything the panel needs to judge it (OV-21, OV-22).
 *
 * [limits] travels with the draft because they are the platform's, read from the connected
 * service (`SM-17`), and a panel that judged a Step against the defaults would let a Step through
 * on a device whose limits are lower — which is a Step that silently does nothing.
 */
data class EditingStep(
    val draft: StepDraft,
    /**
     * TP-29: the pixels of every Template this Step refers to, decoded when the panel opened.
     *
     * Doing double duty on purpose. It is what the panel draws, so the user can see *which
     * picture* a Step is looking for rather than a bare identifier — and its keys are the Templates
     * that are actually on disk, which is exactly what `TP-29`'s check needs. A Template that
     * could not be decoded is absent from both at once, and cannot be out of step with itself.
     */
    val previews: Map<UUID, ImageBitmap> = emptyMap(),
    /** The Step as saved, so the panel can tell whether there is anything to lose (`OV-24`). */
    val original: Step,
    /** OV-6: counting from 1, as the Marker shows it. */
    val stepNumber: Int,
    val stepCount: Int,
    val limits: GestureLimits = GestureLimits(),
    val profile: ScreenProfile? = null,
) {
    /** SM-17: every reason this cannot be saved, all of them at once. */
    val violations: List<StepViolation> get() = draft.violations(limits, profile, previews.keys)

    /** OV-22: Save is offered only when there is nothing wrong to save. */
    val canSave: Boolean get() = violations.isEmpty()

    val canMoveUp: Boolean get() = stepNumber > 1

    val canMoveDown: Boolean get() = stepNumber < stepCount

    /** Whether Save would change anything. Moving a Step does not, so it stays available. */
    val isDirty: Boolean get() = draft.toStep() != original

    /**
     * OV-24: walking to the next Step is refused while there are unsaved edits.
     *
     * Refused rather than silently discarding or silently saving. Both of those are guesses about
     * what the user meant, and Save and Cancel are already on screen to be asked.
     */
    val canGoBack: Boolean get() = stepNumber > 1 && !isDirty

    val canGoForward: Boolean get() = stepNumber < stepCount && !isDirty
}

/**
 * OV-33: "I am finished", which is the one thing the editor could not previously be told.
 *
 * It writes nothing. Every edit was already on disk the moment it was made (`FS-15`), so there is
 * no pending state for a Save button to flush — what was missing was a way to put the editor away
 * in one press instead of closing the panel, then collapsing, and leaving the Markers behind.
 */
internal fun OverlayUiState.done(): OverlayUiState = copy(panel = null, collapsed = true, lastFinish = null)

/**
 * OV-36: asked for a way out of the Step panel.
 *
 * Nothing about the Step is written and nothing is thrown away yet. If the draft differs from the
 * Step on disk the question is put on screen and the panel stays exactly as it was; if it does
 * not, there is nothing to ask about and the exit happens at once. Asking either way would train
 * the user to dismiss the dialogue without reading it, which is the same as not having one.
 *
 * This is the half `OV-24` was missing. The arrows between Steps refuse to move while there are
 * unsaved edits, because walking sideways looks like staying put; leaving is visibly leaving, so
 * it is allowed — once the user has said so.
 */
internal fun OverlayUiState.leaving(exit: PanelExit): OverlayUiState {
    val open = panel as? PanelState.StepEditor ?: return this
    return if (open.step.isDirty) copy(panel = open.copy(leaving = exit)) else left(exit)
}

/** OV-36: the question answered with *discard*, so the exit that was asked about happens. */
internal fun OverlayUiState.leftBehind(): OverlayUiState {
    val open = panel as? PanelState.StepEditor ?: return this
    return left(open.leaving ?: PanelExit.CLOSED)
}

/** OV-36: the question answered with *keep editing*, so nothing happens but the asking stops. */
internal fun OverlayUiState.stayed(): OverlayUiState = copy(panel = (panel as? PanelState.StepEditor)?.copy(leaving = null) ?: panel)

private fun OverlayUiState.left(exit: PanelExit): OverlayUiState =
    when (exit) {
        PanelExit.TO_SCENARIO -> copy(panel = PanelState.ScenarioEditor())
        PanelExit.CLOSED -> copy(panel = null)
    }
