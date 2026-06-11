package com.guideme.travel.domain.analytics

interface GuideMeAnalytics {
    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap())
    fun logScreen(screenName: String, screenClass: String? = null)
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
    fun logBreadcrumb(message: String)
    fun recordNonFatal(throwable: Throwable, context: Map<String, Any?> = emptyMap())
}
