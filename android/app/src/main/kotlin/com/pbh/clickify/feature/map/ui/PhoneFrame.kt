package com.pbh.clickify.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pbh.clickify.R
import com.pbh.clickify.domain.map.Beat
import com.pbh.clickify.domain.map.MapChip
import com.pbh.clickify.domain.map.ScenarioMap
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.overlay.ui.summary

/** The largest frame with [aspect], bezel included, that fits in [availableWidth] by [availableHeight] (`MP-3`). */
internal fun phoneFrameSize(
    availableWidth: Dp,
    availableHeight: Dp,
    aspect: Float,
): Pair<Dp, Dp> {
    val innerWidth = availableWidth - BEZEL_DP.dp * 2
    val innerHeight = availableHeight - BEZEL_DP.dp * 2
    val (width, height) =
        if (innerWidth / innerHeight > aspect) innerHeight * aspect to innerHeight else innerWidth to innerWidth / aspect
    return (width + BEZEL_DP.dp * 2) to (height + BEZEL_DP.dp * 2)
}

/**
 * The phone (`MP-3`): a bezel that never moves, and inside it the map, which zooms and pans.
 *
 * Tapping is hit-tested in screen space against where the layout put each dot and chip, so it is
 * right at any zoom (`MP-13`).
 */
@Composable
internal fun PhoneFrame(
    scenario: Scenario,
    map: ScenarioMap,
    frame: ScreenProfile,
    state: ScenarioMapUiState,
    actions: FrameActions,
    modifier: Modifier = Modifier,
) {
    val colours = MapColours(MaterialTheme.colorScheme)
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val summaries = scenario.steps.map { it.summary() }
    val text = rememberMapText(map, summaries, measurer)

    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val view = FrameView(frame, size, zoom, pan, density)
    val layout = remember(view, text, map) { layoutMap(view, map, text) }

    val currentView by rememberUpdatedState(view)
    val currentLayout by rememberUpdatedState(layout)
    val currentActions by rememberUpdatedState(actions)
    FollowCamera(state.followCamera && state.playing, focusOf(layout, state), { currentView }) { pan += it }

    val scene = sceneOf(map, layout, state, colours, view)
    val frameDescription = stringResource(R.string.map_frame_description)

    Box(modifier.clip(RoundedCornerShape(BEZEL_CORNER_DP.dp)).background(colours.bezel).padding(BEZEL_DP.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(SCREEN_CORNER_DP.dp))
                .background(colours.background)
                .onSizeChanged { size = it }
                .semantics { contentDescription = frameDescription },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pinchAndPan(LocalViewConfiguration.current.touchSlop, { currentView }) {
                        zoom = it.zoom
                        pan = it.pan
                        currentActions.onUserMovedCamera()
                    }.pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val hit = currentLayout.hit(tap, density * HIT_RADIUS_DP)
                            if (hit == null) currentActions.onEmptyTap() else currentActions.onTapStep(hit)
                        }
                    },
            ) {
                drawMap(scene)
            }
            if (scenario.steps.isEmpty()) {
                Text(stringResource(R.string.map_empty), Modifier.align(Alignment.Center).padding(16.dp), color = colours.muted)
            }
            FrameOverlays(scenario, state, layout, scene.play, actions.onEmptyTap)
            DotTargets(layout, density, actions.onTapStep)
        }
    }
}

/** What drawing needs to know of Playback, with the `wait 200 ms` label measured while a travel beat runs (`MP-8`). */
@Composable
private fun sceneOf(
    map: ScenarioMap,
    layout: MapLayout,
    state: ScenarioMapUiState,
    colours: MapColours,
    view: FrameView,
): MapScene {
    val measurer = rememberTextMeasurer()
    val beat = state.beats.getOrNull(state.position.beatIndex)
    val waitLabel =
        (beat as? Beat.TravelBeat)?.let {
            measurer.measure(stringResource(R.string.map_wait, it.delayMilliseconds), MaterialTheme.typography.labelSmall)
        }
    val play = ScenePlayback(beat, state.position, state.currentStep?.plus(1), waitLabel, state.beats)
    return MapScene(map, layout, state.thumbnails, colours, view, play)
}

