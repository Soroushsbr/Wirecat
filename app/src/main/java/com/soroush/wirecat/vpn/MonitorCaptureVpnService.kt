package com.soroush.wirecat.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.soroush.wirecat.R
import com.soroush.wirecat.data.PacketDirection
import com.soroush.wirecat.data.PacketLogEntry
import com.soroush.wirecat.data.PacketLogStore
import com.soroush.wirecat.data.PacketType
import com.soroush.wirecat.ui.HostActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MonitorCaptureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var outputStream: FileOutputStream? = null
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var readJob: Job? = null
    private var cleanupJob: Job? = null

    private val tcpFlows = ConcurrentHashMap<FlowKey, TcpFlow>()
    private val udpFlows = ConcurrentHashMap<FlowKey, UdpFlow>()

    private data class FlowKey(val protocol: Int, val srcPort: Int, val destIp: String, val destPort: Int)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (vpnInterface == null) {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                return START_NOT_STICKY
            }
            else -> startCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession(getString(R.string.app_name) + " Monitor Capture")
            .addAddress(TUN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .setMtu(MTU)
            .setBlocking(true)

        try {
            builder.addDisallowedApplication(packageName) // don't route our own traffic through ourselves
        } catch (e: Exception) {  }

        vpnInterface = builder.establish() ?: run {
            stopSelf()
            return
        }
        outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

        startForeground(NOTIFICATION_ID, buildNotification())
        _isCapturing.value = true

        readJob = scope.launch { readLoop() }
        cleanupJob = scope.launch { cleanupLoop() }
    }

    private fun stopCapture() {
        readJob?.cancel()
        cleanupJob?.cancel()
        readJob = null
        cleanupJob = null

        for (flow in tcpFlows.values) flow.close()
        tcpFlows.clear()
        for (flow in udpFlows.values) flow.close()
        udpFlows.clear()

        try { vpnInterface?.close() } catch (e: Exception) {  }
        vpnInterface = null
        outputStream = null
        _isCapturing.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun readLoop() {
        val fd = vpnInterface ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val buffer = ByteArray(MTU + 40)
        var consecutiveErrors = 0
        while (vpnInterface != null) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                -1
            }
            if (length <= 0) {
                consecutiveErrors++
                if (consecutiveErrors > 20) break
                continue
            }
            consecutiveErrors = 0
            try {
                handlePacket(buffer.copyOf(length))
            } catch (e: Exception) {

            }
        }
    }

    private suspend fun handlePacket(packet: ByteArray) {
        if (packet.size < 20) return
        val versionAndIhl = packet[0].toInt()
        if ((versionAndIhl and 0xF0) shr 4 != 4) return // IPv6 not supported by this relay
        val ihl = (versionAndIhl and 0x0F) * 4
        if (packet.size < ihl) return
        val protocol = packet[9].toInt() and 0xFF
        val sourceIp = ipBytes(packet, 12)
        val destIp = ipBytes(packet, 16)
        val destIpString = ipString(destIp)

        when (protocol) {
            6 -> handleTcp(packet, ihl, sourceIp, destIp, destIpString)
            17 -> handleUdp(packet, ihl, sourceIp, destIp, destIpString)
            else -> {

                PacketLogStore.add(
                    PacketLogEntry(PacketType.OTHER, PacketDirection.SENT, System.currentTimeMillis(), null,
                        sourceAddress = ipString(sourceIp), destAddress = destIpString, ipVersion = 4)
                )
            }
        }
    }

    private suspend fun handleUdp(packet: ByteArray, ihl: Int, sourceIp: ByteArray, destIp: ByteArray, destIpString: String) {
        if (packet.size < ihl + 8) return
        val srcPort = readUInt16(packet, ihl)
        val destPort = readUInt16(packet, ihl + 2)
        val payloadOffset = ihl + 8
        val payload = packet.copyOfRange(payloadOffset, packet.size)

        val key = FlowKey(17, srcPort, destIpString, destPort)
        var flow = udpFlows[key]
        if (flow == null) {
            if (udpFlows.size >= MAX_FLOWS) return
            val appLabel = resolveOwningApp(17, ipString(sourceIp), srcPort, destIpString, destPort)
            flow = UdpFlow(this, srcPort, destIpString, destPort) { data ->
                writeUdpToTun(destIpBytes = sourceIp, destPort = srcPort, sourceIpBytes = destIp, sourcePort = destPort, payload = data, appLabel = appLabel)
            }
            flow.appLabel = appLabel
            udpFlows[key] = flow
            flow.start(scope)
        }
        flow.send(payload)
        logPacket(PacketType.UDP, PacketDirection.SENT, ipString(sourceIp), srcPort, destIpString, destPort, flow.appLabel, sizeBytes = payload.size)
    }

    private suspend fun writeUdpToTun(destIpBytes: ByteArray, destPort: Int, sourceIpBytes: ByteArray, sourcePort: Int, payload: ByteArray, appLabel: String?) {
        val packet = PacketBuilder.buildUdp(sourceIpBytes, sourcePort, destIpBytes, destPort, payload)
        writeToTun(packet)
        logPacket(PacketType.UDP, PacketDirection.RECEIVED, ipString(sourceIpBytes), sourcePort, ipString(destIpBytes), destPort, appLabel, sizeBytes = payload.size)
    }

    private suspend fun handleTcp(packet: ByteArray, ihl: Int, sourceIp: ByteArray, destIp: ByteArray, destIpString: String) {
        if (packet.size < ihl + 20) return
        val srcPort = readUInt16(packet, ihl)
        val destPort = readUInt16(packet, ihl + 2)
        val seq = readUInt32(packet, ihl + 4)
        val ack = readUInt32(packet, ihl + 8)
        val dataOffset = ((packet[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val flags = packet[ihl + 13].toInt() and 0xFF
        val payloadOffset = ihl + dataOffset
        val payload = if (payloadOffset < packet.size) packet.copyOfRange(payloadOffset, packet.size) else ByteArray(0)

        // standard TCP header flag bits
        val syn = flags and 0x02 != 0
        val ackFlag = flags and 0x10 != 0
        val fin = flags and 0x01 != 0
        val rst = flags and 0x04 != 0

        val key = FlowKey(6, srcPort, destIpString, destPort)

        if (rst) { // connection reset - tear the flow down immediately
            tcpFlows.remove(key)?.close()
            logPacket(PacketType.TCP, PacketDirection.SENT, ipString(sourceIp), srcPort, destIpString, destPort, null)
            return
        }

        if (syn && !ackFlag) { // start of a new outbound TCP connection
            if (tcpFlows.containsKey(key)) return
            if (tcpFlows.size >= MAX_FLOWS) return
            val appLabel = resolveOwningApp(6, ipString(sourceIp), srcPort, destIpString, destPort)
            val flow = TcpFlow(
                service = this,
                localIp = sourceIp,
                localPort = srcPort,
                remoteIp = destIp,
                remoteIpString = destIpString,
                remotePort = destPort,
                clientIsn = seq,
                writeToTun = { data -> writeToTun(data) },
                onLog = { direction, size -> logPacket(PacketType.TCP, direction, ipString(sourceIp), srcPort, destIpString, destPort, appLabel, sizeBytes = size) },
                onClosed = { tcpFlows.remove(key) }
            )
            tcpFlows[key] = flow
            flow.start(scope)
            logPacket(PacketType.TCP, PacketDirection.SENT, ipString(sourceIp), srcPort, destIpString, destPort, appLabel, sizeBytes = payload.size)
            return
        }

        val flow = tcpFlows[key] ?: run {
            // packet for a connection we're not tracking - reject it instead of silently dropping
            if (!fin) {
                writeToTun(PacketBuilder.buildTcpControl(destIp, destPort, sourceIp, srcPort, ack, seq + 1, rstFlag = true, ackFlag = true))
            }
            return
        }
        logPacket(PacketType.TCP, PacketDirection.SENT, ipString(sourceIp), srcPort, destIpString, destPort, flow.appLabel, sizeBytes = payload.size)
        flow.onIncoming(seq, ack, fin, payload)
    }

    private suspend fun writeToTun(data: ByteArray) {
        writeMutex.withLock {
            try {
                outputStream?.write(data)
            } catch (e: Exception) {

            }
        }
    }

    private fun logPacket(type: PacketType, direction: PacketDirection, srcIp: String, srcPort: Int, dstIp: String, dstPort: Int, appLabel: String?, sizeBytes: Int? = null) {
        PacketLogStore.add(
            PacketLogEntry(
                type = type,
                direction = direction,
                timestampMillis = System.currentTimeMillis(),
                appLabel = appLabel,
                sourceAddress = srcIp,
                destAddress = dstIp,
                sourcePort = srcPort,
                destPort = dstPort,
                sizeBytes = sizeBytes,
                ipVersion = 4
            )
        )
    }

    private fun resolveOwningApp(protocol: Int, sourceIp: String, sourcePort: Int, destIp: String, destPort: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null // getConnectionOwnerUid needs API 29+
        return try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return null
            val local = InetSocketAddress(sourceIp, sourcePort)
            val remote = InetSocketAddress(destIp, destPort)
            val uid = cm.getConnectionOwnerUid(protocol, local, remote)
            if (uid <= 0) return null
            val pkgs = packageManager.getPackagesForUid(uid) ?: return null
            val pkg = pkgs.firstOrNull() ?: return null
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun cleanupLoop() {
        while (vpnInterface != null) {
            delay(15_000) // periodic sweep for idle flows, so they don't leak forever
            val now = System.currentTimeMillis()
            tcpFlows.entries.removeAll { (_, flow) ->
                val stale = flow.isStale(now)
                if (stale) flow.close()
                stale
            }
            udpFlows.entries.removeAll { (_, flow) ->
                val stale = flow.isStale(now)
                if (stale) flow.close()
                stale
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Packet capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, HostActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopCapture()
        super.onRevoke()
    }

    companion object {
        const val ACTION_STOP = "com.soroush.wirecat.action.STOP_CAPTURE"
        private const val CHANNEL_ID = "wirecat_capture"
        private const val NOTIFICATION_ID = 3001
        private const val TUN_ADDRESS = "10.11.11.2"
        private const val MTU = 1500
        private const val MAX_FLOWS = 200 // cap so one chatty app can't exhaust memory/sockets

        private val _isCapturing = MutableStateFlow(false)
        val isCapturing = _isCapturing.asStateFlow()
    }
}

internal fun ipBytes(packet: ByteArray, offset: Int): ByteArray =
    byteArrayOf(packet[offset], packet[offset + 1], packet[offset + 2], packet[offset + 3])

internal fun ipString(ip: ByteArray): String =
    "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"

internal fun readUInt16(packet: ByteArray, offset: Int): Int =
    ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

internal fun readUInt32(packet: ByteArray, offset: Int): Long {
    var v = 0L
    for (i in 0 until 4) v = (v shl 8) or (packet[offset + i].toLong() and 0xFF)
    return v
}
