package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals

class ScenarioRepairTest {
    private val point = ScreenPoint(100, 200)

    @Test
    fun `an out-of-range delay is clamped, not rejected`() {
        val scenario =
            Scenario(
                name = "Trade",
                steps =
                    listOf(
                        Step(action = StepAction.Tap(), target = StepTarget(point), delayMillisecondsAfter = -50),
                        Step(action = StepAction.Tap(), target = StepTarget(point), delayMillisecondsAfter = 99_999_999),
                    ),
            )

        val repaired = scenario.clampedToLimits()

        assertEquals(2, repaired.steps.size)
        assertEquals(ScenarioLimits.delayMilliseconds.first, repaired.steps[0].delayMillisecondsAfter)
        assertEquals(ScenarioLimits.delayMilliseconds.last, repaired.steps[1].delayMillisecondsAfter)
    }

    @Test
    fun `a blank name becomes the untranslated repair value`() {
        assertEquals(Scenario.REPAIRED_NAME, Scenario(name = "   ").clampedToLimits().name)
    }

    @Test
    fun `a name the user chose is left alone`() {
        assertEquals("Quest run", Scenario(name = "Quest run").clampedToLimits().name)
    }

    @Test
    fun `until stopped survives clamping`() {
        val scenario = Scenario(name = "Skip", runCount = RunCount.UntilStopped)

        assertEquals(RunCount.UntilStopped, scenario.clampedToLimits().runCount)
    }

    @Test
    fun `a run count of zero becomes one`() {
        val scenario = Scenario(name = "Skip", runCount = RunCount.Times(0))

        assertEquals(RunCount.Times(1), scenario.clampedToLimits().runCount)
    }

    @Test
    fun `an eleventh contact is dropped rather than failing the whole scenario`() {
        val paths = List(11) { GesturePath(point, point, durationMilliseconds = 50) }
        val scenario =
            Scenario(
                name = "Chord",
                steps = listOf(Step(action = StepAction.MultiTouch(paths), target = StepTarget(point))),
            )

        val action =
            scenario
                .clampedToLimits()
                .steps
                .single()
                .action

        assertEquals(GestureLimits.DEFAULT_MAX_STROKE_COUNT, (action as StepAction.MultiTouch).paths.size)
    }

    @Test
    fun `a hold longer than the platform allows is clamped to it`() {
        val scenario =
            Scenario(
                name = "Hold",
                steps = listOf(Step(action = StepAction.Tap(holdMilliseconds = 90_000), target = StepTarget(point))),
            )

        val action =
            scenario
                .clampedToLimits()
                .steps
                .single()
                .action

        assertEquals(
            GestureLimits.DEFAULT_MAX_GESTURE_DURATION_MILLISECONDS,
            (action as StepAction.Tap).holdMilliseconds,
        )
    }

    @Test
    fun `an overlong string is truncated`() {
        val scenario =
            Scenario(
                name = "Paste",
                steps = listOf(Step(action = StepAction.SetText("x".repeat(9_000)), target = StepTarget(point))),
            )

        val action =
            scenario
                .clampedToLimits()
                .steps
                .single()
                .action

        assertEquals(ScenarioLimits.setTextLength.last, (action as StepAction.SetText).text.length)
    }
}
