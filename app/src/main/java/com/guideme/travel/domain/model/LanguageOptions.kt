package com.guideme.travel.domain.model

object LanguageOptions {
    val supported = listOf(
        SupportedLanguage("en", "English"),
        SupportedLanguage("hi", "Hindi"),
        SupportedLanguage("es", "Spanish"),
        SupportedLanguage("fr", "French"),
        SupportedLanguage("de", "German"),
        SupportedLanguage("zh", "Chinese"),
        SupportedLanguage("ja", "Japanese"),
        SupportedLanguage("ar", "Arabic"),
        SupportedLanguage("pt", "Portuguese"),
        SupportedLanguage("ru", "Russian"),
        SupportedLanguage("it", "Italian"),
        SupportedLanguage("ko", "Korean"),
        SupportedLanguage("bn", "Bengali"),
        SupportedLanguage("ta", "Tamil"),
        SupportedLanguage("te", "Telugu")
    )

    fun isSupported(code: String): Boolean = supported.any { it.code == code }

    fun displayName(code: String): String =
        supported.firstOrNull { it.code == code }?.displayName ?: code
}
