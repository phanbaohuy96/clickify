package com.pbh.clickify.domain.scenario

import java.util.UUID

/**
 * An ordered sequence of Steps, and the screen they were measured against (SM-1).
 *
 * [screenProfile] is null only while the Scenario has no Step with a point in it: SM-14 says the
 * profile is captured when the first Marker is placed, and an empty Scenario has not reached that
 * moment yet.
 */
data class Scenario(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val steps: List<Step> = emptyList(),
    val runCount: RunCount = RunCount.Times(1),
    val countdownMilliseconds: Int = ScenarioLimits.DEFAULT_COUNTDOWN_MILLISECONDS,
    val screenProfile: ScreenProfile? = null,
) {
    /** SM-8: how many Markers the Overlay draws for this Scenario. */
    val markerCount: Int get() = steps.count { it.hasMarker }

    /** TP-28: whether anything in here needs a schema an older build could not read. */
    val usesRecognition: Boolean get() = steps.any { it.effectiveSearch != null || it.guard != null }

    /**
     * TP-27: every Template this Scenario still refers to, so the rest can be swept.
     *
     * Reads `search` rather than `effectiveSearch` on purpose. A search left on a Step whose
     * Action stopped using a Target is ignored at run time (`TP-22`) and is still the user's
     * picture — deleting the file because they tried `globalAction` for a moment would be losing
     * their work to tidy a directory.
     */
    val templateIds: Set<UUID>
        get() = steps.flatMap { listOfNotNull(it.search?.templateId, it.guard?.search?.templateId) }.toSet()

    companion object {
        /**
         * The name given to a Scenario whose scenario.json has none (SM-4).
         *
         * Deliberately **not** translated: it is a repair value, not a name the user chose, and it
         * is written back on the next save. Translating it would let an interface setting rewrite
         * user data, and would give the same damaged file a different name on every phone.
         */
        const val REPAIRED_NAME = "Untitled scenario"
    }
}
