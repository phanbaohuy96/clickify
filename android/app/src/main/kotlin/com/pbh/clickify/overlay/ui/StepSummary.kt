package com.pbh.clickify.overlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction

/**
 * One line saying what a Step does, for the Scenario panel's list (`OV-28`).
 *
 * Coordinates are in it because they are the only thing that tells two otherwise identical taps
 * apart, and because a list of fifteen rows all reading "Tap" would be worse than no list.
 *
 * How often and how long it waits are **not** in it — they are [timing], on the line below.
 */
@Composable
internal fun Step.summary(): String {
    val body =
        when (val current = action) {
            is StepAction.Tap -> stringResource(R.string.summary_tap, target.point.x, target.point.y)

            is StepAction.Swipe ->
                stringResource(
                    R.string.summary_swipe,
                    target.point.x,
                    target.point.y,
                    current.destination.x,
                    current.destination.y,
                )

            is StepAction.MultiTouch -> stringResource(R.string.summary_multi_touch, current.paths.size)
            is StepAction.Global -> current.action.label()
            is StepAction.SetText -> stringResource(R.string.summary_set_text, current.text)
        }
    return body
}

/**
 * The second line of a Step's row: what it costs in time (`OV-38`).
 *
 * Kept apart from [summary] rather than appended to it. A Scenario is mostly timing — the whole
 * difference between a working one and a broken one is often a wait — and on one line the numbers
 * ran into the coordinates until neither could be scanned.
 */
@Composable
internal fun Step.timing(): String {
    val clock =
        if (repeatCount > 1) {
            stringResource(R.string.step_row_timing_repeated, repeatCount, delayMillisecondsAfter)
        } else {
            stringResource(R.string.step_row_timing, delayMillisecondsAfter)
        }
    // TP-19, TP-24: a row that looks for something is doing something the coordinates do not
    // explain, and the list is where a user decides which Step to open.
    val looks =
        listOfNotNull(
            stringResource(R.string.recognition_row_finds).takeIf { effectiveSearch != null },
            stringResource(R.string.recognition_row_guarded).takeIf { guard != null },
        )
    return (looks + clock).joinToString(SEPARATOR)
}

private const val SEPARATOR = " · "
