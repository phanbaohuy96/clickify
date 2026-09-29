package com.pbh.clickify.feature.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import com.pbh.clickify.domain.map.MapChip
import com.pbh.clickify.domain.map.ScenarioMap
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile

/** The map's pixels-to-screen transform at one moment: fit to width, then zoom, then pan (`MP-13`). */
internal data class FrameView(
    val frame: ScreenProfile,
    val size: IntSize,
    val zoom: Float,
    val pan: Offset,
    val density: Float,
) {
    private val fit: Float get() = if (frame.widthPixels == 0) 1f else size.width / frame.widthPixels.toFloat()

    /** Raw pixels to a place on the canvas. */
    fun toScreen(point: ScreenPoint): Offset = Offset(point.x * fit * zoom + pan.x, point.y * fit * zoom + pan.y)

    /** The length of [pixels] raw pixels on the canvas, at this zoom. */
    fun scaled(pixels: Int): Float = pixels * fit * zoom

    /** Zooms by [zoomChange] about [centroid], then pans, never leaving the frame's edge. */
    fun transformed(
        zoomChange: Float,
        centroid: Offset,
        panChange: Offset,
    ): FrameView {
        val next = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        return copy(zoom = next, pan = clamped(centroid - (centroid - pan) * (next / zoom) + panChange, next))
    }

    /** The pan that puts [point] in the middle of the frame, as far as the edges allow. */
    fun centredOn(point: ScreenPoint): Offset =
        clamped(Offset(size.width / 2f - point.x * fit * zoom, size.height / 2f - point.y * fit * zoom), zoom)

    private fun clamped(
        pan: Offset,
        zoom: Float,
    ) = Offset(pan.x.coerceIn(size.width * (1f - zoom), 0f), pan.y.coerceIn(size.height * (1f - zoom), 0f))
}

/** A dot where the layout put it, on screen. */
internal class PlacedDot(
    val dot: Dot,
    val centre: Offset,
    val rect: Rect,
)

/** A chip where the layout put it, on screen, after being kept off the dots and inside the frame (`MP-6`). */
internal class PlacedChip(
    val chip: MapChip,
    val rect: Rect,
)

/** Where everything on the map is on screen, for both drawing and tapping (`MP-6`). */
internal class MapLayout(
    val dots: List<PlacedDot>,
    val chips: List<PlacedChip>,
    val badges: Map<Int, Offset>,
    val text: MapText,
) {
    /** The box the Step numbered [stepNumber] occupies, whether it is a dot or a chip. */
    fun rectOf(stepNumber: Int): Rect? =
        dots.firstOrNull { stepNumber in it.dot.numbers }?.rect ?: chips.firstOrNull { it.chip.stepNumber == stepNumber }?.rect

    /** The Step numbers under [tap], or null for empty frame. A dot wins over a chip beneath it. */
    fun hit(
        tap: Offset,
        reach: Float,
    ): List<Int>? {
        val dot = dots.minByOrNull { (it.centre - tap).getDistance() }
        if (dot != null && (dot.centre - tap).getDistance() <= reach) return dot.dot.numbers
        return chips.firstOrNull { it.rect.contains(tap) }?.let { listOf(it.chip.stepNumber) }
    }
}

/** `MP-6`: dots at their pixels, chips pushed off any dot and into the frame, badges beside what they belong to. */
internal fun layoutMap(
    view: FrameView,
    map: ScenarioMap,
    text: MapText,
): MapLayout {
    val dp = view.density
    val bounds = Size(view.size.width.toFloat(), view.size.height.toFloat())
    val dots =
        text.dots.map { (dot, layout) ->
            val centre = view.toScreen(dot.point)
            val half = maxOf(dp * DOT_RADIUS_DP, layout.size.width / 2f + dp * DOT_PADDING_DP)
            val radius = dp * DOT_RADIUS_DP
            PlacedDot(dot, centre, Rect(centre.x - half, centre.y - radius, centre.x + half, centre.y + radius))
        }
    val pad = dp * CHIP_PADDING_DP
    val chips =
        map.chips.map { chip ->
            val layout = text.chips.getValue(chip)
            val centre = view.toScreen(chip.position)
            val rect =
                Rect(
                    Offset(centre.x - layout.size.width / 2f - pad, centre.y - layout.size.height / 2f - pad),
                    Size(layout.size.width + pad * 2, layout.size.height + pad * 2),
                )
            PlacedChip(chip, rect.awayFrom(dots.map { it.rect }, bounds, dp * CHIP_CLEARANCE_DP))
        }
    val badges =
        text.badges
            .mapNotNull { (stepNumber, layout) ->
                val anchor =
                    dots.firstOrNull { stepNumber in it.dot.numbers }?.rect
                        ?: chips.firstOrNull { it.chip.stepNumber == stepNumber }?.rect
                anchor?.let { stepNumber to placeBeside(it, dp * 2, layout.size.toSize(), bounds, preferAbove = true) }
            }.toMap()
    return MapLayout(dots, chips, badges, text)
}

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())

/**
 * `MP-6`: this box, inside [bounds]; when that puts it on a dot, below the dot or above it by [clearance],
 * a fixed distance on screen rather than one in raw pixels.
 */
private fun Rect.awayFrom(
    dots: List<Rect>,
    bounds: Size,
    clearance: Float,
): Rect {
    val start = insideOf(bounds)
    val blocking = dots.firstOrNull { it.overlaps(start) } ?: return start
    val below = translate(0f, blocking.bottom + clearance - top).insideOf(bounds)
    val above = translate(0f, blocking.top - clearance - bottom).insideOf(bounds)
    return listOf(below, above).firstOrNull { candidate -> dots.none { it.overlaps(candidate) } } ?: start
}

private fun Rect.insideOf(bounds: Size): Rect {
    val dx =
        when {
            left < 0f -> -left
            right > bounds.width -> bounds.width - right
            else -> 0f
        }
    val dy =
        when {
            top < 0f -> -top
            bottom > bounds.height -> bounds.height - bottom
            else -> 0f
        }
    return translate(dx, dy)
}
