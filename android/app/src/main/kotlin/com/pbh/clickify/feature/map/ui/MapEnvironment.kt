package com.pbh.clickify.feature.map.ui

import android.content.Context
import android.provider.Settings
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRotation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** `MP-12`: whether the phone lets things move; false under *Remove animations*. */
fun interface AnimationsEnabled {
    fun enabled(): Boolean
}

/** `MP-2`: the display a Scenario with no profile is drawn on. */
fun interface CurrentDisplay {
    fun profile(): ScreenProfile
}

/** `Settings.Global.ANIMATOR_DURATION_SCALE` is 0 exactly when the user has removed animations. */
class SystemAnimations
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AnimationsEnabled {
        override fun enabled(): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

class SystemDisplay
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CurrentDisplay {
        override fun profile(): ScreenProfile {
            val metrics = context.resources.displayMetrics
            return ScreenProfile(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, ScreenRotation.PORTRAIT)
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class MapEnvironmentModule {
    @Binds
    abstract fun animations(impl: SystemAnimations): AnimationsEnabled

    @Binds
    abstract fun display(impl: SystemDisplay): CurrentDisplay
}
