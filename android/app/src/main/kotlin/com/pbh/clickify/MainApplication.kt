package com.pbh.clickify

import android.app.Application
import com.pbh.clickify.core.designsystem.preloadFonts
import com.pbh.clickify.core.ui.AppLanguage
import com.pbh.clickify.domain.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Application entry point: Hilt, and the one thing that has to happen before anything is drawn. */
@HiltAndroidApp
class MainApplication : Application() {
    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        // IL-1: read once at start-up and followed for as long as the process lives. Both
        // surfaces — the Activity and the Overlay — read it from here, which is the only place
        // either of them could agree on.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        settings.settings
            .map { it.languageTag }
            .distinctUntilChanged()
            .onEach { AppLanguage.set(it) }
            .launchIn(scope)
        // DS-1: the first composition that needs a bundled font reads it off disk on the thread it
        // is composing on. That thread, for the floating control, is the one between the user
        // leaving the app and seeing anything at all — and on the emulator it was seconds.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { preloadFonts(this@MainApplication) }
        }
    }
}
