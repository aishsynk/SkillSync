package com.example.skillsync.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.skillsync.util.SkillSyncNotificationWorker
import com.example.skillsync.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** Schedules OS-backed periodic sync, rapid chained background passes, and an immediate pass when internet returns. */
object SyncScheduler {
    private const val PERIODIC_NAME = "SkillEdgeDataSync"
    private const val IMMEDIATE_NAME = "SkillEdgeConnectivitySync"
    private const val RAPID_CHAIN_NAME = "SkillEdgeRapidSync"
    @Volatile private var callbackRegistered = false
    private val _online = MutableStateFlow(false)
    val online = _online.asStateFlow()

    fun start(context: Context) {
        val app = context.applicationContext
        _online.value = RetrofitClient.isNetworkAvailable(app)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<SkillSyncNotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic,
        )
        registerConnectivityCallback(app)
        enqueueImmediate(app)
        enqueueRapidChain(app, delaySeconds = 60)
    }

    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<SkillSyncNotificationWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request,
        )
    }

    /**
     * Rapid chained background pass: schedules next high-frequency check
     * so unallocated demand alerts trigger within ~60s rather than waiting 15 min.
     */
    fun enqueueRapidChain(context: Context, delaySeconds: Long = 60) {
        val request = OneTimeWorkRequestBuilder<SkillSyncNotificationWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RAPID_CHAIN_NAME, ExistingWorkPolicy.REPLACE, request,
        )
    }

    private fun registerConnectivityCallback(context: Context) {
        if (callbackRegistered) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = Unit
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _online.value = validated
                if (validated) {
                    enqueueImmediate(context)
                }
            }
            override fun onLost(network: Network) {
                _online.value = RetrofitClient.isNetworkAvailable(context)
            }
        })
        callbackRegistered = true
    }
}
