package com.zmove.jarvis

import android.content.Context

/** Preferências da escuta em tempo integral. */
class WakeWordPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var accessKey: String
        get() = prefs.getString(KEY_ACCESS_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACCESS_KEY, value.trim()).apply()

    var alwaysListening: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, value).apply()

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) = prefs.edit()
            .putFloat(KEY_SENSITIVITY, value.coerceIn(0.1f, 0.9f))
            .apply()

    val hasAccessKey: Boolean
        get() = accessKey.isNotBlank()

    companion object {
        private const val FILE_NAME = "jarvis_wake_word"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_ALWAYS_LISTENING = "always_listening"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val DEFAULT_SENSITIVITY = 0.6f
    }
}
