package com.pbh.clickify.overlay

import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.overlay.Marker
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.overlay.ui.RecordingEvent
import java.util.UUID

/**
 * What the Overlay asks the service to do. Nothing in the Overlay decides anything by itself.
 *
 * Four interfaces rather than one list of eighteen methods, because they answer to four different
 * parts of the specification and change for four different reasons: running a Scenario (`GX-*`),
 * recording one (`RD-*`), editing one (`SM-*`, `OV-21` to `OV-28`), and the Overlay's own
 * existence (`OV-14`, `OV-30`).
 */
interface OverlayCallbacks :
    RunCallbacks,
    RecordCallbacks,
    PickCallbacks,
    CropCallbacks,
    EditCallbacks,
    ShellCallbacks

/** GX-7, GX-8, GX-11: starting, stopping, and the one recovery that is not either. */
interface RunCallbacks {
    fun onStart()

    /**
     * GX-1 for one Step: run **this draft**, on its own, without the Scenario around it.
     *
     * The draft rather than the Step on disk, because the question being asked is "does what I
     * have just changed do what I meant", and a Try that ran the old version would answer a
     * different question. Nothing is saved by trying.
     */
    fun onTryStep(draft: StepDraft)

    fun onStop()

    fun onFreeTheTouch()
}

/** RD-1 to RD-8: recording a session of real touches and turning it into Steps. */
interface RecordCallbacks {
    /** RD-5, RD-9: [passThrough] hands each touch back to the application; silent does not. */
    fun onRecord(passThrough: Boolean)

    fun onStopRecording()

    /** RD-2: one touch event, with Android's `MotionEvent` left at the boundary. */
    fun onRecordingEvent(event: RecordingEvent)
}

/**
 * PK-1 to PK-3: aiming one Step at the screen underneath.
 *
 * Separate from [RecordCallbacks] although both watch the same touches, because they answer
 * opposite questions. Recording asks *what did the user do*, and hands the touch on so the
 * application advances with it. Picking asks *where does the user mean*, and deliberately
 * swallows the touch so the screen stays where it was.
 */
interface PickCallbacks {
    /** PK-2: one touch event while the screen is armed. The finger leaving ends the pick. */
    fun onPickEvent(event: RecordingEvent)

    /** PK-3: leave aiming without creating anything. */
    fun onCancelPick()
}

/**
 * TP-7: cropping a **Template** out of a still frame of the screen.
 *
 * Its own interface for the same reason [PickCallbacks] is not [RecordCallbacks]: all three take
 * the Overlay off the screen and wait for a finger, and all three mean something different by it.
 * Recording asks *what did you do*, picking asks *where do you mean*, and this asks *what does it
 * look like*.
 */
interface CropCallbacks {
    /** Takes the Overlay down, takes one frame, and puts the frame back up to be cropped. */
    fun onCropTemplate(purpose: CropPurpose)

    /** TP-8: the rectangle the user settled on, in raw display pixels. */
    fun onCropped(region: ScreenRegion)

    fun onCancelCrop()
}

/** Everything that changes the open Scenario. Each of these is written to disk at once (`FS-15`). */
interface EditCallbacks {
    fun onAddStep()

    fun onMarkerMoved(
        marker: Marker,
        to: ScreenPoint,
    )

    /** OV-7: a Marker is placed by dragging and configured by tapping. This is the tap. */
    fun onMarkerTapped(marker: Marker)

    /** OV-28: opens the panel on one named Step, whether or not it draws a Marker. */
    fun onStepOpened(stepId: UUID)

    /** OV-22: only ever called with a draft that has no violations left. */
    fun onStepSaved(draft: StepDraft)

    fun onStepDeleted(stepId: UUID)

    /** OV-6: renumbers this Marker and its neighbours', and is applied at once. */
    fun onStepMoved(
        stepId: UUID,
        by: Int,
    )

    /** OV-24: opens the panel on the Step [by] places away in the Scenario. */
    fun onStepNavigated(
        stepId: UUID,
        by: Int,
    )

    /** OV-28: the Scenario's own fields — its name, how often it runs, its countdown. */
    fun onScenarioChanged(scenario: Scenario)

    /** SM-18: re-measure this Scenario against the screen in front of the user. */
    fun onRebuildForThisScreen()
}

/** The Overlay as a thing on the screen, rather than as an editor. */
interface ShellCallbacks {
    /** OV-14: where the control was let go of, so the next launch can put it back there. */
    fun onControlMoved(at: ScreenPoint)

    /** OV-30: bring the Activity back. */
    fun onOpenApp()

    /** OV-30: take the Overlay away entirely. */
    fun onCloseOverlay()
}
