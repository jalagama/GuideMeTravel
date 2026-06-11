package com.guideme.travel.analytics

import com.guideme.travel.domain.analytics.GuideMeAnalytics

data class AnalyticsRecord(
    val type: String,
    val name: String,
    val params: Map<String, Any?>,
    val throwable: Throwable? = null
)

class InMemoryGuideMeAnalytics : GuideMeAnalytics {
    val records = mutableListOf<AnalyticsRecord>()
    var trackedUserId: String? = null
    val userProperties = mutableMapOf<String, String?>()
    val breadcrumbs = mutableListOf<String>()

    override fun logEvent(eventName: String, params: Map<String, Any?>) {
        records += AnalyticsRecord("event", eventName, params)
    }

    override fun logScreen(screenName: String, screenClass: String?) {
        records += AnalyticsRecord(
            "screen",
            screenName,
            mapOf("screen_class" to screenClass)
        )
    }

    override fun setUserId(userId: String?) {
        trackedUserId = userId
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
    }

    override fun logBreadcrumb(message: String) {
        breadcrumbs += message
    }

    override fun recordNonFatal(throwable: Throwable, context: Map<String, Any?>) {
        records += AnalyticsRecord("non_fatal", throwable::class.simpleName.orEmpty(), context, throwable)
    }

    fun clear() {
        records.clear()
        breadcrumbs.clear()
        userProperties.clear()
        trackedUserId = null
    }
}
