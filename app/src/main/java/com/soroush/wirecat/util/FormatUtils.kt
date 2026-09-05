package com.soroush.wirecat.util

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[exp - 1])
    }

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"
}
