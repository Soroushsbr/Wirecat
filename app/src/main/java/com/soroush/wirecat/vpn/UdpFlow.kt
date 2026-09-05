package com.soroush.wirecat.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

// relays one UDP "session" (UDP is connectionless, but flows are still tracked by src/dst/port)
// through a real DatagramSocket while the app talks raw UDP via the tun interface
class UdpFlow(
    private val service: MonitorCaptureVpnService,
    private val localPort: Int,
    private val remoteIpString: String,
    private val remotePort: Int,
    private val onResponse: suspend (ByteArray) -> Unit
) {
    var appLabel: String? = null
    private var socket: DatagramSocket? = null
    private var readerJob: Job? = null
    @Volatile private var lastActivity = System.currentTimeMillis()

    fun start(scope: CoroutineScope) {
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val s = DatagramSocket()
                service.protect(s) // exclude this socket from the VPN, or it would try to route through itself
                socket = s
                val remoteAddress = InetSocketAddress(remoteIpString, remotePort)
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    lastActivity = System.currentTimeMillis()
                    onResponse(packet.data.copyOfRange(packet.offset, packet.offset + packet.length))
                }
            } catch (e: Exception) {

            }
        }
    }

    fun send(payload: ByteArray) {
        lastActivity = System.currentTimeMillis()
        val s = socket ?: return
        try {
            s.send(DatagramPacket(payload, payload.size, InetSocketAddress(remoteIpString, remotePort)))
        } catch (e: Exception) {

        }
    }

    fun isStale(now: Long): Boolean = now - lastActivity > IDLE_TIMEOUT_MS

    fun close() {
        readerJob?.cancel()
        try { socket?.close() } catch (e: Exception) {  }
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 60_000L
    }
}
