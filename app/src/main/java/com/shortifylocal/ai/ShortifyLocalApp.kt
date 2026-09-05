package com.shortifylocal.ai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Source: androidx-main Configuration.kt — interface Provider { public val workManagerConfiguration: Configuration }
// ATTENTION : la forme `override fun getWorkManagerConfiguration()` des docs officielles est périmée
// et ne compile pas contre WorkManager 2.11.2 — seule la propriété Kotlin est valide (pitfall 2).
@HiltAndroidApp
class ShortifyLocalApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
