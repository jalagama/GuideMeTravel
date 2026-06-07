package com.guideme.travel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.guideme.travel.R
import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.util.LocationPermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TripGuideForegroundService : Service() {

    @Inject
    lateinit var geofenceHandler: GeofenceHandler

    private lateinit var geofencingClient: GeofencingClient

    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!LocationPermissionHelper.canStartLocationForegroundService(this)) {
            Log.w(TAG, "Location permission missing; stopping guide service.")
            stopSelf()
            return START_NOT_STICKY
        }

        val tripId = intent?.getStringExtra(EXTRA_TRIP_ID).orEmpty()
        val attractionNames = intent?.getStringArrayExtra(EXTRA_ATTRACTION_NAMES)?.toList().orEmpty()
        val attractionIds = intent?.getStringArrayExtra(EXTRA_ATTRACTION_IDS)?.toList().orEmpty()
        val latitudes = intent?.getDoubleArrayExtra(EXTRA_LATITUDES)?.toList().orEmpty()
        val longitudes = intent?.getDoubleArrayExtra(EXTRA_LONGITUDES)?.toList().orEmpty()

        geofenceHandler.setActiveTrip(tripId)

        val notification = buildNotification(attractionNames.firstOrNull() ?: "Trip active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val attractions = attractionIds.mapIndexed { index, id ->
            Attraction(
                id = id,
                name = attractionNames.getOrElse(index) { "Spot" },
                description = "",
                latitude = latitudes.getOrElse(index) { 0.0 },
                longitude = longitudes.getOrElse(index) { 0.0 },
                orderIndex = index
            )
        }

        registerGeofences(attractions)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        geofencingClient.removeGeofences(GeofenceBroadcastReceiver.pendingIntent(this))
        super.onDestroy()
    }

    private fun registerGeofences(attractions: List<Attraction>) {
        if (!LocationPermissionHelper.hasFineOrCoarseLocation(this)) {
            return
        }

        val geofences = attractions.map { attraction ->
            Geofence.Builder()
                .setRequestId(attraction.id)
                .setCircularRegion(attraction.latitude, attraction.longitude, GEOFENCE_RADIUS_METERS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, GeofenceBroadcastReceiver.pendingIntent(this))
    }

    private fun buildNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.trip_in_progress))
            .setContentText(title)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(GeofenceBroadcastReceiver.openAppIntent(this))
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_trip),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "TripGuideForegroundService"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_ATTRACTION_IDS = "extra_attraction_ids"
        const val EXTRA_ATTRACTION_NAMES = "extra_attraction_names"
        const val EXTRA_LATITUDES = "extra_latitudes"
        const val EXTRA_LONGITUDES = "extra_longitudes"
        private const val CHANNEL_ID = "trip_guide_channel"
        private const val NOTIFICATION_ID = 1001
        private const val GEOFENCE_RADIUS_METERS = 400f
    }
}
