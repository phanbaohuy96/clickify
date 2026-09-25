package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScenarioTest {
    private val point = ScreenPoint(540, 1200)
    private val target = StepTarget(point)

    @Test
    fun `a step whose action ignores its target draws no marker`() {
        assertTrue(Step(action = StepAction.Tap(), target = target).hasMarker)
        assertFalse(Step(action = StepAction.SetText("hi"), target = target).hasMarker)
        assertFalse(Step(action = StepAction.Global(GlobalActionKind.HOME), target = target).hasMarker)
    }

    @Test
    fun `the overlay draws one marker per step that has one`() {
        val scenario =
            Scenario(
                name = "Trade",
                steps =
                    listOf(
                        Step(action = StepAction.Tap(), target = target),
                        Step(action = StepAction.SetText("50"), target = target),
                        Step(action = StepAction.Tap(), target = target),
                        Step(action = StepAction.Global(GlobalActionKind.BACK), target = target),
                    ),
            )

        assertEquals(4, scenario.steps.size)
        assertEquals(2, scenario.markerCount)
    }

    @Test
    fun `a swipe touches both ends`() {
        val destination = ScreenPoint(540, 400)
        val swipe = StepAction.Swipe(destination = destination, durationMilliseconds = 250)

        assertEquals(listOf(point, destination), Step(action = swipe, target = target).points)
    }

    @Test
    fun `a multi-touch touches both ends of every path`() {
        val paths =
            listOf(
                GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 100),
                GesturePath(ScreenPoint(300, 300), ScreenPoint(400, 400), 100),
            )

        assertEquals(
            listOf(
                ScreenPoint(100, 100),
                ScreenPoint(200, 200),
                ScreenPoint(300, 300),
                ScreenPoint(400, 400),
            ),
            Step(action = StepAction.MultiTouch(paths), target = target).points,
        )
    }

    @Test
    fun `an empty scenario has no screen profile yet`() {
        assertEquals(null, Scenario(name = "New").screenProfile)
    }

    @Test
    fun `until stopped has no last iteration`() {
        assertEquals(null, RunCount.UntilStopped.totalIterations)
        assertEquals(7, RunCount.Times(7).totalIterations)
    }

    @Test
    fun `two steps built the same way are still different steps`() {
        val first = Step(action = StepAction.Tap(), target = target)
        val second = Step(action = StepAction.Tap(), target = target)

        // Identity comes from the id, not the contents: ten identical taps are ten Markers.
        assertFalse(first == second)
    }
}
