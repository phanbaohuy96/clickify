package com.pbh.clickify.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.security.advancedprotection.AdvancedProtectionManager
import androidx.core.content.ContextCompat
import com.pbh.clickify.service.AccessibilityServiceState

/**
 * What Clickify is allowed to do right now (`PM-1` to `PM-6`).
 *
 * Read fresh every time rather than remembered. Every one of these can be turned off in Settings
 * while the app is in the background, and `PM-9` says that is a supported thing for a user to do.
 */
data class PermissionStatus(
    /** PM-6: read from `Settings.Secure`, not from whether the service object happens to exist. */
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    /** PM-4, PM-5: whether this build is the kind Android 13 puts behind restricted settings. */
    val sideloaded: Boolean,
    /**
     * PM-11: whether Advanced Protection is on, which settles the question rather than delaying
     * it.
     *
     * A device with it on refuses accessibility permission to any service declaring
     * `isAccessibilityTool="false"`, and revokes it where it was already granted. There is no
     * per-app exception and there is nothing to retry. Clickify is an automation tool and says
     * so (`PM-3`), so on such a device it says the setting's name and stops.
     */
    val advancedProtection: Boolean = false,
) {
    /**
     * Whether a Scenario can be opened at all.
     *
     * Notifications are not in it. Without them a run is less visible than it should be, which is
     * a fault worth fixing and not a reason to refuse to run.
     */
    val ready: Boolean get() = accessibility && overlay
}

fun Context.readPermissionStatus(): PermissionStatus =
    PermissionStatus(
        accessibility = AccessibilityServiceState.isEnabled(this),
        overlay = Settings.canDrawOverlays(this),
        notifications =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        sideloaded = isSideloaded(),
        advancedProtection = isAdvancedProtectionOn(),
    )

/** PM-11. The API arrived in API 36; below that the mode does not exist to be on. */
private fun Context.isAdvancedProtectionOn(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
        runCatching {
            getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled == true
        }.getOrDefault(false)

/**
 * PM-5: a null or unknown installing package means sideloaded, and so does a call that throws.
 *
 * Sideloaded is the assumption on failure rather than the other way round, because being wrong in
 * that direction costs the user one instruction they did not need, and being wrong in the other
 * costs them a greyed-out toggle with no explanation anywhere in the app.
 */
private fun Context.isSideloaded(): Boolean =
    runCatching { packageManager.getInstallSourceInfo(packageName).installingPackageName }.getOrNull() == null
