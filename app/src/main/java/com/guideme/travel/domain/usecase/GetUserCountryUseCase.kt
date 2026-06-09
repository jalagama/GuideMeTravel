package com.guideme.travel.domain.usecase

import android.location.Geocoder
import com.guideme.travel.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import android.content.Context

class GetUserCountryUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(): String = withContext(Dispatchers.IO) {
        val fromGps = runCatching { resolveFromGps() }.getOrNull()
        if (!fromGps.isNullOrBlank()) return@withContext fromGps.uppercase(Locale.US)

        Locale.getDefault().country.uppercase(Locale.US).ifBlank { "IN" }
    }

    private suspend fun resolveFromGps(): String? {
        val location = locationRepository.getLastLocation() ?: return null
        if (!Geocoder.isPresent()) return null

        @Suppress("DEPRECATION")
        val addresses = Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?: return null

        return addresses.firstOrNull()?.countryCode
    }
}
