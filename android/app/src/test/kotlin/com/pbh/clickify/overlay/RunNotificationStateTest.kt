package com.pbh.clickify.overlay

import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OV-29: what the run notification is told, and — as importantly — how rarely it is told anything.
 *
 * The countdown case is here because it produced a sentence on a device: the notification read
 * "Step 0 of 1" for three seconds, because "a run exists" and "a Step is running" had been treated
 * as the same fact.
 */
class RunNotificationStateTest {
    private val scenario =
        Scenario(
            name = "Daily",
            steps =
                List(3) {
                    Step(
                        action = StepAction.Tap(),
                        target =
                            StepTarget(
                                com.pbh.clickify.domain.scenario
                                    .ScreenPoint(1, 1),
                            ),
                    )
                },
        )
    private val open = OverlayUiState(scenario = scenario)

    @Test
    fun `nothing running says so, by name`() {
        val state = open.notification()

        assertEquals(RunNotificationState.Phase.IDLE, state.phase)
        assertEquals("Daily", state.scenarioName)
        assertEquals(3, state.stepCount)
    }

    @Test
    fun `a countdown is its own phase, because it has no step number to show`() {
        val state = open.copy(run = OverlayUiState.RunState.CountingDown(3_000)).notification()

        assertEquals(RunNotificationState.Phase.COUNTING_DOWN, state.phase)
        assertEquals(0, state.stepNumber)
    }

    @Test
    fun `a running step is reported with its number`() {
        val state = open.copy(run = OverlayUiState.RunState.Running(2, 3)).notification()

        assertEquals(RunNotificationState.Phase.RUNNING, state.phase)
        assertEquals(2, state.stepNumber)
    }

    @Test
    fun `finishing the last stroke still counts as running, so Stop stays offered`() {
        val state = open.copy(run = OverlayUiState.RunState.Stopping).notification()

        assertEquals(RunNotificationState.Phase.RUNNING, state.phase)
    }

    /**
     * OV-29: the notification is re-posted only when what it would say has changed.
     *
     * A countdown ticks every hundred milliseconds. Without this, the shade would be rewritten
     * thirty times on the way to the first Step, which flickers and loses its place.
     */
    @Test
    fun `two moments of the same countdown are the same notification`() {
        val early = open.copy(run = OverlayUiState.RunState.CountingDown(3_000)).notification()
        val late = open.copy(run = OverlayUiState.RunState.CountingDown(900)).notification()

        assertEquals(early, late)
    }

    @Test
    fun `moving between steps is not the same notification`() {
        val first = open.copy(run = OverlayUiState.RunState.Running(1, 3)).notification()
        val second = open.copy(run = OverlayUiState.RunState.Running(2, 3)).notification()

        assert(first != second)
    }
}
