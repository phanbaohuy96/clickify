package com.pbh.clickify.domain.scenario

/**
 * The screen facts a coordinate depends on (SM-13).
 *
 * A Scenario stores raw pixels, so it is only meaningful against the screen those pixels were
 * measured on. Keeping the profile beside them is what makes a mismatch detectable instead of
 * silently wrong — see ADR-0013.
 */
data class ScreenProfile(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val rotation: ScreenRotation,
)

/**
 * SM-18: which way round the screen is, as the user holds it.
 *
 * Derived from the pixels rather than from [ScreenRotation], because that is what the coordinates
 * depend on. A device reporting `LANDSCAPE_LEFT` on a square screen changes nothing about where a
 * point is, and a tablet that reports `PORTRAIT` while wider than it is tall would lie here.
 */
val ScreenProfile.orientation: ScreenOrientation
    get() = if (widthPixels > heightPixels) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT

/** The two ways a screen can be held, which is as much as a **Scenario** needs to know (`SM-18`). */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE,
}

/** The screen's rotation, as the window manager reports it. */
enum class ScreenRotation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    PORTRAIT_UPSIDE_DOWN,
    LANDSCAPE_RIGHT,
}

/** One way in which the screen in front of the user differs from the one a Scenario was built on. */
enum class ScreenProfileDifference {
    WIDTH,
    HEIGHT,
    DENSITY,
    ROTATION,
}

/**
 * Everything that differs between [this] and [other], for the message SM-15 requires.
 *
 * Empty means the two profiles agree and the Scenario may run. The result is a list rather than a
 * boolean because "it does not match" is not a useful thing to tell someone whose phone has simply
 * been rotated.
 */
fun ScreenProfile.differencesFrom(other: ScreenProfile): List<ScreenProfileDifference> =
    buildList {
        if (widthPixels != other.widthPixels) add(ScreenProfileDifference.WIDTH)
        if (heightPixels != other.heightPixels) add(ScreenProfileDifference.HEIGHT)
        if (densityDpi != other.densityDpi) add(ScreenProfileDifference.DENSITY)
        if (rotation != other.rotation) add(ScreenProfileDifference.ROTATION)
    }

/** SM-15: any difference blocks the run. */
fun ScreenProfile.matches(other: ScreenProfile): Boolean = differencesFrom(other).isEmpty()
