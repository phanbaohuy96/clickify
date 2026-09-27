package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** TP-20: a match moves the whole Step, so the gesture keeps the shape it was drawn with. */
class StepMovedTest {
    private fun step(
        action: StepAction,
        at: ScreenPoint = ScreenPoint(100, 100),
    ) = Step(action = action, target = StepTarget(at))

    @Test
    fun `a tap moves to the point it was given`() {
        val moved = step(StepAction.Tap()).movedTo(ScreenPoint(400, 900))

        assertEquals(ScreenPoint(400, 900), moved.target.point)
    }

    @Test
    fun `a swipe keeps its direction and its length`() {
        val swipe =
            step(StepAction.Swipe(destination = ScreenPoint(300, 100), durationMilliseconds = 200))

        val moved = swipe.movedTo(ScreenPoint(500, 700))

        assertEquals(ScreenPoint(500, 700), moved.target.point)
        assertEquals(ScreenPoint(700, 700), assertIs<StepAction.Swipe>(moved.action).destination)
    }

    @Test
    fun `every contact of a multi-touch step travels together`() {
        val paths =
            listOf(
                GesturePath(ScreenPoint(100, 100), ScreenPoint(120, 140), 100),
                GesturePath(ScreenPoint(200, 300), ScreenPoint(200, 340), 100),
            )

        val moved = step(StepAction.MultiTouch(paths)).movedTo(ScreenPoint(110, 90))

        val after = assertIs<StepAction.MultiTouch>(moved.action).paths
        assertEquals(ScreenPoint(110, 90), after[0].start)
        assertEquals(ScreenPoint(130, 130), after[0].end)
        assertEquals(ScreenPoint(210, 290), after[1].start)
        assertEquals(ScreenPoint(210, 330), after[1].end)
    }

    @Test
    fun `a step that is already there is returned untouched`() {
        val original = step(StepAction.Tap())

        assertSame(original, original.movedTo(ScreenPoint(100, 100)))
    }

    /** SM-8: the two Actions with no points have nothing to move, and moving must not invent any. */
    @Test
    fun `an action with no points keeps its target and nothing else changes`() {
        val moved = step(StepAction.Global(GlobalActionKind.HOME)).movedTo(ScreenPoint(4, 5))

        assertEquals(ScreenPoint(4, 5), moved.target.point)
        assertEquals(StepAction.Global(GlobalActionKind.HOME), moved.action)
    }
}
