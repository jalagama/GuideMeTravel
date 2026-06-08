package com.guideme.travel.domain.usecase

import com.guideme.travel.domain.model.TripPlan
import com.guideme.travel.domain.model.TripStatus
import com.guideme.travel.domain.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateTripUseCaseTest {

    private val tripRepository: TripRepository = mockk()
    private val useCase = CreateTripUseCase(tripRepository)

    @Test
    fun invoke_delegatesToRepository() = runTest {
        val expected = TripPlan(
            id = "trip-1",
            origin = "Bangalore",
            destination = "Hampi",
            languageCode = "en",
            status = TripStatus.READY,
            attractions = emptyList(),
            createdAtMillis = 1L
        )
        coEvery {
            tripRepository.createTrip("Bangalore", "Hampi", "en")
        } returns expected

        val result = useCase("Bangalore", "Hampi", "en")

        assertEquals(expected, result)
        coVerify { tripRepository.createTrip("Bangalore", "Hampi", "en") }
    }
}
