package com.pbh.clickify.domain.scenario

import com.pbh.clickify.domain.scenario.ScenarioLimits.clampedTo

/**
 * Brings a Scenario read from disk inside the limits (SM-16).
 *
 * Clamping rather than rejecting is the whole point: a file with one impossible delay in it is a
 * file the user still wants, and refusing to open it loses everything else in it. The editor is
 * the strict end — see [violations].
 */
fun Scenario.clampedToLimits(): Scenario =
    copy(
        name = name.ifBlank { Scenario.REPAIRED_NAME },
        steps = steps.map { it.clampedToLimits() },
        runCount = runCount.clampedToLimits(),
        countdownMilliseconds = countdownMilliseconds.clampedTo(ScenarioLimits.countdownMilliseconds),
    )

fun Step.clampedToLimits(): Step =
    copy(
        action = action.clampedToLimits(),
        repeatCount = repeatCount.clampedTo(ScenarioLimits.stepRepeatCount),
        delayMillisecondsAfter = delayMillisecondsAfter.clampedTo(ScenarioLimits.delayMilliseconds),
        search = search?.clampedToLimits(),
        guard = guard?.let { it.copy(search = it.search.clampedToLimits()) },
    )

/** TP-30: the two numbers recognition added, clamped by the same rule as every other one. */
fun TemplateSearch.clampedToLimits(): TemplateSearch =
    copy(
        threshold = threshold.coerceIn(ScenarioLimits.matchThreshold),
        waitMilliseconds = waitMilliseconds.clampedTo(ScenarioLimits.waitMilliseconds),
    )

private fun RunCount.clampedToLimits(): RunCount =
    when (this) {
        is RunCount.Times -> RunCount.Times(count.clampedTo(ScenarioLimits.runCount))
        RunCount.UntilStopped -> this
    }

private fun StepAction.clampedToLimits(limits: GestureLimits = GestureLimits()): StepAction =
    when (this) {
        is StepAction.Tap ->
            copy(holdMilliseconds = holdMilliseconds.coerceIn(limits.holdMilliseconds))

        is StepAction.Swipe ->
            copy(durationMilliseconds = durationMilliseconds.coerceIn(limits.swipeDurationMilliseconds))

        is StepAction.MultiTouch ->
            copy(
                paths =
                    paths
                        .take(limits.maxStrokeCount)
                        .map { it.copy(durationMilliseconds = it.durationMilliseconds.coerceIn(limits.swipeDurationMilliseconds)) },
            )

        is StepAction.SetText -> copy(text = text.take(ScenarioLimits.setTextLength.last))
        is StepAction.Global -> this
    }
