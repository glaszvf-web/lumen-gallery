package com.lumen.gallery

import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class TiffGpsTest {

    @Test
    fun parseGpsPayload() {
        val f = File("testimgs/sample_gps.jpg")
        val raw = f.readBytes()
        // 走 JPEG 段定位 APP1
        var i = 2
        var payload: ByteArray? = null
        while (i + 4 < raw.size) {
            if (raw[i] != 0xFF.toByte()) { i++; continue }
            val marker = raw[i + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01) { i += 2; continue }
            val len = ((raw[i + 2].toInt() and 0xFF) shl 8) or (raw[i + 3].toInt() and 0xFF)
            if (marker == 0xE1) {
                val d = raw.copyOfRange(i + 4, i + 2 + len)
                val id = String(d, 0, minOf(29, d.size), Charsets.US_ASCII)
                if (id.startsWith("Exif")) {
                    payload = d.copyOfRange(6, d.size)
                    break
                }
            }
            i += 2 + len
        }
        println("payload size=${payload?.size}")
        val warnings = mutableListOf<String>()
        val result = com.lumen.gallery.data.TiffReader.readIfd(payload!!, 0, warnings)
        result.forEach { (tag, v) -> println("tag=$tag value=${v.take(60)}") }
        println("warnings=$warnings")
        val gps = result.filter { it.first == 0x8800 }
        assert(gps.isNotEmpty()) { "GPS 文本缺失" }
        assert(gps.any { it.second.contains("39.9") }) { "纬度应为 39.9x: $gps" }
    }

    @Test
    fun parseViaRandomAccess() {
        // 复刻 App 内 RandomAccessFile 路径
        val ra = RandomAccessFile("testimgs/sample_gps.jpg", "r")
        ra.seek(2)
        var off = 2L
        var payload: ByteArray? = null
        while (off + 4 <= ra.length()) {
            ra.seek(off)
            var b = ra.read()
            if (b != 0xFF) { off += 1; continue }
            var marker = ra.read()
            if (marker == 0xFF) { off += 1; continue }
            when (marker) {
                0xD8, 0x01 -> { off += 2; continue }
                0xD9 -> break
            }
            val lenHi = ra.read(); val lenLo = ra.read()
            val segLen = (lenHi shl 8) or lenLo
            if (marker == 0xE1) {
                val d = ByteArray(segLen - 2)
                ra.seek(off + 4); ra.readFully(d)
                val id = String(d, 0, minOf(29, d.size), Charsets.US_ASCII)
                if (id.startsWith("Exif")) { payload = d.copyOfRange(6, d.size); break }
            }
            off = off + 2 + segLen
        }
        ra.close()
        println("RAF payload size=${payload?.size} head=${payload?.take(4)?.joinToString("") { "%02X".format(it) }}")
        val warnings = mutableListOf<String>()
        val result = com.lumen.gallery.data.TiffReader.readIfd(payload!!, 0, warnings)
        val gps = result.filter { it.first == 0x8800 }
        gps.forEach { println("GPS: ${it.second}") }
        assert(gps.any { it.second.contains("39.9") }) { "RAF 路径 GPS 纬度错误: $gps" }
    }
}
