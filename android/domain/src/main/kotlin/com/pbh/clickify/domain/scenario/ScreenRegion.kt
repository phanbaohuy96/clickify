package com.pbh.clickify.domain.scenario

import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle of the display in raw device pixels, used as a **Search region** (`TP-9`).
 *
 * Half-open: [left] and [top] are inside, [right] and [bottom] are the first column and row that
 * are not. Same convention as every other rectangle on the platform, and the reason [width] and
 * [height] are subtractions rather than corrections.
 */
data class ScreenRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** This region with nothing outside [profile] left in it. */
    fun clampedInto(profile: ScreenProfile): ScreenRegion =
        ScreenRegion(
            left = left.coerceIn(0, profile.widthPixels),
            top = top.coerceIn(0, profile.heightPixels),
            right = right.coerceIn(0, profile.widthPixels),
            bottom = bottom.coerceIn(0, profile.heightPixels),
        )

    companion object {
        /** The rectangle two dragged corners describe, whichever way round they were dragged. */
        fun between(
            one: ScreenPoint,
            other: ScreenPoint,
        ): ScreenRegion =
            ScreenRegion(
                left = min(one.x, other.x),
                top = min(one.y, other.y),
                right = max(one.x, other.x),
                bottom = max(one.y, other.y),
            )
    }
}

/**
 * The **Search region** suggested after a crop (`TP-9`).
 *
 * The padding is half the **Template**'s longer side, floored and capped, because what a game asks
 * you to press almost always stays near where it was cropped — and a whole-screen scan is both
 * slower and likelier to find an identical patch somewhere else.
 */
fun ScreenRegion.paddedForSearch(
    profile: ScreenProfile,
    density: Float,
): ScreenRegion {
    val padding =
        (max(width, height) / 2)
            .coerceIn((MINIMUM_PADDING_DP * density).toInt(), (MAXIMUM_PADDING_DP * density).toInt())
    return ScreenRegion(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding,
    ).clampedInto(profile)
}

private const val MINIMUM_PADDING_DP = 48
private const val MAXIMUM_PADDING_DP = 160
