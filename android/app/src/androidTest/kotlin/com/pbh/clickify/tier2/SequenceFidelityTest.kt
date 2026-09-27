package com.pbh.clickify.tier2

import com.pbh.clickify.domain.run.FinishReason
import com.pbh.clickify.domain.run.ScenarioRunner
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.service.ClickifyAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tier 2, priorities 2 and 3: **order and speed**, and **coordinates**.
 *
 * `A1` is finished, says `docs/sdd/01-scope.md`, "when a 15-step sequence of fixed taps runs in the
 * right order, at speed, and Stop always works". The first two clauses are this file; the third is
 * [StrokeReleaseTest]. Until these ran, that sentence was a plan.
 */
class SequenceFidelityTest {
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
    fun a_tap_lands_on_the_pixel_its_Marker_names() {
        val where = target.point(HALF, HALF)
        val finished = runBlocking { runner.run(sequence(listOf(where)), currentProfile = null) }
        target.settle()

        assertEquals(FinishReason.Completed, finished)
        val down = target.arrived.downs().single()
        assertTrue(
            abs(down.x - where.x) <= TOLERANCE_PIXELS && abs(down.y - where.y) <= TOLERANCE_PIXELS,
            "aimed at (${where.x}, ${where.y}) and hit (${down.x}, ${down.y})",
        )
        // The touch is indistinguishable from an injected one and says so: ADR-0013's raw pixels
        // arrive as raw pixels, and the device id is the virtual one every injection carries.
        assertEquals(INJECTED_DEVICE_ID, down.deviceId, "an injected touch should carry the virtual device id")
        Tier2.note("aimed at (${where.x}, ${where.y}); arrived at (${down.x}, ${down.y}) from device ${down.deviceId}")
    }

    @Test
    fun fifteen_steps_arrive_in_order_at_their_own_pixels_and_at_speed() {
        val points = grid()
        assertEquals(EXPECTED_STEPS, points.size)

        val started = System.currentTimeMillis()
        val finished = runBlocking { runner.run(sequence(points), currentProfile = null) }
        val elapsed = System.currentTimeMillis() - started
        target.settle()

        assertEquals(FinishReason.Completed, finished)
        val downs = target.arrived.downs()
        target.arrived.assertNoPointerLeftDown()
        assertEquals(points.size, downs.size, "15 Steps, ${downs.size} contacts:\n${target.arrived.joinToString("\n")}")

        points.forEachIndexed { index, expected ->
            val arrived = downs[index]
            assertTrue(
                abs(arrived.x - expected.x) <= TOLERANCE_PIXELS && abs(arrived.y - expected.y) <= TOLERANCE_PIXELS,
                "Step ${index + 1} aimed at (${expected.x}, ${expected.y}) and arrived at (${arrived.x}, ${arrived.y}) — " +
                    "either out of order or off target:\n${downs.joinToString("\n")}",
            )
        }

        val gaps = downs.zipWithNext { left, right -> right.uptimeMilliseconds - left.uptimeMilliseconds }
        Tier2.note("15 zero-delay taps took ${elapsed}ms end to end; gaps between contacts: $gaps")
        assertTrue(
            gaps.all { it <= GAP_CEILING_MILLISECONDS },
            "a Step took ${gaps.max()}ms to follow the one before it, which is not speed: $gaps",
        )
        assertTrue(
            elapsed <= WHOLE_SEQUENCE_CEILING_MILLISECONDS,
            "15 zero-delay taps took ${elapsed}ms (gaps: $gaps)",
        )
    }

    @Test
    fun a_Step_repeats_in_place() {
        val where = target.point(HALF, THIRD)
        val scenario =
            Scenario(
                name = "repeat in place",
                steps = listOf(tapping(where, 0L).copy(repeatCount = REPEATS)),
                runCount = RunCount.Times(1),
                countdownMilliseconds = 0,
            )
        val finished = runBlocking { runner.run(scenario, currentProfile = null) }
        target.settle()

        assertEquals(FinishReason.Completed, finished)
        val downs = target.arrived.downs()
        assertEquals(REPEATS, downs.size, "GX-4 repeats the Action in place:\n${target.arrived.joinToString("\n")}")
        assertTrue(
            downs.all { abs(it.x - where.x) <= TOLERANCE_PIXELS && abs(it.y - where.y) <= TOLERANCE_PIXELS },
            "a repetition wandered off the Marker: ${downs.joinToString()}",
        )
    }

