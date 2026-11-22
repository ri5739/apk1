package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

fun configureAppCheck(context: Context) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            // The SDK generates a debug token and prints it to Logcat on first launch.
            // Register that token in Firebase Console > App Check > Debug tokens.
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.i(TAG, "Debug App Check provider installed. Register the SDK-generated debug token in Firebase Console.")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES, "string", context.packageName
            )
            if (resId == 0) {
                Log.e(TAG, "Missing recaptcha_site_key resource. Release App Check is not configured.")
                return
            }
            val siteKey = context.getString(resId)
            if (siteKey.isBlank()) {
                Log.e(TAG, "recaptcha_site_key resource is blank. Release App Check is not configured.")
                return
            }
            appCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.i(TAG, "Release Recaptcha App Check provider installed.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
