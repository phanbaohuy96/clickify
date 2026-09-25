package com.pbh.clickify.domain.recording

import com.pbh.clickify.domain.editor.DEFAULT_TRAVEL_MILLISECONDS
import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.StepAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * RD-3 and RD-4: what a session of touching the screen turns into.
 *
 * This is the whole of recording that can be got wrong without a phone noticing, so it is the
 * whole of recording that is tested here.
 */
class RecordedTouchTest {
    private fun touch(
        from: ScreenPoint = ScreenPoint(100, 200),
        to: ScreenPoint = from,
        duration: Long = 40,
        gap: Long = 0,
    ) = RecordedTouch(start = from, end = to, durationMilliseconds = duration, gapBeforeMilliseconds = gap)

    @Test
    fun `a finger that stayed put is a tap at the point it touched`() {
        val steps = listOf(touch(from = ScreenPoint(540, 1820))).toSteps()

        val action = assertIs<StepAction.Tap>(steps.single().action)
        assertEquals(ScreenPoint(540, 1820), steps.single().target.point)
        assertEquals(40L, action.holdMilliseconds)
    }

    @Test
    fun `a long press keeps how long it was held`() {
        val steps = listOf(touch(duration = 1_500)).toSteps()

        assertEquals(1_500L, assertIs<StepAction.Tap>(steps.single().action).holdMilliseconds)
    }

    @Test
    fun `a finger that wandered inside the slop is still a tap`() {
        val steps = listOf(touch(from = ScreenPoint(100, 100), to = ScreenPoint(112, 108))).toSteps(slopPixels = 24)

        assertIs<StepAction.Tap>(steps.single().action)
    }

    @Test
    fun `a finger that travelled is a swipe from where it started to where it ended`() {
        val steps =
            listOf(
                touch(from = ScreenPoint(540, 1800), to = ScreenPoint(540, 600), duration = 260),
            ).toSteps()

        val action = assertIs<StepAction.Swipe>(steps.single().action)
        assertEquals(ScreenPoint(540, 1800), steps.single().target.point)
        assertEquals(ScreenPoint(540, 600), action.destination)
        assertEquals(260L, action.durationMilliseconds)
    }

    @Test
    fun `a swipe never has a duration of zero, because the platform rejects one`() {
        val steps = listOf(touch(to = ScreenPoint(900, 900), duration = 0)).toSteps()

        assertEquals(1L, assertIs<StepAction.Swipe>(steps.single().action).durationMilliseconds)
    }

    /** RD-4: waiting happened between two actions, so it belongs to the gap, not to either end. */
    @Test
    fun `the pause before a touch becomes the previous step's delay`() {
        val steps =
            listOf(
                touch(gap = 5_000),
                touch(gap = 840),
                touch(gap = 1_200),
            ).toSteps()

        assertEquals(840, steps[0].delayMillisecondsAfter)
        assertEquals(1_200, steps[1].delayMillisecondsAfter)
    }

    @Test
    fun `the wait before the first touch is discarded`() {
        // It is somebody finding their aim, not part of what they are automating.
        val steps = listOf(touch(gap = 9_000), touch(gap = 100)).toSteps()

        assertEquals(100, steps[0].delayMillisecondsAfter)
        assertTrue(steps.none { it.delayMillisecondsAfter == 9_000 })
    }

    @Test
    fun `the last step keeps the ordinary default, because nothing followed it`() {
        val steps = listOf(touch(gap = 500)).toSteps()

        assertEquals(ScenarioLimits.DEFAULT_DELAY_MILLISECONDS, steps.single().delayMillisecondsAfter)
    }

    @Test
    fun `a pause longer than a Scenario may hold is clamped rather than refused`() {
        // SM-16: one out-of-range number must not cost the user the recording.
        val steps = listOf(touch(), touch(gap = 99_999_999)).toSteps()

        assertEquals(ScenarioLimits.delayMilliseconds.last, steps[0].delayMillisecondsAfter)
    }

    @Test
    fun `a hold longer than the device allows is clamped to what it allows`() {
        val limits = GestureLimits(maxGestureDurationMilliseconds = 60_000)

        val steps = listOf(touch(duration = 90_000)).toSteps(limits = limits)

        assertEquals(60_000L, assertIs<StepAction.Tap>(steps.single().action).holdMilliseconds)
    }

    @Test
    fun `an empty session produces no steps`() {
        assertEquals(emptyList(), emptyList<RecordedTouch>().toSteps())
    }

    @Test
    fun `the order of the touches is the order of the steps`() {
        val steps =
            listOf(
                touch(from = ScreenPoint(1, 1)),
                touch(from = ScreenPoint(2, 2)),
                touch(from = ScreenPoint(3, 3)),
            ).toSteps()

        assertEquals(listOf(1, 2, 3), steps.map { it.target.point.x })
    }

    // PK-2: the same touch, read as aim rather than as performance.

    @Test
    fun `an aimed tap lands on the point the finger touched`() {
        val step = touch(from = ScreenPoint(447, 1081)).toPickedStep()

        assertIs<StepAction.Tap>(step.action)
        assertEquals(ScreenPoint(447, 1081), step.target.point)
    }

    @Test
    fun `an aimed drag becomes a swipe between its two ends`() {
        val step = touch(from = ScreenPoint(672, 2000), to = ScreenPoint(672, 1000)).toPickedStep()

        val action = assertIs<StepAction.Swipe>(step.action)
        assertEquals(ScreenPoint(672, 2000), step.target.point)
        assertEquals(ScreenPoint(672, 1000), action.destination)
    }

    /**
     * The difference between aiming and recording, stated as a test.
     *
     * Three seconds with a finger on the screen while deciding where to put a Step is hesitation,
     * not an instruction to hold for three seconds. Recording keeps that duration on purpose
     * (`RD-3`); picking must not, or every Step made this way would carry however long its author
     * took to make up their mind.
     */
    @Test
    fun `aiming throws the timing away where recording keeps it`() {
        val slow = touch(duration = 3_000)

        assertEquals(3_000L, assertIs<StepAction.Tap>(listOf(slow).toSteps().single().action).holdMilliseconds)
        assertEquals(0L, assertIs<StepAction.Tap>(slow.toPickedStep().action).holdMilliseconds)
    }

    @Test
    fun `an aimed swipe travels for as long as one drafted by hand`() {
        val step = touch(from = ScreenPoint(0, 0), to = ScreenPoint(500, 0), duration = 4_000).toPickedStep()

        assertEquals(DEFAULT_TRAVEL_MILLISECONDS, assertIs<StepAction.Swipe>(step.action).durationMilliseconds)
    }

    @Test
    fun `a finger that wandered inside the slop is still an aimed tap`() {
        val step = touch(from = ScreenPoint(100, 200), to = ScreenPoint(110, 205)).toPickedStep(slopPixels = 24)

        assertIs<StepAction.Tap>(step.action)
        assertEquals(ScreenPoint(100, 200), step.target.point)
    }
}
