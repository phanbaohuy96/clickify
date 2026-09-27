package com.pbh.clickify.feature.scenario.ui

import androidx.lifecycle.viewModelScope
import com.pbh.clickify.core.ui.AppLanguage
import com.pbh.clickify.core.ui.BaseViewModel
import com.pbh.clickify.core.ui.UiEffect
import com.pbh.clickify.data.scenario.FileScenarioStore
import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.domain.scenario.Scenario
import com.pbh.clickify.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject

data class ScenarioListUiState(
    val scenarios: List<StoredScenario> = emptyList(),
    val loading: Boolean = true,
    /** IL-1: the chosen language, or null while the phone's own is being followed. */
    val languageTag: String? = null,
)

sealed interface ScenarioListEffect : UiEffect {
    /** The Overlay is where a Scenario is authored and run, so opening one leaves the Activity. */
    data class OpenOverlay(
        val scenarioId: UUID,
    ) : ScenarioListEffect
}

@HiltViewModel
class ScenarioListViewModel
    @Inject
    constructor(
        private val store: FileScenarioStore,
        private val settings: SettingsRepository,
    ) : BaseViewModel<ScenarioListUiState, ScenarioListEffect>(ScenarioListUiState()) {
        init {
            store
                .observeScenarios()
                .onEach { setState { copy(scenarios = it, loading = false) } }
                .launchIn(viewModelScope)
            settings.settings
                .onEach { setState { copy(languageTag = it.languageTag) } }
                .launchIn(viewModelScope)
            launch { store.refresh() }
        }

        /**
         * IL-1: written, and taking effect before the write lands.
         *
         * [AppLanguage] is set first so the interface changes under the user's finger; the store
         * is what makes it survive the process. Doing it the other way round makes a language
         * change look like a disk write, because that is what it would be waiting on.
         */
        fun chooseLanguage(tag: String?) {
            AppLanguage.set(tag)
            launch { settings.setLanguageTag(tag) }
        }

        fun createScenario(name: String) {
            launch {
                val scenario = Scenario(name = name)
                store.save(scenario)
                sendEffect(ScenarioListEffect.OpenOverlay(scenario.id))
            }
        }

        fun open(scenario: StoredScenario) {
            // FS-14: a file this build cannot decode has no Steps, so there is nothing to open.
            if (!scenario.readOnly) sendEffect(ScenarioListEffect.OpenOverlay(scenario.scenario.id))
        }

        fun delete(scenario: StoredScenario) {
            launch { store.delete(scenario.scenario.id) }
        }

        /**
         * FS-15 again: renaming **is** saving, so there is no second step and no Save button.
         *
         * A blank name is refused rather than repaired. `SM-4`'s repair value exists for a file
         * that arrived damaged; a user emptying the field is asking a question, and the answer is
         * "a Scenario has to be called something", not a silent rewrite to "Untitled scenario".
         */
        fun rename(
            scenario: StoredScenario,
            name: String,
        ) {
            val trimmed = name.trim()
            if (scenario.readOnly || trimmed.isEmpty()) return
            launch { store.save(scenario.scenario.copy(name = trimmed)) }
        }

        /** FS-3: a copy of the whole directory, Templates included (`TP-27`). */
        fun duplicate(scenario: StoredScenario) {
            if (scenario.readOnly) return
            launch { store.duplicate(scenario.scenario.id) }
        }
    }
