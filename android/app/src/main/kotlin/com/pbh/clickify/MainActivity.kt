package com.pbh.clickify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.pbh.clickify.core.designsystem.AppThemeDefaults
import com.pbh.clickify.core.designsystem.ClickifyTheme
import com.pbh.clickify.feature.onboarding.readPermissionStatus
import com.pbh.clickify.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main launcher activity: theming, Scenario management and onboarding.
 *
 * This is not where the app does its work. Authoring and running happen in the Overlay, which is a
 * `WindowManager` view rather than an Activity — see [ADR-0015].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PM-10: decided once, at launch. A user who has chosen not to grant something is not
        // pushed back to the checklist every time they open the app.
        val needsSetUp = !readPermissionStatus().ready
        setContent {
            val themeConfig =
                if (isSystemInDarkTheme()) {
                    AppThemeDefaults.dark()
                } else {
                    AppThemeDefaults.light()
                }
            ClickifyTheme(config = themeConfig) {
                AppNavHost(startWithOnboarding = needsSetUp)
            }
        }
    }
}
