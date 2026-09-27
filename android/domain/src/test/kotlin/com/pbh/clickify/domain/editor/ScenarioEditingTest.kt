package com.pbh.clickify.domain.editor

import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScenarioEditingTest {
    private val portrait =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    private val landscape = portrait.copy(widthPixels = 2400, heightPixels = 1080, rotation = ScreenRotation.LANDSCAPE_LEFT)

    private fun step(
        action: StepAction = StepAction.Tap(),
        id: UUID = UUID.randomUUID(),
    ) = Step(id = id, action = action, target = StepTarget(ScreenPoint(100, 100)))

    private fun scenario(vararg steps: Step) = Scenario(name = "Test", steps = steps.toList())

    @Test
    fun `a new step is a tap, because a tap needs no further decision to be valid`() {
        val added = newStep(ScreenPoint(300, 400))

        assertEquals(StepAction.Tap(), added.action)
        assertEquals(ScreenPoint(300, 400), added.target.point)
    }

    @Test
    fun `the screen profile is captured when the scenario gains its first marker`() {
        val empty = scenario()
        assertNull(empty.screenProfile)

        assertEquals(portrait, empty.withStepAdded(newStep(ScreenPoint(1, 1)), portrait).screenProfile)
    }

    @Test
    fun `the captured profile is kept, not re-captured on the next edit`() {
        val built = scenario().withStepAdded(newStep(ScreenPoint(1, 1)), portrait)

        // SM-15 is where a rotated phone gets explained. Overwriting the profile here would swap
        // it for a screen the coordinates were never measured against, and the mismatch would
        // never be reported at all.
        assertEquals(portrait, built.withStepAdded(newStep(ScreenPoint(2, 2)), landscape).screenProfile)
    }

    @Test
    fun `a scenario of steps that touch nothing never captures a profile`() {
        val scenario =
            scenario()
                .withStepAdded(step(StepAction.Global(GlobalActionKind.BACK)), portrait)
                .withStepAdded(step(StepAction.SetText("hello")), portrait)

        assertNull(scenario.screenProfile)
    }

    @Test
    fun `changing a setText step to a tap captures the profile`() {
        val text = step(StepAction.SetText("hello"))
        val scenario = scenario().withStepAdded(text, portrait)
        assertNull(scenario.screenProfile)

        val tapped = scenario.withStepReplaced(text.copy(action = StepAction.Tap()), portrait)

        assertEquals(portrait, tapped.screenProfile)
    }

    @Test
    fun `removing the last marker forgets the screen profile`() {
        val only = step()
        val scenario = scenario().withStepAdded(only, portrait)
        assertNotNull(scenario.screenProfile)

        // Otherwise a scenario that touches the screen nowhere would still refuse to run on a
        // rotated phone, which is a warning about a danger that no longer exists.
        assertNull(scenario.withStepRemoved(only.id).screenProfile)
    }

    @Test
    fun `changing the last marker away to a global action forgets the screen profile`() {
        val only = step()
        val scenario = scenario().withStepAdded(only, portrait)

        val replaced = scenario.withStepReplaced(only.copy(action = StepAction.Global(GlobalActionKind.HOME)), portrait)

        assertNull(replaced.screenProfile)
    }

    @Test
    fun `replacing a step leaves the others and the order alone`() {
        val first = step()
        val middle = step()
        val last = step()
        val scenario = scenario(first, middle, last)

        val replaced = scenario.withStepReplaced(middle.copy(repeatCount = 9), portrait)

        assertEquals(listOf(first.id, middle.id, last.id), replaced.steps.map { it.id })
        assertEquals(listOf(1, 9, 1), replaced.steps.map { it.repeatCount })
    }

    @Test
    fun `a preview swaps the step in without touching the screen profile`() {
        val only = step()
        val scenario = scenario().withStepAdded(only, portrait)

        // OV-21: an unsaved draft draws its own Markers, and must not be able to drop or capture
        // the profile on the way — that only happens when the user saves.
        val previewed = scenario.previewing(only.copy(action = StepAction.Global(GlobalActionKind.HOME)))

        assertEquals(portrait, previewed.screenProfile)
        assertEquals(0, previewed.markerCount)
        assertEquals(portrait, scenario.screenProfile)
    }

    @Test
    fun `a preview of a step that is not there changes nothing`() {
        val scenario = scenario(step(), step())

        assertEquals(scenario, scenario.previewing(step()))
    }

    @Test
    fun `replacing a step that is not there changes nothing`() {
        val scenario = scenario(step(), step())

        assertEquals(scenario.steps, scenario.withStepReplaced(step(), portrait).steps)
    }

    @Test
    fun `moving a step changes the order`() {
        val first = step()
        val second = step()
        val third = step()

        val moved = scenario(first, second, third).withStepMoved(third.id, by = -1)

        assertEquals(listOf(first.id, third.id, second.id), moved.steps.map { it.id })
    }

    @Test
    fun `moving a step past either end does nothing`() {
        val first = step()
        val last = step()
        val scenario = scenario(first, last)

        assertEquals(scenario, scenario.withStepMoved(first.id, by = -1))
        assertEquals(scenario, scenario.withStepMoved(last.id, by = 1))
        assertEquals(scenario, scenario.withStepMoved(UUID.randomUUID(), by = 1))
    }

    @Test
    fun `removing a step that is not there changes nothing`() {
        val scenario = scenario(step(), step())

        assertEquals(scenario, scenario.withStepRemoved(UUID.randomUUID()))
    }
}
