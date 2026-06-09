package com.guideme.travel

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

@HiltAndroidApp
class GuideMeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        installAppCheck()

        val mapTilerKey = BuildConfig.MAPTILER_API_KEY
        MapLibre.getInstance(
            this,
            mapTilerKey.ifBlank { "" },
            if (mapTilerKey.isBlank()) WellKnownTileServer.MapLibre else WellKnownTileServer.MapTiler
        )

    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
