package com.guideme.travel.logging

import com.guideme.travel.domain.logging.GuideMeLogger

data class LogRecord(
    val level: String,
    val tag: String,
    val message: String,
    val metadata: Map<String, Any?>,
    val throwable: Throwable? = null
)

class InMemoryGuideMeLogger : GuideMeLogger {
    val records = mutableListOf<LogRecord>()

    override fun debug(tag: String, message: String, metadata: Map<String, Any?>) {
        records += LogRecord("debug", tag, message, metadata)
    }

    override fun info(tag: String, message: String, metadata: Map<String, Any?>) {
        records += LogRecord("info", tag, message, metadata)
    }

    override fun warn(
        tag: String,
        message: String,
        metadata: Map<String, Any?>,
        throwable: Throwable?
    ) {
        records += LogRecord("warn", tag, message, metadata, throwable)
    }

    override fun error(
        tag: String,
        message: String,
        metadata: Map<String, Any?>,
        throwable: Throwable?
    ) {
        records += LogRecord("error", tag, message, metadata, throwable)
    }

    override fun logCacheHit(scope: String, key: String, metadata: Map<String, Any?>) {
        info("Cache", "cache_hit", metadata + mapOf("scope" to scope, "key" to key))
    }

    override fun logCacheMiss(scope: String, key: String, metadata: Map<String, Any?>) {
        info("Cache", "cache_miss", metadata + mapOf("scope" to scope, "key" to key))
    }

    override fun logRemoteRequest(functionName: String, payload: Map<String, Any?>) {
        debug("Remote", "remote_request", mapOf("function" to functionName, "payload" to payload))
    }

    override fun logRemoteResponse(functionName: String, summary: Map<String, Any?>) {
        debug("Remote", "remote_response", mapOf("function" to functionName, "summary" to summary))
    }

    override fun logRemoteError(functionName: String, error: Throwable, payload: Map<String, Any?>) {
        error("Remote", "remote_error", mapOf("function" to functionName, "payload" to payload), error)
    }

    override fun logLocalUpsert(entity: String, key: String, metadata: Map<String, Any?>) {
        debug("Local", "local_upsert", metadata + mapOf("entity" to entity, "key" to key))
    }

    override fun logBackgroundWork(workName: String, message: String, metadata: Map<String, Any?>) {
        info("Worker", message, metadata + mapOf("work" to workName))
    }

    fun clear() {
        records.clear()
    }
}
