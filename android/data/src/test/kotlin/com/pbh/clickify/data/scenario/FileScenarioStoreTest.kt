package com.pbh.clickify.data.scenario

import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.model.DomainError
import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.scenario.GesturePath
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.RunCount
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.scenario.ScreenPoint
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import com.pbh.clickify.domain.scenario.Step
import com.pbh.clickify.domain.scenario.StepAction
import com.pbh.clickify.domain.scenario.StepTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileScenarioStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private val dispatchers =
        object : DispatcherProvider {
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }

    private fun store(): FileScenarioStore = FileScenarioStore(folder.root, dispatchers)

    private val profile =
        ScreenProfile(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 440,
            rotation = ScreenRotation.PORTRAIT,
        )

    private fun scenario(name: String = "Daily quest") =
        Scenario(
            name = name,
            steps =
                listOf(
                    Step(
                        action = StepAction.Tap(holdMilliseconds = 40),
                        target = StepTarget(ScreenPoint(820, 410)),
                        repeatCount = 3,
                        delayMillisecondsAfter = 200,
                    ),
                    Step(
                        action =
                            StepAction.Swipe(
                                destination = ScreenPoint(540, 600),
                                durationMilliseconds = 250,
                            ),
                        target = StepTarget(ScreenPoint(540, 1800)),
                    ),
                    Step(
                        action = StepAction.Global(GlobalActionKind.BACK),
                        target = StepTarget(ScreenPoint(0, 0)),
                    ),
                    Step(
                        action = StepAction.SetText("50"),
                        target = StepTarget(ScreenPoint(0, 0)),
                    ),
                ),
            runCount = RunCount.Times(20),
            screenProfile = profile,
        )

    private fun writeRaw(
        id: UUID,
        json: String,
    ) {
        File(folder.root, id.toString())
            .apply { mkdirs() }
            .resolve(FileScenarioStore.SCENARIO_FILE)
            .writeText(json)
    }

    @Test
    fun `what is written is what comes back`() =
        runTest(dispatcher) {
            val store = store()
            val original = scenario()

            assertIs<AppResult.Success<Unit>>(store.save(original))

            val loaded = store.load(original.id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertEquals(original, loaded.data.scenario)
            assertFalse(loaded.data.readOnly)
        }

    @Test
    fun `the file is laid out one directory per scenario`() =
        runTest(dispatcher) {
            val original = scenario()
            store().save(original)

            val directory = File(folder.root, original.id.toString())

            assertTrue(directory.isDirectory)
            assertTrue(File(directory, "scenario.json").isFile)
        }

    @Test
    fun `no temporary file survives a write`() =
        runTest(dispatcher) {
            val original = scenario()
            store().save(original)

            val leftovers =
                File(folder.root, original.id.toString())
                    .listFiles()
                    .orEmpty()
                    .filter { it.name.endsWith(".tmp") }

            assertEquals(emptyList(), leftovers)
        }

    @Test
    fun `an unreadable scenario is skipped and the rest still load`() =
        runTest(dispatcher) {
            val store = store()
            store.save(scenario(name = "Good one"))
            writeRaw(UUID.randomUUID(), "{ this is not json")

            val loaded = store.refresh()

            assertEquals(listOf("Good one"), loaded.map { it.scenario.name })
        }

    @Test
    fun `a directory with no scenario file is not a scenario`() =
        runTest(dispatcher) {
            File(folder.root, "not-a-scenario").mkdirs()

            assertEquals(emptyList(), store().refresh())
        }

    @Test
    fun `a file from a newer build loads read-only with no steps`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(
                id,
                """
                {
                  "schemaVersion": 99,
                  "id": "$id",
                  "name": "From the future",
                  "steps": [ { "what": "this build cannot read" } ]
                }
                """.trimIndent(),
            )

            val loaded = store().load(id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertTrue(loaded.data.readOnly)
            assertEquals(99, loaded.data.fileSchemaVersion)
            assertEquals("From the future", loaded.data.scenario.name)
            assertEquals(emptyList(), loaded.data.scenario.steps)
        }

    @Test
    fun `a file from a newer build cannot be duplicated`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(id, """{ "schemaVersion": 99, "id": "$id", "name": "From the future" }""")

            val result = store().duplicate(id)

            assertIs<AppResult.Failure>(result)
            assertEquals(DomainError.ScenarioTooNew(99, StoredScenario.CURRENT_SCHEMA_VERSION), result.error)
        }

    @Test
    fun `a file from a newer build can still be deleted`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(id, """{ "schemaVersion": 99, "id": "$id", "name": "From the future" }""")

            assertIs<AppResult.Success<Unit>>(store().delete(id))
            assertFalse(File(folder.root, id.toString()).exists())
        }

    @Test
    fun `duplicating copies the whole directory, not just the json`() =
        runTest(dispatcher) {
            val store = store()
            val original = scenario()
            store.save(original)
            File(folder.root, "${original.id}/templates")
                .apply { mkdirs() }
                .resolve("button.png")
                .writeBytes(byteArrayOf(1, 2, 3))

            val copy = store.duplicate(original.id)

            assertIs<AppResult.Success<StoredScenario>>(copy)
            val copied = File(folder.root, "${copy.data.scenario.id}/templates/button.png")
            assertTrue(copied.isFile)
            assertEquals(3, copied.length().toInt())
        }

    @Test
    fun `a duplicate is a different scenario with the same steps`() =
        runTest(dispatcher) {
            val store = store()
            val original = scenario()
            store.save(original)

            val copy = store.duplicate(original.id)

            assertIs<AppResult.Success<StoredScenario>>(copy)
            assertFalse(copy.data.scenario.id == original.id)
            assertEquals(original.steps, copy.data.scenario.steps)
        }

    @Test
    fun `deleting takes the whole directory`() =
        runTest(dispatcher) {
            val store = store()
            val original = scenario()
            store.save(original)
            File(folder.root, "${original.id}/templates")
                .apply { mkdirs() }
                .resolve("button.png")
                .writeBytes(byteArrayOf(1))

            assertIs<AppResult.Success<Unit>>(store.delete(original.id))
            assertFalse(File(folder.root, original.id.toString()).exists())
        }

    @Test
    fun `loading something that was never there fails rather than inventing it`() =
        runTest(dispatcher) {
            val result = store().load(UUID.randomUUID())

            assertIs<AppResult.Failure>(result)
            assertIs<DomainError.ScenarioUnreadable>(result.error)
        }

    @Test
    fun `an out-of-range value is clamped rather than losing the file`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(
                id,
                """
                {
                  "schemaVersion": 1,
                  "id": "$id",
                  "name": "Rough edges",
                  "repeat": 0,
                  "countdownMilliseconds": 999999,
                  "steps": [
                    {
                      "id": "${UUID.randomUUID()}",
                      "action": { "kind": "tap", "holdMilliseconds": 0 },
                      "target": { "kind": "point", "x": 10, "y": 10 },
                      "repeat": -4,
                      "delayMillisecondsAfter": -1
                    }
                  ]
                }
                """.trimIndent(),
            )

            val loaded = store().load(id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertEquals(RunCount.Times(1), loaded.data.scenario.runCount)
            assertEquals(60_000, loaded.data.scenario.countdownMilliseconds)
            assertEquals(
                1,
                loaded.data.scenario.steps
                    .single()
                    .repeatCount,
            )
            assertEquals(
                0,
                loaded.data.scenario.steps
                    .single()
                    .delayMillisecondsAfter,
            )
        }

    @Test
    fun `fields this build does not know about are ignored`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(
                id,
                """
                {
                  "schemaVersion": 1,
                  "id": "$id",
                  "name": "Tolerant",
                  "somethingElse": { "nested": true },
                  "steps": []
                }
                """.trimIndent(),
            )

            val loaded = store().load(id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertEquals("Tolerant", loaded.data.scenario.name)
        }

    @Test
    fun `a scenario with no name is repaired rather than rejected`() =
        runTest(dispatcher) {
            val id = UUID.randomUUID()
            writeRaw(id, """{ "schemaVersion": 1, "id": "$id", "steps": [] }""")

            val loaded = store().load(id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertEquals(Scenario.REPAIRED_NAME, loaded.data.scenario.name)
        }

    @Test
    fun `the list is published to whoever is watching`() =
        runTest(dispatcher) {
            val store = store()
            assertEquals(emptyList(), store.refresh())

            store.save(scenario(name = "Beta"))
            store.save(scenario(name = "alpha"))

            // Sorted by name, case-insensitively, so the list does not reorder itself on a capital.
            assertEquals(listOf("alpha", "Beta"), store.refresh().map { it.scenario.name })
        }

    @Test
    fun `a multi-touch scenario survives the round trip with every path`() =
        runTest(dispatcher) {
            val store = store()
            val paths =
                listOf(
                    GesturePath(ScreenPoint(100, 100), ScreenPoint(200, 200), 120),
                    GesturePath(ScreenPoint(300, 300), ScreenPoint(400, 400), 180),
                )
            val original =
                Scenario(
                    name = "Chord",
                    steps =
                        listOf(
                            Step(
                                action = StepAction.MultiTouch(paths),
                                target = StepTarget(ScreenPoint(100, 100)),
                            ),
                        ),
                    screenProfile = profile,
                )

            store.save(original)
            val loaded = store.load(original.id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            val action =
                loaded.data.scenario.steps
                    .single()
                    .action
            assertIs<StepAction.MultiTouch>(action)
            assertEquals(paths, action.paths)
        }

    @Test
    fun `an empty scenario has no screen profile on disk either`() =
        runTest(dispatcher) {
            val store = store()
            val empty = Scenario(name = "New")
            store.save(empty)

            val loaded = store.load(empty.id)

            assertIs<AppResult.Success<StoredScenario>>(loaded)
            assertNull(loaded.data.scenario.screenProfile)
        }
}
