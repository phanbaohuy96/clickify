package com.pbh.clickify.domain.scenario

/**
 * The valid range of every numeric field in a [Scenario] (SM-16).
 *
 * A value read from disk that falls outside its range is **clamped** into it rather than rejected:
 * one bad number must not cost the user a whole Scenario. The editor is stricter — it refuses to
 * create an out-of-range value in the first place.
 */
object ScenarioLimits {
    val runCount = 1..1_000_000
    val stepRepeatCount = 1..1_000_000
    val delayMilliseconds = 0..3_600_000

    /** Past a minute a countdown is a schedule, and scheduling is out of scope (SM-3). */
    val countdownMilliseconds = 0..60_000

    /** Beyond this no field is a text field, it is a paste target (SM-16). */
    val setTextLength = 0..5_000

    /** TP-30: below a half, a correlation score is noise with a number written on it. */
    val matchThreshold = 0.50..1.00

    /** TP-30: ten minutes. Past that a wait is a schedule, and scheduling is out of scope. */
    val waitMilliseconds = 0..600_000

    /**
     * TP-8: the smallest Template worth cropping, in raw pixels.
     *
     * Below this there is not enough left to correlate against, and the match becomes a coin toss
     * with a confident number written on it — which is worse than not matching.
     */
    const val MINIMUM_TEMPLATE_SIDE_PIXELS = 8

    /** Long enough for the finger that pressed Start to be out of the way (SM-3). */
    const val DEFAULT_COUNTDOWN_MILLISECONDS = 3_000

    const val DEFAULT_DELAY_MILLISECONDS = 100

    fun Int.clampedTo(range: IntRange): Int = coerceIn(range.first, range.last)
}

/**
 * The two bounds Android owns rather than Clickify (SM-17).
 *
 * They are read from `GestureDescription.getMaxStrokeCount()` and
 * `GestureDescription.getMaxGestureDuration()` and handed in here, so that a platform that changes
 * them changes behaviour without a code change — and so that `:domain` stays free of Android.
 * The defaults are what those calls return today, and exist for tests and for the editor before a
 * service is connected.
 */
data class GestureLimits(
    val maxStrokeCount: Int = DEFAULT_MAX_STROKE_COUNT,
    val maxGestureDurationMilliseconds: Long = DEFAULT_MAX_GESTURE_DURATION_MILLISECONDS,
) {
    val strokeCount: IntRange get() = MINIMUM_STROKE_COUNT..maxStrokeCount
    val holdMilliseconds: LongRange get() = 0L..maxGestureDurationMilliseconds
    val swipeDurationMilliseconds: LongRange get() = 1L..maxGestureDurationMilliseconds

    companion object {
        const val DEFAULT_MAX_STROKE_COUNT = 10
        const val DEFAULT_MAX_GESTURE_DURATION_MILLISECONDS = 60_000L

        /** One path is a swipe; multi-touch begins at two (SM-16). */
        const val MINIMUM_STROKE_COUNT = 2
    }
}
