package com.pbh.clickify.core.ui

import com.pbh.clickify.core.R
import com.pbh.clickify.domain.model.DomainError
import com.pbh.clickify.domain.model.RequiredPermission

/**
 * Central error-to-text policy — the single place that turns a [DomainError] into a localized
 * [UiText]. One exhaustive `when` instead of a per-feature copy.
 *
 * This is the **fallback** every screen can use, not the last word. A screen that owns an error
 * says more about it: SM-15 requires the Screen profile block to name what changed, and the list
 * of differences is assembled by the screen that shows it, where each difference has its own
 * translated name. Here it is one sentence, because a snackbar has room for one sentence.
 */
fun DomainError.toUiText(): UiText =
    when (this) {
        is DomainError.PermissionMissing -> permission.toUiText()
        is DomainError.ScreenProfileMismatch -> UiText.Resource(R.string.core_error_screen_profile)
        is DomainError.ScenarioTooNew ->
            UiText.Resource(
                R.string.core_error_scenario_too_new,
                listOf(fileSchemaVersion, supportedSchemaVersion),
            )

        is DomainError.ScenarioUnreadable -> UiText.Resource(R.string.core_error_scenario_unreadable)
        DomainError.Unknown -> UiText.Resource(R.string.core_error_generic)
    }

private fun RequiredPermission.toUiText(): UiText =
    when (this) {
        RequiredPermission.ACCESSIBILITY_SERVICE -> UiText.Resource(R.string.core_error_permission_accessibility)
        RequiredPermission.OVERLAY -> UiText.Resource(R.string.core_error_permission_overlay)
        RequiredPermission.POST_NOTIFICATIONS -> UiText.Resource(R.string.core_error_permission_notifications)
    }
