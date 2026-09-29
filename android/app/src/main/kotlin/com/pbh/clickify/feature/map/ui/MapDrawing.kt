package com.pbh.clickify.feature.map.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pbh.clickify.R
import com.pbh.clickify.domain.map.Beat
import com.pbh.clickify.domain.map.MapChip
import com.pbh.clickify.domain.map.PlaybackPosition
import com.pbh.clickify.domain.map.ScenarioMap
import com.pbh.clickify.domain.overlay.MarkerRole
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.ScreenPoint
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A dot on the map: every Step number it stands for (`MP-4`) and the pixel it is at. */
internal data class Dot(
    val point: ScreenPoint,
    val numbers: List<Int>,
) {
    val label: String get() = numbers.joinToString("·")
}

/** `MP-4`: the merged anchors, and the further contacts of a multiTouch, which have no anchor of their own. */
internal fun ScenarioMap.dots(): List<Dot> {
    val extras =
        markers
            .filter { it.role == MarkerRole.TOUCH_START }
            .filter { marker -> nodes.none { it.point == marker.point && marker.stepNumber in it.stepNumbers } }
            .map { Dot(it.point, listOf(it.stepNumber)) }
    return nodes.map { Dot(it.point, it.stepNumbers) } + extras
}

/** The screen is one dark scheme in both themes (`MP-2`), so these are read from it and not from the page. */
internal class MapColours(
    scheme: ColorScheme,
) {
    val background = scheme.surface
    val bezel = scheme.surfaceVariant
    val ink = scheme.onSurface
    val accent = scheme.primary
    val onAccent = scheme.onPrimary
    val muted = scheme.onSurfaceVariant
    val outline = scheme.outline
    val stop = scheme.error
    val guard = scheme.tertiary
}

/** The words on the map, measured once, because both drawing and hit-testing need their size. */
internal class MapText(
    val dots: Map<Dot, TextLayoutResult>,
    val chips: Map<MapChip, TextLayoutResult>,
    val badges: Map<Int, TextLayoutResult>,
)

@Composable
internal fun rememberMapText(
    map: ScenarioMap,
    summaries: List<String>,
    measurer: TextMeasurer,
): MapText {
    val style = MaterialTheme.typography.labelSmall
    val badges =
        map.guards.associate {
            it.stepNumber to
                stringResource(if (it.presence == Presence.PRESENT) R.string.map_badge_present else R.string.map_badge_absent)
        }
    return remember(map, summaries, badges, style) {
        MapText(
            dots = map.dots().associateWith { measurer.measure(it.label, style.copy(fontWeight = FontWeight.Bold)) },
            chips =
                map.chips.associateWith {
                    measurer.measure(
                        text = "${it.stepNumber} ${summaries[it.stepNumber - 1]}",
                        style = style,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = CHIP_TEXT_MAX_PX),
                    )
                },
            badges = badges.mapValues { measurer.measure("◇ ${it.value}", style) },
        )
    }
}

/** Where Playback is, as far as drawing is concerned (`MP-8`). */
internal class ScenePlayback(
    val beat: Beat?,
    val position: PlaybackPosition,
    /** The Step Playback is on, counting from 1, or null. */
    val currentNumber: Int?,
    /** `MP-8`: `wait 200 ms`, measured; only during a travel beat. */
    val waitLabel: TextLayoutResult?,
    val beats: List<Beat>,
)

internal class MapScene(
    val map: ScenarioMap,
    val layout: MapLayout,
    val thumbnails: Map<UUID, ImageBitmap?>,
    val colours: MapColours,
    val view: FrameView,
    val play: ScenePlayback,
) {
    val dp: Float get() = view.density
    val dotRadius: Float get() = dp * DOT_RADIUS_DP
}

/** Everything on the map, back to front: pictures, order, travelled path, gestures, give-up, marks, finger, label. */
internal fun DrawScope.drawMap(scene: MapScene) {
    drawTemplates(scene)
    drawOrder(scene)
    drawTravelled(scene)
    drawGestures(scene)
    scene.map.giveUps
        .filter { it.stepNumber == scene.play.currentNumber }
        .forEach { drawGiveUp(it, scene) }
    drawDots(scene)
    drawChips(scene)
    drawGuards(scene)
    drawFinger(scene)
    drawWaitLabel(scene)
}

/** `MP-5`: where it was cropped, scaled with the map; a picture that has gone is an empty box. */
private fun DrawScope.drawTemplates(scene: MapScene) {
    val view = scene.view
    scene.map.templates.forEach { template ->
        val centre = view.toScreen(template.center)
        val image = scene.thumbnails[template.templateId]
        if (image != null) {
            val width = view.scaled(image.width)
            val height = view.scaled(image.height)
            drawImage(
                image,
                dstOffset = IntOffset((centre.x - width / 2).toInt(), (centre.y - height / 2).toInt()),
                dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
            )
        } else {
            val side = view.scaled(MISSING_TEMPLATE_PIXELS).coerceAtLeast(scene.dp * MIN_BOX_DP)
            drawRect(scene.colours.muted, Offset(centre.x - side / 2, centre.y - side / 2), Size(side, side), style = Stroke(scene.dp))
        }
    }
}

