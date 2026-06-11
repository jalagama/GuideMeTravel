package com.guideme.travel.data.logging

import android.util.Log
import com.guideme.travel.BuildConfig
import com.guideme.travel.data.analytics.sanitizeEventName
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import com.guideme.travel.domain.logging.GuideMeLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGuideMeLogger @Inject constructor(
    private val analytics: GuideMeAnalytics
) : GuideMeLogger {

    private fun formatMessage(message: String, metadata: Map<String, Any?>): String {
        if (metadata.isEmpty()) return message
        val meta = metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }
        return "$message | $meta"
    }

    private fun withTag(tag: String, metadata: Map<String, Any?>): Map<String, Any?> {
        return metadata + mapOf(AnalyticsParams.TAG to tag)
    }

    override fun debug(tag: String, message: String, metadata: Map<String, Any?>) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, formatMessage(message, metadata))
        }
    }

    override fun info(tag: String, message: String, metadata: Map<String, Any?>) {
        Log.i(tag, formatMessage(message, metadata))
        analytics.logEvent(sanitizeEventName(message), withTag(tag, metadata))
        analytics.logBreadcrumb("$tag: $message")
    }

    override fun warn(
        tag: String,
        message: String,
        metadata: Map<String, Any?>,
        throwable: Throwable?
    ) {
        Log.w(tag, formatMessage(message, metadata), throwable)
        analytics.logEvent(
            AnalyticsEvents.APP_WARNING,
            withTag(tag, metadata + mapOf(AnalyticsParams.MESSAGE to message))
        )
        if (throwable != null) {
            analytics.recordNonFatal(throwable, withTag(tag, metadata))
        } else {
            analytics.logBreadcrumb("$tag: $message")
        }
    }

    override fun error(
        tag: String,
        message: String,
        metadata: Map<String, Any?>,
        throwable: Throwable?
    ) {
        Log.e(tag, formatMessage(message, metadata), throwable)
        analytics.logEvent(
            AnalyticsEvents.APP_ERROR,
            withTag(tag, metadata + mapOf(AnalyticsParams.MESSAGE to message))
        )
        if (throwable != null) {
            analytics.recordNonFatal(throwable, withTag(tag, metadata))
        } else {
            analytics.logBreadcrumb("$tag: $message")
        }
    }

    override fun logCacheHit(scope: String, key: String, metadata: Map<String, Any?>) {
        info(
            "Cache",
            AnalyticsEvents.CACHE_HIT,
            metadata + mapOf(
                AnalyticsParams.CACHE_SCOPE to scope,
                AnalyticsParams.CACHE_KEY to key
            )
        )
    }

    override fun logCacheMiss(scope: String, key: String, metadata: Map<String, Any?>) {
        info(
            "Cache",
            AnalyticsEvents.CACHE_MISS,
            metadata + mapOf(
                AnalyticsParams.CACHE_SCOPE to scope,
                AnalyticsParams.CACHE_KEY to key
            )
        )
    }

    override fun logRemoteRequest(functionName: String, payload: Map<String, Any?>) {
        debug(
            "Remote",
            AnalyticsEvents.REMOTE_REQUEST,
            mapOf(AnalyticsParams.FUNCTION_NAME to functionName, "payload" to payload)
        )
        analytics.logEvent(
            AnalyticsEvents.REMOTE_REQUEST,
            mapOf(AnalyticsParams.FUNCTION_NAME to functionName, AnalyticsParams.COUNT to payload.size)
        )
    }

    override fun logRemoteResponse(functionName: String, summary: Map<String, Any?>) {
        debug(
            "Remote",
            AnalyticsEvents.REMOTE_RESPONSE,
            mapOf(AnalyticsParams.FUNCTION_NAME to functionName, "summary" to summary)
        )
        analytics.logEvent(
            AnalyticsEvents.REMOTE_RESPONSE,
            mapOf(AnalyticsParams.FUNCTION_NAME to functionName, AnalyticsParams.COUNT to summary.size)
        )
    }

    override fun logRemoteError(functionName: String, error: Throwable, payload: Map<String, Any?>) {
        error(
            "Remote",
            AnalyticsEvents.REMOTE_ERROR,
            mapOf(AnalyticsParams.FUNCTION_NAME to functionName, AnalyticsParams.COUNT to payload.size),
            error
        )
    }

    override fun logLocalUpsert(entity: String, key: String, metadata: Map<String, Any?>) {
        debug(
            "Local",
            AnalyticsEvents.LOCAL_UPSERT,
            metadata + mapOf(AnalyticsParams.ENTITY to entity, AnalyticsParams.CACHE_KEY to key)
        )
        analytics.logEvent(
            AnalyticsEvents.LOCAL_UPSERT,
            metadata + mapOf(AnalyticsParams.ENTITY to entity, AnalyticsParams.CACHE_KEY to key)
        )
    }

    override fun logBackgroundWork(workName: String, message: String, metadata: Map<String, Any?>) {
        info(
            "Worker",
            message,
            metadata + mapOf(AnalyticsParams.WORK_NAME to workName)
        )
    }
}