    @Test
    fun a_swipe_starts_at_its_Target_and_ends_at_its_destination() {
        val from = target.point(HALF, THIRD)
        val to = target.point(HALF, TWO_THIRDS)
        val scenario =
            Scenario(
                name = "one swipe",
                steps =
                    listOf(
                        Step(
                            action = StepAction.Swipe(destination = to, durationMilliseconds = SWIPE_MILLISECONDS),
                            target = StepTarget(from),
                            delayMillisecondsAfter = 0,
                        ),
                    ),
                runCount = RunCount.Times(1),
                countdownMilliseconds = 0,
            )
        val finished = runBlocking { runner.run(scenario, currentProfile = null) }
        target.settle()

        assertEquals(FinishReason.Completed, finished)
        val touches = target.arrived
        touches.assertNoPointerLeftDown()
        val down = touches.first { it.isDown }
        val up = touches.last { it.endsTheContact }
        assertTrue(
            abs(down.x - from.x) <= TOLERANCE_PIXELS && abs(down.y - from.y) <= TOLERANCE_PIXELS,
            "the swipe started at (${down.x}, ${down.y}) rather than (${from.x}, ${from.y})",
        )
        assertTrue(
            abs(up.x - to.x) <= TOLERANCE_PIXELS && abs(up.y - to.y) <= TOLERANCE_PIXELS,
            "the swipe ended at (${up.x}, ${up.y}) rather than (${to.x}, ${to.y})",
        )
        assertTrue(touches.size > 2, "a swipe should produce moves between its ends, got ${touches.size} events")
        Tier2.note(
            "a ${SWIPE_MILLISECONDS}ms swipe (${from.x}, ${from.y}) -> (${to.x}, ${to.y}) arrived as ${touches.size} events, " +
                "ending at (${up.x}, ${up.y}) after ${up.uptimeMilliseconds - down.uptimeMilliseconds}ms",
        )
    }

    @Test
    fun a_screen_profile_mismatch_touches_nothing() {
        val screen = ScreenProfile(widthPixels = 1_080, heightPixels = 2_400, densityDpi = 420, rotation = ScreenRotation.PORTRAIT)
        val scenario =
            sequence(listOf(target.point(HALF, HALF)))
                .copy(screenProfile = screen.copy(widthPixels = screen.widthPixels + 1))

        val finished = runBlocking { runner.run(scenario, currentProfile = screen) }
        target.settle()

        assertTrue(finished is FinishReason.ScreenProfileMismatch, "expected a refusal, got $finished")
        // GX-2 refuses before the countdown, and ADR-0013's whole argument is that it refuses rather
        // than acting approximately. One touch here would be the bug that decision exists to prevent.
        assertEquals(emptyList(), target.arrived, "a mismatched Scenario touched the screen")
    }

    /** Three columns by five rows, well inside the window, so every point is distinguishable. */
    private fun grid(): List<ScreenPoint> =
        (0 until ROWS).flatMap { row ->
            (0 until COLUMNS).map { column ->
                target.point(
                    fractionX = FIRST_EDGE + column * (SPAN / (COLUMNS - 1)),
                    fractionY = FIRST_EDGE + row * (SPAN / (ROWS - 1)),
                )
            }
        }

    private fun sequence(points: List<ScreenPoint>) =
        Scenario(
            name = "tier 2 sequence",
            steps = points.map { tapping(it, 0L) },
            runCount = RunCount.Times(1),
            countdownMilliseconds = 0,
        )

    private companion object {
        const val EXPECTED_STEPS = 15
        const val COLUMNS = 3
        const val ROWS = 5
        const val REPEATS = 3
        const val SWIPE_MILLISECONDS = 500L

        /** A dispatched point is delivered exactly; the slack is for rounding, not for aim. */
        const val TOLERANCE_PIXELS = 2

        /** `dumpsys input` reports every injected event as coming from this virtual device. */
        const val INJECTED_DEVICE_ID = -1

        /**
         * Measured rather than guessed, which is the only way a ceiling means anything: on this
         * emulator the fifteen taps land 13–17ms apart and the whole sequence takes 234ms. The
         * runner's own floor is 10ms (`GX-5`), so nearly all of that is `dispatchGesture` itself.
         * These leave room for a slower machine and would still catch a Step that waited.
         */
        const val GAP_CEILING_MILLISECONDS = 60L
        const val WHOLE_SEQUENCE_CEILING_MILLISECONDS = 2_000L

        const val FIRST_EDGE = 0.2
        const val SPAN = 0.6
        const val THIRD = 0.33
        const val HALF = 0.5
        const val TWO_THIRDS = 0.66
    }
}