/** `MP-9`, `MP-10`: the card when there is one, and the tooltip on a Step beat, inside the frame (`MP-6`). */
@Composable
private fun FrameOverlays(
    scenario: Scenario,
    state: ScenarioMapUiState,
    layout: MapLayout,
    play: ScenePlayback,
    onCloseCard: () -> Unit,
) {
    val currentNumber = play.currentNumber
    val beat = play.beat
    val card = state.cardFor
    if (card != null) {
        val anchor = layout.rectOf(card.first()) ?: return
        FloatingInFrame(anchor, CARD_GAP_DP) {
            MapCard(card.map { it to scenario.steps[it - 1] }, state.thumbnails, onCloseCard)
        }
    } else if (beat is Beat.StepBeat && currentNumber != null) {
        val anchor = layout.rectOf(currentNumber) ?: return
        FloatingInFrame(anchor, TOOLTIP_GAP_DP) {
            MapTooltip(currentNumber, scenario.steps[currentNumber - 1], state.thumbnails)
        }
    }
}

/** Where the camera looks, on screen: the current Step as drawn, or the point the arrow has reached (`MP-13`). */
private fun focusOf(
    layout: MapLayout,
    state: ScenarioMapUiState,
): Offset? =
    when (val beat = state.beats.getOrNull(state.position.beatIndex)) {
        is Beat.StepBeat -> layout.centreOf(beat.stepIndex + 1)
        is Beat.TravelBeat -> {
            val from = layout.centreOf(beat.fromIndex + 1)
            val to = layout.centreOf(beat.toIndex + 1)
            if (from == null || to == null) null else from + (to - from) * state.position.progress
        }
        null -> null
    }

/** `MP-13`: two fingers zoom and pan; one finger pans only once zoomed. */
private fun Modifier.pinchAndPan(
    slop: Float,
    view: () -> FrameView,
    onChange: (FrameView) -> Unit,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var pastSlop = false
            var travelled = Offset.Zero
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (fingers > 1 || view().zoom > 1f) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    travelled += panChange
                    pastSlop = pastSlop || fingers > 1 || travelled.getDistance() > slop
                    if (pastSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                        onChange(view().transformed(zoomChange, event.calculateCentroid(useCurrent = false), panChange))
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

private const val BEZEL_DP = 8f
private const val BEZEL_CORNER_DP = 32f
private const val SCREEN_CORNER_DP = 24f
private const val HIT_RADIUS_DP = 24f
private const val DOT_TARGET_DP = 48f
private const val FOLLOW_EASE = 0.15f
private const val TOOLTIP_GAP_DP = 16f
private const val CARD_GAP_DP = 16f

/**
 * `MP-13`: while playing and zoomed, the camera eases towards [focus], and stops the moment the user takes
 * over ([following] turns false). [onPan] is given how far to move.
 */
@Composable
private fun FollowCamera(
    following: Boolean,
    focus: Offset?,
    view: () -> FrameView,
    onPan: (Offset) -> Unit,
) {
    val currentFocus by rememberUpdatedState(focus)
    val currentView by rememberUpdatedState(view)
    LaunchedEffect(following) {
        while (following) {
            withFrameNanos { }
            val now = currentView()
            val target = currentFocus?.let { now.panToCentre(it) }
            if (target != null && now.zoom > 1f) onPan((target - now.pan) * FOLLOW_EASE)
        }
    }
}

/** Accessibility: one invisible target per dot, so a screen reader can reach what the canvas only paints. */
@Composable
private fun DotTargets(
    layout: MapLayout,
    density: Float,
    onTapStep: (List<Int>) -> Unit,
) {
    layout.chips.forEach { placed ->
        val description = stringResource(R.string.map_dot_description, placed.chip.stepNumber.toString())
        val width = placed.rect.width.toInt()
        val height = placed.rect.height.toInt()
        Box(
            Modifier
                .offset { IntOffset(placed.rect.left.toInt(), placed.rect.top.toInt()) }
                .size(with(LocalDensity.current) { width.toDp() }, with(LocalDensity.current) { height.toDp() })
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onTapStep(listOf(placed.chip.stepNumber))
                        true
                    }
                },
        )
    }
    layout.dots.forEach { placed ->
        val description = stringResource(R.string.map_dot_description, placed.dot.label)
        val half = density * DOT_TARGET_DP / 2
        Box(
            Modifier
                .offset { IntOffset((placed.centre.x - half).toInt(), (placed.centre.y - half).toInt()) }
                .size(DOT_TARGET_DP.dp)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onTapStep(placed.dot.numbers)
                        true
                    }
                },
        )
    }
}
