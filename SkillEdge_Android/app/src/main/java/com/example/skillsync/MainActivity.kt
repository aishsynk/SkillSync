package com.example.skillsync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.skillsync.theme.SkillSyncTheme

class MainActivity : ComponentActivity() {
  // Notifications are useless without this on Android 13+, and nothing was
  // ever requesting it -- the manifest declaring POST_NOTIFICATIONS is not
  // enough on its own. If the user declines, showNotification() already
  // no-ops silently rather than crashing.
  private val requestNotificationPermission =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    com.example.skillsync.data.SessionManager.init(applicationContext)
    com.example.skillsync.data.api.RetrofitClient.init(applicationContext)
    com.example.skillsync.data.cache.LocalCache.init(applicationContext)
    com.example.skillsync.util.NotificationStateStore.init(applicationContext)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
    ) {
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Schedule background push service
    val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.skillsync.util.SkillSyncNotificationWorker>(
        15, java.util.concurrent.TimeUnit.MINUTES
    ).build()
    androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
        "SkillSyncPushService",
        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )

    enableEdgeToEdge()

    setContent {
      SkillSyncTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
