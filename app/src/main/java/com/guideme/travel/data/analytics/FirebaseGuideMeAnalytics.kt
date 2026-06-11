package com.guideme.travel.data.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.guideme.travel.domain.analytics.AnalyticsEvents
import com.guideme.travel.domain.analytics.AnalyticsParams
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGuideMeAnalytics @Inject constructor(
    @ApplicationContext context: Context
) : GuideMeAnalytics {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun logEvent(eventName: String, params: Map<String, Any?>) {
        val sanitizedName = sanitizeEventName(eventName)
        val bundle = params.toAnalyticsBundle()
        firebaseAnalytics.logEvent(sanitizedName, bundle)
    }

    override fun logScreen(screenName: String, screenClass: String?) {
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(AnalyticsParams.SCREEN_NAME, screenName.take(100))
            param(AnalyticsParams.SCREEN_CLASS, (screenClass ?: screenName).take(100))
        }
        logBreadcrumb("screen:$screenName")
    }

    override fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
        crashlytics.setUserId(userId ?: "")
    }

    override fun setUserProperty(name: String, value: String?) {
        firebaseAnalytics.setUserProperty(sanitizeParamName(name), value?.take(36))
    }

    override fun logBreadcrumb(message: String) {
        crashlytics.log(message.take(500))
    }

    override fun recordNonFatal(throwable: Throwable, context: Map<String, Any?>) {
        context.forEach { (key, value) ->
            if (value != null) {
                setCrashlyticsCustomKey(key, value)
            }
        }
        crashlytics.recordException(throwable)
        logEvent(
            AnalyticsEvents.APP_ERROR,
            context + mapOf(
                AnalyticsParams.ERROR_MESSAGE to (throwable.message ?: throwable::class.simpleName)
            )
        )
    }

    private fun setCrashlyticsCustomKey(key: String, value: Any) {
        val sanitizedKey = sanitizeParamName(key)
        when (value) {
            is Boolean -> crashlytics.setCustomKey(sanitizedKey, value)
            is Int -> crashlytics.setCustomKey(sanitizedKey, value)
            is Long -> crashlytics.setCustomKey(sanitizedKey, value)
            is Float -> crashlytics.setCustomKey(sanitizedKey, value)
            is Double -> crashlytics.setCustomKey(sanitizedKey, value)
            else -> crashlytics.setCustomKey(sanitizedKey, value.toString().take(100))
        }
    }
}
