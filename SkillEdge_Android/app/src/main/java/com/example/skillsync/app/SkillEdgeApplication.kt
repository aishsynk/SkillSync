package com.example.skillsync.app

import android.app.Application
import androidx.work.Configuration
import com.example.skillsync.core.sync.SyncCoordinator
import com.example.skillsync.core.sync.SyncScheduler

class SkillEdgeApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        SyncCoordinator.initialize(this)
        SyncScheduler.start(this)
    }
}
