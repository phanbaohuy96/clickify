package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenProfileDifference
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioRunnerTest {
    private val profile =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    private fun tap(
        x: Int,
        y: Int,
        repeatCount: Int = 1,
        delayAfter: Int = 0,
    ) = Step(
        action = StepAction.Tap(),
        target = StepTarget(ScreenPoint(x, y)),
        repeatCount = repeatCount,
        delayMillisecondsAfter = delayAfter,
    )

    private fun scenario(
        steps: List<Step>,
        runCount: RunCount = RunCount.Times(1),
        countdown: Int = 0,
        screenProfile: ScreenProfile? = profile,
    ) = Scenario(
        name = "Test",
        steps = steps,
        runCount = runCount,
        countdownMilliseconds = countdown,
        screenProfile = screenProfile,
    )

    @Test
    fun `steps run in the order they were written`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val runner = ScenarioRunner(dispatcher)

            val reason =
                runner.run(scenario(listOf(tap(10, 10), tap(20, 20), tap(30, 30))), profile)

            assertEquals(FinishReason.Completed, reason)
            assertEquals(
                listOf(ScreenPoint(10, 10), ScreenPoint(20, 20), ScreenPoint(30, 30)),
                dispatcher.gestures.map { it.strokes.single().from },
            )
        }

    @Test
    fun `a repeat count repeats the action in place`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()

            ScenarioRunner(dispatcher).run(scenario(listOf(tap(10, 10, repeatCount = 4))), profile)

            assertEquals(4, dispatcher.gestures.size)
        }

    @Test
    fun `a scenario repeat runs the whole list again`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val steps = listOf(tap(10, 10), tap(20, 20))

            ScenarioRunner(dispatcher).run(scenario(steps, runCount = RunCount.Times(3)), profile)

            assertEquals(6, dispatcher.gestures.size)
        }

    @Test
    fun `every stroke is released when the run completes`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()

            ScenarioRunner(dispatcher).run(scenario(listOf(tap(10, 10))), profile)

            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `every stroke is released when the screen does not match`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val rotated = profile.copy(rotation = ScreenRotation.LANDSCAPE_LEFT)

            val reason = ScenarioRunner(dispatcher).run(scenario(listOf(tap(10, 10))), rotated)

            assertIs<FinishReason.ScreenProfileMismatch>(reason)
            assertEquals(listOf(ScreenProfileDifference.ROTATION), reason.differences)
            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `a screen that does not match is refused before the countdown, not after it`() =
        runTest {
            val events = mutableListOf<RunEvent>()
            val rotated = profile.copy(rotation = ScreenRotation.LANDSCAPE_LEFT)

            ScenarioRunner(FakeGestureDispatcher())
                .run(scenario(listOf(tap(10, 10)), countdown = 3_000), rotated) { events += it }

            assertTrue(events.none { it is RunEvent.CountingDown }, "$events")
        }

    @Test
    fun `a scenario with no screen profile yet is not blocked`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()

            val reason =
                ScenarioRunner(dispatcher)
                    .run(scenario(listOf(tap(10, 10)), screenProfile = null), profile)

            assertEquals(FinishReason.Completed, reason)
        }

    @Test
    fun `stop ends the run and no further step begins`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val runner = ScenarioRunner(dispatcher)
            val steps = List(20) { tap(it, it, delayAfter = 100) }
            var reason: FinishReason? = null

            val job = launch { reason = runner.run(scenario(steps), profile) }
            advanceTimeBy(250)
            runner.requestStop()
            job.join()

            assertEquals(FinishReason.Stopped, reason)
            assertTrue(dispatcher.gestures.size < steps.size, "${dispatcher.gestures.size}")
            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `stop during the countdown never reaches the first step`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val runner = ScenarioRunner(dispatcher)
            var reason: FinishReason? = null

            val job = launch { reason = runner.run(scenario(listOf(tap(1, 1)), countdown = 3_000), profile) }
            advanceTimeBy(500)
            runner.requestStop()
            job.join()

            assertEquals(FinishReason.Stopped, reason)
            assertEquals(emptyList(), dispatcher.gestures)
            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `an until-stopped scenario keeps going until it is stopped`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val runner = ScenarioRunner(dispatcher)
            var reason: FinishReason? = null

            val job =
                launch {
                    reason =
                        runner.run(
                            scenario(listOf(tap(1, 1, delayAfter = 50)), runCount = RunCount.UntilStopped),
                            profile,
                        )
                }
            advanceTimeBy(1_000)
            runCurrent()
            val soFar = dispatcher.gestures.size
            runner.requestStop()
            job.join()

            assertEquals(FinishReason.Stopped, reason)
            assertTrue(soFar > 5, "only $soFar gestures in a second")
        }

    @Test
    fun `a cancelled gesture stops the run and says which step`() =
        runTest {
            val dispatcher = FakeGestureDispatcher(outcome = GestureOutcome.CANCELLED)

            val reason =
                ScenarioRunner(dispatcher).run(scenario(listOf(tap(1, 1), tap(2, 2))), profile)

            assertEquals(FinishReason.GestureCancelled(stepIndex = 0), reason)
            assertEquals(1, dispatcher.gestures.size)
            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `a gesture that never reports back is treated as cancelled`() =
        runTest {
            // GX-6: the callback arrives long after the duration plus its one-second grace.
            val dispatcher = FakeGestureDispatcher(extraLatencyMilliseconds = 30_000)

            val reason = ScenarioRunner(dispatcher).run(scenario(listOf(tap(1, 1))), profile)

            assertEquals(FinishReason.GestureCancelled(stepIndex = 0), reason)
            assertEquals(1, dispatcher.releaseCount)
        }

    @Test
    fun `a hold of zero becomes the shortest stroke the platform accepts`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()

            ScenarioRunner(dispatcher).run(scenario(listOf(tap(1, 1))), profile)

            assertEquals(
                1L,
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
                    .durationMilliseconds,
            )
        }

    @Test
    fun `a swipe is one stroke from the target to the destination`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action =
                        StepAction.Swipe(
                            destination = ScreenPoint(540, 400),
                            durationMilliseconds = 250,
                        ),
                    target = StepTarget(ScreenPoint(540, 1800)),
                )

            ScenarioRunner(dispatcher).run(scenario(listOf(step)), profile)

            val stroke =
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
            assertEquals(ScreenPoint(540, 1800), stroke.from)
            assertEquals(ScreenPoint(540, 400), stroke.to)
            assertEquals(250L, stroke.durationMilliseconds)
        }

    @Test
    fun `a multi-touch is one gesture carrying every path`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val paths =
                listOf(
                    GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 120),
                    GesturePath(ScreenPoint(300, 300), ScreenPoint(400, 400), 120),
                )
            val step =
                Step(action = StepAction.MultiTouch(paths), target = StepTarget(ScreenPoint(100, 100)))

            ScenarioRunner(dispatcher).run(scenario(listOf(step)), profile)

            assertEquals(
                2,
                dispatcher.gestures
                    .single()
                    .strokes.size,
            )
        }

    @Test
    fun `a global action reaches the system and ignores its target`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Global(GlobalActionKind.BACK),
                    target = StepTarget(ScreenPoint(999, 999)),
                )

            ScenarioRunner(dispatcher).run(scenario(listOf(step)), profile)

            assertEquals(listOf(GlobalActionKind.BACK), dispatcher.globalActions)
            assertEquals(emptyList(), dispatcher.gestures)
        }

    @Test
    fun `a refused global action stops the run rather than carrying on`() =
        runTest {
            val dispatcher = FakeGestureDispatcher(globalActionSucceeds = false)
            val step =
                Step(action = StepAction.Global(GlobalActionKind.HOME), target = StepTarget(ScreenPoint(0, 0)))

            val reason = ScenarioRunner(dispatcher).run(scenario(listOf(step, tap(1, 1))), profile)

            assertEquals(FinishReason.GlobalActionRefused(stepIndex = 0), reason)
            assertEquals(emptyList(), dispatcher.gestures)
        }

    @Test
    fun `set text with nowhere to put it fails visibly`() =
        runTest {
            val dispatcher = FakeGestureDispatcher(setTextSucceeds = false)
            val step =
                Step(action = StepAction.SetText("50"), target = StepTarget(ScreenPoint(0, 0)))

            val reason = ScenarioRunner(dispatcher).run(scenario(listOf(step, tap(1, 1))), profile)

            assertEquals(FinishReason.NoFocusedField(stepIndex = 0), reason)
            assertEquals(listOf("50"), dispatcher.texts)
            assertEquals(emptyList(), dispatcher.gestures)
        }

    @Test
    fun `two gestures are always separated, even with every delay at zero`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val steps = listOf(tap(1, 1, repeatCount = 3, delayAfter = 0))

            val before = testScheduler.currentTime
            ScenarioRunner(dispatcher).run(scenario(steps), profile)
            val elapsed = testScheduler.currentTime - before

            // Three 1 ms strokes, two 10 ms gaps between them, and one 10 ms gap after the step.
            assertEquals(3 + 2 * 10 + 10, elapsed.toInt())
        }

    @Test
    fun `the countdown is reported as it runs`() =
        runTest {
            val events = mutableListOf<RunEvent>()

            ScenarioRunner(FakeGestureDispatcher())
                .run(scenario(listOf(tap(1, 1)), countdown = 500), profile) { events += it }

            val ticks = events.filterIsInstance<RunEvent.CountingDown>()
            assertEquals(500, ticks.first().remainingMilliseconds)
            assertEquals(100, ticks.last().remainingMilliseconds)
            assertEquals(5, ticks.size)
        }

    @Test
    fun `each step announces itself with its index and iteration`() =
        runTest {
            val events = mutableListOf<RunEvent>()
            val steps = listOf(tap(1, 1), tap(2, 2))

            ScenarioRunner(FakeGestureDispatcher())
                .run(scenario(steps, runCount = RunCount.Times(2)), profile) { events += it }

            assertEquals(
                listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
                events.filterIsInstance<RunEvent.StepStarted>().map { it.stepIndex to it.iteration },
            )
        }

    @Test
    fun `the last event is always why the run ended`() =
        runTest {
            val events = mutableListOf<RunEvent>()

            ScenarioRunner(FakeGestureDispatcher())
                .run(scenario(listOf(tap(1, 1))), profile) { events += it }

            assertEquals(RunEvent.Finished(FinishReason.Completed), events.last())
        }
}
