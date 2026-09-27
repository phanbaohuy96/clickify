package com.pbh.clickify.tier2

import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.domain.run.ScenarioRunner
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.service.ClickifyAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tier 2, priority 1: **every stroke is terminated, on every exit** — `GX-8`, `GX-9`, and `SF-1` in
 * Android's vocabulary. The category's worst bug, and the reason this tier exists.
 *
 * The assertion is written the way `GX-8` writes the rule: a stroke in flight is **completed, not
 * abandoned**, so what is checked is that the contact lasts no longer than that stroke's own
 * duration and that no further **Step** begins. A test demanding an immediate lift would fail
 * against correct behaviour — `docs/testing.md` says so, and this is where that sentence is spent.
 */
class StrokeReleaseTest {
    private lateinit var service: ClickifyAccessibilityService
    private lateinit var target: TargetApp
    private lateinit var runner: ScenarioRunner

    @Before
    fun setUp() {
        service = Tier2.awaitService()
        target = TargetApp.launch()
        runner = ScenarioRunner(dispatcher = service, limits = service.gestureLimits)
    }

    @After
    fun tearDown() {
        // Guarded: when setUp fails there is nothing to close, and an exception here would hide the
        // failure that actually matters.
        if (::target.isInitialized) target.close()
    }

    @Test
    fun a_hold_arrives_and_is_let_go() {
        val where = target.point(HALF, HALF)
        val finished = runBlocking { runner.run(holding(where, HOLD_MILLISECONDS), currentProfile = null) }
        target.settle()

        assertEquals(FinishReason.Completed, finished)
        val touches = target.arrived
        touches.assertNoPointerLeftDown()
        assertEquals(1, touches.downs().size, "one Step, one contact, but got:\n${touches.joinToString("\n")}")
        val held = touches.last { it.endsTheContact }.uptimeMilliseconds - touches.first { it.isDown }.uptimeMilliseconds
        Tier2.note("a ${HOLD_MILLISECONDS}ms hold was on the screen for ${held}ms")
        assertTrue(
            held in HOLD_MILLISECONDS - TIMING_SLACK..HOLD_MILLISECONDS + TIMING_SLACK,
            "a ${HOLD_MILLISECONDS}ms hold was on the screen for ${held}ms",
        )
    }

    @Test
    fun stop_lets_the_stroke_in_flight_finish_and_starts_no_other() {
        val first = target.point(THIRD, HALF)
        val second = target.point(TWO_THIRDS, HALF)
        val scenario =
            Scenario(
                name = "stop mid-stroke",
                steps = listOf(tapping(first, LONG_HOLD_MILLISECONDS), tapping(second, 0L)),
                runCount = RunCount.Times(1),
                countdownMilliseconds = 0,
            )

        val finished =
            runBlocking {
                var reason: FinishReason? = null
                val run = launch(Dispatchers.Default) { reason = runner.run(scenario, currentProfile = null) }
                Tier2.awaitTrue(STROKE_START_MILLISECONDS, "the first stroke never landed") {
                    target.arrived.downs().isNotEmpty()
                }
                delay(STOP_AFTER_MILLISECONDS)
                // The contact is still on the screen at this moment: that is the whole premise.
                assertEquals(0, target.arrived.count { it.endsTheContact }, "the stroke ended before Stop was pressed")
                runner.requestStop()
                run.join()
                reason
            }
        target.settle()

        assertEquals(FinishReason.Stopped, finished)
        val touches = target.arrived
        touches.assertNoPointerLeftDown()
        assertEquals(1, touches.downs().size, "Step 2 began after Stop:\n${touches.joinToString("\n")}")
        val down = touches.first { it.isDown }
        val up = touches.last { it.endsTheContact }
        val held = up.uptimeMilliseconds - down.uptimeMilliseconds
        Tier2.note(
            "Stop asked for after ${STOP_AFTER_MILLISECONDS}ms of a ${LONG_HOLD_MILLISECONDS}ms stroke: " +
                "the contact ended ${held}ms after it began, and Step 2 never started",
        )
        assertTrue(
            held <= LONG_HOLD_MILLISECONDS + TIMING_SLACK,
            "Stop left the contact down for ${held}ms, longer than the stroke's own ${LONG_HOLD_MILLISECONDS}ms",
        )
        assertTrue(
            held > STOP_AFTER_MILLISECONDS,
            "the contact ended after ${held}ms, which means Stop cut the stroke short instead of letting it finish (GX-8)",
        )
    }

    @Test
    fun cancelling_the_run_leaves_no_finger_on_the_screen() {
        val scenario = holding(target.point(HALF, TWO_THIRDS), LONG_HOLD_MILLISECONDS)

        runBlocking {
            val run = launch(Dispatchers.Default) { runner.run(scenario, currentProfile = null) }
            Tier2.awaitTrue(STROKE_START_MILLISECONDS, "the stroke never landed") {
                target.arrived.downs().isNotEmpty()
            }
            delay(STOP_AFTER_MILLISECONDS)
            run.cancelAndJoin()
        }

        // `GX-10` is why this holds: no stroke is ever dispatched with `willContinue`, so a gesture
        // the system has already accepted ends by itself whatever happens to the coroutine that
        // asked for it. Cancelling cannot abandon a contact, and this is the proof rather than the
        // claim.
        Tier2.awaitTrue(LONG_HOLD_MILLISECONDS + TIMING_SLACK, "the contact never ended after the run was cancelled") {
            target.arrived.count { it.endsTheContact } == target.arrived.downs().size
        }
        target.settle()
        target.arrived.assertNoPointerLeftDown()
        val touches = target.arrived
        val held = touches.last { it.endsTheContact }.uptimeMilliseconds - touches.first { it.isDown }.uptimeMilliseconds
        Tier2.note("the run was cancelled ${STOP_AFTER_MILLISECONDS}ms in; the contact still ended by itself, ${held}ms after it began")
    }

    private fun holding(
        where: ScreenPoint,
        milliseconds: Long,
    ) = Scenario(
        name = "one hold",
        steps = listOf(tapping(where, milliseconds)),
        runCount = RunCount.Times(1),
        countdownMilliseconds = 0,
    )

    private companion object {
        const val HOLD_MILLISECONDS = 600L
        const val LONG_HOLD_MILLISECONDS = 2_500L
        const val STOP_AFTER_MILLISECONDS = 500L
        const val STROKE_START_MILLISECONDS = 3_000L

        /**
         * Measured: a 600ms hold is on the screen for 600ms and a 2500ms stroke for 2500ms, to the
         * millisecond, because the platform stamps the contact's ends at the stroke's own
         * boundaries. The slack is for a slower machine, not for aim.
         */
        const val TIMING_SLACK = 150L

        const val THIRD = 0.33
        const val HALF = 0.5
        const val TWO_THIRDS = 0.66
    }
}

internal fun tapping(
    where: ScreenPoint,
    holdMilliseconds: Long,
) = Step(
    action = StepAction.Tap(holdMilliseconds = holdMilliseconds),
    target = StepTarget(where),
    delayMillisecondsAfter = 0,
)

/** A point inside the window the target app is really being touched in, as a fraction of it. */
internal fun TargetApp.point(
    fractionX: Double,
    fractionY: Double,
): ScreenPoint =
    ScreenPoint(
        x = bounds.left + (bounds.width() * fractionX).toInt(),
        y = bounds.top + (bounds.height() * fractionY).toInt(),
    )
