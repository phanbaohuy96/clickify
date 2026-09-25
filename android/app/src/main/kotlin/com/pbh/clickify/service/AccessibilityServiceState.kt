package com.pbh.clickify.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Whether Clickify's accessibility service is switched on, read the way PM-6 requires.
 *
 * **Not** from [ClickifyAccessibilityService.instance]. That handle is null whenever the process
 * has been restarted, which is most of the time between two visits to Settings — exactly when this
 * question gets asked. `Settings.Secure` survives the process; the handle does not.
 */
object AccessibilityServiceState {
    fun isEnabled(context: Context): Boolean {
        val raw =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        val ours = ComponentName(context, ClickifyAccessibilityService::class.java)
        return enabledComponents(raw).any { it.matches(ours) }
    }

    /**
     * The components named in the setting.
     *
     * Colon-separated, and both spellings appear in the wild — the fully qualified
     * `com.example/com.example.Service` and the short `com.example/.Service` — so each entry is
     * unflattened rather than string-compared.
     */
    internal fun enabledComponents(raw: String?): List<ComponentName> =
        raw
            .orEmpty()
            .split(':')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .mapNotNull(ComponentName::unflattenFromString)

    private fun ComponentName.matches(other: ComponentName): Boolean =
        packageName.equals(other.packageName, ignoreCase = true) &&
            className.removePrefix(other.packageName) == other.className.removePrefix(other.packageName)
}
