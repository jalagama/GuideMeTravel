package com.guideme.travel.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOptionsTest {

    @Test
    fun supportedLanguages_includeGlobalTopSet() {
        assertTrue(LanguageOptions.isSupported("en"))
        assertTrue(LanguageOptions.isSupported("hi"))
        assertTrue(LanguageOptions.isSupported("ja"))
        assertFalse(LanguageOptions.isSupported("xx"))
    }
}
