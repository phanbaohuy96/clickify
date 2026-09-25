package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile

// The points and durations the editor invents when a Step changes shape.
//
// Every one of them has the same job: give the user something visible and draggable the moment they
// choose a different Action, instead of a swipe that starts and ends in the same place or a
// duration of zero that the platform would reject. They are starting positions, not suggestions —
// the user drags them where they belong (OV-7).

/** Long enough to read as a movement rather than a flick, short enough not to feel slow. */
const val DEFAULT_TRAVEL_MILLISECONDS = 200L

/** How far an invented point sits from the one it came from: a quarter of the screen. */
private const val SCREEN_FRACTION = 4

/** With no Screen profile to measure against, a distance that is visible on any phone. */
private const val FALLBACK_TRAVEL_PIXELS = 300

/**
 * Where a swipe chosen in the panel ends up before the user drags it: **above** its start.
 *
 * Above, because the Step panel occupies the bottom of the screen for exactly as long as this
 * value is being invented. A destination placed below the start is placed underneath the panel
 * that asked for it, and the user is told to drag a Marker they cannot see.
 */
internal fun defaultDestination(
    from: ScreenPoint,
    profile: ScreenProfile?,
): ScreenPoint = from.movedWithinScreen(-travel(profile?.heightPixels), profile, vertical = true)

/**
 * The two contacts a multi-touch Step starts life with (`OV-9`).
 *
 * Both stay still: a two-finger hold is the multi-touch that needs the least explaining, and the
 * user who wanted a pinch drags the ends apart from there.
 */
internal fun defaultPaths(
    from: ScreenPoint,
    profile: ScreenProfile?,
): List<GesturePath> =
    listOf(
        GesturePath(from, from, DEFAULT_TRAVEL_MILLISECONDS),
        GesturePath(nextContact(from, profile), nextContact(from, profile), DEFAULT_TRAVEL_MILLISECONDS),
    )

/** Beside [from] rather than below it, so a new contact does not land on the line of a swipe. */
internal fun nextContact(
    from: ScreenPoint,
    profile: ScreenProfile?,
): ScreenPoint = from.movedWithinScreen(travel(profile?.widthPixels), profile, vertical = false)

private fun travel(extent: Int?): Int = (extent ?: (FALLBACK_TRAVEL_PIXELS * SCREEN_FRACTION)) / SCREEN_FRACTION

/**
 * [this] moved [distance] along one axis, **turned around** when the screen runs out.
 *
 * Turning around rather than clamping is the whole reason this is not one line. A point invented
 * near an edge would clamp straight back onto the point it came from, and a swipe that starts and
 * ends in the same place is not what anybody meant by choosing "swipe".
 */
private fun ScreenPoint.movedWithinScreen(
    distance: Int,
    profile: ScreenProfile?,
    vertical: Boolean,
): ScreenPoint {
    val extent = if (vertical) profile?.heightPixels else profile?.widthPixels
    val from = if (vertical) y else x
    val forward = from + distance
    val fits = forward >= 0 && (extent == null || forward <= extent - 1)
    val moved = if (fits) forward else from - distance
    val inside = if (extent == null) moved.coerceAtLeast(0) else moved.coerceIn(0, extent - 1)
    return if (vertical) copy(y = inside) else copy(x = inside)
}
