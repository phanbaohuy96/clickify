package com.pbh.clickify.domain.repository

import com.pbh.clickify.domain.model.AppResult
import com.pbh.clickify.domain.scenario.Scenario
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Every Scenario on the device, and the four things that can be done to one.
 *
 * There is no `save()` with a button behind it: FS-15 writes when editing stops, so the caller
 * hands over a whole Scenario and the store makes the disk match.
 */
interface ScenarioRepository {
    /** Emits the full list again whenever any Scenario changes. */
    fun observeScenarios(): Flow<List<StoredScenario>>

    suspend fun load(id: UUID): AppResult<StoredScenario>

    suspend fun save(scenario: Scenario): AppResult<Unit>

    suspend fun delete(id: UUID): AppResult<Unit>

    /** FS-3: copies the whole directory. FS-14 refuses when the original could not be decoded. */
    suspend fun duplicate(id: UUID): AppResult<StoredScenario>
}

/**
 * A Scenario as it came off disk, carrying whether this build may act on it (FS-14).
 *
 * [readOnly] is true for a file written by a newer build. Its [scenario] then has a name and an id
 * and no Steps, because the Steps are in the part this build cannot decode — so the interface must
 * not offer to run it, and must not offer to copy it either.
 */
data class StoredScenario(
    val scenario: Scenario,
    val readOnly: Boolean = false,
    val fileSchemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    companion object {
        /**
         * FS-5. Raised only when the format changes in a way an older build cannot read.
         *
         * 2 is recognition (`TP-28`), and a **Scenario** that does not use it is still written as
         * 1 — this is the highest version this build can *read*, not the version it always writes.
         */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
