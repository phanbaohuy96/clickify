package com.pbh.clickify.domain.scenario

/**
 * What a Step does, kept separate from where it does it (ADR-0002).
 *
 * Exactly five, and adding a sixth is a specification change (SM-7).
 */
sealed interface StepAction {
    /** One contact, held for [holdMilliseconds], at the Step's Target. */
    data class Tap(
        val holdMilliseconds: Long = 0L,
    ) : StepAction

    /** One contact travelling from the Step's Target to [destination] over [durationMilliseconds]. */
    data class Swipe(
        val destination: ScreenPoint,
        val durationMilliseconds: Long,
    ) : StepAction

    /**
     * Two or more contacts performed together, capped by the platform's stroke count (SM-16).
     *
     * The Step's Target is the first path's start, so that a multi-touch Step still has one Marker
     * to drag like any other.
     */
    data class MultiTouch(
        val paths: List<GesturePath>,
    ) : StepAction

    /** A fixed system operation. Carries no coordinates (SM-8). */
    data class Global(
        val action: GlobalActionKind,
    ) : StepAction

    /** Writes [text] into whichever field holds input focus. Not typing (SM-9). */
    data class SetText(
        val text: String,
    ) : StepAction

    /** SM-8: these two ignore their Step's Target, so their Step draws no Marker. */
    val usesTarget: Boolean
        get() =
            when (this) {
                is Tap, is Swipe, is MultiTouch -> true
                is Global, is SetText -> false
            }
}

/** One contact's journey: where it lands, where it leaves, and how long it takes. */
data class GesturePath(
    val start: ScreenPoint,
    val end: ScreenPoint,
    val durationMilliseconds: Long,
)
