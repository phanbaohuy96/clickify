package com.pbh.clickify.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pbh.clickify.feature.onboarding.navigation.OnboardingRoute
import com.pbh.clickify.feature.onboarding.ui.OnboardingScreen
import com.pbh.clickify.feature.scenario.navigation.ScenarioListRoute
import com.pbh.clickify.feature.scenario.ui.ScenarioListScreen

/**
 * Root navigation host for the Activity surface.
 *
 * The Activity is the **smaller** of the app's two surfaces: it sets the app up, manages
 * Scenarios, and hands one to the Overlay. Authoring and running happen in the Overlay, which is
 * not an Activity and so is not reachable from here — see [ADR-0015].
 */
@Composable
fun AppNavHost(startWithOnboarding: Boolean) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) OnboardingRoute else ScenarioListRoute,
    ) {
        composable<ScenarioListRoute> {
            ScenarioListScreen(onSetUp = { navController.navigate(OnboardingRoute) })
        }
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = {
                    // PM-10: from here on the checklist is somewhere the user goes, not somewhere
                    // they are sent. Popping it means Back from the list leaves the app.
                    if (!navController.popBackStack(ScenarioListRoute, inclusive = false)) {
                        navController.navigate(ScenarioListRoute) {
                            popUpTo(OnboardingRoute) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
