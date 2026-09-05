package com.soroush.wirecat.vpn

object PacketBuilder {

    private const val IPV4_HEADER_LEN = 20
    private const val UDP_HEADER_LEN = 8
    private const val TCP_HEADER_LEN = 20

    fun buildUdp(sourceIp: ByteArray, sourcePort: Int, destIp: ByteArray, destPort: Int, payload: ByteArray): ByteArray {
        val totalLength = IPV4_HEADER_LEN + UDP_HEADER_LEN + payload.size
        val packet = ByteArray(totalLength)

        writeIpv4Header(packet, sourceIp, destIp, protocol = 17, totalLength = totalLength)

        val udpOffset = IPV4_HEADER_LEN
        writeUInt16(packet, udpOffset, sourcePort)
        writeUInt16(packet, udpOffset + 2, destPort)
        writeUInt16(packet, udpOffset + 4, UDP_HEADER_LEN + payload.size)
        writeUInt16(packet, udpOffset + 6, 0)
        System.arraycopy(payload, 0, packet, udpOffset + UDP_HEADER_LEN, payload.size)

        return packet
    }

    fun buildTcpSegment(
        sourceIp: ByteArray, sourcePort: Int,
        destIp: ByteArray, destPort: Int,
        seq: Long, ack: Long,
        synFlag: Boolean = false, ackFlag: Boolean = true, finFlag: Boolean = false,
        rstFlag: Boolean = false, pshFlag: Boolean = false,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val totalLength = IPV4_HEADER_LEN + TCP_HEADER_LEN + payload.size
        val packet = ByteArray(totalLength)

        writeIpv4Header(packet, sourceIp, destIp, protocol = 6, totalLength = totalLength)

        val tcpOffset = IPV4_HEADER_LEN
        writeUInt16(packet, tcpOffset, sourcePort)
        writeUInt16(packet, tcpOffset + 2, destPort)
        writeUInt32(packet, tcpOffset + 4, seq)
        writeUInt32(packet, tcpOffset + 8, ack)
        packet[tcpOffset + 12] = ((TCP_HEADER_LEN / 4) shl 4).toByte() // data offset: header length in 4-byte words
        var flags = 0
        if (finFlag) flags = flags or 0x01
        if (synFlag) flags = flags or 0x02
        if (rstFlag) flags = flags or 0x04
        if (pshFlag) flags = flags or 0x08
        if (ackFlag) flags = flags or 0x10
        packet[tcpOffset + 13] = flags.toByte()
        writeUInt16(packet, tcpOffset + 14, 65535) // window size
        writeUInt16(packet, tcpOffset + 16, 0) // checksum placeholder, filled in below
        writeUInt16(packet, tcpOffset + 18, 0)
        if (payload.isNotEmpty()) System.arraycopy(payload, 0, packet, tcpOffset + TCP_HEADER_LEN, payload.size)

        val checksum = Checksums.transportChecksum(sourceIp, destIp, 6, packet, tcpOffset, TCP_HEADER_LEN + payload.size)
        writeUInt16(packet, tcpOffset + 16, checksum)

        return packet
    }

    fun buildTcpControl(
        sourceIp: ByteArray, sourcePort: Int,
        destIp: ByteArray, destPort: Int,
        seq: Long, ack: Long,
        rstFlag: Boolean, ackFlag: Boolean
    ): ByteArray = buildTcpSegment(sourceIp, sourcePort, destIp, destPort, seq, ack, rstFlag = rstFlag, ackFlag = ackFlag)

    private fun writeIpv4Header(packet: ByteArray, sourceIp: ByteArray, destIp: ByteArray, protocol: Int, totalLength: Int) {
        packet[0] = 0x45 // version 4, header length 5 words (20 bytes)
        packet[1] = 0
        writeUInt16(packet, 2, totalLength)
        writeUInt16(packet, 4, (System.nanoTime() and 0xFFFF).toInt())
        writeUInt16(packet, 6, 0x4000) // "don't fragment" flag set, no fragment offset
        packet[8] = 64 // TTL
        packet[9] = protocol.toByte()
        writeUInt16(packet, 10, 0)
        System.arraycopy(sourceIp, 0, packet, 12, 4)
        System.arraycopy(destIp, 0, packet, 16, 4)
        val checksum = Checksums.ipv4HeaderChecksum(packet, 0, IPV4_HEADER_LEN)
        writeUInt16(packet, 10, checksum)
    }

    private fun writeUInt16(packet: ByteArray, offset: Int, value: Int) {
        packet[offset] = ((value shr 8) and 0xFF).toByte()
        packet[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeUInt32(packet: ByteArray, offset: Int, value: Long) {
        packet[offset] = ((value shr 24) and 0xFF).toByte()
        packet[offset + 1] = ((value shr 16) and 0xFF).toByte()
        packet[offset + 2] = ((value shr 8) and 0xFF).toByte()
        packet[offset + 3] = (value and 0xFF).toByte()
    }
}
