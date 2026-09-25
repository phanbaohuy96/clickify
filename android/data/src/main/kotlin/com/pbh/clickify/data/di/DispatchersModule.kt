package com.pbh.clickify.data.di

import com.pbh.clickify.core.common.DefaultDispatcherProvider
import com.pbh.clickify.core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the dispatcher set that `:core` defines.
 *
 * It lives here rather than in `:core` so that `:core` stays free of Hilt: it is the module the
 * Overlay's own UI is built from, and the Overlay is not an Activity ([ADR-0015]), so the less it
 * carries the better.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
