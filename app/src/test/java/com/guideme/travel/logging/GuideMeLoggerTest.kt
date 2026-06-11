package com.guideme.travel.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideMeLoggerTest {
    @Test
    fun inMemoryLogger_recordsRemoteAndCacheEvents() {
        val logger = InMemoryGuideMeLogger()
        logger.logRemoteRequest("getCountryGenres", mapOf("countryCode" to "IN"))
        logger.logRemoteResponse("getCountryGenres", mapOf("genreCount" to 12))
        logger.logCacheHit("countryGenres", "IN")
        logger.logLocalUpsert("TourPackageDetail", "in-goa")

        assertEquals(4, logger.records.size)
        assertTrue(logger.records.any { it.message == "remote_request" })
        assertTrue(logger.records.any { it.message == "cache_hit" })
    }
}
