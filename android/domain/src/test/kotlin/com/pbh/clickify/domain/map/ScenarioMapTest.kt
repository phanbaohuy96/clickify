package com.pbh.clickify.domain.map

import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.TemplateSearch
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MP-4 to MP-7: what the Scenario map lays out, and where. Drawing it is not tested here. */
class ScenarioMapTest {
    private val profile = ScreenProfile(1080, 1920, 420, ScreenRotation.PORTRAIT)

    private fun tap(
        x: Int,
        y: Int,
        search: TemplateSearch? = null,
        guard: Guard? = null,
    ) = Step(action = StepAction.Tap(), target = StepTarget(ScreenPoint(x, y)), search = search, guard = guard)

    private fun global() = Step(action = StepAction.Global(GlobalActionKind.BACK), target = StepTarget(ScreenPoint(0, 0)))

    private fun scenario(vararg steps: Step) = Scenario(name = "s", steps = steps.toList(), screenProfile = profile)

    @Test
    fun `taps in order give arrows one to two to three`() {
        val map = scenario(tap(100, 100), tap(200, 300), tap(400, 500)).map()

        assertEquals(listOf(1 to 2, 2 to 3), map.arrows.map { it.fromStep to it.toStep })
        assertEquals(ScreenPoint(100, 100), map.arrows[0].from)
        assertEquals(ScreenPoint(200, 300), map.arrows[0].to)
        assertEquals(profile, map.frame)
        assertEquals(3, map.nodes.size)
    }

    @Test
    fun `a swipe and a multiTouch are anchored at their target and keep their markers`() {
        val swipe =
            Step(
                action = StepAction.Swipe(ScreenPoint(900, 900), 300),
                target = StepTarget(ScreenPoint(100, 100)),
            )
        val multi =
            Step(
                action =
                    StepAction.MultiTouch(
                        listOf(
                            GesturePath(ScreenPoint(300, 300), ScreenPoint(300, 500), 200),
                            GesturePath(ScreenPoint(600, 300), ScreenPoint(600, 500), 200),
                        ),
                    ),
                target = StepTarget(ScreenPoint(300, 300)),
            )
        val map = scenario(swipe, multi).map()

        assertEquals(listOf(ScreenPoint(100, 100), ScreenPoint(300, 300)), map.nodes.map { it.point })
        assertEquals(ScreenPoint(100, 100), map.arrows.single().from)
        assertEquals(ScreenPoint(300, 300), map.arrows.single().to)
        assertEquals(6, map.markers.size)
    }

    @Test
    fun `a global between two taps sits on the segment`() {
        val map = scenario(tap(100, 100), global(), tap(300, 500)).map()

        val chip = map.chips.single()
        assertEquals(2, chip.stepNumber)
        assertEquals(ScreenPoint(200, 300), chip.position)
        assertEquals(1, chip.previousAnchorStep)
        assertEquals(3, chip.nextAnchorStep)
        assertEquals(listOf(1 to 3), map.arrows.map { it.fromStep to it.toStep })
    }

    @Test
    fun `two in a row are evenly spaced`() {
        val map = scenario(tap(0, 0), global(), global(), tap(300, 600)).map()

        assertEquals(listOf(ScreenPoint(100, 200), ScreenPoint(200, 400)), map.chips.map { it.position })
    }

    @Test
    fun `a pointless step first sits before the first dot and last after the last`() {
        val map = scenario(global(), tap(500, 500), tap(600, 700), global()).map()

        val first = map.chips.first { it.stepNumber == 1 }
        assertEquals(ScreenPoint(500, 500 - 96), first.position)
        assertNull(first.previousAnchorStep)
        val last = map.chips.first { it.stepNumber == 4 }
        assertEquals(ScreenPoint(600, 700 + 96), last.position)
        assertNull(last.nextAnchorStep)
    }

    @Test
    fun `chips around coincident anchors hang below the dot instead of on a line of no length`() {
        val map = scenario(tap(500, 500), global(), tap(500, 500)).map()

        assertEquals(ScreenPoint(500, 596), map.chips.single().position)
        assertTrue(map.arrows.isEmpty())
    }

    @Test
    fun `all pointless steps give a centred column in the fallback frame`() {
        val map = Scenario(name = "s", steps = listOf(global(), global(), global())).map(profile)

        assertNull(map.frame)
        assertTrue(map.nodes.isEmpty())
        assertEquals(listOf(540, 540, 540), map.chips.map { it.position.x })
        assertEquals(listOf(480, 960, 1440), map.chips.map { it.position.y })
    }

    @Test
    fun `coincident anchors merge into one dot with every number`() {
        val map = scenario(tap(1, 1), tap(50, 50), tap(2, 2), tap(3, 3), tap(50, 50)).map()

        val merged = map.nodes.single { it.point == ScreenPoint(50, 50) }
        assertEquals(listOf(2, 5), merged.stepNumbers)
        assertEquals(2, merged.stepIds.size)
        assertEquals(4, map.nodes.size)
        assertEquals(4, map.arrows.size)
    }

    @Test
    fun `a guard and an effective search are listed`() {
        val find = TemplateSearch(UUID.randomUUID())
        val wait = Guard(TemplateSearch(UUID.randomUUID()), Presence.ABSENT)
        val map = scenario(tap(10, 20, search = find), tap(30, 40, guard = wait)).map()

        assertEquals(listOf(find.templateId), map.templates.map { it.templateId })
        assertEquals(ScreenPoint(10, 20), map.templates.single().center)
        assertEquals(listOf(2 to Presence.ABSENT), map.guards.map { it.stepNumber to it.presence })
    }

    @Test
    fun `setText with a stale search lists no template`() {
        val stale =
            Step(
                action = StepAction.SetText("hi"),
                target = StepTarget(ScreenPoint(0, 0)),
                search = TemplateSearch(UUID.randomUUID()),
            )

        assertTrue(scenario(tap(1, 1), stale).map().templates.isEmpty())
    }

    @Test
    fun `skip and stop outcomes are told apart and bounded by their own neighbours`() {
        val skip = TemplateSearch(UUID.randomUUID(), onTimeout = OnTimeout.SKIP_STEP)
        val stop = Guard(TemplateSearch(UUID.randomUUID(), onTimeout = OnTimeout.STOP_SCENARIO))
        val map = scenario(tap(1, 1), tap(2, 2, search = skip), tap(3, 3, guard = stop), global(), tap(4, 4)).map()

        val (first, second) = map.giveUps
        assertEquals(GiveUpKind.SEARCH, first.kind)
        assertEquals(OnTimeout.SKIP_STEP, first.outcome)
        assertEquals(ScreenPoint(1, 1), first.before)
        assertEquals(ScreenPoint(3, 3), first.after)
        assertEquals(GiveUpKind.GUARD, second.kind)
        assertEquals(OnTimeout.STOP_SCENARIO, second.outcome)
        assertEquals(ScreenPoint(3, 3), second.at)
    }

    @Test
    fun `a guard on a pointless step is placed at its chip`() {
        val guarded = global().copy(guard = Guard(TemplateSearch(UUID.randomUUID())))
        val map = scenario(tap(0, 0), guarded, tap(200, 200)).map()

        assertEquals(map.chips.single().position, map.giveUps.single().at)
    }

    @Test
    fun `an empty scenario gives an empty map`() {
        val map = Scenario(name = "s").map()

        assertNull(map.frame)
        assertTrue(map.nodes.isEmpty() && map.chips.isEmpty() && map.arrows.isEmpty())
        assertTrue(map.markers.isEmpty() && map.templates.isEmpty() && map.guards.isEmpty() && map.giveUps.isEmpty())
    }
}
