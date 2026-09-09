package com.soroush.wirecat.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.soroush.wirecat.R
import com.soroush.wirecat.data.LiveAppUsage
import com.soroush.wirecat.data.PacketDirection
import com.soroush.wirecat.data.PacketLogEntry
import com.soroush.wirecat.data.PacketLogStore
import com.soroush.wirecat.data.PacketType
import com.soroush.wirecat.data.SessionEntity
import com.soroush.wirecat.data.SessionType
import com.soroush.wirecat.data.WirecatDbHelper
import com.soroush.wirecat.ui.HostActivity
import com.soroush.wirecat.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream

class TunnelVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var readJob: Job? = null

    private var sessionStart = 0L

    private val sessionPackets = ArrayList<PacketLogEntry>()
    private val sessionAppCounts = LinkedHashMap<String, AppCount>()
    private var blockedPacketCount = 0L

    private data class AppCount(val packageName: String, val label: String, var count: Long)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (vpnInterface == null) {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        when (intent?.action) {
            // stopService() never reaches onStartCommand, so Stop must go through an action intent instead
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (vpnInterface != null) return
        val prefs = PrefsManager(applicationContext)
        val blocked = prefs.tunnelBlockedPackages
        if (blocked.isEmpty()) {
            stopSelf()
            return
        }

        // Only these apps get routed through the tunnel; everything else bypasses it untouched
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.10.10.2", 32)
            .addRoute("0.0.0.0", 0)
            .setBlocking(true)

        for (pkg in blocked) {
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {

            }
        }

        // Android allows only one active VpnService at a time, so this fails if another VPN is running
        vpnInterface = builder.establish() ?: run {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        prefs.isTunnelActive = true
        _isTunnelRunning.value = true

        sessionStart = System.currentTimeMillis()
        sessionPackets.clear()
        sessionAppCounts.clear()
        blockedPacketCount = 0L

        readJob = scope.launch { readAndDropLoop() }
    }

    private fun readAndDropLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val packet = ByteArray(32 * 1024)
        var consecutiveErrors = 0
        while (vpnInterface != null) {
            // We never write anything back to the tun fd, which is what actually blocks the traffic
            val length = try {
                input.read(packet)
            } catch (e: Exception) {
                -1
            }
            if (length <= 0) {
                consecutiveErrors++
                if (consecutiveErrors > 20) {
                    // interface is likely dead, stop instead of spinning the CPU forever
                    break
                }
                continue
            }
            consecutiveErrors = 0

            blockedPacketCount++
            logPacket(packet, length)
        }
    }

    private fun logPacket(packet: ByteArray, length: Int) {
        if (length < 1) return
        val versionAndIhl = packet[0].toInt()
        val version = (versionAndIhl and 0xF0) shr 4

        val parsed = when (version) {
            4 -> parseIpv4Header(packet, length)
            6 -> parseIpv6Header(packet, length)
            else -> null
        }

        if (parsed == null) {
            // not IPv4 or IPv6 (or too short to have a full header) - logged with no address info
            val entry = PacketLogEntry(PacketType.OTHER, PacketDirection.BLOCKED, System.currentTimeMillis(), null, sizeBytes = length)
            PacketLogStore.add(entry)
            sessionPackets.add(entry)
            return
        }

        val resolvedApp = resolveOwningApp(parsed.protocol, parsed.sourceIp, parsed.sourcePort, parsed.destIp, parsed.destPort)

        val entry = PacketLogEntry(
            type = parsed.type,
            direction = PacketDirection.BLOCKED,
            timestampMillis = System.currentTimeMillis(),
            appLabel = resolvedApp?.label,
            sourceAddress = parsed.sourceIp,
            destAddress = parsed.destIp,
            sourcePort = parsed.sourcePort,
            destPort = parsed.destPort,
            packageName = resolvedApp?.packageName,
            sizeBytes = length,
            ipVersion = parsed.ipVersion
        )
        PacketLogStore.add(entry)
        sessionPackets.add(entry)

        if (resolvedApp != null) {
            val existing = sessionAppCounts[resolvedApp.packageName]
            if (existing != null) {
                existing.count++
            } else {
                sessionAppCounts[resolvedApp.packageName] = AppCount(resolvedApp.packageName, resolvedApp.label, 1)
            }
        }
    }

    private data class ParsedHeader(
        val type: PacketType,
        val protocol: Int,
        val sourceIp: String,
        val destIp: String,
        val sourcePort: Int?,
        val destPort: Int?,
        val ipVersion: Int
    )

    private fun parseIpv4Header(packet: ByteArray, length: Int): ParsedHeader? {
        if (length < 20) return null // 20 bytes is the minimum possible IPv4 header
        val versionAndIhl = packet[0].toInt()
        val ihl = (versionAndIhl and 0x0F) * 4 // header length is variable, given in 4-byte words
        val protocol = packet[9].toInt() and 0xFF
        val sourceIp = ipv4ToString(packet, 12)
        val destIp = ipv4ToString(packet, 16)

        var sourcePort: Int? = null
        var destPort: Int? = null
        if ((protocol == 6 || protocol == 17) && length >= ihl + 4) {
            sourcePort = readUInt16(packet, ihl)
            destPort = readUInt16(packet, ihl + 2)
        }

        return ParsedHeader(protocolToType(protocol), protocol, sourceIp, destIp, sourcePort, destPort, ipVersion = 4)
    }

    private fun parseIpv6Header(packet: ByteArray, length: Int): ParsedHeader? {
        if (length < 40) return null // IPv6's fixed header is always exactly 40 bytes
        val nextHeader = packet[6].toInt() and 0xFF // same protocol numbers as IPv4's protocol field
        val sourceIp = ipv6ToString(packet, 8)
        val destIp = ipv6ToString(packet, 24)

        var sourcePort: Int? = null
        var destPort: Int? = null
        if ((nextHeader == 6 || nextHeader == 17) && length >= 40 + 4) {
            sourcePort = readUInt16(packet, 40)
            destPort = readUInt16(packet, 42)
        }

        return ParsedHeader(protocolToType(nextHeader), nextHeader, sourceIp, destIp, sourcePort, destPort, ipVersion = 6)
    }

    private fun protocolToType(protocol: Int): PacketType = when (protocol) {
        6 -> PacketType.TCP
        17 -> PacketType.UDP
        1, 58 -> PacketType.ICMP
        else -> PacketType.OTHER
    }

    private fun ipv4ToString(packet: ByteArray, offset: Int): String {
        return "${packet[offset].toInt() and 0xFF}.${packet[offset + 1].toInt() and 0xFF}." +
            "${packet[offset + 2].toInt() and 0xFF}.${packet[offset + 3].toInt() and 0xFF}"
    }

    private fun ipv6ToString(packet: ByteArray, offset: Int): String {
        return try {
            val bytes = packet.copyOfRange(offset, offset + 16)
            java.net.InetAddress.getByAddress(bytes).hostAddress ?: "Unknown" // lets the JDK format it correctly
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun readUInt16(packet: ByteArray, offset: Int): Int {
        return ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
    }

    private data class ResolvedApp(val packageName: String, val label: String)

    private fun resolveOwningApp(
        protocol: Int,
        sourceIp: String,
        sourcePort: Int?,
        destIp: String,
        destPort: Int?
    ): ResolvedApp? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null // getConnectionOwnerUid needs API 29+
        if (sourcePort == null || destPort == null) return null
        if (protocol != 6 && protocol != 17) return null
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return null
            val local = java.net.InetSocketAddress(sourceIp, sourcePort)
            val remote = java.net.InetSocketAddress(destIp, destPort)
            val uid = cm.getConnectionOwnerUid(protocol, local, remote)
            if (uid <= 0) return null
            val pkgs = packageManager.getPackagesForUid(uid) ?: return null
            val pkg = pkgs.firstOrNull() ?: return null
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            ResolvedApp(pkg, packageManager.getApplicationLabel(appInfo).toString())
        } catch (e: Exception) {
            null
        }
    }

    private fun stopTunnel() {
        readJob?.cancel()
        readJob = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {  }
        vpnInterface = null
        _isTunnelRunning.value = false
        PrefsManager(applicationContext).isTunnelActive = false
        saveSessionToHistory()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveSessionToHistory() {
        if (sessionStart == 0L || blockedPacketCount == 0L) return
        val endTime = System.currentTimeMillis()
        val session = SessionEntity(
            type = SessionType.TUNNEL,
            startTimeMillis = sessionStart,
            endTimeMillis = endTime,
            totalBytes = blockedPacketCount
        )
        val apps = sessionAppCounts.values.map { LiveAppUsage(it.packageName, it.label, it.count) }
        val packets = sessionPackets.toList()
        scope.launch(Dispatchers.IO) {
            WirecatDbHelper.get(applicationContext).saveSession(session, apps, packets)
        }
        sessionStart = 0L
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, getString(R.string.tunnel_channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, HostActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tunnel_title))
            .setContentText(getString(R.string.tunnel_subtitle))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    companion object {
        const val ACTION_STOP = "com.soroush.wirecat.action.STOP_TUNNEL"
        private const val CHANNEL_ID = "wirecat_tunnel"
        private const val NOTIFICATION_ID = 2001

        private val _isTunnelRunning = MutableStateFlow(false)
        val isTunnelRunning = _isTunnelRunning.asStateFlow()
    }
}
