package com.guideme.travel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import com.google.android.libraries.places.api.Places
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
}
