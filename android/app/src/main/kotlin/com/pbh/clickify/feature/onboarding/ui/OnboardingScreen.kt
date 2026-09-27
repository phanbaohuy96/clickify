package com.pbh.clickify.feature.onboarding.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pbh.clickify.R
import com.pbh.clickify.core.designsystem.components.AppScaffold
import com.pbh.clickify.feature.onboarding.PermissionStatus
import com.pbh.clickify.feature.onboarding.readPermissionStatus

/**
 * The screen that decides whether the app works at all (`02-permissions-and-onboarding.md`).
 *
 * Not a splash screen and not a wizard. It is a checklist that tells the truth about its own
 * state, because every one of these permissions can be turned off in Settings while the app is in
 * the background (`PM-9`) — so it is re-read on every resume rather than remembered.
 *
 * The one piece of real sequencing is `PM-4`: the "Allow restricted settings" menu entry **does
 * not exist until the restriction has been triggered**, so the instruction for it appears only
 * after a visit to Accessibility has come back with the service still off (`PM-7`).
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.readPermissionStatus()) }
    // PM-7: set the first time the user comes back from Accessibility with nothing granted.
    var accessibilityRefused by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val fresh = context.readPermissionStatus()
        if (!fresh.accessibility && status.accessibility != fresh.accessibility) accessibilityRefused = true
        status = fresh
        onPauseOrDispose {}
    }

    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            status = context.readPermissionStatus()
        }

    AppScaffold(modifier = modifier.fillMaxSize(), title = stringResource(R.string.onboarding_title)) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PM-2: the rest of the sentence Android's own consent dialogue starts.
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (status.advancedProtection && !status.accessibility) {
                // PM-11: the honest end of the road. No retry, and no way round it offered.
                Blocked()
                return@AppScaffold
            }

            PermissionCard(
                title = stringResource(R.string.onboarding_accessibility_title),
                body = stringResource(R.string.onboarding_accessibility_body),
                granted = status.accessibility,
                action = stringResource(R.string.onboarding_open_accessibility),
                onAction = {
                    accessibilityRefused = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )

            // PM-4, PM-7: only after Accessibility has actually refused, and only on a build
            // Android puts behind the restriction in the first place.
            if (status.sideloaded && accessibilityRefused && !status.accessibility) {
                PermissionCard(
                    title = stringResource(R.string.onboarding_restricted_title),
                    // PM-8: the path, and nothing that pretends to know this manufacturer's menus.
                    body = stringResource(R.string.onboarding_restricted_body),
                    granted = false,
                    action = stringResource(R.string.onboarding_open_app_info),
                    onAction = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                )
            }

            PermissionCard(
                title = stringResource(R.string.onboarding_overlay_title),
                body = stringResource(R.string.onboarding_overlay_body),
                granted = status.overlay,
                action = stringResource(R.string.onboarding_open_overlay),
                onAction = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )

            PermissionCard(
                title = stringResource(R.string.onboarding_notifications_title),
                body = stringResource(R.string.onboarding_notifications_body),
                granted = status.notifications,
                optional = true,
                action = stringResource(R.string.onboarding_allow),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            Button(
                onClick = onDone,
                enabled = status.ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (status.ready) R.string.onboarding_done else R.string.onboarding_waiting))
            }
        }
    }
}

/** One permission: what it buys, what it does not cost, and whether it is granted (`PM-2`). */
@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text =
                        stringResource(
                            when {
                                granted -> R.string.onboarding_granted
                                optional -> R.string.onboarding_optional
                                else -> R.string.onboarding_needed
                            },
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (granted) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (!granted) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** PM-11: says which setting is responsible, and stops. */
@Composable
private fun Blocked() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.onboarding_blocked_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_blocked_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
