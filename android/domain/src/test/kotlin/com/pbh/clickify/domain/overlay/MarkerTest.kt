package com.pbh.clickify.domain.overlay

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkerTest {
    private val profile =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    private fun step(
        action: StepAction,
        at: ScreenPoint = ScreenPoint(100, 100),
    ) = Step(action = action, target = StepTarget(at))

    private fun scenario(vararg steps: Step) = Scenario(name = "Test", steps = steps.toList())

    @Test
    fun `a tap draws one marker at its target`() {
        val markers = scenario(step(StepAction.Tap(), at = ScreenPoint(820, 410))).markers()

        assertEquals(1, markers.size)
        assertEquals(ScreenPoint(820, 410), markers.single().point)
        assertEquals(MarkerRole.POINT, markers.single().role)
        assertNull(markers.single().connectedTo)
    }

    @Test
    fun `markers are numbered by step order, counting from one`() {
        val markers =
            scenario(
                step(StepAction.Tap()),
                step(StepAction.Tap()),
                step(StepAction.Tap()),
            ).markers()

        assertEquals(listOf(1, 2, 3), markers.map { it.stepNumber })
    }

    @Test
    fun `reordering the scenario renumbers every marker`() {
        val first = step(StepAction.Tap(), at = ScreenPoint(10, 10))
        val second = step(StepAction.Tap(), at = ScreenPoint(20, 20))

        val reversed = scenario(second, first).markers()

        assertEquals(
            listOf(ScreenPoint(20, 20) to 1, ScreenPoint(10, 10) to 2),
            reversed.map { it.point to it.stepNumber },
        )
    }

    @Test
    fun `an action that ignores its target draws nothing`() {
        val markers =
            scenario(
                step(StepAction.SetText("50")),
                step(StepAction.Global(GlobalActionKind.BACK)),
            ).markers()

        assertEquals(emptyList(), markers)
    }

    @Test
    fun `a step that draws nothing still takes its number from the order`() {
        val markers =
            scenario(
                step(StepAction.Tap(), at = ScreenPoint(10, 10)),
                step(StepAction.SetText("50")),
                step(StepAction.Tap(), at = ScreenPoint(30, 30)),
            ).markers()

        // The second Step has no Marker, and the third is still number 3.
        assertEquals(listOf(1, 3), markers.map { it.stepNumber })
    }

    @Test
    fun `a swipe draws two markers joined to each other`() {
        val destination = ScreenPoint(540, 400)
        val swipe = StepAction.Swipe(destination = destination, durationMilliseconds = 250)

        val markers = scenario(step(swipe, at = ScreenPoint(540, 1800))).markers()

        assertEquals(2, markers.size)
        assertEquals(MarkerRole.SWIPE_START, markers[0].role)
        assertEquals(MarkerRole.SWIPE_END, markers[1].role)
        assertEquals(destination, markers[0].connectedTo)
        assertEquals(ScreenPoint(540, 1800), markers[1].connectedTo)
        assertEquals(listOf(1, 1), markers.map { it.stepNumber })
    }

    @Test
    fun `every contact of a multi-touch carries the same step number`() {
        val paths =
            listOf(
                GesturePath(ScreenPoint(100, 100), ScreenPoint(100, 100), 100),
                GesturePath(ScreenPoint(300, 300), ScreenPoint(300, 300), 100),
            )

        val markers = scenario(step(StepAction.MultiTouch(paths))).markers()

        assertEquals(listOf(1, 1), markers.map { it.stepNumber })
        assertEquals(listOf(0, 1), markers.map { it.pathIndex })
    }

    @Test
    fun `a multi-touch contact that does not move draws one marker, not two`() {
        val paths = listOf(GesturePath(ScreenPoint(100, 100), ScreenPoint(100, 100), 100))

        val markers = scenario(step(StepAction.MultiTouch(paths))).markers()

        assertEquals(1, markers.size)
        assertNull(markers.single().connectedTo)
    }

    @Test
    fun `a multi-touch contact that travels draws both ends`() {
        val paths = listOf(GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 100))

        val markers = scenario(step(StepAction.MultiTouch(paths))).markers()

        assertEquals(
            listOf(MarkerRole.TOUCH_START, MarkerRole.TOUCH_END),
            markers.map { it.role },
        )
    }

    @Test
    fun `a marker stops at the edge instead of leaving the screen`() {
        assertEquals(ScreenPoint(0, 0), ScreenPoint(-40, -1).clampedInto(profile))
        assertEquals(ScreenPoint(1079, 2399), ScreenPoint(5000, 5000).clampedInto(profile))
    }

    @Test
    fun `a marker already on the screen is left where it is`() {
        assertEquals(ScreenPoint(540, 1200), ScreenPoint(540, 1200).clampedInto(profile))
    }

    @Test
    fun `every marker knows which step it belongs to`() {
        val tap = step(StepAction.Tap())
        val markers = scenario(tap).markers()

        assertEquals(tap.id, markers.single().stepId)
    }

    @Test
    fun `dragging a tap moves its target`() {
        val original = scenario(step(StepAction.Tap(), at = ScreenPoint(10, 10)))
        val marker = original.markers().single()

        val moved = original.withMarkerMoved(marker, ScreenPoint(700, 900))

        assertEquals(
            ScreenPoint(700, 900),
            moved.steps
                .single()
                .target.point,
        )
    }

    @Test
    fun `dragging the arrow of a swipe moves the destination, not the start`() {
        val swipe = StepAction.Swipe(destination = ScreenPoint(540, 400), durationMilliseconds = 250)
        val original = scenario(step(swipe, at = ScreenPoint(540, 1800)))
        val end = original.markers().first { it.role == MarkerRole.SWIPE_END }

        val moved = original.withMarkerMoved(end, ScreenPoint(200, 200))

        val action = moved.steps.single().action as StepAction.Swipe
        assertEquals(ScreenPoint(200, 200), action.destination)
        assertEquals(
            ScreenPoint(540, 1800),
            moved.steps
                .single()
                .target.point,
        )
    }

    @Test
    fun `dragging the number of a swipe moves the start, not the destination`() {
        val swipe = StepAction.Swipe(destination = ScreenPoint(540, 400), durationMilliseconds = 250)
        val original = scenario(step(swipe, at = ScreenPoint(540, 1800)))
        val start = original.markers().first { it.role == MarkerRole.SWIPE_START }

        val moved = original.withMarkerMoved(start, ScreenPoint(200, 200))

        val action = moved.steps.single().action as StepAction.Swipe
        assertEquals(
            ScreenPoint(200, 200),
            moved.steps
                .single()
                .target.point,
        )
        assertEquals(ScreenPoint(540, 400), action.destination)
    }

    @Test
    fun `dragging one contact of a multi-touch leaves the others alone`() {
        val paths =
            listOf(
                GesturePath(ScreenPoint(100, 100), ScreenPoint(100, 100), 100),
                GesturePath(ScreenPoint(300, 300), ScreenPoint(300, 300), 100),
            )
        val original = scenario(step(StepAction.MultiTouch(paths)))
        val second = original.markers()[1]

        val moved = original.withMarkerMoved(second, ScreenPoint(900, 900))

        val action = moved.steps.single().action as StepAction.MultiTouch
        assertEquals(ScreenPoint(100, 100), action.paths[0].start)
        assertEquals(ScreenPoint(900, 900), action.paths[1].start)
    }

    @Test
    fun `dragging the far end of a travelling contact moves that end`() {
        val paths = listOf(GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 100))
        val original = scenario(step(StepAction.MultiTouch(paths)))
        val end = original.markers().first { it.role == MarkerRole.TOUCH_END }

        val moved = original.withMarkerMoved(end, ScreenPoint(900, 900))

        val action = moved.steps.single().action as StepAction.MultiTouch
        assertEquals(ScreenPoint(100, 100), action.paths.single().start)
        assertEquals(ScreenPoint(900, 900), action.paths.single().end)
    }

    @Test
    fun `dragging a marker whose step is gone changes nothing`() {
        val original = scenario(step(StepAction.Tap(), at = ScreenPoint(10, 10)))
        val stale = original.markers().single().copy(stepId = java.util.UUID.randomUUID())

        assertEquals(original, original.withMarkerMoved(stale, ScreenPoint(700, 900)))
    }

    @Test
    fun `every other step is left untouched by a drag`() {
        val first = step(StepAction.Tap(), at = ScreenPoint(10, 10))
        val second = step(StepAction.Tap(), at = ScreenPoint(20, 20))
        val original = scenario(first, second)

        val moved = original.withMarkerMoved(original.markers()[1], ScreenPoint(900, 900))

        assertEquals(ScreenPoint(10, 10), moved.steps[0].target.point)
        assertEquals(ScreenPoint(900, 900), moved.steps[1].target.point)
    }
}
