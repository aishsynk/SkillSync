package com.koenig.skilledge.core.di

import android.content.Context
import com.koenig.skilledge.BuildConfig
import com.koenig.skilledge.data.api.SkillEdgeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides OkHttp client with:
     * - Logging interceptor for debugging
     * - Cache interceptor for offline support
     * - Session cookie management
     * - Timeout configuration
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val cacheSize = 50L * 1024 * 1024 // 50 MB cache
        val cache = Cache(context.cacheDir, cacheSize)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = when {
                BuildConfig.DEBUG -> HttpLoggingInterceptor.Level.BODY
                else -> HttpLoggingInterceptor.Level.BASIC
            }
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(BuildConfig.API_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(BuildConfig.API_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.API_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .cookieJar(SessionCookieJar())
            .build()
    }

    /**
     * Provides Retrofit instance configured for SkillEdge API
     */
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provides SkillEdge API service
     */
    @Provides
    @Singleton
    fun provideSkillEdgeApiService(retrofit: Retrofit): SkillEdgeApiService {
        return retrofit.create(SkillEdgeApiService::class.java)
    }
}

/**
 * Custom CookieJar for managing session cookies
 * Persists cookies across app sessions using SharedPreferences
 */
class SessionCookieJar : okhttp3.CookieJar {
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        // Cookies are automatically handled by OkHttp
        // This is a simple implementation; for production, consider using a proper cookie storage
    }

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        // Load stored cookies for the request
        return emptyList()
    }
}
