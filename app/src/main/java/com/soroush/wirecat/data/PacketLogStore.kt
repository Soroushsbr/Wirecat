package com.soroush.wirecat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PacketDirection { SENT, RECEIVED, BLOCKED }
enum class PacketType { TCP, UDP, ICMP, OTHER }

data class PacketLogEntry(
    val type: PacketType,
    val direction: PacketDirection,
    val timestampMillis: Long,
    val appLabel: String?,
    val sourceAddress: String? = null,
    val destAddress: String? = null,
    val sourcePort: Int? = null,
    val destPort: Int? = null,
    val packageName: String? = null,
    val sizeBytes: Int? = null,
    val ipVersion: Int? = null,
    val id: Long = 0L
) {
    fun formattedTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMillis))

    fun formattedFullTime(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMillis))

    fun formattedRoute(): String {
        val src = sourceAddress ?: return "Unknown source/destination"
        val dst = destAddress ?: return src
        val srcLabel = if (sourcePort != null) "$src:$sourcePort" else src
        val dstLabel = if (destPort != null) "$dst:$destPort" else dst
        return "$srcLabel \u2192 $dstLabel"
    }

    fun formattedSource(): String =
        if (sourceAddress == null) "Unknown"
        else if (sourcePort != null) "$sourceAddress:$sourcePort" else sourceAddress

    fun formattedDest(): String =
        if (destAddress == null) "Unknown"
        else if (destPort != null) "$destAddress:$destPort" else destAddress

    fun formattedDirection(): String = when (direction) {
        PacketDirection.SENT -> "Sent (device \u2192 destination)"
        PacketDirection.RECEIVED -> "Received (destination \u2192 device)"
        PacketDirection.BLOCKED -> "Blocked (never left device)"
    }

    fun formattedSize(): String = if (sizeBytes != null) "$sizeBytes bytes" else "Unknown"

    fun formattedIpVersion(): String = when (ipVersion) {
        4 -> "IPv4"
        6 -> "IPv6"
        else -> ""
    }
}

object PacketLogStore {

    private const val MAX_ENTRIES = 500 // ring buffer - oldest entries drop off so this can't grow forever
    private val entries = ArrayDeque<PacketLogEntry>()
    private val _logFlow = MutableStateFlow<List<PacketLogEntry>>(emptyList())
    val logFlow = _logFlow.asStateFlow()
    private var nextId = 1L

    @Synchronized
    fun add(entry: PacketLogEntry) {
        val withId = entry.copy(id = nextId++)
        entries.addFirst(withId)
        while (entries.size > MAX_ENTRIES) entries.removeLast()
        _logFlow.value = entries.toList()
    }

    @Synchronized
    fun clear() {
        entries.clear()
        _logFlow.value = emptyList()
    }
}
