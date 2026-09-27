package com.pbh.clickify.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pbh.clickify.domain.settings.AppSettings
import com.pbh.clickify.domain.settings.ControlPosition
import com.pbh.clickify.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [AppSettings] in a Preferences DataStore (`AP-1`).
 *
 * Preferences rather than a file of its own, because this is a handful of scalars with no schema
 * worth versioning — the opposite of a Scenario, which is a document and gets [ADR-0014]'s
 * treatment.
 */
class DataStoreSettings(
    private val store: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    override suspend fun setControlPosition(position: ControlPosition) {
        store.edit {
            it[CONTROL_X] = position.x
            it[CONTROL_Y] = position.y
        }
    }

    /** IL-1: removed rather than emptied, so "follow the phone" and "chose English" stay different. */
    override suspend fun setLanguageTag(tag: String?) {
        store.edit {
            if (tag == null) it.remove(LANGUAGE) else it[LANGUAGE] = tag
        }
    }

    /** Both coordinates or neither: half a position is not a position. */
    private fun Preferences.toSettings(): AppSettings {
        val x = this[CONTROL_X]
        val y = this[CONTROL_Y]
        return AppSettings(
            controlPosition = if (x != null && y != null) ControlPosition(x, y) else null,
            languageTag = this[LANGUAGE],
        )
    }

    private companion object {
        val CONTROL_X = intPreferencesKey("control_x")
        val CONTROL_Y = intPreferencesKey("control_y")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
