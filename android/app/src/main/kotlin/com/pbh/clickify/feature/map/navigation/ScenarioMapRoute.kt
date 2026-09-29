package com.pbh.clickify.feature.map.navigation

import kotlinx.serialization.Serializable

/** The Scenario map (`MP-1`) of one Scenario, named by its id as the directory on disk is. */
@Serializable
data class ScenarioMapRoute(
    val scenarioId: String,
)
