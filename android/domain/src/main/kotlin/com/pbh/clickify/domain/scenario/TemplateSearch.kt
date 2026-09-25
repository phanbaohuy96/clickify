package com.pbh.clickify.domain.scenario

import java.util.UUID

/**
 * Looking for a **Template** on the screen: what to look for, where, how sure, and for how long
 * (`TP-19`, `TP-21`).
 *
 * One type serves both places a search appears — a **Step**'s **Target** and its **Guard**
 * (`TP-24`) — because the question being asked is the same one and only the answer is read
 * differently.
 */
data class TemplateSearch(
    val templateId: UUID,
    /** `TP-15`: how close a match has to be, `0…1`. */
    val threshold: Double = DEFAULT_THRESHOLD,
    /** `TP-9`: null means the whole display. */
    val region: ScreenRegion? = null,
    /** `TP-21`: zero is legitimate — one frame, one look, and then the timeout. */
    val waitMilliseconds: Int = DEFAULT_WAIT_MILLISECONDS,
    val onTimeout: OnTimeout = OnTimeout.STOP_SCENARIO,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 0.90
        const val DEFAULT_WAIT_MILLISECONDS = 5_000
    }
}

/**
 * What happens when a search runs out of time (`TP-21`, mirroring macOS `DM-16`).
 *
 * Two members, and there will not be a third: a third would be a jump, and [ADR-0011] says a
 * **Scenario** has no branches.
 */
enum class OnTimeout {
    /** The run ends, and the user is told which **Step** and why. */
    STOP_SCENARIO,

    /** This one **Step** does not happen, and the next one does. */
    SKIP_STEP,
}

/**
 * A condition a **Step** waits for before it runs (`TP-24`).
 *
 * **Not a branch** (`TP-25`). A **Guard** that never comes true has exactly the two outcomes
 * [OnTimeout] already had, and there is no else and no block — which is what keeps [ADR-0011]
 * true with a conditional in the language.
 */
data class Guard(
    val search: TemplateSearch,
    val expects: Presence = Presence.PRESENT,
)

enum class Presence {
    PRESENT,
    ABSENT,
}
