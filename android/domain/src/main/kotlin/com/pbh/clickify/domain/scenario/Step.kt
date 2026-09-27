package com.pbh.clickify.domain.scenario

import java.util.UUID

/**
 * One Action at one Target, repeated [repeatCount] times, followed by [delayMillisecondsAfter]
 * (SM-5).
 *
 * Nothing is clamped here. SM-16 clamps on the way in from disk and the editor refuses out-of-range
 * values outright, which keeps this a plain value and keeps `copy()` honest.
 */
data class Step(
    val id: UUID = UUID.randomUUID(),
    val action: StepAction,
    val target: StepTarget,
    val repeatCount: Int = 1,
    val delayMillisecondsAfter: Int = ScenarioLimits.DEFAULT_DELAY_MILLISECONDS,
    /**
     * TP-19: a **Template** to find first, whose match moves this Step's points.
     *
     * Null is the ordinary case and means [target] is where the Step acts, full stop.
     */
    val search: TemplateSearch? = null,
    /** TP-24: a condition waited for before the Action, or null when there is none. */
    val guard: Guard? = null,
) {
    /** SM-8: a Step whose Action ignores its Target draws no Marker. */
    val hasMarker: Boolean get() = action.usesTarget

    /** TP-22: the two Actions that ignore their Target ignore a search attached to it as well. */
    val effectiveSearch: TemplateSearch? get() = search.takeIf { action.usesTarget }

    /** Every point this Step touches, so a Screen profile check has something to walk. */
    val points: List<ScreenPoint>
        get() =
            when (val current = action) {
                is StepAction.Tap -> listOf(target.point)
                is StepAction.Swipe -> listOf(target.point, current.destination)
                is StepAction.MultiTouch -> current.paths.flatMap { listOf(it.start, it.end) }
                is StepAction.Global, is StepAction.SetText -> emptyList()
            }

    /**
     * TP-20: this Step with every point moved so that [target] lands on [to].
     *
     * The whole Step moves rigidly rather than only its first point. Resolving the start and
     * leaving the rest absolute would turn "swipe this card away" into "swipe from wherever the
     * card is towards one fixed corner" — a different gesture each time the card moves. Moving
     * everything keeps the **shape** the user drew, which is what they drew it for.
     */
    fun movedTo(to: ScreenPoint): Step {
        val dx = to.x - target.point.x
        val dy = to.y - target.point.y
        if (dx == 0 && dy == 0) return this
        return copy(target = StepTarget(target.point.movedBy(dx, dy)), action = action.movedBy(dx, dy))
    }
}

private fun ScreenPoint.movedBy(
    dx: Int,
    dy: Int,
): ScreenPoint = ScreenPoint(x + dx, y + dy)

private fun StepAction.movedBy(
    dx: Int,
    dy: Int,
): StepAction =
    when (this) {
        is StepAction.Swipe -> copy(destination = destination.movedBy(dx, dy))
        is StepAction.MultiTouch ->
            copy(paths = paths.map { it.copy(start = it.start.movedBy(dx, dy), end = it.end.movedBy(dx, dy)) })

        is StepAction.Tap, is StepAction.Global, is StepAction.SetText -> this
    }
