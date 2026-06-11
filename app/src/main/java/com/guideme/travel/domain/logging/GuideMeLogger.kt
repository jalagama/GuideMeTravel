package com.guideme.travel.domain.logging

interface GuideMeLogger {
    fun debug(tag: String, message: String, metadata: Map<String, Any?> = emptyMap())
    fun info(tag: String, message: String, metadata: Map<String, Any?> = emptyMap())
    fun warn(
        tag: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    )
    fun error(
        tag: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    )

    fun logCacheHit(scope: String, key: String, metadata: Map<String, Any?> = emptyMap())
    fun logCacheMiss(scope: String, key: String, metadata: Map<String, Any?> = emptyMap())
    fun logRemoteRequest(functionName: String, payload: Map<String, Any?>)
    fun logRemoteResponse(functionName: String, summary: Map<String, Any?>)
    fun logRemoteError(functionName: String, error: Throwable, payload: Map<String, Any?> = emptyMap())
    fun logLocalUpsert(entity: String, key: String, metadata: Map<String, Any?> = emptyMap())
    fun logBackgroundWork(workName: String, message: String, metadata: Map<String, Any?> = emptyMap())
}

object LogTags {
    const val CURATED_REPO = "CuratedRepo"
    const val CURATED_LOCAL = "CuratedLocal"
    const val CURATED_REMOTE = "CuratedRemote"
    const val CATALOG_PREFETCH = "CatalogPrefetch"
    const val HOME_VM = "HomeViewModel"
    const val GENRE_VM = "GenreViewModel"
    const val PACKAGE_DETAIL_VM = "PackageDetailVM"
    const val BOOK_TRIP_VM = "BookTripViewModel"
}
