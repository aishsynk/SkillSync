package com.example.skillsync.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * One-time nudge to exempt SkillEdge from Doze / battery optimisation.
 *
 * OEM battery managers otherwise freeze the foreground [MonitoringService] and
 * defer WorkManager, which is exactly the "stops working when the app is closed"
 * failure the manager reported. We ask once; if declined we never nag again.
 */
object BatteryOptimization {
    private const val PREF = "skilledge_session"
    private const val KEY_ASKED = "battery_exemption_asked"

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Launches the system exemption dialog at most once, unless already exempt. */
    fun requestOnce(context: Context) {
        if (isIgnoring(context)) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
