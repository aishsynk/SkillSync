package com.example.skillsync.data.api

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

        // Carry the backend-issued session on every request after login. The
        // login call itself naturally has no stored session yet. Centralising
        // this here prevents individual repositories from accidentally making
        // unauthenticated requests as Version 2 route enforcement rolls out.
        val sessionInterceptor = Interceptor { chain ->
            val sessionId = com.example.skillsync.data.SessionManager.getSessionId()
            val request = if (sessionId.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $sessionId")
                    .build()
            }
            chain.proceed(request)
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

    val instance: SkillEdgeApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient ?: throw IllegalStateException("RetrofitClient not initialized"))
            .build()

        retrofit.create(SkillEdgeApi::class.java)
    }
}
