package com.example.skillsync.core.network

import com.example.skillsync.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Dedicated, deliberately-simple client for the LinkedIn Capture backend.
 *
 * Unlike [RetrofitClient] (used by the SkillSync manager API) this client has:
 *  - no session / offline / cache interceptors (the capture backend is stateless),
 *  - generous timeouts for LLM-powered analysis,
 *  - a hard [isConfigured] guard so a build without
 *    `-PlinkedinBackendBaseUrl=…` fails fast with a friendly message instead of
 *    pointing at a placeholder host.
 */
object LinkedInApiClient {

    val isConfigured: Boolean
        get() = BuildConfig.LINKEDIN_BASE_URL.isNotBlank()

    private val retrofit: Retrofit by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(40, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.LINKEDIN_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: LinkedInApi by lazy { retrofit.create(LinkedInApi::class.java) }
}