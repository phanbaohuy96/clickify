package com.pbh.clickify.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pbh.clickify.core.common.DispatcherProvider
import com.pbh.clickify.data.scenario.FileScenarioStore
import com.pbh.clickify.data.scenario.FileTemplateStore
import com.pbh.clickify.data.settings.DataStoreSettings
import com.pbh.clickify.domain.repository.ScenarioRepository
import com.pbh.clickify.domain.repository.TemplateFiles
import com.pbh.clickify.domain.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    /** FS-1: internal storage, so nothing else on the phone can read a Scenario. */
    @Provides
    @Singleton
    fun provideScenarioStore(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): FileScenarioStore = FileScenarioStore(File(context.filesDir, SCENARIOS_DIRECTORY), dispatchers)

    @Provides
    @Singleton
    fun provideScenarioRepository(store: FileScenarioStore): ScenarioRepository = store

    /** TP-27: the same root, so a Scenario's pictures stay inside its own directory. */
    @Provides
    @Singleton
    fun provideTemplateFiles(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): TemplateFiles = FileTemplateStore(File(context.filesDir, SCENARIOS_DIRECTORY), dispatchers)

    @Provides
    @Singleton
    fun provideSettingsStore(
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatchers.io + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_NAME) },
        )

    @Provides
    @Singleton
    fun provideSettingsRepository(store: DataStore<Preferences>): SettingsRepository = DataStoreSettings(store)

    private const val SCENARIOS_DIRECTORY = "scenarios"
    private const val SETTINGS_NAME = "settings"
}
