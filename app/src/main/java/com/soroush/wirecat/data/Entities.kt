package com.soroush.wirecat.data

enum class SessionType { MONITOR, TUNNEL }

data class SessionEntity(
    val id: Long = 0,
    val type: SessionType,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val totalBytes: Long
)

data class AppUsageEntity(
    val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val appLabel: String,
    val bytes: Long
)

data class PacketEntity(
    val id: Long = 0,
    val sessionId: Long,
    val type: PacketType,
    val direction: PacketDirection,
    val timestampMillis: Long,
    val appLabel: String?,
    val sourceAddress: String?,
    val destAddress: String?,
    val sourcePort: Int?,
    val destPort: Int?,
    val sizeBytes: Int?,
    val ipVersion: Int?
) {
    fun toLogEntry(): PacketLogEntry = PacketLogEntry(
        type = type,
        direction = direction,
        timestampMillis = timestampMillis,
        appLabel = appLabel,
        sourceAddress = sourceAddress,
        destAddress = destAddress,
        sourcePort = sourcePort,
        destPort = destPort,
        sizeBytes = sizeBytes,
        ipVersion = ipVersion,
        id = id
    )
}

data class SessionWithApps(
    val session: SessionEntity,
    val apps: List<AppUsageEntity>,
    val packetCount: Int = 0
)

data class LiveAppUsage(
    val packageName: String,
    val appLabel: String,
    var bytes: Long
)
