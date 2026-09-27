package com.pbh.clickify.domain.scenario

import java.util.UUID

/**
 * A reason the editor refuses to save a Step (SM-17).
 *
 * The dispatcher never sees these: a gesture rejected by the platform mid-run is a Step that
 * silently did nothing, which is the failure this exists to prevent.
 */
sealed interface StepViolation {
    /** More contacts than `GestureDescription.getMaxStrokeCount()` allows. */
    data class TooManyPaths(
        val count: Int,
        val maximum: Int,
    ) : StepViolation

    /** Fewer than two contacts is a swipe, not a multi-touch. */
    data class TooFewPaths(
        val count: Int,
        val minimum: Int,
    ) : StepViolation

    /** Longer than `GestureDescription.getMaxGestureDuration()` allows. */
    data class DurationTooLong(
        val milliseconds: Long,
        val maximum: Long,
    ) : StepViolation

    /** A swipe of no duration is a tap, and the platform rejects it. */
    data object ZeroDuration : StepViolation

    /** A point outside the Scenario's Screen profile can never be reached. */
    data class PointOutsideScreen(
        val point: ScreenPoint,
    ) : StepViolation

    data class TextTooLong(
        val length: Int,
        val maximum: Int,
    ) : StepViolation

    /**
     * TP-29: this Step looks for a Template whose file is no longer on disk.
     *
     * Caught here rather than left to the run, where it would be a wait that expires — the right
     * behaviour at run time, and a baffling one to watch when the real answer is "the picture is
     * gone".
     */
    data class TemplateMissing(
        val templateId: UUID,
    ) : StepViolation
}

/**
 * Every reason this Step cannot be saved, against [limits] and the Scenario's [profile] (SM-17).
 *
 * Empty means it is fine. A list rather than the first failure, so the editor can show everything
 * wrong at once instead of one thing at a time.
 */
fun Step.violations(
    limits: GestureLimits = GestureLimits(),
    profile: ScreenProfile? = null,
    knownTemplates: Set<UUID>? = null,
): List<StepViolation> =
    buildList {
        when (val current = action) {
            is StepAction.Tap ->
                addDurationViolation(current.holdMilliseconds, limits, allowZero = true)

            is StepAction.Swipe ->
                addDurationViolation(current.durationMilliseconds, limits, allowZero = false)

            is StepAction.MultiTouch -> {
                val count = current.paths.size
                if (count < limits.strokeCount.first) {
                    add(StepViolation.TooFewPaths(count, limits.strokeCount.first))
                }
                if (count > limits.maxStrokeCount) {
                    add(StepViolation.TooManyPaths(count, limits.maxStrokeCount))
                }
                current.paths.forEach {
                    addDurationViolation(it.durationMilliseconds, limits, allowZero = false)
                }
            }

            is StepAction.SetText ->
                if (current.text.length > ScenarioLimits.setTextLength.last) {
                    add(StepViolation.TextTooLong(current.text.length, ScenarioLimits.setTextLength.last))
                }

            is StepAction.Global -> Unit
        }

        if (profile != null) {
            points.filterNot { it.isInside(profile) }.forEach { add(StepViolation.PointOutsideScreen(it)) }
        }

        // TP-29. Null means the caller has no library to check against — the runner, for one —
        // rather than a library with nothing in it, so nothing is judged.
        if (knownTemplates != null) {
            listOfNotNull(effectiveSearch?.templateId, guard?.search?.templateId)
                .filterNot { it in knownTemplates }
                .forEach { add(StepViolation.TemplateMissing(it)) }
        }
    }

private fun MutableList<StepViolation>.addDurationViolation(
    milliseconds: Long,
    limits: GestureLimits,
    allowZero: Boolean,
) {
    if (!allowZero && milliseconds <= 0L) {
        add(StepViolation.ZeroDuration)
    }
    if (milliseconds > limits.maxGestureDurationMilliseconds) {
        add(StepViolation.DurationTooLong(milliseconds, limits.maxGestureDurationMilliseconds))
    }
}

private fun ScreenPoint.isInside(profile: ScreenProfile): Boolean = x in 0 until profile.widthPixels && y in 0 until profile.heightPixels
