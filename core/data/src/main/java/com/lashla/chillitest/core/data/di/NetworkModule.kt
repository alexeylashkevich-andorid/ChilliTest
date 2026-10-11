package com.lashla.chillitest.core.data.di

import com.lashla.chillitest.core.data.GiphyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(@Named("network_logging") loggingEnabled: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .apply { if (loggingEnabled) addInterceptor(createLoggingInterceptor()) }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TRANSFER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        @Named("api_base_url") baseUrl: String,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideGiphyApi(retrofit: Retrofit): GiphyApi =
        retrofit.create(GiphyApi::class.java)

    internal fun createLoggingInterceptor(
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    ): HttpLoggingInterceptor = HttpLoggingInterceptor { message ->
        logger.log(message.replace(API_KEY_QUERY_PARAMETER, "$1REDACTED"))
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val API_KEY_QUERY_PARAMETER = Regex("([?&]api_key=)[^&\\s]*")

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val TRANSFER_TIMEOUT_SECONDS = 30L
}
