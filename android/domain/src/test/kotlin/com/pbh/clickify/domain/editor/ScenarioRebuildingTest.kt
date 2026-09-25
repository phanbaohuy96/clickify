package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenOrientation
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.orientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SM-18: the one operation that is allowed to replace a Scenario's Screen profile.
 *
 * Everything about `SM-14` is arranged so this cannot happen by accident — the profile is captured
 * once and kept, so a mismatch is reported rather than overwritten. This is the deliberate way,
 * and what it must not do is as important as what it does.
 */
class ScenarioRebuildingTest {
    private val portrait =
        ScreenProfile(widthPixels = 1080, heightPixels = 2400, densityDpi = 440, rotation = ScreenRotation.PORTRAIT)

    private val landscape =
        portrait.copy(widthPixels = 2400, heightPixels = 1080, rotation = ScreenRotation.LANDSCAPE_LEFT)

    private fun scenario(
        vararg steps: Step,
        profile: ScreenProfile? = portrait,
    ) = Scenario(name = "Test", steps = steps.toList(), screenProfile = profile)

    private fun tap(at: ScreenPoint) = Step(action = StepAction.Tap(holdMilliseconds = 250), target = StepTarget(at))

    @Test
    fun `the scenario takes the screen it was rebuilt for`() {
        val rebuilt = scenario(tap(ScreenPoint(100, 200))).rebuiltFor(landscape)

        assertEquals(landscape, rebuilt.screenProfile)
        assertEquals(ScreenOrientation.LANDSCAPE, rebuilt.screenProfile?.orientation)
    }

    /**
     * The point of the whole operation: a point that was reachable in portrait and is not in
     * landscape is brought inside, so its Marker can be found and aimed again.
     */
    @Test
    fun `a point off the new screen is pulled inside it`() {
        val rebuilt = scenario(tap(ScreenPoint(900, 2300))).rebuiltFor(landscape)

        assertEquals(
            ScreenPoint(900, 1079),
            rebuilt.steps
                .single()
                .target.point,
        )
    }

    @Test
    fun `a point already inside the new screen is left exactly where it was`() {
        val rebuilt = scenario(tap(ScreenPoint(640, 480))).rebuiltFor(landscape)

        assertEquals(
            ScreenPoint(640, 480),
            rebuilt.steps
                .single()
                .target.point,
        )
    }

    /**
     * No transform, and this is the assertion that says so.
     *
     * Rotating the coordinate would put 100, 200 at 200, 100 — true about the display and false
     * about the application, whose buttons are somewhere else entirely once it has re-laid itself
     * out (ADR-0013). Clamping keeps the number honest about being unverified.
     */
    @Test
    fun `nothing is rotated, because there is no honest rotation of a point`() {
        val rebuilt = scenario(tap(ScreenPoint(100, 200))).rebuiltFor(landscape)

        assertEquals(
            ScreenPoint(100, 200),
            rebuilt.steps
                .single()
                .target.point,
        )
    }

    @Test
    fun `both ends of a swipe are brought inside`() {
        val swipe =
            Step(
                action = StepAction.Swipe(destination = ScreenPoint(1000, 2399), durationMilliseconds = 200),
                target = StepTarget(ScreenPoint(50, 2000)),
            )

        val rebuilt = scenario(swipe).rebuiltFor(landscape)

        assertEquals(
            ScreenPoint(50, 1079),
            rebuilt.steps
                .single()
                .target.point,
        )
        assertEquals(ScreenPoint(1000, 1079), assertIs<StepAction.Swipe>(rebuilt.steps.single().action).destination)
    }

    @Test
    fun `every contact of a multi-touch is brought inside`() {
        val pinch =
            Step(
                action =
                    StepAction.MultiTouch(
                        paths =
                            listOf(
                                GesturePath(ScreenPoint(10, 2300), ScreenPoint(20, 2200), 300),
                                GesturePath(ScreenPoint(30, 1500), ScreenPoint(40, 900), 300),
                            ),
                    ),
                target = StepTarget(ScreenPoint(10, 2300)),
            )

        val paths =
            assertIs<StepAction.MultiTouch>(
                scenario(pinch)
                    .rebuiltFor(landscape)
                    .steps
                    .single()
                    .action,
            ).paths

        assertEquals(listOf(1079, 1079, 1079, 900), paths.flatMap { listOf(it.start.y, it.end.y) })
    }

    @Test
    fun `the steps, their order and their timing all survive`() {
        val first = tap(ScreenPoint(1, 1)).copy(repeatCount = 4, delayMillisecondsAfter = 750)
        val second = Step(action = StepAction.Global(GlobalActionKind.BACK), target = StepTarget(ScreenPoint(0, 0)))

        val rebuilt = scenario(first, second).rebuiltFor(landscape)

        assertEquals(listOf(first.id, second.id), rebuilt.steps.map { it.id })
        assertEquals(4, rebuilt.steps.first().repeatCount)
        assertEquals(750, rebuilt.steps.first().delayMillisecondsAfter)
        assertEquals(250L, assertIs<StepAction.Tap>(rebuilt.steps.first().action).holdMilliseconds)
        assertEquals(StepAction.Global(GlobalActionKind.BACK), rebuilt.steps.last().action)
    }

    /** SM-14: with no Marker there is no profile to rebuild, and taking one would block a run. */
    @Test
    fun `a scenario with no marker is left alone, profile and all`() {
        val textOnly =
            Step(action = StepAction.SetText("hello"), target = StepTarget(ScreenPoint(0, 0)))

        val rebuilt = scenario(textOnly, profile = null).rebuiltFor(landscape)

        assertNull(rebuilt.screenProfile)
        assertEquals(textOnly, rebuilt.steps.single())
    }

    @Test
    fun `an empty scenario is unchanged`() {
        val empty = Scenario(name = "Empty")

        assertEquals(empty, empty.rebuiltFor(landscape))
    }

    @Test
    fun `rebuilding for the screen it already has changes nothing`() {
        val same = scenario(tap(ScreenPoint(640, 480)))

        assertEquals(same, same.rebuiltFor(portrait))
    }

    /** SM-18: the orientation is read off the pixels, so a square-ish screen is never landscape. */
    @Test
    fun `orientation follows the pixels rather than the reported rotation`() {
        assertEquals(ScreenOrientation.PORTRAIT, portrait.orientation)
        assertEquals(ScreenOrientation.LANDSCAPE, landscape.orientation)
        assertEquals(
            ScreenOrientation.PORTRAIT,
            portrait.copy(rotation = ScreenRotation.LANDSCAPE_LEFT).orientation,
        )
        assertTrue(portrait.copy(widthPixels = 2400).orientation == ScreenOrientation.PORTRAIT)
    }
}
