package com.lumen.gallery.data

/**
 * v27 · CR3（佳能 ISO-BMFF 容器）字节级 GPS 脱敏
 *
 * CR3 结构：ftyp('crx ') → moov → trak/meta 里有 'CMT ' 盒（佳能元数据盒），
 * 盒头 'CMT ' 后跳过 8 字节盒头域，就是一段完整 TIFF（II*\0 42 …），
 * 其中 IFD0 = CMT1（主 Exif）、ExifIFD = CMT2、GPS IFD = CMT3（由 0x8825 指针挂载）。
 *
 * 脱敏策略（与 DNG/TIFF 家族同款字节级手法）：
 *   把 TIFF 里每条 0x8825（GPS Info IFD Pointer）条目的 4 字节值域清零 ——
 *   指针归零后任何读取器都取不到 CMT3，GPS 坐标即被物理抹除，
 *   其余拍摄参数（光圈/快门/白平衡）原样保留。
 * 兼容：一个文件可能有多个 CMT 盒（如多轨道）；全量扫描逐个处理。
 */
object Cr3Strip {

    /** 在 CR3 容器字节流里定位并抹除所有 CMT 盒中的 GPS 指针，返回抹除条数 */
    fun stripGps(data: ByteArray): Int {
        var stripped = 0
        try {
            var searchFrom = 0
            while (true) {
                val found = findAscii(data, "CMT ", searchFrom) ?: break
                searchFrom = found + 4
                // 'CMT ' 后 16 字节内寻找 TIFF 头（II*\0 或 MM\0*），容忍不同写入器的盒头长度差异
                val tiffStart = findTiffSig(data, found + 4, minOf(found + 24, data.size)) ?: continue
                stripped += stripTiffGpsPointers(data, tiffStart)
            }
        } catch (_: Exception) { }
        return stripped
    }

    /** 找 ASCII 序列 */
    private fun findAscii(data: ByteArray, needle: String, from: Int): Int? {
        if (needle.isEmpty() || data.size < needle.length) return null
        val first = needle[0].code
        var i = from.coerceAtLeast(0)
        val last = data.size - needle.length
        while (i <= last) {
            if (data[i].toInt() == first && i + needle.length <= data.size) {
                var hit = true
                for (k in 1 until needle.length) {
                    if (data[i + k].toInt() != needle[k].code) { hit = false; break }
                }
                if (hit) return i
            }
            i++
        }
        return null
    }

    /** 在 [from, to) 里找 TIFF 魔数（II*\0 / MM\0*），返回 TIFF 头起点 */
    private fun findTiffSig(data: ByteArray, from: Int, to: Int): Int? {
        var i = from.coerceAtLeast(0)
        while (i + 4 <= to && i + 4 <= data.size) {
            val a = data[i].toInt() and 0xFF
            val b = data[i + 1].toInt() and 0xFF
            val c = data[i + 2].toInt() and 0xFF
            val d = data[i + 3].toInt() and 0xFF
            if (a == 0x49 && b == 0x49 && c == 0x2A && d == 0x00) return i   // II*\0
            if (a == 0x4D && b == 0x4D && c == 0x00 && d == 0x2A) return i   // MM\0*
            i++
        }
        return null
    }

    /**
     * 抹除一段 TIFF 里全部 IFD 链上的 0x8825 指针（相对 tiffStart 的字节偏移寻址）。
     * 返回抹除条数。与 CleanExport.stripTiffGpsPointers 同逻辑，但支持任意 TIFF 起点。
     */
    private fun stripTiffGpsPointers(data: ByteArray, tiffStart: Int): Int {
        var stripped = 0
        if (tiffStart + 8 > data.size) return 0
        val bo = data[tiffStart].toInt() == 'I'.code && data[tiffStart + 1].toInt() == 'I'.code
        fun u16(o: Int) = if (bo) u16le(data, o) else u16be(data, o)
        fun u32(o: Int) = if (bo) u32le(data, o) else u32be(data, o)
        var off = tiffStart + u32(tiffStart + 4).toInt()
        var guard = 0
        val visited = mutableSetOf<Int>()
        while (off > 0 && off + 2 <= data.size && guard < 8 && off !in visited) {
            guard++; visited.add(off)
            val cnt = u16(off)
            if (cnt <= 0 || cnt > 1024) break
            for (i in 0 until cnt) {
                val e = off + 2 + i * 12
                if (e + 12 > data.size) break
                when (u16(e)) {
                    0x8825 -> {
                        // 指针值清零（4 字节值域，无论内联还是超 4 字节定值，0x8825 恒为 LONG×1 内联）
                        data[e + 8] = 0; data[e + 9] = 0; data[e + 10] = 0; data[e + 11] = 0
                        stripped++
                    }
                    // ExifIFD 指针：递归跟进去（佳能部分机型在 ExifIFD 里再挂 GPS）
                    0x8769 -> {
                        val exifOff = tiffStart + u32(e + 8).toInt()
                        if (exifOff !in visited && exifOff + 2 <= data.size) {
                            visited.add(exifOff)
                            val subCnt = u16(exifOff)
                            if (subCnt in 1..1024) {
                                for (k in 0 until subCnt) {
                                    val se = exifOff + 2 + k * 12
                                    if (se + 12 > data.size) break
                                    if (u16(se) == 0x8825) {
                                        data[se + 8] = 0; data[se + 9] = 0; data[se + 10] = 0; data[se + 11] = 0
                                        stripped++
                                    }
                                }
                            }
                        }
                    }
                }
            }
            off = tiffStart + u32(off + 2 + cnt * 12).toInt()
        }
        return stripped
    }

    private fun u16le(d: ByteArray, o: Int): Int = (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)
    private fun u16be(d: ByteArray, o: Int): Int = ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)
    private fun u32le(d: ByteArray, o: Int): Long =
        (d[o].toLong() and 0xFF) or ((d[o + 1].toLong() and 0xFF) shl 8) or
            ((d[o + 2].toLong() and 0xFF) shl 16) or ((d[o + 3].toLong() and 0xFF) shl 24)
    private fun u32be(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or ((d[o + 1].toLong() and 0xFF) shl 16) or
            ((d[o + 2].toLong() and 0xFF) shl 8) or (d[o + 3].toLong() and 0xFF)
}
