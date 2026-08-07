package com.example.skillsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.skillsync.theme.SkillSyncTheme

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    com.example.skillsync.data.SessionManager.init(applicationContext)
    com.example.skillsync.data.api.RetrofitClient.init(applicationContext)
    enableEdgeToEdge()
    
    lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_RESUME) {
                // We broadcast an intent or rely on MainViewModel's polling loop
                // Actually, an easier way is to trigger it inside MainNavigation via a LaunchedEffect listening to lifecycle.
                // Let's just dispatch an action to ViewModel or keep it simple.
            }
        }
    })

    setContent {
      SkillSyncTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