/** `MP-4`: the order. Thin and muted, so the numbers stay what the eye reads first. */
private fun DrawScope.drawOrder(scene: MapScene) {
    scene.map.arrows.forEach { arrow ->
        arrow(
            scene.view.toScreen(arrow.from),
            scene.view.toScreen(arrow.to),
            scene.colours.muted,
            scene.dp * ARROW_STROKE_DP,
            scene.dotRadius + scene.dp * 2,
            scene.dp * ARROWHEAD_DP,
        )
    }
}

/** `MP-8`: the arrows already travelled, and the one being drawn now, growing towards the next Step. */
private fun DrawScope.drawTravelled(scene: MapScene) {
    val inset = scene.dotRadius + scene.dp * 2
    val head = scene.dp * ARROWHEAD_DP
    val current = scene.play.position
    scene.play.beats.forEachIndexed { index, beat ->
        if (beat !is Beat.TravelBeat || index > current.beatIndex) return@forEachIndexed
        val from = scene.view.toScreen(beat.from)
        val to = scene.view.toScreen(beat.to)
        if (index < current.beatIndex) {
            arrow(from, to, scene.colours.accent.copy(alpha = VISITED_ALPHA), scene.dp * ARROW_STROKE_DP * 2, inset, head)
        } else {
            arrow(from, from + (to - from) * current.progress, scene.colours.accent, scene.dp * EMPHASISED_STROKE_DP, inset, head)
        }
    }
}

/** `MP-4`: the shape of a swipe and of each multiTouch path, ending in an arrowhead. */
private fun DrawScope.drawGestures(scene: MapScene) {
    scene.map.markers
        .filter { it.role == MarkerRole.SWIPE_END || it.role == MarkerRole.TOUCH_END }
        .forEach { end ->
            val from = end.connectedTo ?: return@forEach
            val colour = if (end.stepNumber == scene.play.currentNumber) scene.colours.accent else scene.colours.ink
            arrow(
                scene.view.toScreen(from),
                scene.view.toScreen(end.point),
                colour,
                scene.dp * GESTURE_STROKE_DP,
                scene.dotRadius,
                scene.dp * ARROWHEAD_DP,
            )
        }
}

/** `MP-8`: a finger mark moves along each path of a swipe or multiTouch during that Step's beat. */
private fun DrawScope.drawFinger(scene: MapScene) {
    val beat = scene.play.beat as? Beat.StepBeat ?: return
    val moved = ((scene.play.position.progress - FINGER_START) / (FINGER_END - FINGER_START)).coerceIn(0f, 1f)
    val eased = moved * moved * (3 - 2 * moved)
    scene.map.markers
        .filter { (it.role == MarkerRole.SWIPE_END || it.role == MarkerRole.TOUCH_END) && it.stepNumber - 1 == beat.stepIndex }
        .forEach { end ->
            val from = scene.view.toScreen(end.connectedTo ?: return@forEach)
            val at = from + (scene.view.toScreen(end.point) - from) * eased
            drawCircle(scene.colours.ink.copy(alpha = FINGER_ALPHA), scene.dp * FINGER_RADIUS_DP, at)
            drawCircle(scene.colours.accent, scene.dp * FINGER_RADIUS_DP, at, style = Stroke(scene.dp * 2))
        }
}

/** `MP-8`: the real delay, at the midpoint of the arrow being drawn, fading as the beat ends. Never waited. */
private fun DrawScope.drawWaitLabel(scene: MapScene) {
    val beat = scene.play.beat as? Beat.TravelBeat ?: return
    val label = scene.play.waitLabel ?: return
    val progress = scene.play.position.progress
    val alpha = if (progress < FADE_FROM) 1f else ((1f - progress) / (1f - FADE_FROM)).coerceIn(0f, 1f)
    val middle = scene.view.toScreen(ScreenPoint((beat.from.x + beat.to.x) / 2, (beat.from.y + beat.to.y) / 2))
    val pad = scene.dp * CHIP_PADDING_DP
    val width = label.size.width + pad * 2
    val height = label.size.height + pad * 2
    // MP-6: inside the frame, like every other label.
    val left = (middle.x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    val top = (middle.y - height - scene.dp * LABEL_LIFT_DP).coerceIn(0f, (size.height - height).coerceAtLeast(0f))
    drawRoundRect(
        scene.colours.background.copy(alpha = alpha),
        Offset(left, top),
        Size(width, height),
        androidx.compose.ui.geometry
            .CornerRadius(pad * 2),
    )
    drawText(label, scene.colours.ink, Offset(left + pad, top + pad), alpha = alpha)
}

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 5f
internal const val DOT_RADIUS_DP = 12f
internal const val DOT_PADDING_DP = 6f
internal const val CHIP_PADDING_DP = 4f
internal const val CHIP_CLEARANCE_DP = 6f
internal const val LIT_GROWTH = 1.25f
private const val CHIP_TEXT_MAX_PX = 360
private const val ARROW_STROKE_DP = 1f
private const val EMPHASISED_STROKE_DP = 2.5f
private const val GESTURE_STROKE_DP = 2f
private const val ARROWHEAD_DP = 9f
private const val MISSING_TEMPLATE_PIXELS = 120
private const val MIN_BOX_DP = 20f
private const val VISITED_ALPHA = 0.5f
private const val FINGER_START = 0.1f
private const val FINGER_END = 0.8f
private const val FINGER_ALPHA = 0.35f
private const val FINGER_RADIUS_DP = 12f
private const val FADE_FROM = 0.7f
private const val LABEL_LIFT_DP = 8f
