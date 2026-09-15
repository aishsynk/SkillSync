package com.example.skillsync.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

object RetrofitClient {
    // Hosted Python backend URL
    private const val BASE_URL = "https://skilledge-backend-fpcl.onrender.com/"

    private var okHttpClient: OkHttpClient? = null

    fun init(context: Context) {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Production payloads contain employee and customer data.
            level = HttpLoggingInterceptor.Level.NONE
        }

        val cacheSize = 10L * 1024 * 1024 // 10 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        val offlineInterceptor = Interceptor { chain ->
            var request = chain.request()
            if (!isNetworkAvailable(context)) {
                val maxStale = 60 * 60 * 24 * 7 // 7 days
                request = request.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=$maxStale")
                    .build()
            } else {
                // The explicit app-private LocalCache owns offline display.
                // Online sync must revalidate instead of silently accepting an
                // OkHttp response declared fresh for two hours.
                request = request.newBuilder().header("Cache-Control", "no-cache").build()
            }
            chain.proceed(request)
        }

        val rewriteCacheControlInterceptor = Interceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            if (isNetworkAvailable(context)) {
                originalResponse.newBuilder()
                    .header("Cache-Control", "public, max-age=0, must-revalidate")
                    .build()
            } else {
                val maxStale = 60 * 60 * 24 * 7 // 7 days
                originalResponse.newBuilder()
                    .header("Cache-Control", "public, only-if-cached, max-stale=$maxStale")
                    .build()
            }
        }

        // Carry the backend-issued session on every request after login.
        // If a 401 occurs (e.g. Render restart or session expiry) the stale
        // session can no longer be refreshed silently — password is required —
        // so we clear it and let the app route back to the Login screen.
        val sessionInterceptor = Interceptor { chain ->
            val sessionId = com.example.skillsync.core.data.SessionManager.getSessionId()
            val request = if (sessionId.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $sessionId")
                    .build()
            }
            val response = chain.proceed(request)
            if (response.code == 401) {
                val url = request.url.encodedPath
                if (!url.contains("/auth/login") && !url.contains("/auth/check") &&
                    !com.example.skillsync.core.data.SessionManager.getEmail().isNullOrBlank()
                ) {
                    response.close()
                    com.example.skillsync.core.data.SessionManager.clearSession()
                }
            }
            response
        }

        okHttpClient = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(sessionInterceptor)
            .addInterceptor(offlineInterceptor)
            .addNetworkInterceptor(rewriteCacheControlInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * The single shared [Retrofit] instance — same [BASE_URL], same
     * [okHttpClient] (and therefore the same interceptors/cache/timeouts) for
     * every domain API interface. Domain repositories get their own narrow
     * API interface (`AuthApi`, `TrainerApi`, ...) via [create], never their
     * own [Retrofit]/[OkHttpClient] — the transport layer stays centralized,
     * only the business-endpoint surface is split.
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient ?: throw IllegalStateException("RetrofitClient not initialized"))
            .build()
    }

    /** One Retrofit-backed implementation of a domain API interface. */
    inline fun <reified T> create(): T = retrofit.create(T::class.java)

    /**
     * Backward-compatible alias for repositories not yet migrated to a
     * narrower domain API interface. Kept only until every [SkillEdgeApi]
     * method has moved to its domain owner and no repository asks for the
     * whole interface any more (Phase 4, `docs/phase4-api-ownership-matrix.md`).
     */
    val instance: SkillEdgeApi by lazy { create<SkillEdgeApi>() }
}
