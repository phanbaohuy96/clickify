package com.pbh.clickify.domain.run

import com.pbh.clickify.domain.recognition.GrayImage
import com.pbh.clickify.domain.recognition.ScreenSource
import com.pbh.clickify.domain.recognition.TemplateFinder
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.TemplateSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TP-20 to TP-26: a run that looks at the screen before it acts.
 *
 * Driven through the **real** [TemplateFinder] over hand-built images rather than through a
 * stubbed one. What is being tested here is the sentence "the match moves the Step", and a fake
 * finder would let that sentence be true of the test and false of the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScenarioRunnerRecognitionTest {
    private val templateId = UUID.randomUUID()

    private fun noise(
        width: Int,
        height: Int,
        seed: Int,
    ): GrayImage {
        val random = Random(seed)
        return GrayImage(width, height, FloatArray(width * height) { random.nextFloat() })
    }

    private val template = noise(10, 8, seed = 1)

    /** The template sitting at (30, 20), so its centre — and therefore the match — is (35, 24). */
    private val frameWithIt: GrayImage =
        run {
            val haystack = noise(200, 200, seed = 2)
            val copy = haystack.pixels.copyOf()
            for (y in 0 until template.height) {
                for (x in 0 until template.width) {
                    copy[(20 + y) * haystack.width + 30 + x] = template[x, y]
                }
            }
            GrayImage(haystack.width, haystack.height, copy)
        }
    private val matchCentre = ScreenPoint(35, 24)
    private val frameWithoutIt = noise(200, 200, seed = 3)

    private fun TestScope.finder(frame: GrayImage?) =
        TemplateFinder(
            screen = ScreenSource { frame },
            templates = { id -> template.takeIf { id == templateId } },
            elapsedMilliseconds = { testScheduler.currentTime },
        )

    private fun search(
        onTimeout: OnTimeout = OnTimeout.STOP_SCENARIO,
        waitMilliseconds: Int = 0,
    ) = TemplateSearch(templateId = templateId, waitMilliseconds = waitMilliseconds, onTimeout = onTimeout)

    private fun scenario(vararg steps: Step) = Scenario(name = "Test", steps = steps.toList(), countdownMilliseconds = 0)

    private fun events(): Pair<MutableList<RunEvent>, (RunEvent) -> Unit> {
        val seen = mutableListOf<RunEvent>()
        return seen to { event: RunEvent -> seen += event }
    }

    /** TP-20, the whole point: the Step goes where the picture is, not where it was drawn. */
    @Test
    fun `a tap lands on the match rather than on the point it was drawn at`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(100, 100)),
                    delayMillisecondsAfter = 0,
                    search = search(),
                )

            ScenarioRunner(dispatcher, finder = finder(frameWithIt)).run(scenario(step), null)

            assertEquals(
                matchCentre,
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
                    .from,
            )
        }

    /**
     * TP-20: a swipe keeps the shape it was drawn with.
     *
     * Resolving only the start would turn "swipe this card away" into "swipe from wherever the
     * card is towards one fixed corner", which is a different gesture every time it moves.
     */
    @Test
    fun `a swipe moves as one piece, so its direction and length survive`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Swipe(destination = ScreenPoint(140, 100), durationMilliseconds = 200),
                    target = StepTarget(ScreenPoint(100, 100)),
                    delayMillisecondsAfter = 0,
                    search = search(),
                )

            ScenarioRunner(dispatcher, finder = finder(frameWithIt)).run(scenario(step), null)

            val stroke =
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
            assertEquals(matchCentre, stroke.from)
            assertEquals(ScreenPoint(matchCentre.x + 40, matchCentre.y), stroke.to)
        }

    @Test
    fun `a template that never appears stops the run when that is what was asked for`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(10, 10)),
                    search = search(onTimeout = OnTimeout.STOP_SCENARIO),
                )

            val reason = ScenarioRunner(dispatcher, finder = finder(frameWithoutIt)).run(scenario(step), null)

            assertIs<FinishReason.TemplateNotFound>(reason)
            assertEquals(0, reason.stepIndex)
            assertTrue(dispatcher.gestures.isEmpty(), "nothing should have been pressed")
        }

    /** TP-21: *skip* is the other half, and it is the whole of "close it if it is there". */
    @Test
    fun `a template that never appears skips its step and lets the next one run`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val (seen, onEvent) = events()
            val absent =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(10, 10)),
                    delayMillisecondsAfter = 0,
                    search = search(onTimeout = OnTimeout.SKIP_STEP),
                )
            val ordinary =
                Step(action = StepAction.Tap(), target = StepTarget(ScreenPoint(70, 80)), delayMillisecondsAfter = 0)

            val reason =
                ScenarioRunner(dispatcher, finder = finder(frameWithoutIt))
                    .run(scenario(absent, ordinary), null, onEvent)

            assertEquals(FinishReason.Completed, reason)
            assertEquals(
                ScreenPoint(70, 80),
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
                    .from,
            )
            assertEquals(listOf(RunEvent.StepSkipped(stepIndex = 0)), seen.filterIsInstance<RunEvent.StepSkipped>())
        }

    /** TP-24: the condition is met, so the Step runs exactly as it would without one. */
    @Test
    fun `a guard that is satisfied lets the step through`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(70, 80)),
                    delayMillisecondsAfter = 0,
                    guard = Guard(search = search(), expects = Presence.PRESENT),
                )

            ScenarioRunner(dispatcher, finder = finder(frameWithIt)).run(scenario(step), null)

            // The Target has no search of its own, so the guard moves nothing.
            assertEquals(
                ScreenPoint(70, 80),
                dispatcher.gestures
                    .single()
                    .strokes
                    .single()
                    .from,
            )
        }

    @Test
    fun `a guard waiting for something to go away is satisfied when it is not there`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(70, 80)),
                    delayMillisecondsAfter = 0,
                    guard = Guard(search = search(), expects = Presence.ABSENT),
                )

            ScenarioRunner(dispatcher, finder = finder(frameWithoutIt)).run(scenario(step), null)

            assertEquals(1, dispatcher.gestures.size)
        }

    @Test
    fun `a guard that never comes true stops the run when that is what was asked for`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(70, 80)),
                    guard = Guard(search = search(onTimeout = OnTimeout.STOP_SCENARIO), expects = Presence.PRESENT),
                )

            val reason = ScenarioRunner(dispatcher, finder = finder(frameWithoutIt)).run(scenario(step), null)

            assertIs<FinishReason.GuardUnmet>(reason)
            assertTrue(dispatcher.gestures.isEmpty())
        }

    @Test
    fun `a guard that never comes true can skip instead`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(70, 80)),
                    delayMillisecondsAfter = 0,
                    guard = Guard(search = search(onTimeout = OnTimeout.SKIP_STEP), expects = Presence.PRESENT),
                )

            val reason = ScenarioRunner(dispatcher, finder = finder(frameWithoutIt)).run(scenario(step), null)

            assertEquals(FinishReason.Completed, reason)
            assertTrue(dispatcher.gestures.isEmpty())
        }

    /**
     * TP-26: once per repetition, before the Action.
     *
     * A Step that presses a button ten times is ten chances for the button to move, and resolving
     * once outside the loop would spend nine of them on a stale answer.
     */
    @Test
    fun `a repeated step looks again for every repetition`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            var looks = 0
            val counting =
                ScreenSource {
                    looks++
                    frameWithIt
                }
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(100, 100)),
                    repeatCount = 3,
                    delayMillisecondsAfter = 0,
                    search = search(),
                )
            val finder =
                TemplateFinder(
                    screen = counting,
                    templates = { template },
                    elapsedMilliseconds = { testScheduler.currentTime },
                )

            ScenarioRunner(dispatcher, finder = finder).run(scenario(step), null)

            assertEquals(3, looks)
            assertEquals(3, dispatcher.gestures.size)
        }

    /** TP-22: `globalAction` ignores its Target, so it ignores a search attached to one. */
    @Test
    fun `a global action never looks at the screen, however its target is decorated`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            var looks = 0
            val counting =
                ScreenSource {
                    looks++
                    frameWithoutIt
                }
            val step =
                Step(
                    action = StepAction.Global(GlobalActionKind.BACK),
                    target = StepTarget(ScreenPoint(0, 0)),
                    delayMillisecondsAfter = 0,
                    search = search(onTimeout = OnTimeout.STOP_SCENARIO),
                )
            val finder =
                TemplateFinder(
                    screen = counting,
                    templates = { template },
                    elapsedMilliseconds = { testScheduler.currentTime },
                )

            val reason = ScenarioRunner(dispatcher, finder = finder).run(scenario(step), null)

            assertEquals(FinishReason.Completed, reason)
            assertEquals(0, looks)
            assertEquals(listOf(GlobalActionKind.BACK), dispatcher.globalActions)
        }

    /**
     * No accessibility service, so no frames: a Step told to wait for a button must not run as
     * though the button were there.
     */
    @Test
    fun `with no way to look at the screen, every search times out`() =
        runTest {
            val dispatcher = FakeGestureDispatcher()
            val step =
                Step(
                    action = StepAction.Tap(),
                    target = StepTarget(ScreenPoint(10, 10)),
                    search = search(onTimeout = OnTimeout.STOP_SCENARIO),
                )

            val reason = ScenarioRunner(dispatcher, finder = null).run(scenario(step), null)

            assertIs<FinishReason.TemplateNotFound>(reason)
        }
}
