package com.pbh.clickify.core.ui

import com.pbh.clickify.core.R
import com.pbh.clickify.domain.model.DomainError
import com.pbh.clickify.domain.model.RequiredPermission
import com.pbh.clickify.domain.scenario.ScreenProfileDifference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The fallback every screen can use. What matters is that no error falls through to the generic
 * message by accident, and that two different errors never read the same.
 */
class DomainErrorTextTest {
    @Test
    fun `each permission says which one it means`() {
        val texts =
            RequiredPermission.entries.map {
                DomainError.PermissionMissing(it).toUiText()
            }

        assertEquals(texts.size, texts.distinct().size, "two permissions share a message")
        assertEquals(
            UiText.Resource(R.string.core_error_permission_accessibility),
            DomainError.PermissionMissing(RequiredPermission.ACCESSIBILITY_SERVICE).toUiText(),
        )
    }

    @Test
    fun `a scenario from a newer build names both versions`() {
        val text = DomainError.ScenarioTooNew(fileSchemaVersion = 4, supportedSchemaVersion = 1).toUiText()

        assertIs<UiText.Resource>(text)
        assertEquals(listOf(4, 1), text.args)
    }

    @Test
    fun `a screen mismatch does not fall through to the generic message`() {
        val text =
            DomainError
                .ScreenProfileMismatch(listOf(ScreenProfileDifference.ROTATION))
                .toUiText()

        assertEquals(UiText.Resource(R.string.core_error_screen_profile), text)
    }

    @Test
    fun `every error has its own message`() {
        val errors =
            listOf(
                DomainError.PermissionMissing(RequiredPermission.OVERLAY),
                DomainError.ScreenProfileMismatch(emptyList()),
                DomainError.ScenarioTooNew(2, 1),
                DomainError.ScenarioUnreadable("abc"),
                DomainError.Unknown,
            )

        val texts = errors.map { it.toUiText() }

        assertEquals(errors.size, texts.distinct().size, "$texts")
    }
}
