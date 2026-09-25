package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.scenario.ScreenProfileDifference

/** What the interface is told while a Scenario runs. */
sealed interface RunEvent {
    /** GX-3: counted down visibly, and cancellable. */
    data class CountingDown(
        val remainingMilliseconds: Int,
    ) : RunEvent

    data class StepStarted(
        val stepIndex: Int,
        val iteration: Int,
    ) : RunEvent

    /**
     * TP-21: this Step's Guard or Template search ran out of time, and the user chose *skip*.
     *
     * Reported rather than passed over in silence. A Step that quietly does nothing is exactly the
     * failure this app spends most of its code avoiding, and here it is a Step doing what it was
     * told — which the user is entitled to see.
     */
    data class StepSkipped(
        val stepIndex: Int,
    ) : RunEvent

    /** GX-8: Stop was asked for and a stroke is still in flight. */
    data object Stopping : RunEvent

    data class Finished(
        val reason: FinishReason,
    ) : RunEvent
}

/**
 * Why a run ended.
 *
 * Every one of these leaves the screen with no stroke on it (GX-9); the difference is only what the
 * user is told.
 */
sealed interface FinishReason {
    /** Every Step ran the requested number of times. */
    data object Completed : FinishReason

    /** The user pressed Stop. */
    data object Stopped : FinishReason

    /** GX-2: the screen is not the one this Scenario was built on. */
    data class ScreenProfileMismatch(
        val differences: List<ScreenProfileDifference>,
    ) : FinishReason

    /** GX-6, GX-9: the system took a gesture away, or never reported back. */
    data class GestureCancelled(
        val stepIndex: Int,
    ) : FinishReason

    /** GX-17: nothing editable held input focus, so the text went nowhere. */
    data class NoFocusedField(
        val stepIndex: Int,
    ) : FinishReason

    /** GX-16: the system refused the global action. */
    data class GlobalActionRefused(
        val stepIndex: Int,
    ) : FinishReason

    /** TP-21: the Template this Step aims at never appeared, and the user chose *stop*. */
    data class TemplateNotFound(
        val stepIndex: Int,
    ) : FinishReason

    /** TP-24: this Step's condition never came true, and the user chose *stop*. */
    data class GuardUnmet(
        val stepIndex: Int,
    ) : FinishReason
}
