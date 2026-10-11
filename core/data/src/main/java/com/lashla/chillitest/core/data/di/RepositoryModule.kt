package com.lashla.chillitest.core.data.di

import com.lashla.chillitest.core.data.GiphyApi
import com.lashla.chillitest.core.data.GiphyRepository
import com.lashla.chillitest.core.domain.GifRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    internal fun provideGifRepository(
        api: GiphyApi,
        @Named("api_key") apiKey: String,
    ): GifRepository = GiphyRepository(api, apiKey)
}
