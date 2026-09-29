package com.pbh.clickify.feature.map.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import com.pbh.clickify.domain.map.Beat
import com.pbh.clickify.domain.map.MapGiveUp
import com.pbh.clickify.domain.scenario.OnTimeout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** `MP-4`, `MP-8`: a dot is a pill when it carries several numbers, and the current Step's swells and is ringed. */
internal fun DrawScope.drawDots(scene: MapScene) {
    val colours = scene.colours
    scene.layout.dots.forEach { placed ->
        val layout =
            scene.layout.text.dots
                .getValue(placed.dot)
        val lit = scene.play.currentNumber in placed.dot.numbers
        val grow = if (lit) LIT_GROWTH * scene.play.pulse() else 1f
        val radius = scene.dotRadius * grow
        val halfWidth = maxOf(radius, placed.rect.width / 2f * grow)
        val at = placed.centre
        if (lit) drawCircle(colours.accent.copy(alpha = HALO_ALPHA), radius * HALO_SCALE, at)
        val topLeft = Offset(at.x - halfWidth, at.y - radius)
        val size = Size(halfWidth * 2, radius * 2)
        drawRoundRect(colours.accent, topLeft, size, CornerRadius(radius))
        if (lit) drawRoundRect(colours.ink, topLeft, size, CornerRadius(radius), Stroke(scene.dp * 2))
        drawText(layout, colours.onAccent, Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }
}

/** `MP-4`: a Step without a point, sitting on the path, kept off any dot and inside the frame (`MP-6`). */
internal fun DrawScope.drawChips(scene: MapScene) {
    val colours = scene.colours
    val pad = scene.dp * CHIP_PADDING_DP
    scene.layout.chips.forEach { placed ->
        val layout =
            scene.layout.text.chips
                .getValue(placed.chip)
        val rect = placed.rect
        val lit = placed.chip.stepNumber == scene.play.currentNumber
        drawRoundRect(colours.background, rect.topLeft, rect.size, CornerRadius(pad * 2))
        if (lit) drawRoundRect(colours.accent.copy(alpha = HALO_ALPHA), rect.topLeft, rect.size, CornerRadius(pad * 2))
        drawRoundRect(
            if (lit) colours.accent else colours.outline,
            rect.topLeft,
            rect.size,
            CornerRadius(pad * 2),
            Stroke(scene.dp * if (lit) 2 else 1),
        )
        drawText(layout, colours.ink, Offset(rect.left + pad, rect.top + pad))
    }
}

/** `MP-6`: a diamond and one word, beside the dot or chip it belongs to, where the layout put it. */
internal fun DrawScope.drawGuards(scene: MapScene) {
    scene.layout.badges.forEach { (stepNumber, at) ->
        val layout = scene.layout.text.badges[stepNumber] ?: return@forEach
        drawText(layout, scene.colours.guard, at)
    }
}

/** `MP-7`: a Step that gives up is drawn going around itself, or ending, and never anywhere else. */
internal fun DrawScope.drawGiveUp(
    giveUp: MapGiveUp,
    scene: MapScene,
) {
    val at = giveUp.at?.let { scene.view.toScreen(it) } ?: return
    when (giveUp.outcome) {
        OnTimeout.SKIP_STEP -> drawSkip(giveUp, at, scene)
        OnTimeout.STOP_SCENARIO -> drawStop(giveUp, at, scene)
    }
}

/** A dashed companion from the arrow into the Step to the end of the arrow out of it, around the dot. */
private fun DrawScope.drawSkip(
    giveUp: MapGiveUp,
    at: Offset,
    scene: MapScene,
) {
    val before = giveUp.before?.let { scene.view.toScreen(it) }
    val after = giveUp.after?.let { scene.view.toScreen(it) }
    if (before == null || after == null) return
    val along = after - before
    val length = along.getDistance()
    if (length <= 0f) return
    val normal = Offset(-along.y / length, along.x / length)
    // A quadratic passes through half of its control point's offset, so double it to clear the dot.
    val through = at + normal * (scene.dp * SKIP_BULGE_DP)
    val control = through * 2f - (before + after) * 0.5f
    val path =
        Path().apply {
            moveTo(before.x, before.y)
            quadraticTo(control.x, control.y, after.x, after.y)
        }
    val dashes = PathEffect.dashPathEffect(floatArrayOf(scene.dp * 6, scene.dp * 5))
    drawPath(path, scene.colours.guard, style = Stroke(scene.dp * 2, pathEffect = dashes))
}

/** A short stub, away from the Step towards where the path would have gone, ending in a stop mark. */
private fun DrawScope.drawStop(
    giveUp: MapGiveUp,
    at: Offset,
    scene: MapScene,
) {
    val towards = giveUp.after?.let { scene.view.toScreen(it) - at }
    val direction = if (towards != null && towards.getDistance() > 0f) towards / towards.getDistance() else Offset(0f, 1f)
    val dp = scene.dp
    val end = at + direction * (dp * (DOT_RADIUS_DP + STUB_DP))
    drawLine(scene.colours.stop, at + direction * scene.dotRadius, end, dp * 2)
    drawRect(
        scene.colours.stop,
        Offset(end.x - dp * STOP_MARK_DP / 2, end.y - dp * STOP_MARK_DP / 2),
        Size(dp * STOP_MARK_DP, dp * STOP_MARK_DP),
    )
}

/** A line from [from] to [to] with an open arrowhead at [to], both ends held [inset] off the points. */
internal fun DrawScope.arrow(
    from: Offset,
    to: Offset,
    colour: Color,
    width: Float,
    inset: Float,
    head: Float,
) {
    val along = to - from
    val length = along.getDistance()
    if (length <= inset * 2) return
    val unit = along / length
    val end = to - unit * inset
    drawLine(colour, from + unit * inset, end, width)
    val left = Offset(-unit.x * COS_HEAD - unit.y * SIN_HEAD, -unit.y * COS_HEAD + unit.x * SIN_HEAD)
    val right = Offset(-unit.x * COS_HEAD + unit.y * SIN_HEAD, -unit.y * COS_HEAD - unit.x * SIN_HEAD)
    drawLine(colour, end, end + left * head, width)
    drawLine(colour, end, end + right * head, width)
}

/** `MP-8`: the lit dot swells once per pulse of its Step, `repeat` capped at three. */
internal fun ScenePlayback.pulse(): Float {
    val beat = beat as? Beat.StepBeat ?: return 1f
    return 1f + PULSE_GROWTH * abs(sin(PI * beat.pulses * position.progress)).toFloat()
}

private const val HALO_ALPHA = 0.25f
private const val HALO_SCALE = 1.8f
private const val SKIP_BULGE_DP = 44f
private const val STUB_DP = 28f
private const val STOP_MARK_DP = 10f
private const val PULSE_GROWTH = 0.3f
private val COS_HEAD = cos(Math.toRadians(25.0)).toFloat()
private val SIN_HEAD = sin(Math.toRadians(25.0)).toFloat()
