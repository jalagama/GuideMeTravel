package com.guideme.travel.data.analytics

import android.os.Bundle

private const val MAX_EVENT_NAME_LENGTH = 40
private const val MAX_PARAM_NAME_LENGTH = 40
private const val MAX_STRING_VALUE_LENGTH = 100

fun sanitizeEventName(raw: String): String {
    val normalized = raw
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .trim('_')
        .ifBlank { "event" }
    val startsWithLetter = if (normalized.first().isDigit()) "e_$normalized" else normalized
    return startsWithLetter.take(MAX_EVENT_NAME_LENGTH)
}

fun sanitizeParamName(raw: String): String {
    val normalized = raw
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .trim('_')
        .ifBlank { "param" }
    val startsWithLetter = if (normalized.first().isDigit()) "p_$normalized" else normalized
    return startsWithLetter.take(MAX_PARAM_NAME_LENGTH)
}

fun Map<String, Any?>.toAnalyticsBundle(): Bundle {
    val bundle = Bundle()
    for ((key, value) in this) {
        if (value == null) continue
        val paramKey = sanitizeParamName(key)
        when (value) {
            is String -> bundle.putString(paramKey, value.take(MAX_STRING_VALUE_LENGTH))
            is Int -> bundle.putLong(paramKey, value.toLong())
            is Long -> bundle.putLong(paramKey, value)
            is Float -> bundle.putDouble(paramKey, value.toDouble())
            is Double -> bundle.putDouble(paramKey, value)
            is Boolean -> bundle.putString(paramKey, value.toString())
            is Number -> bundle.putLong(paramKey, value.toLong())
            else -> bundle.putString(paramKey, value.toString().take(MAX_STRING_VALUE_LENGTH))
        }
        if (bundle.size() >= 25) break
    }
    return bundle
}
