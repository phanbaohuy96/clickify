package com.pbh.clickify.data.scenario

import android.util.Log
import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.model.DomainError
import com.pbh.clickify.domain.repository.ScenarioRepository
import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.scenario.Scenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * One directory per Scenario under [root] (FS-1, FS-2), and nothing else.
 *
 * Takes a plain [File] rather than a `Context` so the whole of this class is exercised by ordinary
 * JVM tests against a temporary directory — which is the tier the test plan puts storage in.
 */
class FileScenarioStore(
    private val root: File,
    private val dispatchers: DispatcherProvider,
    private val json: Json = defaultJson,
) : ScenarioRepository {
    private val state = MutableStateFlow<List<StoredScenario>>(emptyList())

    override fun observeScenarios(): Flow<List<StoredScenario>> = state.asStateFlow()

    /** Reads every Scenario off disk and publishes the result. Safe to call again at any time. */
    suspend fun refresh(): List<StoredScenario> =
        withContext(dispatchers.io) {
            val loaded =
                root
                    .listFiles { file -> file.isDirectory }
                    .orEmpty()
                    .mapNotNull { readOrSkip(it) }
                    .sortedBy { it.scenario.name.lowercase() }
            state.value = loaded
            loaded
        }

    override suspend fun load(id: UUID): AppResult<StoredScenario> =
        withContext(dispatchers.io) {
            val directory = directoryFor(id)
            when {
                !directory.isDirectory -> AppResult.Failure(DomainError.ScenarioUnreadable(id.toString()))
                else ->
                    readOrSkip(directory)
                        ?.let { AppResult.Success(it) }
                        ?: AppResult.Failure(DomainError.ScenarioUnreadable(id.toString()))
            }
        }

    override suspend fun save(scenario: Scenario): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val directory = directoryFor(scenario.id).apply { mkdirs() }
                writeAtomically(File(directory, SCENARIO_FILE), json.encodeToString(scenario.toDto()))
            }.fold(
                onSuccess = {
                    refresh()
                    AppResult.Success(Unit)
                },
                onFailure = {
                    Log.w(TAG, "could not write scenario ${scenario.id}", it)
                    AppResult.Failure(DomainError.ScenarioUnreadable(scenario.id.toString()))
                },
            )
        }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        withContext(dispatchers.io) {
            // FS-3: the whole directory, templates included. Deleting is allowed even for a file
            // this build cannot decode (FS-14), because it is something the user does on purpose.
            val removed = directoryFor(id).deleteRecursively()
            refresh()
            if (removed) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(DomainError.ScenarioUnreadable(id.toString()))
            }
        }

    override suspend fun duplicate(id: UUID): AppResult<StoredScenario> =
        withContext(dispatchers.io) {
            val source = directoryFor(id)
            val original = readOrSkip(source)
            when {
                original == null -> AppResult.Failure(DomainError.ScenarioUnreadable(id.toString()))

                // FS-14: the copy would carry the original's name, this build's schemaVersion and
                // no Steps — exactly what that requirement exists to prevent.
                original.readOnly ->
                    AppResult.Failure(
                        DomainError.ScenarioTooNew(
                            fileSchemaVersion = original.fileSchemaVersion,
                            supportedSchemaVersion = StoredScenario.CURRENT_SCHEMA_VERSION,
                        ),
                    )

                else -> {
                    val copy = original.scenario.copy(id = UUID.randomUUID())
                    val destination = directoryFor(copy.id)
                    runCatching {
                        source.copyRecursively(destination, overwrite = true)
                        writeAtomically(
                            File(destination, SCENARIO_FILE),
                            json.encodeToString(copy.toDto()),
                        )
                    }.fold(
                        onSuccess = {
                            refresh()
                            AppResult.Success(StoredScenario(copy))
                        },
                        onFailure = {
                            Log.w(TAG, "could not duplicate scenario $id", it)
                            destination.deleteRecursively()
                            AppResult.Failure(DomainError.ScenarioUnreadable(id.toString()))
                        },
                    )
                }
            }
        }

    private fun directoryFor(id: UUID) = File(root, id.toString())

    /** FS-11: a directory that cannot be read is skipped and logged, and the rest still load. */
    private fun readOrSkip(directory: File): StoredScenario? {
        val file = File(directory, SCENARIO_FILE)
        if (!file.isFile) return null
        return runCatching { decode(file.readText()) }
            .onFailure { Log.w(TAG, "skipping unreadable scenario at ${directory.name}", it) }
            .getOrNull()
    }

    private fun decode(text: String): StoredScenario {
        val root = json.parseToJsonElement(text) as JsonObject
        val version = root[SCHEMA_VERSION_FIELD]?.jsonPrimitive?.int ?: 0

        // FS-14: read the version before the body. A newer file keeps its name and its id and
        // loses its Steps, because the Steps are in the part this build cannot decode.
        if (version > StoredScenario.CURRENT_SCHEMA_VERSION) {
            return StoredScenario(
                scenario =
                    Scenario(
                        id = UUID.fromString(root[ID_FIELD]!!.jsonPrimitive.content),
                        name =
                            root[NAME_FIELD]
                                ?.jsonPrimitive
                                ?.content
                                ?.takeIf { it.isNotBlank() }
                                ?: Scenario.REPAIRED_NAME,
                    ),
                readOnly = true,
                fileSchemaVersion = version,
            )
        }

        return StoredScenario(
            scenario = json.decodeFromJsonElement(ScenarioDto.serializer(), root).toDomain(),
            readOnly = false,
            fileSchemaVersion = version,
        )
    }

    /** FS-13: same directory, flushed, then an atomic replace. */
    private fun writeAtomically(
        target: File,
        contents: String,
    ) {
        val temporary = File(target.parentFile, target.name + TEMPORARY_SUFFIX)
        FileOutputStream(temporary).use { stream ->
            stream.write(contents.toByteArray())
            stream.flush()
            stream.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    companion object {
        const val SCENARIO_FILE = "scenario.json"
        private const val TEMPORARY_SUFFIX = ".tmp"
        private const val SCHEMA_VERSION_FIELD = "schemaVersion"
        private const val ID_FIELD = "id"
        private const val NAME_FIELD = "name"
        private const val TAG = "ScenarioStore"

        /** FS-12: unrecognised fields are ignored rather than fatal. */
        val defaultJson =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = true
            }
    }
}
