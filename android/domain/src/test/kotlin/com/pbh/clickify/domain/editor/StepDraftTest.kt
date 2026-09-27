package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.GestureLimits
import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.StepViolation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StepDraftTest {
    private val profile =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    private fun step(
        action: StepAction,
        at: ScreenPoint = ScreenPoint(540, 1200),
    ) = Step(action = action, target = StepTarget(at))

    @Test
    fun `a step opened in the editor and saved again is the same step`() {
        val actions =
            listOf(
                StepAction.Tap(holdMilliseconds = 750),
                StepAction.Swipe(ScreenPoint(900, 300), durationMilliseconds = 400),
                StepAction.MultiTouch(
                    listOf(
                        GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 300),
                        GesturePath(ScreenPoint(800, 100), ScreenPoint(700, 200), 300),
                    ),
                ),
                StepAction.Global(GlobalActionKind.RECENTS),
                StepAction.SetText("hello"),
            )

        actions.forEach { action ->
            val original = step(action).copy(repeatCount = 4, delayMillisecondsAfter = 250)

            assertEquals(original, original.toDraft(profile).toStep())
        }
    }

    @Test
    fun `switching the action away and back loses nothing`() {
        val original = step(StepAction.Swipe(ScreenPoint(900, 300), durationMilliseconds = 400))
        val draft = original.toDraft(profile)

        val wandered =
            draft
                .copy(kind = StepActionKind.TAP)
                .copy(kind = StepActionKind.GLOBAL_ACTION)
                .copy(kind = StepActionKind.SET_TEXT)
                .copy(kind = StepActionKind.SWIPE)

        assertEquals(original, wandered.toStep())
    }

    @Test
    fun `a step whose action ignores its target keeps the target anyway`() {
        val draft = step(StepAction.SetText("abc"), at = ScreenPoint(700, 1900)).toDraft(profile)

        // SM-8: the model always has one, so switching back to a tap restores the Marker where the
        // user last left it rather than somewhere the editor guessed.
        assertEquals(
            ScreenPoint(700, 1900),
            draft
                .copy(kind = StepActionKind.TAP)
                .toStep()
                .target.point,
        )
    }

    @Test
    fun `choosing swipe invents a destination that is somewhere else and on the screen`() {
        val draft = step(StepAction.Tap(), at = ScreenPoint(540, 1200)).toDraft(profile)

        val destination = draft.swipeDestination
        assertNotEquals(draft.target, destination)
        assertTrue(destination.x in 0 until profile.widthPixels)
        assertTrue(destination.y in 0 until profile.heightPixels)
    }

    @Test
    fun `an invented swipe travels upwards, away from the panel that asked for it`() {
        val draft = step(StepAction.Tap(), at = ScreenPoint(540, 1200)).toDraft(profile)

        // The Step panel holds the bottom of the screen while this value is invented, so a
        // destination below the start is a Marker the user is told to drag and cannot see.
        assertTrue(draft.swipeDestination.y < draft.target.y)
    }

    @Test
    fun `a swipe invented at the top of the screen turns around`() {
        val draft = step(StepAction.Tap(), at = ScreenPoint(540, 0)).toDraft(profile)

        // Clamping would put the destination back on the start, and a swipe that goes nowhere is
        // not what choosing "swipe" meant.
        assertTrue(draft.swipeDestination.y > draft.target.y)
        assertTrue(draft.swipeDestination.y < profile.heightPixels)
    }

    @Test
    fun `an invented point stays on the screen with no profile to measure against`() {
        val draft = step(StepAction.Tap(), at = ScreenPoint(540, 1200)).toDraft(profile = null)

        assertNotEquals(draft.target, draft.swipeDestination)
        assertTrue(draft.swipeDestination.y >= 0)
    }

    @Test
    fun `choosing multi-touch starts with two contacts, not one`() {
        val draft = step(StepAction.Tap()).toDraft(profile)

        assertEquals(2, draft.paths.size)
        assertContentEquals(emptyList(), draft.copy(kind = StepActionKind.MULTI_TOUCH).violations(profile = profile))
    }

    @Test
    fun `the two invented contacts do not sit on top of each other`() {
        val draft = step(StepAction.Tap()).toDraft(profile)

        assertNotEquals(draft.paths[0].start, draft.paths[1].start)
    }

    @Test
    fun `adding a contact stops at the platform's stroke count`() {
        val limits = GestureLimits(maxStrokeCount = 3)
        var draft = step(StepAction.Tap()).toDraft(profile)

        repeat(10) { draft = draft.withPathAdded(limits, profile) }

        assertEquals(3, draft.paths.size)
    }

    @Test
    fun `removing contacts stops at one, so there is something left to explain`() {
        var draft = step(StepAction.Tap()).toDraft(profile).copy(kind = StepActionKind.MULTI_TOUCH)

        repeat(5) { draft = draft.withPathRemoved(0) }

        assertEquals(1, draft.paths.size)
        assertTrue(draft.violations().any { it is StepViolation.TooFewPaths })
    }

    @Test
    fun `removing a contact out of range changes nothing`() {
        val draft = step(StepAction.Tap()).toDraft(profile)

        assertEquals(draft, draft.withPathRemoved(9))
    }

    @Test
    fun `only the action in force is judged`() {
        // A destination left over from a swipe the user backed out of is not a reason to refuse
        // the tap they settled on — even when it is off the screen entirely.
        val draft =
            step(StepAction.Tap()).toDraft(profile).copy(
                swipeDestination = ScreenPoint(99_999, 99_999),
                swipeDurationMilliseconds = 0,
            )

        assertContentEquals(emptyList(), draft.violations(profile = profile))
        assertTrue(draft.copy(kind = StepActionKind.SWIPE).violations(profile = profile).isNotEmpty())
    }

    @Test
    fun `dragging a marker moves the points and keeps everything else the draft holds`() {
        val draft =
            step(StepAction.Swipe(ScreenPoint(900, 300), durationMilliseconds = 400))
                .toDraft(profile)
                .copy(text = "typed earlier", holdMilliseconds = 750)

        val dragged =
            draft.withPointsFrom(
                step(StepAction.Swipe(ScreenPoint(120, 130), 400), at = ScreenPoint(10, 20)),
            )

        assertEquals(ScreenPoint(10, 20), dragged.target)
        assertEquals(ScreenPoint(120, 130), dragged.swipeDestination)
        // The four Actions not in force are untouched: coming back to them must find them as left.
        assertEquals("typed earlier", dragged.text)
        assertEquals(750, dragged.holdMilliseconds)
        assertEquals(400, dragged.swipeDurationMilliseconds)
    }

    @Test
    fun `dragging a marker of a multi-touch step moves that contact only`() {
        val paths =
            listOf(
                GesturePath(ScreenPoint(100, 100), ScreenPoint(100, 100), 300),
                GesturePath(ScreenPoint(800, 100), ScreenPoint(800, 100), 300),
            )
        val draft = step(StepAction.MultiTouch(paths)).toDraft(profile)
        val moved = paths.toMutableList().also { it[1] = it[1].copy(start = ScreenPoint(500, 900)) }

        val dragged = draft.withPointsFrom(step(StepAction.MultiTouch(moved)))

        assertEquals(ScreenPoint(100, 100), dragged.paths[0].start)
        assertEquals(ScreenPoint(500, 900), dragged.paths[1].start)
    }

    @Test
    fun `an action with no points has nothing to drag`() {
        val draft = step(StepAction.SetText("hello")).toDraft(profile)

        assertEquals(draft, draft.withPointsFrom(step(StepAction.SetText("hello"), at = ScreenPoint(9, 9))))
    }

    @Test
    fun `a hold longer than the platform allows is refused at the editor`() {
        val draft = step(StepAction.Tap()).toDraft(profile).copy(holdMilliseconds = 60_001)

        assertTrue(draft.violations().any { it is StepViolation.DurationTooLong })
    }

    @Test
    fun `text past five thousand characters is refused at the editor`() {
        val draft = step(StepAction.Tap()).toDraft(profile).copy(kind = StepActionKind.SET_TEXT, text = "x".repeat(5_001))

        assertTrue(draft.violations().any { it is StepViolation.TextTooLong })
    }
}
