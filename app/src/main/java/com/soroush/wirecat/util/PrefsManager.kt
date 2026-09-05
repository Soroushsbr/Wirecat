package com.soroush.wirecat.util

import android.content.Context

class PrefsManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("wirecat_prefs", Context.MODE_PRIVATE)

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var tunnelBlockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_TUNNEL_PACKAGES, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_TUNNEL_PACKAGES, value).apply() }

    var isTunnelActive: Boolean
        get() = prefs.getBoolean(KEY_TUNNEL_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_TUNNEL_ACTIVE, value).apply()

    var isMonitorCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_CAPTURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_CAPTURE_ENABLED, value).apply()

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_TUNNEL_PACKAGES = "tunnel_packages"
        private const val KEY_TUNNEL_ACTIVE = "tunnel_active"
        private const val KEY_MONITOR_CAPTURE_ENABLED = "monitor_capture_enabled"
    }
}
