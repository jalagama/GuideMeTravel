package com.guideme.travel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.android.libraries.places.api.Places
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

@HiltAndroidApp
class GuideMeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private lateinit var cachedWorkManagerConfiguration: Configuration

    override fun onCreate() {
        super.onCreate()
        cachedWorkManagerConfiguration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(this, cachedWorkManagerConfiguration)
        installAppCheck()
        installCrashlytics()

        val mapTilerKey = BuildConfig.MAPTILER_API_KEY
        MapLibre.getInstance(
            this,
            mapTilerKey.ifBlank { "" },
            if (mapTilerKey.isBlank()) WellKnownTileServer.MapLibre else WellKnownTileServer.MapTiler
        )

        val placesKey = BuildConfig.PLACES_API_KEY
        if (placesKey.isNotBlank() && !Places.isInitialized()) {
            Places.initialize(applicationContext, placesKey)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = cachedWorkManagerConfiguration

    private fun installCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE)
        crashlytics.log("app_started")
    }
}
