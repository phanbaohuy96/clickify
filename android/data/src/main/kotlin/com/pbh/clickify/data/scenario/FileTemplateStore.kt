package com.pbh.clickify.data.scenario

import android.util.Log
import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.domain.repository.TemplateFiles
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * `<scenario>/templates/<template>.png`, and nothing else (`TP-27`).
 *
 * Takes the same [root] as [FileScenarioStore] and knows nothing about it, so a Scenario's
 * document and its pictures cannot get out of step in the way two stores would allow — this is
 * [ADR-0014]'s argument, one directory down.
 *
 * [ADR-0014]: ../../../../../../../../docs/adr/0014-no-database.md
 */
class FileTemplateStore(
    private val root: File,
    private val dispatchers: DispatcherProvider,
) : TemplateFiles {
    override suspend fun ids(scenarioId: UUID): Set<UUID> =
        withContext(dispatchers.io) {
            directoryFor(scenarioId)
                .listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
                .orEmpty()
                .mapNotNull { runCatching { UUID.fromString(it.name.removeSuffix(EXTENSION)) }.getOrNull() }
                .toSet()
        }

    override suspend fun read(
        scenarioId: UUID,
        templateId: UUID,
    ): ByteArray? =
        withContext(dispatchers.io) {
            fileFor(scenarioId, templateId).takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
        }

    /** FS-13's rule, for a picture: written beside its destination, then moved into place. */
    override suspend fun write(
        scenarioId: UUID,
        templateId: UUID,
        bytes: ByteArray,
    ): Boolean =
        withContext(dispatchers.io) {
            runCatching {
                val target = fileFor(scenarioId, templateId).apply { parentFile?.mkdirs() }
                val temporary = File(target.parentFile, target.name + TEMPORARY_SUFFIX)
                FileOutputStream(temporary).use { stream ->
                    stream.write(bytes)
                    stream.flush()
                    stream.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.onFailure { Log.w(TAG, "could not write template $templateId", it) }.isSuccess
        }

    override suspend fun sweep(
        scenarioId: UUID,
        keep: Set<UUID>,
    ) {
        withContext(dispatchers.io) {
            ids(scenarioId).filterNot { it in keep }.forEach { fileFor(scenarioId, it).delete() }
        }
    }

    private fun directoryFor(scenarioId: UUID) = File(File(root, scenarioId.toString()), TEMPLATES_DIRECTORY)

    private fun fileFor(
        scenarioId: UUID,
        templateId: UUID,
    ) = File(directoryFor(scenarioId), "$templateId$EXTENSION")

    private companion object {
        const val TAG = "FileTemplateStore"
        const val TEMPLATES_DIRECTORY = "templates"
        const val EXTENSION = ".png"
        const val TEMPORARY_SUFFIX = ".tmp"
    }
}
