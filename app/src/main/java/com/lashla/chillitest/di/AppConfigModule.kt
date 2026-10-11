package com.lashla.chillitest.di

import com.lashla.chillitest.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Named("network_logging")
    fun provideNetworkLogging(): Boolean = BuildConfig.DEBUG

    @Provides
    @Singleton
    @Named("api_base_url")
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    @Named("api_key")
    fun provideApiKey(): String = BuildConfig.API_KEY
}
