package com.pbh.clickify.domain.repository

import java.util.UUID

/**
 * The pixels of a Scenario's Templates, which live beside its `scenario.json` (`TP-27`).
 *
 * Bytes rather than images on purpose. Encoding a PNG is Android's job and the store's job is the
 * directory, so `:data` stays free of `Bitmap` and the whole of it keeps being testable on the JVM
 * against a temporary folder.
 *
 * Separate from [ScenarioRepository] rather than four more methods on it, because they are two
 * different kinds of thing: one owns a document, the other owns the attachments. `FS-3` already
 * copies the directory, so duplicating a Scenario carries its Templates with it and neither of
 * these had to know.
 */
interface TemplateFiles {
    /** Every Template this Scenario has pixels for. Used by `TP-29`'s check in the editor. */
    suspend fun ids(scenarioId: UUID): Set<UUID>

    /** The PNG bytes, or null when the file has gone (`TP-29`). */
    suspend fun read(
        scenarioId: UUID,
        templateId: UUID,
    ): ByteArray?

    suspend fun write(
        scenarioId: UUID,
        templateId: UUID,
        bytes: ByteArray,
    ): Boolean

    /**
     * Deletes every Template this Scenario no longer refers to.
     *
     * Called when a Scenario is **opened** rather than when one is saved, and that is deliberate.
     * A Template is written the moment it is cropped, before the Step that uses it has been saved
     * (`FS-15` notwithstanding — a draft is not a Step yet), so a sweep on save would delete the
     * picture the user is in the middle of aiming with. On open there is no draft anywhere, and
     * what is on disk is the whole truth.
     */
    suspend fun sweep(
        scenarioId: UUID,
        keep: Set<UUID>,
    )
}
