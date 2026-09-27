package com.pbh.clickify.overlay

import com.pbh.clickify.domain.editor.StepDraft
import com.pbh.clickify.domain.editor.toDraft
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * OV-36: the two ways out of the Step panel, and the one question between them.
 *
 * Worth a test rather than a comment because the failure is silent in both directions. Asking when
 * there is nothing to lose trains the user to dismiss the question without reading it; not asking
 * when there is loses their work with no way back — and the difference between the two is one
 * comparison that nothing on screen would show was wrong.
 */
class PanelExitTest {
    private val saved =
        Step(
            action = StepAction.Tap(holdMilliseconds = 100),
            target = StepTarget(ScreenPoint(400, 900)),
        )

    private fun editing(draft: StepDraft = saved.toDraft(null)) =
        EditingStep(draft = draft, original = saved, stepNumber = 2, stepCount = 5)

    private fun open(step: EditingStep = editing()) = OverlayUiState(panel = PanelState.StepEditor(step))

    private fun dirty() = editing(saved.toDraft(null).copy(delayMillisecondsAfter = 4_000))

    @Test
    fun `an untouched step goes back to the scenario without being asked about`() {
        val left = open().leaving(PanelExit.TO_SCENARIO)

        assertIs<PanelState.ScenarioEditor>(left.panel)
    }

    @Test
    fun `an untouched step closes without being asked about`() {
        assertNull(open().leaving(PanelExit.CLOSED).panel)
    }

    @Test
    fun `an edited step asks first, and stays exactly where it was`() {
        val asked = open(dirty()).leaving(PanelExit.TO_SCENARIO)

        val panel = assertIs<PanelState.StepEditor>(asked.panel)
        assertEquals(PanelExit.TO_SCENARIO, panel.leaving)
        assertEquals(4_000, panel.step.draft.delayMillisecondsAfter)
    }

    /**
     * The reason the pending exit is a value and not a boolean.
     *
     * Two exits, one question. Answering it has to take the user where they asked to go, and a
     * dialogue that closed the panel when they pressed *back* would be the most annoying kind of
     * bug: it only happens to somebody who has unsaved work.
     */
    @Test
    fun `discarding goes wherever the exit that was asked about was going`() {
        assertIs<PanelState.ScenarioEditor>(
            open(dirty()).leaving(PanelExit.TO_SCENARIO).leftBehind().panel,
        )
        assertNull(open(dirty()).leaving(PanelExit.CLOSED).leftBehind().panel)
    }

    @Test
    fun `keeping the edits puts the question away and nothing else`() {
        val kept = open(dirty()).leaving(PanelExit.CLOSED).stayed()

        val panel = assertIs<PanelState.StepEditor>(kept.panel)
        assertNull(panel.leaving)
        assertEquals(4_000, panel.step.draft.delayMillisecondsAfter)
    }

    @Test
    fun `moving a step is not an edit, so leaving afterwards asks nothing`() {
        // OV-6: a move is applied to the Scenario at once and never sits in the draft.
        val moved = open(editing().copy(stepNumber = 3))

        assertIs<PanelState.ScenarioEditor>(moved.leaving(PanelExit.TO_SCENARIO).panel)
    }

    @Test
    fun `the scenario panel has no exit to ask about`() {
        val scenario = OverlayUiState(panel = PanelState.ScenarioEditor())

        assertEquals(scenario, scenario.leaving(PanelExit.CLOSED))
        assertEquals(scenario, scenario.leftBehind())
        assertEquals(scenario, scenario.stayed())
    }
}
