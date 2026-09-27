package com.pbh.clickify.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pbh.clickify.core.ui.AppLanguage
import com.pbh.clickify.core.ui.localisedFor
import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.editor.previewing
import com.pbh.clickify.domain.editor.rebuiltFor
import com.pbh.clickify.domain.editor.toDraft
import com.pbh.clickify.domain.editor.toStep
import com.pbh.clickify.domain.editor.withPointsFrom
import com.pbh.clickify.domain.editor.withStepAdded
import com.pbh.clickify.domain.editor.withStepMoved
import com.pbh.clickify.domain.editor.withStepRemoved
import com.pbh.clickify.domain.editor.withStepReplaced
import com.pbh.clickify.domain.editor.withStepsAdded
import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.overlay.Marker
import com.pbh.clickify.domain.overlay.withMarkerMoved
import com.pbh.clickify.domain.recognition.TemplateFinder
import com.pbh.clickify.domain.recording.toPickedStep
import com.pbh.clickify.domain.recording.toSteps
import com.pbh.clickify.domain.repository.ScenarioRepository
import com.pbh.clickify.domain.repository.TemplateFiles
import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.domain.run.RunEvent
import com.pbh.clickify.domain.run.ScenarioRunner
import com.pbh.clickify.domain.run.freeTheTouchGesture
import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.TemplateSearch
import com.pbh.clickify.domain.scenario.paddedForSearch
import com.pbh.clickify.domain.settings.ControlPosition
import com.pbh.clickify.domain.settings.SettingsRepository
import com.pbh.clickify.overlay.ui.RecordingEvent
import com.pbh.clickify.service.ClickifyAccessibilityService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Where a run lives (GX-7), and what holds the Overlay windows open.
 *
 * A foreground service for two reasons, only one of which is the system's: it keeps the process
 * alive while another application is in front, and it puts the run behind a notification the user
 * cannot miss. Something driving the phone by itself should never be invisible.
 */
@AndroidEntryPoint
class OverlayService : Service() {
    @Inject
    lateinit var scenarios: ScenarioRepository

    @Inject
    lateinit var settings: SettingsRepository

    /** TP-27: the pixels of this Scenario's Templates, beside its scenario.json. */
    @Inject
    lateinit var templates: TemplateFiles

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The Context every Overlay window and every screen measurement goes through.
     *
     * A window context rather than the Service's own, and not as a formality: a Service Context is
     * not associated with a display, so `Context.display` throws on it and
     * [currentScreenProfile] with it. It is also what `TYPE_APPLICATION_OVERLAY` has wanted since
     * API 30 — the same fix answers both.
     *
     * Built from an explicit [Display], because the two-argument `createWindowContext` needs a
     * display-associated Context to begin with and a Service has none. Naming the display is the
     * only way in from here.
     */
    private lateinit var overlayContext: Context
    private var coordinator: OverlayCoordinator? = null
    private var runner: ScenarioRunner? = null
    private var runJob: Job? = null
    private var recorder: Recorder? = null

    /** RD-5: true while a recorded touch is being handed back, so nothing records the handing. */
    private var replaying = false

