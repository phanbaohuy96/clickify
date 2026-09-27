package com.pbh.clickify.overlay

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbh.clickify.core.designsystem.OverlayTheme
import com.pbh.clickify.core.overlay.OverlayLayoutParams
import com.pbh.clickify.core.overlay.OverlayWindow
import com.pbh.clickify.domain.editor.previewing
import com.pbh.clickify.domain.editor.toStep
import com.pbh.clickify.domain.overlay.markers
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.overlay.ui.FloatingControl
import com.pbh.clickify.overlay.ui.FloatingControlActions
import com.pbh.clickify.overlay.ui.ScenarioPanel
import com.pbh.clickify.overlay.ui.StepPanel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the Overlay windows and decides which of them are attached (OV-1, OV-4, OV-27).
 *
 * Windows are removed rather than hidden, because an Overlay left attached keeps drawing and keeps
 * a `ViewModelStore` alive — [OverlayWindow] clears it on the way out and this class is what calls
 * that.
 *
 * Placing the control and drawing the Markers are each a job of their own, in
 * [ControlPlacement] and [MarkerWindows]. What is left here is the one question this class exists
 * to answer: given the state, which windows should exist.
 */
class OverlayCoordinator(
    private val context: Context,
    windowManager: WindowManager,
    private val callbacks: OverlayCallbacks,
) {
    private val control = OverlayWindow(context, windowManager)
    private val panel = OverlayWindow(context, windowManager)
    private val captures =
        CaptureWindows(
            context = context,
            windowManager = windowManager,
            onRecordingEvent = callbacks::onRecordingEvent,
            onPickEvent = callbacks::onPickEvent,
            onCropped = callbacks::onCropped,
            onCancelCrop = callbacks::onCancelCrop,
        )

    private val _state = MutableStateFlow(OverlayUiState(screen = context.overlayScreen()))
    val state: StateFlow<OverlayUiState> = _state.asStateFlow()

    private val placement =
        ControlPlacement(context, control, callbacks::onControlMoved).also {
            // OV-13: the panel and the control both want the bottom of the screen, and the
            // control is the one drawn on top. It moves; the panel does not.
            //
            // The inset comes off because the two live in different coordinate spaces (`OV-32`):
            // the panel is measured against the display and reaches past the navigation bar, while
            // the control is placed inside the system bars. Without this the control is held a
            // navigation bar's height too high and a strip of the application shows between them.
            //
            // OV-37: which edge the panel took depends on how the phone is being held.
            panel.onResized = { width, height ->
                val screen = _state.value.screen ?: context.overlayScreen()
                if (screen.landscape) {
                    it.keepClearOf(end = width - screen.bounds.rightInset)
                } else {
                    it.keepClearOf(bottom = height - screen.bounds.bottomInset)
                }
            }
        }
    private val markers =
        MarkerWindows(
            context = context,
            windowManager = windowManager,
            state = state,
            onMoved = callbacks::onMarkerMoved,
            onTapped = callbacks::onMarkerTapped,
        )

    /**
     * The window attributes the panel was last given (`OV-20`, `OV-37`).
     *
     * Kept so they are re-applied only when they actually change. `updateViewLayout` on a window
     * that is gaining or losing focus disturbs the focus inside it, and re-applying the same value
     * on every state change is how that becomes a loop. The screen is part of the signature
     * because a rotation changes the panel's shape without changing anything about the state the
     * user can see.
     */
    private var panelSignature: String? = null

    /**
     * OV-13: what was attached last time round, so the control can be put back on top.
     *
     * Android stacks windows of one type in the order they were attached, and there is no way to
     * ask for a different one. A Marker handle or the panel attached after the control is
     * therefore drawn **over** it — over Stop, which is the one thing this app promises is always
     * reachable. Re-attaching the control is the only way to take the top back.
     */
    private var attached: String? = null

    fun update(reduce: OverlayUiState.() -> OverlayUiState) {
        _state.update { it.reduce().withMarkers() }
        refreshWindows()
    }

    /** The same thing as [update], in the shape a plain function reference can be passed in. */
    private fun reduce(edit: (OverlayUiState) -> OverlayUiState) = update { edit(this) }

    fun show(scenario: Scenario) {
        update { copy(scenario = scenario, authoringProfile = scenario.screenProfile) }
    }

    /** OV-14: where the control was left last time, or nothing if it has never been moved. */
    fun placeControl(remembered: ScreenPoint?) = placement.place(remembered)

    /**
     * OV-37: the phone was rotated, so every window has to be placed against new edges.
     *
     * Nothing here is recreated — an Overlay window is not an Activity and survives the
     * configuration change untouched, which is exactly the problem: it keeps the size and the
     * position it was given for the screen that is no longer there. Clearing [panelSignature] is
     * what makes the panel accept new attributes; the state carrying the new [OverlayScreen] is
     * what makes the content inside it re-measure.
     */
    fun onScreenChanged() {
        panelSignature = null
        placement.onScreenChanged()
        update { copy(screen = context.overlayScreen()) }
    }

    fun hide() {
        captures.dismiss()
        panel.dismiss()
        markers.dismiss()
        control.dismiss()
        _state.value = OverlayUiState(screen = context.overlayScreen())
    }

    /**
     * OV-21: the Markers show the **draft**, not the Step last written to disk.
     *
     * Derived on every update rather than stored, because two copies of the same truth is how a
     * Marker ends up somewhere the Scenario does not agree with. It also answers the thing that
     * looked like a bug on a real device: choosing "swipe" tells the user to drag a destination
     * that, without this, is not drawn until they save.
     */
    private fun OverlayUiState.withMarkers(): OverlayUiState {
        val saved = scenario ?: return copy(markers = emptyList())
        val previewed = editing?.let { saved.previewing(it.draft.toStep()) } ?: saved
        return copy(markers = previewed.markers())
    }

    private fun refreshWindows() {
        val current = _state.value
        markers.refresh(current)
        refreshPanel(current)
        captures.refresh(current)

        val signature = "${markers.signature}|${panel.isShowing}|${captures.signature}"
        if (attached != null && attached != signature) control.dismiss()
        attached = signature

        // TP-7: the one moment the control comes down. Nothing can be running while a Template
        // is being cropped, so no Stop is being taken away.
        if (!current.showControl) {
            control.dismiss(immediate = true)
            return
        }

        val wasShowing = control.isShowing
        refreshControl()
        if (!wasShowing) placement.settle()
    }

    private fun refreshControl() {
        val at = placement.displayed()
        control.show(OverlayLayoutParams.floating(x = at.x, y = at.y)) {
            OverlayTheme {
                val live by state.collectAsStateWithLifecycle()
                FloatingControl(
                    state = live,
                    actions =
                        FloatingControlActions(
                            onStart = callbacks::onStart,
                            onStop = callbacks::onStop,
                            onAddStep = callbacks::onAddStep,
                            onCancelPick = callbacks::onCancelPick,
                            onRecord = { callbacks.onRecord(passThrough = true) },
                            onStopRecording = callbacks::onStopRecording,
                            onOpenPanel = { update { copy(panel = PanelState.ScenarioEditor()) } },
                            onDone = { update { done() } },
                            onFreeTheTouch = callbacks::onFreeTheTouch,
                            onToggleCollapsed = { update { copy(collapsed = !collapsed) } },
                            onDragBy = placement::moveBy,
                            onDragFinished = placement::finishDrag,
                        ),
                )
            }
        }
    }

    /**
     * OV-20, the enforcement half.
     *
     * [OverlayUiState.showPanel] is false whenever anything is running, so the one window allowed
     * to take input focus cannot survive into a run — a `setText` Step never finds this window
     * under `findFocus(FOCUS_INPUT)`. Nothing here has to remember to close it.
     */
    private fun refreshPanel(current: OverlayUiState) {
        if (!current.showPanel) {
            panelSignature = null
            // TP-7: the panel is the biggest thing on the screen and the one most likely to be
            // over whatever the user wants to crop.
            panel.dismiss(immediate = current.isCropping)
            placement.keepClearOf()
            return
        }

        val screen = current.screen ?: context.overlayScreen()
        val signature = "${current.typing}|${screen.profile.widthPixels}x${screen.profile.heightPixels}"
        if (panelSignature == signature) return
        panelSignature = signature

        panel.show(context.panelParams(current.typing, screen)) {
            OverlayTheme {
                val live by state.collectAsStateWithLifecycle()
                when (val open = live.panel) {
                    is PanelState.StepEditor ->
                        StepPanel(
                            editing = open.step,
                            leaving = open.leaving,
                            screen = live.screen,
                            actions = stepPanelActions(open.step, callbacks, ::reduce),
                        )

                    is PanelState.ScenarioEditor ->
                        live.scenario?.let {
                            ScenarioPanel(
                                scenario = it,
                                screen = live.screen,
                                confirmingRebuild = open.confirmingRebuild,
                                actions = scenarioPanelActions(it, callbacks, ::reduce),
                            )
                        }

                    null -> Unit
                }
            }
        }
    }
}

/**
 * OV-37: a sheet on the bottom edge, or one on the end edge, depending on how the phone is held.
 *
 * The width in landscape is capped rather than proportional. Half of a 2992-pixel screen is a
 * text field two-thirds of a metre wide on paper and unreadable in practice; what a form wants
 * is a column, and a column has a width that stops growing.
 */
private fun Context.panelParams(
    typing: Boolean,
    screen: OverlayScreen,
): WindowManager.LayoutParams =
    if (screen.landscape) {
        val density = resources.displayMetrics.density
        OverlayLayoutParams.sidePanel(
            typing = typing,
            width = (SIDE_PANEL_WIDTH_DP * density).toInt().coerceAtMost(screen.profile.widthPixels / 2),
            displayHeight = screen.profile.heightPixels,
            endInsetPixels = screen.bounds.rightInset,
        )
    } else {
        OverlayLayoutParams.panel(
            typing = typing,
            displayWidth = screen.profile.widthPixels,
            bottomInsetPixels = screen.bounds.bottomInset,
        )
    }

/** OV-37: as wide as a phone's portrait sheet, which is as wide as a form should be. */
private const val SIDE_PANEL_WIDTH_DP = 400
