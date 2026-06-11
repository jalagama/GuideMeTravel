package com.guideme.travel.analytics

import com.guideme.travel.data.analytics.sanitizeEventName
import com.guideme.travel.data.analytics.sanitizeParamName
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsBundleMapperTest {
    @Test
    fun sanitizeEventName_normalizesInvalidCharacters() {
        assertEquals("genres_loaded", sanitizeEventName("genres_loaded"))
        assertEquals("e_123_event", sanitizeEventName("123-event"))
    }

    @Test
    fun sanitizeParamName_normalizesInvalidCharacters() {
        assertEquals("country_code", sanitizeParamName("country_code"))
        assertEquals("p_123_param", sanitizeParamName("123-param"))
    }
}
