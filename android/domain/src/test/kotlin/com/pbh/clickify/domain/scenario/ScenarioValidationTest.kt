package com.pbh.clickify.domain.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioValidationTest {
    private val profile =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )
    private val point = ScreenPoint(540, 1200)

    private fun step(action: StepAction) = Step(action = action, target = StepTarget(point))

    @Test
    fun `an ordinary tap has nothing wrong with it`() {
        assertEquals(emptyList(), step(StepAction.Tap()).violations(profile = profile))
    }

    @Test
    fun `a tap may hold for zero, a swipe may not travel for zero`() {
        assertEquals(emptyList(), step(StepAction.Tap(holdMilliseconds = 0)).violations())

        val swipe = StepAction.Swipe(destination = ScreenPoint(540, 600), durationMilliseconds = 0)

        assertEquals(listOf(StepViolation.ZeroDuration), step(swipe).violations())
    }

    @Test
    fun `an eleventh contact is refused by the editor`() {
        val paths = List(11) { GesturePath(point, point, durationMilliseconds = 50) }

        assertEquals(
            listOf(StepViolation.TooManyPaths(count = 11, maximum = 10)),
            step(StepAction.MultiTouch(paths)).violations(),
        )
    }

    @Test
    fun `one contact is a swipe, not a multi-touch`() {
        val paths = listOf(GesturePath(point, point, durationMilliseconds = 50))

        assertEquals(
            listOf(StepViolation.TooFewPaths(count = 1, minimum = 2)),
            step(StepAction.MultiTouch(paths)).violations(),
        )
    }

    @Test
    fun `a hold past the platform maximum is refused`() {
        val violations = step(StepAction.Tap(holdMilliseconds = 60_001)).violations()

        assertEquals(
            listOf(StepViolation.DurationTooLong(milliseconds = 60_001, maximum = 60_000)),
            violations,
        )
    }

    @Test
    fun `the platform limits are taken from the platform, not hard-coded`() {
        // SM-17: a device reporting different bounds changes the answer without a code change.
        val stricter = GestureLimits(maxStrokeCount = 2, maxGestureDurationMilliseconds = 1_000)
        val paths = List(3) { GesturePath(point, point, durationMilliseconds = 50) }

        assertEquals(
            listOf(StepViolation.TooManyPaths(count = 3, maximum = 2)),
            step(StepAction.MultiTouch(paths)).violations(limits = stricter),
        )
    }

    @Test
    fun `a point off the edge of the screen is refused`() {
        val outside = ScreenPoint(1080, 500)
        val offEdge = Step(action = StepAction.Tap(), target = StepTarget(outside))

        assertEquals(
            listOf(StepViolation.PointOutsideScreen(outside)),
            offEdge.violations(profile = profile),
        )
    }

    @Test
    fun `a swipe destination off the screen is refused too`() {
        val swipe = StepAction.Swipe(destination = ScreenPoint(540, 2400), durationMilliseconds = 200)

        assertEquals(
            listOf(StepViolation.PointOutsideScreen(ScreenPoint(540, 2400))),
            step(swipe).violations(profile = profile),
        )
    }

    @Test
    fun `without a profile no point can be judged`() {
        val offEdge = Step(action = StepAction.Tap(), target = StepTarget(ScreenPoint(99_999, 99_999)))

        assertEquals(emptyList(), offEdge.violations())
    }

    @Test
    fun `an action with no coordinates is never judged on its point`() {
        val outside = StepTarget(ScreenPoint(99_999, 99_999))
        val setText = Step(action = StepAction.SetText("hello"), target = outside)
        val global = Step(action = StepAction.Global(GlobalActionKind.BACK), target = outside)

        assertEquals(emptyList(), setText.violations(profile = profile))
        assertEquals(emptyList(), global.violations(profile = profile))
    }

    @Test
    fun `every fault is reported at once, not one at a time`() {
        val paths = List(11) { GesturePath(point, point, durationMilliseconds = 60_001) }

        val violations = step(StepAction.MultiTouch(paths)).violations()

        assertTrue(violations.any { it is StepViolation.TooManyPaths })
        assertTrue(violations.any { it is StepViolation.DurationTooLong })
    }

    @Test
    fun `an overlong string is refused by the editor even though the reader truncates it`() {
        val violations = step(StepAction.SetText("x".repeat(5_001))).violations()

        assertEquals(listOf(StepViolation.TextTooLong(length = 5_001, maximum = 5_000)), violations)
    }
}
