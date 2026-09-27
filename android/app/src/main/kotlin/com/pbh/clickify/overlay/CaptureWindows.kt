package com.pbh.clickify.overlay

import android.content.Context
import android.view.WindowManager
import com.pbh.clickify.core.designsystem.OverlayTheme
import com.pbh.clickify.core.overlay.OverlayLayoutParams
import com.pbh.clickify.core.overlay.OverlayWindow
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.overlay.ui.CropLayer
import com.pbh.clickify.overlay.ui.PickLayer
import com.pbh.clickify.overlay.ui.RecordingEvent
import com.pbh.clickify.overlay.ui.RecordingLayer

/**
 * The two display-sized layers that take a touch instead of letting it through.
 *
 * They are one class because they are the same window with two different promises. Recording
 * (`RD-1`) keeps the touch **and hands it on**, so the application underneath advances exactly as
 * it would under a finger; picking (`PK-1`) keeps it and hands on nothing, so the screen the user
 * is aiming at stays put. Everything else — display coordinates (`OV-31`), the border that says
 * which mode is in force, the full-screen frame — is shared, and a second copy of it would be a
 * second place for the coordinate space to be got wrong.
 *
 * Never both at once: [OverlayUiState] cannot be recording and picking together, and the refresh
 * below dismisses whichever the state does not name.
 */
internal class CaptureWindows(
    private val context: Context,
    windowManager: WindowManager,
    private val onRecordingEvent: (RecordingEvent) -> Unit,
    private val onPickEvent: (RecordingEvent) -> Unit,
    private val onCropped: (ScreenRegion) -> Unit,
    private val onCancelCrop: () -> Unit,
) {
    private val recording = OverlayWindow(context, windowManager)
    private val picking = OverlayWindow(context, windowManager)
    private val cropping = OverlayWindow(context, windowManager)

    /** OV-13: part of what tells the coordinator the window stack has changed under the control. */
    val signature: String get() = "${recording.isShowing}|${picking.isShowing}|${cropping.isShowing}"

    fun refresh(current: OverlayUiState) {
        refreshRecording(current)
        refreshPicking(current)
        refreshCropping(current)
    }

    fun dismiss() {
        cropping.dismiss()
        picking.dismiss()
        recording.dismiss()
    }

    /**
     * TP-7: the third layer, and the only one that shows the user something rather than taking it.
     *
     * Attached only once the frame has come back. While it has not, [CropState.frame] is null and
     * **nothing at all** of Clickify's is on the screen — which is the whole reason the frame is
     * worth anything.
     */
    private fun refreshCropping(current: OverlayUiState) {
        val frame = current.crop?.frame
        val purpose = current.crop?.purpose
        if (frame == null || purpose == null) {
            cropping.dismiss()
            return
        }
        val bounds = context.overlayBounds()
        cropping.show(layout(listening = true)) {
            OverlayTheme {
                CropLayer(
                    frame = frame,
                    purpose = purpose,
                    onConfirm = onCropped,
                    onCancel = onCancelCrop,
                    // OV-31: this window is measured against the display, so the system bars are
                    // inside it rather than outside. The bar would otherwise sit on the clock.
                    bounds = bounds,
                )
            }
        }
    }

    /**
     * RD-1: re-shown rather than rebuilt when [RecordingSession.listening] changes.
     *
     * `show` on an attached window is an `updateViewLayout`, which is what swapping the touchable
     * flag has to be. Rebuilding it would drop the gesture in progress.
     */
    private fun refreshRecording(current: OverlayUiState) {
        val session = current.recording
        if (session == null) {
            recording.dismiss()
            return
        }
        recording.show(layout(listening = session.listening)) {
            OverlayTheme { RecordingLayer(onTouch = onRecordingEvent, passThrough = session.passThrough) }
        }
    }

    /**
     * PK-1: always listening, unlike the recording layer.
     *
     * Nothing is handed back to the application underneath (`PK-2`), so there is never a moment
     * when this window has to stop taking touches — which is also why picking needs neither of the
     * two guards `RD-5` needed.
     */
    private fun refreshPicking(current: OverlayUiState) {
        if (!current.isPicking) {
            picking.dismiss()
            return
        }
        picking.show(layout(listening = true)) {
            OverlayTheme { PickLayer(onTouch = onPickEvent) }
        }
    }

    private fun layout(listening: Boolean): WindowManager.LayoutParams {
        val profile = context.currentScreenProfile()
        return OverlayLayoutParams.recordingLayer(
            displayWidth = profile.widthPixels,
            displayHeight = profile.heightPixels,
            listening = listening,
        )
    }
}
