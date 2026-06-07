package com.guideme.travel.data.local

import com.guideme.travel.domain.model.Attraction
import com.guideme.travel.domain.model.AttractionStatus
import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus

fun TripEntity.toDomain(attractions: List<Attraction>): TripPlan = TripPlan(
    id = id,
    origin = origin,
    destination = destination,
    languageCode = languageCode,
    status = TripStatus.valueOf(status),
    attractions = attractions,
    createdAtMillis = createdAtMillis,
    offlinePackSizeMb = offlinePackSizeMb,
    offlinePackDownloaded = offlinePackDownloaded
)

fun AttractionEntity.toDomain(): Attraction = Attraction(
    id = id,
    name = name,
    description = description,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    orderIndex = orderIndex,
    status = AttractionStatus.valueOf(status),
    audioLocalPath = audioLocalPath,
    transcript = transcript,
    estimatedMinutes = estimatedMinutes
)

fun TripPlan.toEntity(): TripEntity = TripEntity(
    id = id,
    origin = origin,
    destination = destination,
    languageCode = languageCode,
    status = status.name,
    createdAtMillis = createdAtMillis,
    offlinePackSizeMb = offlinePackSizeMb,
    offlinePackDownloaded = offlinePackDownloaded
)

fun Attraction.toEntity(tripId: String): AttractionEntity = AttractionEntity(
    id = id,
    tripId = tripId,
    name = name,
    description = description,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    orderIndex = orderIndex,
    status = status.name,
    audioLocalPath = audioLocalPath,
    transcript = transcript,
    estimatedMinutes = estimatedMinutes
)
