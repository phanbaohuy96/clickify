package com.pbh.clickify.data.scenario

import com.pbh.clickify.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TP-27: `<scenario>/templates/<template>.png`, and the sweep that keeps it honest. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileTemplateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private val dispatchers =
        object : DispatcherProvider {
            override val default: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val main: CoroutineDispatcher = dispatcher
        }

    private fun store(): FileTemplateStore = FileTemplateStore(folder.root, dispatchers)

    private val scenarioId = UUID.randomUUID()

    private fun bytes(marker: Byte) = ByteArray(8) { marker }

    @Test
    fun `a template written is a template read back`() =
        runTest {
            val store = store()
            val id = UUID.randomUUID()

            assertTrue(store.write(scenarioId, id, bytes(7)))

            assertContentEquals(bytes(7), store.read(scenarioId, id))
        }

    /** FS-2: inside the Scenario's own directory, which is what makes FS-3 carry it for free. */
    @Test
    fun `a template lives beside its own scenario`() =
        runTest {
            val id = UUID.randomUUID()

            store().write(scenarioId, id, bytes(1))

            assertTrue(File(folder.root, "$scenarioId/templates/$id.png").isFile)
        }

    @Test
    fun `a template that was never written reads as nothing`() =
        runTest {
            assertNull(store().read(scenarioId, UUID.randomUUID()))
        }

    @Test
    fun `the ids are every template this scenario has pixels for`() =
        runTest {
            val store = store()
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            store.write(scenarioId, first, bytes(1))
            store.write(scenarioId, second, bytes(2))
            store.write(UUID.randomUUID(), UUID.randomUUID(), bytes(3))

            assertEquals(setOf(first, second), store.ids(scenarioId))
        }

    @Test
    fun `a file that is not a template is not counted as one`() =
        runTest {
            val store = store()
            val directory = File(folder.root, "$scenarioId/templates").apply { mkdirs() }
            File(directory, "notes.txt").writeText("hello")
            File(directory, "not-a-uuid.png").writeText("hello")

            assertEquals(emptySet(), store.ids(scenarioId))
        }

    @Test
    fun `sweeping keeps what is still referred to and deletes the rest`() =
        runTest {
            val store = store()
            val kept = UUID.randomUUID()
            val orphan = UUID.randomUUID()
            store.write(scenarioId, kept, bytes(1))
            store.write(scenarioId, orphan, bytes(2))

            store.sweep(scenarioId, keep = setOf(kept))

            assertEquals(setOf(kept), store.ids(scenarioId))
            assertFalse(File(folder.root, "$scenarioId/templates/$orphan.png").exists())
        }

    @Test
    fun `sweeping a scenario with no templates does nothing at all`() =
        runTest {
            store().sweep(scenarioId, keep = emptySet())

            assertEquals(emptySet(), store().ids(scenarioId))
        }

    @Test
    fun `writing the same template again replaces it`() =
        runTest {
            val store = store()
            val id = UUID.randomUUID()

            store.write(scenarioId, id, bytes(1))
            store.write(scenarioId, id, bytes(9))

            assertContentEquals(bytes(9), store.read(scenarioId, id))
        }

    /** FS-13's rule, for a picture: nothing half-written is ever left where a reader will find it. */
    @Test
    fun `no temporary file is left behind`() =
        runTest {
            store().write(scenarioId, UUID.randomUUID(), bytes(1))

            val leftovers = File(folder.root, "$scenarioId/templates").listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
            assertTrue(leftovers.isEmpty(), "left behind $leftovers")
        }
}
