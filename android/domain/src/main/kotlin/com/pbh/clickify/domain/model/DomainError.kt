package com.pbh.clickify.domain.model

import com.pbh.clickify.domain.scenario.ScreenProfileDifference

/**
 * Typed failure reasons shared by domain, data and UI translation layers.
 *
 * The template arrived with a network- and auth-shaped taxonomy — `Unauthorized`, `Offline`,
 * `Remote(code)`, email and password validation — and Clickify has no server, no account and no
 * network permission, so none of it described a failure this app can have. These are the failures
 * it can have.
 */
sealed interface DomainError {
    /** A permission the app cannot work without is not granted (PM-9). */
    data class PermissionMissing(
        val permission: RequiredPermission,
    ) : DomainError

    /** The screen in front of the user is not the one the Scenario was built on (SM-15). */
    data class ScreenProfileMismatch(
        val differences: List<ScreenProfileDifference>,
    ) : DomainError

    /** A scenario.json written by a newer build than this one, which refuses to guess. */
    data class ScenarioTooNew(
        val fileSchemaVersion: Int,
        val supportedSchemaVersion: Int,
    ) : DomainError

    /** A scenario.json that could not be read at all. */
    data class ScenarioUnreadable(
        val scenarioId: String,
    ) : DomainError

    /** Fallback for unexpected failures that should still be shown safely. */
    data object Unknown : DomainError
}

/** The permissions Clickify asks for, and the order onboarding asks in (PM-1). */
enum class RequiredPermission {
    ACCESSIBILITY_SERVICE,
    OVERLAY,
    POST_NOTIFICATIONS,
}
