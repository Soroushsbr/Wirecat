package com.soroush.wirecat.vpn

// standard Internet checksum (RFC 1071): sum 16-bit words, fold carries, take one's complement
object Checksums {

    private fun sum16(data: ByteArray, offset: Int, length: Int, initial: Long = 0L): Long {
        var sum = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8 // odd byte left over, padded with a zero byte
        }
        return sum
    }

    private fun foldAndComplement(sumIn: Long): Int {
        var sum = sumIn
        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16) // fold any carry back into the low 16 bits
        }
        return (sum.inv() and 0xFFFF).toInt() // one's complement
    }

    fun ipv4HeaderChecksum(packet: ByteArray, offset: Int, headerLength: Int): Int {
        return foldAndComplement(sum16(packet, offset, headerLength))
    }

    fun transportChecksum(
        sourceIp: ByteArray,
        destIp: ByteArray,
        protocol: Int,
        segment: ByteArray,
        segmentOffset: Int,
        segmentLength: Int
    ): Int {
        // TCP/UDP checksums are computed over a "pseudo-header" (src/dst IP + protocol + length)
        // plus the actual segment - this isn't part of the real packet, just checksum input
        var sum = sum16(sourceIp, 0, 4)
        sum = sum16(destIp, 0, 4, sum)
        sum += protocol
        sum += segmentLength
        sum = sum16(segment, segmentOffset, segmentLength, sum)
        return foldAndComplement(sum)
    }
}
