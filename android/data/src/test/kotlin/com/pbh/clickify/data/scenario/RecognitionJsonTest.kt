package com.pbh.clickify.data.scenario

import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.Guard
import com.pbh.clickify.domain.scenario.OnTimeout
import com.pbh.clickify.domain.scenario.Presence
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import com.pbh.clickify.domain.scenario.TemplateSearch
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TP-19, TP-21, TP-24, TP-28: recognition on disk. */
class RecognitionJsonTest {
    private val json = FileScenarioStore.defaultJson
    private val templateId = UUID.fromString("0c22a1f0-0000-4000-8000-0000000000aa")

    private fun encode(scenario: Scenario): String = json.encodeToString(scenario.toDto())

    private fun decode(text: String): Scenario = json.decodeFromString(ScenarioDto.serializer(), text).toDomain()

    private val search =
        TemplateSearch(
            templateId = templateId,
            threshold = 0.82,
            region = ScreenRegion(left = 10, top = 20, right = 310, bottom = 220),
            waitMilliseconds = 8_000,
            onTimeout = OnTimeout.SKIP_STEP,
        )

    private fun step(
        search: TemplateSearch? = null,
        guard: Guard? = null,
        action: StepAction = StepAction.Tap(),
    ) = Step(
        id = UUID.fromString("0c22a1f0-0000-4000-8000-000000000001"),
        action = action,
        target = StepTarget(ScreenPoint(820, 410)),
        search = search,
        guard = guard,
    )

    @Test
    fun `a search survives a round trip intact`() {
        val original = Scenario(name = "Find", steps = listOf(step(search = search)))

        val returned = decode(encode(original)).steps.single().search

        assertEquals(search, assertNotNull(returned))
    }

    @Test
    fun `a guard survives a round trip intact`() {
        val guard = Guard(search = search, expects = Presence.ABSENT)
        val original = Scenario(name = "Wait", steps = listOf(step(guard = guard)))

        assertEquals(guard, assertNotNull(decode(encode(original)).steps.single().guard))
    }

    /** FS-6 again: the file reads as prose, so a timeout is a name and not a number. */
    @Test
    fun `a timeout and a condition are spelled out`() {
        val text = encode(Scenario(name = "Find", steps = listOf(step(search = search, guard = Guard(search)))))

        assertTrue(text.contains(""""onTimeout": "SKIP_STEP""""), text)
        assertTrue(text.contains(""""expects": "PRESENT""""), text)
    }

    @Test
    fun `a search region is four named sides, never an array`() {
        val text = encode(Scenario(name = "Find", steps = listOf(step(search = search))))

        assertTrue(text.contains(""""left": 10"""), text)
        assertTrue(text.contains(""""bottom": 220"""), text)
    }

    /**
     * TP-28: the version written is the lowest that can express the Scenario.
     *
     * Writing 2 for a sequence of plain taps would make every Scenario on the phone unreadable to
     * the previous build in exchange for nothing.
     */
    @Test
    fun `a scenario without recognition is still a version one file`() {
        val text = encode(Scenario(name = "Taps", steps = listOf(step())))

        assertTrue(text.contains(""""schemaVersion": 1"""), text)
    }

    @Test
    fun `a scenario with a search is a version two file`() {
        val text = encode(Scenario(name = "Find", steps = listOf(step(search = search))))

        assertTrue(text.contains(""""schemaVersion": 2"""), text)
    }

    @Test
    fun `a scenario with a guard is a version two file`() {
        val text = encode(Scenario(name = "Wait", steps = listOf(step(guard = Guard(search)))))

        assertTrue(text.contains(""""schemaVersion": 2"""), text)
    }

    /**
     * TP-22: a search on an Action that ignores its Target is ignored, and does not raise the
     * version — an older build reading this file would behave identically.
     */
    @Test
    fun `a search on a global action does not make the file newer`() {
        val scenario =
            Scenario(
                name = "Back",
                steps = listOf(step(search = search, action = StepAction.Global(GlobalActionKind.BACK))),
            )

        assertTrue(encode(scenario).contains(""""schemaVersion": 1"""), encode(scenario))
    }

    /** FS-12: a name from a newer build falls back to the safest member rather than throwing. */
    @Test
    fun `an unknown timeout falls back to stopping, which is the one the user can see`() {
        val text =
            """
            {
              "schemaVersion": 2,
              "id": "0c22a1f0-0000-4000-8000-000000000009",
              "name": "Odd",
              "steps": [
                {
                  "id": "0c22a1f0-0000-4000-8000-000000000001",
                  "action": { "kind": "tap" },
                  "target": { "kind": "point", "x": 1, "y": 2 },
                  "search": { "template": "$templateId", "onTimeout": "RETRY_FOREVER" }
                }
              ]
            }
            """.trimIndent()

        assertEquals(OnTimeout.STOP_SCENARIO, assertNotNull(decode(text).steps.single().search).onTimeout)
    }

    /** TP-30: one impossible number must not cost the user the Scenario (SM-16's rule). */
    @Test
    fun `a threshold and a wait outside their range are clamped rather than refused`() {
        val text =
            """
            {
              "schemaVersion": 2,
              "id": "0c22a1f0-0000-4000-8000-000000000009",
              "name": "Odd",
              "steps": [
                {
                  "id": "0c22a1f0-0000-4000-8000-000000000001",
                  "action": { "kind": "tap" },
                  "target": { "kind": "point", "x": 1, "y": 2 },
                  "search": { "template": "$templateId", "threshold": 7.5, "waitMilliseconds": 99999999 }
                }
              ]
            }
            """.trimIndent()

        val decoded = assertNotNull(decode(text).steps.single().search)
        assertEquals(1.0, decoded.threshold)
        assertEquals(600_000, decoded.waitMilliseconds)
    }

    /** FS-12: a schema 1 file has none of these fields, and reads as a Scenario with none. */
    @Test
    fun `an older file simply has no search and no guard`() {
        val text =
            """
            {
              "schemaVersion": 1,
              "id": "0c22a1f0-0000-4000-8000-000000000009",
              "name": "Old",
              "steps": [
                {
                  "id": "0c22a1f0-0000-4000-8000-000000000001",
                  "action": { "kind": "tap" },
                  "target": { "kind": "point", "x": 1, "y": 2 }
                }
              ]
            }
            """.trimIndent()

        val step = decode(text).steps.single()
        assertNull(step.search)
        assertNull(step.guard)
    }
}
