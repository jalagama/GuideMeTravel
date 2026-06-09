package com.guideme.travel

import android.app.Application
import android.util.Log

private const val TAG = "GuideMeAppCheck"

fun Application.installAppCheck() {
    Log.w(
        TAG,
        "App Check is disabled in debug builds. In Firebase Console go to App Check > APIs and " +
            "set Cloud Functions (and Authentication) enforcement to Unenforced while developing."
    )
}
