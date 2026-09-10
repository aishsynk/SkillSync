package com.example.skillsync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
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

    // SkillEdgeApplication initializes cache, networking and sync before UI.
    com.example.skillsync.util.NotificationStateStore.init(applicationContext)
    com.example.skillsync.util.NotificationDestinationStore.accept(intent)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
    ) {
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    enableEdgeToEdge()

    setContent {
      SkillSyncTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onResume() {
    super.onResume()
    com.example.skillsync.data.sync.SyncScheduler.enqueueImmediate(applicationContext)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    com.example.skillsync.util.NotificationDestinationStore.accept(intent)
  }
}
