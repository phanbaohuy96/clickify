package com.pbh.clickify.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * The few things Clickify remembers about **itself** rather than about a Scenario (`AP-1`).
 *
 * Kept apart from `scenario.json` on purpose. A Scenario is the user's document and travels
 * ([ADR-0014]); where they happen to like the floating control is a property of this phone and
 * would be wrong on any other.
 */
data class AppSettings(
    /**
     * Where the floating control was left (`OV-14`), as a `Gravity.TOP or Gravity.START` offset.
     *
     * Null until the control has been dragged once, which is not the same as `0, 0`: it is what
     * says "no opinion yet", so the first placement can be chosen rather than inherited from a
     * corner nobody picked.
     */
    val controlPosition: ControlPosition? = null,
    /**
     * IL-1: the interface language as an IETF tag, or null to follow the phone.
     *
     * Null is not "English". A phone set to Japanese shows Japanese without anybody choosing it,
     * and only a choice made on purpose is stored — so a user who never opens the picker keeps
     * following their phone for ever, including after they change it.
     */
    val languageTag: String? = null,
)

data class ControlPosition(
    val x: Int,
    val y: Int,
)

/** Reads and writes [AppSettings]. One value at a time, because each is written on its own. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setControlPosition(position: ControlPosition)

    /** IL-1: null puts it back to following the phone. */
    suspend fun setLanguageTag(tag: String?)
}