    /**
     * TP-7: the still frame being cropped, kept as a Bitmap rather than only as what is drawn.
     *
     * The Overlay shows an `ImageBitmap` and the crop has to come out of the real pixels, so both
     * exist for as long as the crop does and neither outlives it.
     */
    private var cropFrame: Bitmap? = null
    private var scenario: Scenario? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // IL-3: the notification is not Compose, so it does not get the language for free. It is
        // also the one part of the interface that is read while another application is in front.
        withLanguage().createRunNotificationChannel()
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        overlayContext = createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val active =
            OverlayCoordinator(
                context = overlayContext,
                windowManager = overlayContext.getSystemService(WindowManager::class.java),
                callbacks = callbacks,
            )
        coordinator = active
        // OV-29: the notification is re-posted whenever what it would say changes, and never
        // otherwise — a notification rebuilt on every countdown tick flickers and loses its place.
        active.state
            .map { it.notification() }
            .distinctUntilChanged()
            .onEach { withLanguage().updateRunNotification(it) }
            .launchIn(scope)
        scope.launch {
            active.placeControl(
                settings.settings
                    .first()
                    .controlPosition
                    ?.toPoint(),
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(
            NOTIFICATION_ID,
            withLanguage().buildRunNotification(coordinator?.state?.value?.notification() ?: RunNotificationState()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        when (intent?.action) {
            ACTION_OPEN -> intent.scenarioId()?.let(::open)
            ACTION_STOP -> callbacks.onStop()
            ACTION_FREE_TOUCH -> callbacks.onFreeTheTouch()
            ACTION_CLOSE -> stopSelf()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    /**
     * OV-37: the phone was rotated, and no Overlay window has noticed.
     *
     * A Service receives this as a `ComponentCallbacks`, which is the only notice this process
     * gets: the windows are attached to the window manager rather than to an Activity, so nothing
     * recreates them and they keep the size and position they were given for the screen that is
     * no longer there. Left alone, the control ends up off the bottom of a shorter screen and the
     * panel keeps a portrait phone's width on a landscape one.
     *
     * Done twice on purpose. `maximumWindowMetrics` is the display's, and on the test device it
     * still reported the old bounds when this callback arrived; the second pass is what actually
     * lands, and the first is what makes the common case immediate.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        coordinator?.onScreenChanged()
        scope.launch {
            delay(ROTATION_SETTLE_MILLISECONDS)
            coordinator?.onScreenChanged()
        }
    }

    override fun onDestroy() {
        runner?.requestStop()
        coordinator?.hide()
        coordinator = null
        scope.cancel()
        super.onDestroy()
    }

    private fun open(id: UUID) {
        scope.launch {
            when (val loaded = scenarios.load(id)) {
                is AppResult.Success -> {
                    scenario = loaded.data.scenario
                    coordinator?.show(loaded.data.scenario)
                    // TP-27: opening is the one moment with no draft anywhere, so it is the one
                    // moment a Template nothing refers to is safe to delete.
                    templates.sweep(id, loaded.data.scenario.templateIds)
                }

                is AppResult.Failure -> {
                    Log.w(TAG, "could not open scenario $id: ${loaded.error}")
                    stopSelf()
                }
            }
        }
    }

    /** GX-7, GX-8, GX-11: starting a run, ending one, and the recovery that is neither. */
    private val runCallbacks =
        object : RunCallbacks {
            override fun onStart() {
                val current = scenario ?: return
                // OV-20: belt as well as braces. OverlayUiState.showPanel already refuses to draw
                // the panel once a run exists; closing it here means the focusable window is gone
                // before the first Gesture rather than one state update later.
                coordinator?.update { copy(panel = null) }
                startRun(current)
            }

            /**
             * One Step, on its own, with the panel left where it is.
             *
             * The panel is not closed, only hidden — `OverlayUiState.showPanel` is false while
             * anything runs, so it goes away for the length of the try and comes back with the
             * draft still in it. Closing it would make Try cost the user their edits, which is
             * the opposite of what a Try is for.
             *
             * The countdown is dropped: the finger that pressed Try is already on the panel, not
             * over the target, and three seconds of waiting to see one tap is three seconds of
             * wondering whether the button worked. Nothing is written — [applyEdit] is not called.
             */
            override fun onTryStep(draft: StepDraft) {
                val current = scenario ?: return
                startRun(
                    current.copy(
                        steps = listOf(draft.toStep()),
                        runCount = RunCount.Times(1),
                        countdownMilliseconds = 0,
                    ),
                )
            }

            /**
             * OV-25: Stop is pressed when there is nothing to stop, and that is ordinary use.
             *
             * The notification offers Stop whenever a run is possible, and the tile and the
             * control are reachable long before one and long after. [OverlayUiState.stopping]
             * refuses the transition rather than the press: `Stopping` ends only when a runner
             * reports back, so entering it without one strands the Overlay there — no Markers, no
             * panel, and a Stop button wired to nothing.
             */
            override fun onStop() {
                runner?.requestStop()
                coordinator?.update { stopping() }
            }

            /**
             * GX-11, first half: a single one-millisecond tap, which is cheap and usually enough.
             *
             * The corner is the least likely place on a screen to carry a control, and the point
             * of the tap is not where it lands but that a complete down-and-up reaches the system.
             * Whether this clears a latched touch is **unverified on hardware** — see
             * android/docs/testing.md. The second half, cycling the service, is the user's to do
             * from the Settings screen onboarding sends them to.
             */
            override fun onFreeTheTouch() {
                val dispatcher = accessibilityDispatcher() ?: return
                scope.launch {
                    dispatcher.releaseEverything()
                    dispatcher.dispatch(freeTheTouchGesture(ScreenPoint(0, 0)))
                }
            }
        }

    /** PK-1: the gesture being aimed, or null when the screen is not armed. */
    private var picker: Picker? = null

    private val pickCallbacks =
        object : PickCallbacks {
            /**
             * PK-2: the aimed gesture becomes a Step, and the panel opens on it.
             *
             * `scaledTouchSlop` decides tap from swipe, the same platform number recording uses
             * (`RD-3`), so one finger that wandered a little means the same thing on both routes.
             */
            override fun onPickEvent(event: RecordingEvent) {
                val touch = picker?.accept(event) ?: return
                val current = scenario ?: return
                picker = null

                val slop = ViewConfiguration.get(overlayContext).scaledTouchSlop
                val profile = current.screenProfile ?: overlayContext.currentScreenProfile()
                val picked = touch.toPickedStep(slop)
                coordinator?.update { copy(picking = false) }
                applyEdit(current.withStepAdded(picked, profile))
                openPanel(picked.id)
            }

            override fun onCancelPick() {
                picker = null
                coordinator?.update { copy(picking = false) }
            }
        }

    /**
     * RD-1 to RD-8: a session of real touches, handed on to the application underneath as it goes.
     *
     * Recording is not a run and does not go through [ScenarioRunner]: nothing here is being
     * replayed from a **Scenario**, it is being written into one. What the two share is that both
     * take the editor off the screen, because a **Marker** left attached would swallow the very
     * touches being recorded.
     */
    private val recordCallbacks =
        object : RecordCallbacks {
            override fun onRecord(passThrough: Boolean) {
                if (scenario == null) return
                val dispatcher = accessibilityDispatcher() ?: return
                replaying = false
                coordinator?.update { copy(panel = null, recording = RecordingSession(passThrough = passThrough)) }
                recorder =
                    Recorder(dispatcher) { touch ->
                        coordinator?.update {
                            copy(recording = recording?.copy(touches = recording.touches + 1))
                        }
                        Log.d(TAG, "recorded a touch of ${touch.durationMilliseconds}ms")
                    }
            }

            /**
             * RD-3, RD-8: the session becomes Steps and is added to the Scenario.
             *
             * `scaledTouchSlop` decides which of them are taps. It is the platform's own idea of
             * how far a finger may wander while still meaning to stay still, and using anything
             * else would disagree with every other app on the phone.
             */
            override fun onStopRecording() {
                val current = scenario ?: return
                val captured = recorder?.touches.orEmpty().toList()
                recorder = null
                replaying = false
                coordinator?.update { copy(recording = null) }
                if (captured.isEmpty()) return

                val limits = ClickifyAccessibilityService.instance?.gestureLimits ?: GestureLimits()
                val slop = ViewConfiguration.get(overlayContext).scaledTouchSlop
                val profile = current.screenProfile ?: overlayContext.currentScreenProfile()
                applyEdit(current.withStepsAdded(captured.toSteps(limits, slop), profile))
            }

            /**
             * RD-5: the touch is recorded, then handed to whatever is underneath.
             *
             * A dispatched **Gesture** is delivered to the topmost window that accepts touches,
             * and while recording that window is the recording layer — so without care the layer
             * records its own re-emission, and one tap becomes two Steps. It did, on the first
             * run: one tap, `recorded a touch of 0ms` and `recorded a touch of 1ms`, 41ms apart.
             *
             * Two guards, because one was not enough. The layer's touchable flag goes away for the
             * length of the re-emission, which is what lets the touch reach the application at
             * all; and [replaying] refuses events outright, because `updateViewLayout` is not
             * applied the instant it is called and the gap is exactly long enough for a synthetic
             * tap to slip through it.
             *
             * The cost is written down rather than hidden: a real touch arriving inside that
             * window reaches the application and is **not recorded**. For a tap it is a few tens
             * of milliseconds.
             */
            override fun onRecordingEvent(event: RecordingEvent) {
                val active = recorder?.takeUnless { replaying } ?: return
                val gesture = active.accept(event) ?: return
                // RD-9: silent mode is this method minus everything below it. The touch has been
                // recorded and the application underneath is left exactly as it was, which is the
                // whole point — marking six places on one screen without setting any of them off.
                if (coordinator
                        ?.state
                        ?.value
                        ?.recording
                        ?.passThrough == false
                ) {
                    return
                }
                replaying = true
                scope.launch {
                    try {
                        coordinator?.update { copy(recording = recording?.copy(listening = false)) }
                        delay(FLAG_SETTLE_MILLISECONDS)
                        active.replay(gesture)
                        // A second wait, so a trailing synthetic event is still refused.
                        delay(FLAG_SETTLE_MILLISECONDS)
                    } finally {
                        replaying = false
                        coordinator?.update { copy(recording = recording?.copy(listening = true)) }
                    }
                }
            }
        }

    /**
     * TP-7: the Overlay steps aside, one frame is taken, and the frame comes back to be cropped.
     *
     * The wait in the middle is the same wait macOS needed (`RG-5`) for the same reason: taking a
     * window down is a request to the window manager rather than something that has happened by
     * the time the call returns. Capturing too early bakes Clickify's own panel into a
     * **Template** that can then never match anything again.
     */
    private val cropCallbacks =
        object : CropCallbacks {
            override fun onCropTemplate(purpose: CropPurpose) {
                if (scenario == null) return
                val service = accessibilityDispatcher() ?: return
                coordinator?.update { copy(crop = CropState(purpose)) }
                scope.launch {
                    delay(WINDOW_SETTLE_MILLISECONDS)
                    val captured = service.captureScreen()?.fittedTo(overlayContext.currentScreenProfile())
                    cropFrame = captured
                    if (captured == null) {
                        Log.w(TAG, "no frame came back, so there is nothing to crop")
                        coordinator?.update { copy(crop = null) }
                    } else {
                        coordinator?.update { copy(crop = crop?.copy(frame = captured.asImageBitmap())) }
                    }
                }
            }

            override fun onCropped(region: ScreenRegion) {
                val purpose =
                    coordinator
                        ?.state
                        ?.value
                        ?.crop
                        ?.purpose
                val cropped = cropFrame?.cropped(region)
                val current = scenario
                cropFrame = null
                coordinator?.update { copy(crop = null) }
                if (purpose == null || cropped == null || current == null) return
                scope.launch { keepTemplate(current, purpose, region, cropped) }
            }

            override fun onCancelCrop() {
                cropFrame = null
                coordinator?.update { copy(crop = null) }
            }
        }

    /**
     * TP-8, TP-9, TP-23: the crop becomes a file, a search, and — for a Target — a Marker on it.
     *
     * The **Step**'s point moves to the centre of what was cropped, which is `TP-23` from the
     * other end: the **Marker** ends up on the thing the user just drew a box around, which is
     * where the **Step** will act if the match comes back where it was made.
     */
    private suspend fun keepTemplate(
        current: Scenario,
        purpose: CropPurpose,
        region: ScreenRegion,
        cropped: Bitmap,
    ) {
        val templateId = UUID.randomUUID()
        if (!templates.write(current.id, templateId, cropped.toPng())) return
        val profile = current.screenProfile ?: overlayContext.currentScreenProfile()
        val search =
            TemplateSearch(
                templateId = templateId,
                region = region.paddedForSearch(profile, overlayContext.resources.displayMetrics.density),
            )
        val centre = ScreenPoint(region.left + region.width / 2, region.top + region.height / 2)
        val preview = cropped.asImageBitmap()
        coordinator?.update { withTemplate(purpose, search, centre, preview) }
    }

    /** FS-15: every one of these is written to disk as it happens. There is no Save. */
    private val editCallbacks =
        object : EditCallbacks {
            /**
             * PK-1: adding a Step is aiming at the screen, not dropping one in the middle of it.
             *
             * What this used to do was put a Step at the centre of the display and open the panel
             * on it, leaving the user to drag a Marker from the middle of somebody else's
             * application to wherever they actually meant. The centre of the screen is never the
             * answer, so that first drag was unavoidable — and while it was happening the panel
             * was open over the thing being aimed at.
             *
             * So the Overlay gets out of the way instead and waits for one gesture. The panel
             * opens afterwards, on a Step that already knows where it goes.
             */
            override fun onAddStep() {
                if (scenario == null) return
                picker = Picker()
                coordinator?.update { copy(panel = null, picking = true) }
            }

            /**
             * OV-21: while the panel is open, a drag edits the **draft**, like everything else in
             * it. Closed, it edits the Scenario and is saved at once, as dragging always has.
             *
             * One rule, and the reason for it is that the other way loses work: the draft is
             * written over the Step on Save, so a drag that went straight to disk would be
             * silently undone by a Save the user thought was unrelated.
             */
            override fun onMarkerMoved(
                marker: Marker,
                to: ScreenPoint,
            ) {
                val current = scenario ?: return
                val draft =
                    coordinator
                        ?.state
                        ?.value
                        ?.editing
                        ?.draft
                if (draft == null || draft.stepId != marker.stepId) {
                    applyEdit(current.withMarkerMoved(marker, to))
                    return
                }
                val movedStep =
                    current
                        .previewing(draft.toStep())
                        .withMarkerMoved(marker, to)
                        .steps
                        .firstOrNull { it.id == draft.stepId } ?: return
                coordinator?.update {
                    withStep { open -> open.copy(step = open.step.copy(draft = draft.withPointsFrom(movedStep))) }
                }
            }

            /** OV-7: a Marker is placed by dragging and configured by tapping. This is the tap. */
            override fun onMarkerTapped(marker: Marker) {
                openPanel(marker.stepId)
            }

            override fun onStepOpened(stepId: UUID) {
                openPanel(stepId)
            }

            /**
             * SM-18: the same Steps, measured against the screen in front of the user.
             *
             * The **Screen profile** is captured once and kept (`SM-14`) so that a mismatch is
             * reported rather than quietly overwritten — which leaves a user who built a
             * **Scenario** in portrait and now wants it in landscape with nothing to do but
             * delete every **Step**. This is the way out, and it is deliberately only reachable
             * from the panel that is already telling them the screens do not match.
             */
            override fun onRebuildForThisScreen() {
                val current = scenario ?: return
                applyEdit(current.rebuiltFor(overlayContext.currentScreenProfile()))
                coordinator?.update { copy(panel = PanelState.ScenarioEditor()) }
            }

            override fun onStepSaved(draft: StepDraft) {
                val current = scenario ?: return
                val profile = current.screenProfile ?: overlayContext.currentScreenProfile()
                applyEdit(current.withStepReplaced(draft.toStep(), profile))
                coordinator?.update { copy(panel = null) }
            }

            override fun onStepDeleted(stepId: UUID) {
                val current = scenario ?: return
                applyEdit(current.withStepRemoved(stepId))
                // The Scenario panel stays open: deleting from the list is one of several things
                // being done there. The Step panel cannot, because its subject is gone.
                coordinator?.update { copy(panel = if (editing?.draft?.stepId == stepId) null else panel) }
            }

            /**
             * OV-6: the order changes at once, and the open panel renumbers with it.
             *
             * The draft is carried over rather than rebuilt from disk. Moving a Step changes where
             * it sits, not what it does, and losing half-finished edits for pressing an arrow
             * would be a punishment for using the button.
             */
            override fun onStepMoved(
                stepId: UUID,
                by: Int,
            ) {
                val current = scenario ?: return
                val moved = current.withStepMoved(stepId, by)
                applyEdit(moved)
                val index = moved.steps.indexOfFirst { it.id == stepId }
                coordinator?.update {
                    withStep { open ->
                        open.copy(step = open.step.copy(stepNumber = index + 1, stepCount = moved.steps.size))
                    }
                }
            }

            /**
             * OV-24: how a Step that draws no Marker (`SM-8`) is reached from its neighbours.
             *
             * The panel refuses to walk away from unsaved edits, so nothing is discarded here —
             * [EditingStep.canGoBack] has already said no.
             */
            override fun onStepNavigated(
                stepId: UUID,
                by: Int,
            ) {
                val current = scenario ?: return
                val index = current.steps.indexOfFirst { it.id == stepId }
                current.steps.getOrNull(index + by)?.let { openPanel(it.id) }
            }

            /** OV-28: the Scenario's own fields — its name, how often it runs, its countdown. */
            override fun onScenarioChanged(scenario: Scenario) {
                applyEdit(scenario)
            }
        }

    /** OV-14, OV-30: the Overlay as a thing on the screen rather than as an editor. */
    private val shellCallbacks =
        object : ShellCallbacks {
            override fun onControlMoved(at: ScreenPoint) {
                scope.launch { settings.setControlPosition(ControlPosition(at.x, at.y)) }
            }

            /** OV-30: the Overlay is not the only place Clickify lives, and it says so. */
            override fun onOpenApp() {
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }

            override fun onCloseOverlay() {
                stopSelf()
            }
        }

    /**
     * The three handed to the Overlay as one.
     *
     * Delegation rather than one object with fifteen methods in it. They answer to three different
     * parts of the specification and change for three different reasons, and a single list of
     * fifteen was how the last one stopped being readable.
     */
    private val callbacks =
        object :
            OverlayCallbacks,
            RunCallbacks by runCallbacks,
            RecordCallbacks by recordCallbacks,
            PickCallbacks by pickCallbacks,
            CropCallbacks by cropCallbacks,
            EditCallbacks by editCallbacks,
            ShellCallbacks by shellCallbacks {}

    /**
     * GX-7: one run at a time, and everything a run needs built in one place.
     *
     * Start and Try differ in what they hand over and in nothing else. The job, rather than the
     * state, is what knows whether a run is in flight: the two are separate facts, and a second
     * runner would dispatch into the first one's strokes.
     */
    private fun startRun(toRun: Scenario) {
        if (runJob?.isActive == true) return
        val dispatcher = accessibilityDispatcher() ?: return
        // TP-2, TP-27: one screen source and one Template cache for the whole run, so a Template
        // is decoded once however many polls look for it.
        val finder =
            TemplateFinder(
                screen = AccessibilityScreens { overlayContext.currentScreenProfile() },
                templates = StoredTemplates(templates, toRun.id),
            )
        val activeRunner = ScenarioRunner(dispatcher, dispatcher.gestureLimits, finder)
        runner = activeRunner
        runJob =
            scope.launch {
                try {
                    activeRunner.run(toRun, overlayContext.currentScreenProfile()) { event ->
                        coordinator?.update { applied(event, toRun.steps.size) }
                    }
                } finally {
                    // OV-25: the run is over however it ended, and nothing else will say so. A
                    // cancelled coroutine sends no Finished event, and the Overlay would keep the
                    // state only a live runner can leave.
                    runner = null
                    coordinator?.update { settled() }
                }
            }
    }

    /** FS-15: there is no Save button for the Scenario itself — editing it *is* saving it. */
    private fun applyEdit(edited: Scenario) {
        scenario = edited
        coordinator?.show(edited)
        scope.launch { scenarios.save(edited) }
    }

    /**
     * Opens the panel on one Step (`OV-21`).
     *
     * [GestureLimits] come from the connected service rather than from the defaults, because
     * `SM-17` judges a Step against **this** device: a panel using the defaults would let a Step
     * through on a phone whose limits are lower, and that Step would silently do nothing.
     */
    private fun openPanel(stepId: UUID) {
        val current = scenario ?: return
        val index = current.steps.indexOfFirst { it.id == stepId }
        if (index < 0) return
        val limits = ClickifyAccessibilityService.instance?.gestureLimits ?: GestureLimits()
        coordinator?.update {
            copy(
                panel =
                    PanelState.StepEditor(
                        EditingStep(
                            draft = current.steps[index].toDraft(current.screenProfile),
                            original = current.steps[index],
                            stepNumber = index + 1,
                            stepCount = current.steps.size,
                            limits = limits,
                            profile = current.screenProfile,
                        ),
                    ),
            )
        }
        // TP-29: the panel opens at once and the pictures follow. Decoding them is a file read and
        // a decode, and a panel that waited for both would feel like a panel that had not opened.
        scope.launch {
            val previews = templates.previewsFor(current.id, current.steps[index])
            coordinator?.update {
                withStep { open ->
                    if (open.step.draft.stepId == stepId) open.copy(step = open.step.copy(previews = previews)) else open
                }
            }
        }
    }

    companion object {
        private const val TAG = "OverlayService"
        internal const val NOTIFICATION_ID = 1

        /** RD-5: long enough for `updateViewLayout` to have taken the touchable flag away. */
        private const val FLAG_SETTLE_MILLISECONDS = 24L

        /**
         * TP-7: long enough for the screen to be Clickify's no more.
         *
         * Two things have to have happened, and the longer of them sets this. The windows have to
         * be gone, which `removeViewImmediate` now makes true before this delay starts; and the
         * platform's own rate limit on `takeScreenshot` — about one call every 333 ms — has to
         * have elapsed, or the capture is refused and the crop comes back empty.
         */
        private const val WINDOW_SETTLE_MILLISECONDS = 400L

        /** OV-37: long enough for the display's own metrics to have caught up with the rotation. */
        private const val ROTATION_SETTLE_MILLISECONDS = 400L

        const val ACTION_OPEN = "com.pbh.clickify.OPEN"
        const val ACTION_STOP = "com.pbh.clickify.STOP"
        const val ACTION_FREE_TOUCH = "com.pbh.clickify.FREE_TOUCH"
        const val ACTION_CLOSE = "com.pbh.clickify.CLOSE"
        const val EXTRA_SCENARIO_ID = "scenarioId"

        fun open(
            context: Context,
            scenarioId: UUID,
        ) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java)
                    .setAction(ACTION_OPEN)
                    .putExtra(EXTRA_SCENARIO_ID, scenarioId.toString()),
            )
        }
    }
}

/** IL-3: this Context with the chosen language on it, read fresh so a change is picked up. */
private fun Context.withLanguage(): Context = localisedFor(AppLanguage.tag.value)

/** GX-1: the connected service, or a line in the log saying why nothing happened. */
private fun accessibilityDispatcher(): ClickifyAccessibilityService? =
    ClickifyAccessibilityService.instance
        ?: null.also { Log.w("OverlayService", "no accessibility service connected") }

private fun Intent.scenarioId(): UUID? =
    getStringExtra(OverlayService.EXTRA_SCENARIO_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** TP-29: every Template this Step refers to, decoded, so the panel can show what it looks for. */
private suspend fun TemplateFiles.previewsFor(
    scenarioId: UUID,
    step: Step,
): Map<UUID, ImageBitmap> =
    listOfNotNull(step.search?.templateId, step.guard?.search?.templateId)
        .distinct()
        .mapNotNull { id -> decodedPreview(scenarioId, id)?.let { id to it } }
        .toMap()

private fun ControlPosition.toPoint(): ScreenPoint = ScreenPoint(x, y)

/**
 * TP-19, TP-23, TP-24: the crop, applied to whichever half of the draft asked for it.
 *
 * A Target crop moves the Step's point onto what was cropped; a Guard crop does not, because a
 * Guard is a condition rather than a place — "press here once the advert is gone" means *here*,
 * not *on the advert*.
 */
private fun OverlayUiState.withTemplate(
    purpose: CropPurpose,
    search: TemplateSearch,
    centre: ScreenPoint,
    preview: ImageBitmap,
): OverlayUiState =
    withStep { open ->
        val draft =
            when (purpose) {
                CropPurpose.TARGET -> open.step.draft.copy(search = search, target = centre)
                CropPurpose.GUARD -> open.step.draft.copy(guard = Guard(search))
            }
        open.copy(
            step = open.step.copy(draft = draft, previews = open.step.previews + (search.templateId to preview)),
        )
    }

/** Turns a runner event into the shape the Overlay wears (OV-16, OV-17). */
internal fun OverlayUiState.applied(
    event: RunEvent,
    stepCount: Int,
): OverlayUiState =
    when (event) {
        is RunEvent.CountingDown ->
            copy(
                run = OverlayUiState.RunState.CountingDown(event.remainingMilliseconds),
                lastFinish = null,
                skippedSteps = 0,
            )

        is RunEvent.StepStarted ->
            copy(run = OverlayUiState.RunState.Running(event.stepIndex + 1, stepCount))

        // TP-21: counted rather than announced. A skipped Step is not an error and must not read
        // like one, but a run that quietly did nothing ten times over is the thing the user needs
        // to be able to see.
        is RunEvent.StepSkipped -> copy(skippedSteps = skippedSteps + 1)

        RunEvent.Stopping -> copy(run = OverlayUiState.RunState.Stopping)

        is RunEvent.Finished ->
            copy(
                run = OverlayUiState.RunState.Stopped,
                // OV-17: finishing normally is not news; every other reason is.
                lastFinish = event.reason.takeIf { it != FinishReason.Completed },
            )
    }

/**
 * OV-25: Stop asked for while a run is in flight. Asked for at any other moment, nothing moves.
 *
 * `Stopping` is the one state the Overlay cannot leave by itself — it ends when the runner reports
 * `Finished`, and a Stop pressed with no runner behind it would wait for a report that never
 * comes. The cost of getting this wrong is not cosmetic: `showMarkers` and `showPanel` are both
 * "nothing is running", so a stranded `Stopping` takes the whole editor with it.
 */
internal fun OverlayUiState.stopping(): OverlayUiState =
    if (run is OverlayUiState.RunState.Stopped) this else copy(run = OverlayUiState.RunState.Stopping)

/**
 * OV-25: the run is over, however it ended.
 *
 * Belt to [applied]'s braces. `RunEvent.Finished` covers every ending the runner knows about, and
 * a cancelled coroutine is the one it does not: the `finally` inside it still terminates the
 * strokes (`GX-9`), but nobody is left to say so to the interface.
 */
internal fun OverlayUiState.settled(): OverlayUiState =
    if (run is OverlayUiState.RunState.Stopped) this else copy(run = OverlayUiState.RunState.Stopped)
