package com.soroush.wirecat.vpn

import com.soroush.wirecat.data.PacketDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

// relays one TCP connection: speaks raw TCP back to the app via the tun interface while
// actually forwarding data through a real Socket to the remote host
class TcpFlow(
    private val service: MonitorCaptureVpnService,
    private val localIp: ByteArray,
    private val localPort: Int,
    private val remoteIp: ByteArray,
    private val remoteIpString: String,
    private val remotePort: Int,
    private val clientIsn: Long,
    private val writeToTun: suspend (ByteArray) -> Unit,
    private val onLog: (PacketDirection, Int) -> Unit,
    private val onClosed: () -> Unit
) {
    var appLabel: String? = null

    private var socket: Socket? = null
    private var connectJob: Job? = null
    private var upstreamReaderJob: Job? = null
    private var writerJob: Job? = null
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

    @Volatile private var localSeq: Long = Random.nextLong(0, 0xFFFFFFFFL)
    @Volatile private var remoteSeq: Long = (clientIsn + 1) and 0xFFFFFFFFL // +1 for the SYN, masked to 32 bits (TCP sequence numbers wrap)

    @Volatile private var appFinReceived = false
    @Volatile private var localFinSent = false
    @Volatile private var closed = false
    @Volatile private var lastActivity = System.currentTimeMillis()
    @Volatile private var connectedAt = 0L

    fun start(scope: CoroutineScope) {
        connectJob = scope.launch(Dispatchers.IO) {
            try {
                val s = Socket()
                service.protect(s) // exclude this socket from the VPN, or it would try to route through itself
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    s.connect(InetSocketAddress(remoteIpString, remotePort), CONNECT_TIMEOUT_MS.toInt())
                    true
                }
                if (connected != true) {
                    sendControl(rst = true)
                    finish()
                    return@launch
                }
                socket = s
                connectedAt = System.currentTimeMillis()

                writeToTun(
                    PacketBuilder.buildTcpSegment(
                        remoteIp, remotePort, localIp, localPort,
                        seq = localSeq, ack = remoteSeq, synFlag = true, ackFlag = true
                    )
                )
                onLog(PacketDirection.RECEIVED, 0)
                localSeq = (localSeq + 1) and 0xFFFFFFFFL

                writerJob = scope.launch(Dispatchers.IO) { drainWriteChannel(s) }
                upstreamReaderJob = scope.launch(Dispatchers.IO) { readFromSocket(s) }
            } catch (e: Exception) {
                sendControl(rst = true)
                finish()
            }
        }
    }

    suspend fun onIncoming(seq: Long, ack: Long, fin: Boolean, payload: ByteArray) {
        lastActivity = System.currentTimeMillis()
        if (payload.isNotEmpty()) {
            if (seq == remoteSeq) {
                remoteSeq = (remoteSeq + payload.size) and 0xFFFFFFFFL
                writeChannel.trySend(payload)
                writeToTun(PacketBuilder.buildTcpSegment(remoteIp, remotePort, localIp, localPort, seq = localSeq, ack = remoteSeq, ackFlag = true))
            }

        }
        if (fin) {
            appFinReceived = true
            remoteSeq = (remoteSeq + 1) and 0xFFFFFFFFL
            writeToTun(PacketBuilder.buildTcpSegment(remoteIp, remotePort, localIp, localPort, seq = localSeq, ack = remoteSeq, ackFlag = true))
            onLog(PacketDirection.RECEIVED, 0)
            try { socket?.shutdownOutput() } catch (e: Exception) {  }
            if (localFinSent) finish()
        }
    }

    private suspend fun drainWriteChannel(socket: Socket) {
        try {
            val out = socket.getOutputStream()
            for (chunk in writeChannel) {
                out.write(chunk)
                out.flush()
            }
        } catch (e: Exception) {

        }
    }

    private suspend fun readFromSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val buffer = ByteArray(MAX_SEGMENT_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                lastActivity = System.currentTimeMillis()
                var offset = 0
                while (offset < n) {
                    val chunkSize = minOf(MAX_SEGMENT_SIZE, n - offset)
                    writeToTun(
                        PacketBuilder.buildTcpSegment(
                            remoteIp, remotePort, localIp, localPort,
                            seq = localSeq, ack = remoteSeq, ackFlag = true, pshFlag = true,
                            payload = buffer.copyOfRange(offset, offset + chunkSize)
                        )
                    )
                    onLog(PacketDirection.RECEIVED, chunkSize)
                    localSeq = (localSeq + chunkSize) and 0xFFFFFFFFL
                    offset += chunkSize
                }
            }
        } catch (e: IOException) {

        } catch (e: Exception) {

        } finally {
            if (!closed) {
                writeToTun(PacketBuilder.buildTcpSegment(remoteIp, remotePort, localIp, localPort, seq = localSeq, ack = remoteSeq, finFlag = true, ackFlag = true))
                onLog(PacketDirection.RECEIVED, 0)
                localSeq = (localSeq + 1) and 0xFFFFFFFFL
                localFinSent = true
                if (appFinReceived) finish()
            }
        }
    }

    private suspend fun sendControl(rst: Boolean = false) {
        writeToTun(PacketBuilder.buildTcpControl(remoteIp, remotePort, localIp, localPort, seq = localSeq, ack = remoteSeq, rstFlag = rst, ackFlag = true))
        onLog(PacketDirection.RECEIVED, 0)
    }

    private fun finish() {
        if (closed) return
        closed = true
        close()
        onClosed()
    }

    fun isStale(now: Long): Boolean {
        if (closed) return true
        if (connectedAt == 0L) return now - lastActivity > CONNECT_TIMEOUT_MS * 3
        return now - lastActivity > IDLE_TIMEOUT_MS
    }

    fun close() {
        closed = true
        connectJob?.cancel()
        upstreamReaderJob?.cancel()
        writerJob?.cancel()
        writeChannel.close()
        try { socket?.close() } catch (e: Exception) {  }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val IDLE_TIMEOUT_MS = 4 * 60_000L
        private const val MAX_SEGMENT_SIZE = 1400
    }
}
