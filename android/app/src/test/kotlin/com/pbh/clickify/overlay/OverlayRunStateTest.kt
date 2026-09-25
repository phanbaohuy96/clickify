package com.pbh.clickify.overlay

import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.domain.run.RunEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * OV-25 and OV-16: what the Overlay does with a run's events, and with a Stop that has no run.
 *
 * The second half is here because it was found on a device rather than reasoned about: Stop is on
 * the notification at every moment, so it is pressed before anything starts, and the Overlay used
 * to enter a state only a runner could leave it.
 */
class OverlayRunStateTest {
    private val stopped = OverlayUiState()

    @Test
    fun `stop while nothing is running changes nothing`() {
        assertEquals(stopped, stopped.stopping())
    }

    @Test
    fun `stop during a countdown is honoured`() {
        val counting = stopped.copy(run = OverlayUiState.RunState.CountingDown(3_000))

        assertEquals(OverlayUiState.RunState.Stopping, counting.stopping().run)
    }

    @Test
    fun `stop during a step is honoured`() {
        val running = stopped.copy(run = OverlayUiState.RunState.Running(2, 5))

        assertEquals(OverlayUiState.RunState.Stopping, running.stopping().run)
    }

    @Test
    fun `a run that ends without reporting still leaves the stopping state`() {
        val stranded = stopped.copy(run = OverlayUiState.RunState.Stopping)

        assertEquals(OverlayUiState.RunState.Stopped, stranded.settled().run)
    }

    @Test
    fun `settling an already stopped overlay keeps the reason the last run gave`() {
        val finished = stopped.copy(lastFinish = FinishReason.Stopped)

        assertEquals(finished, finished.settled())
    }

    @Test
    fun `the editor is hidden while anything runs and returns when it stops`() {
        val running = stopped.copy(run = OverlayUiState.RunState.Running(1, 1))

        assertEquals(false, running.showMarkers)
        assertEquals(true, running.settled().showMarkers)
    }

    @Test
    fun `finishing normally is not reported, every other reason is`() {
        val completed = stopped.applied(RunEvent.Finished(FinishReason.Completed), stepCount = 1)
        val refused = stopped.applied(RunEvent.Finished(FinishReason.GlobalActionRefused(0)), stepCount = 1)

        assertNull(completed.lastFinish)
        assertEquals(FinishReason.GlobalActionRefused(0), refused.lastFinish)
    }

    @Test
    fun `a step event numbers the step from one`() {
        val state = stopped.applied(RunEvent.StepStarted(stepIndex = 0, iteration = 0), stepCount = 4)

        assertEquals(OverlayUiState.RunState.Running(stepNumber = 1, stepCount = 4), state.run)
    }
}
