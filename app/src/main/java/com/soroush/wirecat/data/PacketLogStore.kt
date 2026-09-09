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
