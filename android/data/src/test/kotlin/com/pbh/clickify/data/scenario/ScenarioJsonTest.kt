package com.pbh.clickify.data.scenario

import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The file format is part of the specification (FS-5 to FS-10), so these assert on the text
 * itself. A serialiser default that changes the spelling should break a test, not a user's file.
 */
class ScenarioJsonTest {
    private val json = FileScenarioStore.defaultJson

    private fun encode(scenario: Scenario): String = json.encodeToString(scenario.toDto())

    private fun step(action: StepAction) =
        Step(
            id = UUID.fromString("0c22a1f0-0000-4000-8000-000000000001"),
            action = action,
            target = StepTarget(ScreenPoint(820, 410)),
        )

    @Test
    fun `repeat is a bare integer, not an object`() {
        val text = encode(Scenario(name = "Ten", runCount = RunCount.Times(10)))

        assertTrue(text.contains(Regex("""^\s*"repeat": 10,""", RegexOption.MULTILINE)), text)
    }

    @Test
    fun `until stopped is a bare string`() {
        val text = encode(Scenario(name = "Forever", runCount = RunCount.UntilStopped))

        assertTrue(text.contains(""""repeat": "until-stopped""""), text)
    }

    @Test
    fun `coordinates are separate fields, never an array`() {
        val text = encode(Scenario(name = "Tap", steps = listOf(step(StepAction.Tap()))))

        assertTrue(text.contains(""""x": 820"""), text)
        assertTrue(text.contains(""""y": 410"""), text)
        assertTrue(!text.contains("[820"), text)
    }

    @Test
    fun `every action spells its kind out`() {
        val actions =
            listOf(
                StepAction.Tap() to "tap",
                StepAction.Swipe(ScreenPoint(1, 2), 100) to "swipe",
                StepAction.Global(GlobalActionKind.HOME) to "globalAction",
                StepAction.SetText("hi") to "setText",
            )

        actions.forEach { (action, kind) ->
            val text = encode(Scenario(name = "One", steps = listOf(step(action))))
            assertTrue(text.contains(""""kind": "$kind""""), "$kind missing from:\n$text")
        }
    }

    @Test
    fun `a global action is named, never numbered`() {
        val text = encode(Scenario(name = "Back", steps = listOf(step(StepAction.Global(GlobalActionKind.BACK)))))

        assertTrue(text.contains(""""action": "BACK""""), text)
    }

    @Test
    fun `the schema version is written every time`() {
        val text = encode(Scenario(name = "Any"))

        // TP-28: the version written is the lowest that can express the Scenario, and a Scenario
        // with nothing in it needs the oldest. RecognitionJsonTest holds the other half.
        assertTrue(text.contains(""""schemaVersion": 1"""), text)
    }

    @Test
    fun `a step whose action ignores its target still writes one`() {
        val text = encode(Scenario(name = "Text", steps = listOf(step(StepAction.SetText("50")))))

        assertTrue(text.contains(""""kind": "point""""), text)
    }

    @Test
    fun `a repeat this build cannot make sense of becomes one`() {
        val decoded = json.decodeFromString(RepeatSerializer, """"every other tuesday"""")

        assertEquals(RepeatDto.Count(1), decoded)
    }
}
