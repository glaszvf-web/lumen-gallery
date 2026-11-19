package com.lumen.gallery.data

import java.io.RandomAccessFile
import java.util.zip.Inflater

/** 结构解析器共享的输出容器 */
class StructOut {
    val chunks = mutableListOf<ChunkInfo>()
    val texts = mutableListOf<Pair<String, String>>()   // key → 文本内容（AI 参数/XMP 线索）
    var xmp: String? = null
    var tiffPayloads = mutableListOf<ByteArray>()       // 内嵌 TIFF（PNG eXIf、WebP EXIF、CR3 CMT）
    var extra = mutableListOf<MetaEntry>()              // 格式专属条目
    val warnings = mutableListOf<String>()
}

object StructureParsers {

    fun parse(path: String, fmt: Fmt, out: StructOut) {
        try {
            RandomAccessFile(path, "r").use { ra ->
                when (fmt) {
                    Fmt.PNG -> parsePng(ra, out)
                    Fmt.JPEG -> parseJpeg(ra, out)
                    Fmt.WEBP -> parseWebp(ra, out)
                    Fmt.GIF -> parseGif(ra, out)
                    Fmt.BMP -> parseBmp(ra, out)
                    Fmt.CR3, Fmt.HEIF, Fmt.AVIF -> parseIsoBmff(ra, fmt, out)
                    Fmt.RAF -> parseRaf(ra, out)
                    // TIFF/DNG/CR2/NEF/ARW/RW2/PEF/SRW 家族的 IFD 深度解析由 TiffDeep 在引擎层完成
                    else -> {}
                }
            }
        } catch (e: Exception) {
            out.warnings.add("结构解析中断：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** v17 · 伽马数值格式化 */
    private fun fmtGamma(g: Double): String =
        if (g % 1.0 == 0.0) "%.0f".format(g) else String.format(java.util.Locale.US, "%.2f", g).trimEnd('0').trimEnd('.')

    private fun parseRaf(ra: RandomAccessFile, out: StructOut) {
        val head = ByteArray(64)
        ra.seek(0); ra.readFully(head)
        out.extra.add(MetaEntry("RAF 头标识", String(head, 0, 16, Charsets.US_ASCII).replace("\u0000", ""), important = true))
        out.extra.add(MetaEntry("富士原始格式", "RAF 专有容器（元数据由 EXIF 引擎提取）"))
        out.chunks.add(ChunkInfo("RAF 头", 0, 340, "富士 CCD-RAW 文件头"))
        out.chunks.add(ChunkInfo("数据区", 340, ra.length() - 340, "JPEG 预览 + CFA 原始数据"))
    }

    // ---------- PNG ----------
    private val PNG_DESC = mapOf(
        "IHDR" to "图像信息头（尺寸/位深/色彩类型）",
        "PLTE" to "调色板",
        "IDAT" to "图像数据流",
        "IEND" to "图像结束标记",
        "tEXt" to "文本块",
        "zTXt" to "压缩文本块",
        "iTXt" to "国际化文本块",
        "eXIf" to "EXIF 数据块",
        "gAMA" to "伽马值",
        "cHRM" to "色度坐标",
        "sRGB" to "sRGB 色彩意向",
        "sBIT" to "有效位深",
        "iCCP" to "ICC 色彩配置文件",
        "pHYs" to "物理像素尺寸",
        "tIME" to "最后修改时间",
        "bKGD" to "背景颜色",
        "hIST" to "调色板直方图",
        "tRNS" to "透明度信息",
        "acTL" to "APNG 动画控制",
        "fcTL" to "APNG 帧控制",
        "fdAT" to "APNG 帧数据",
        "oFFs" to "图像偏移",
        "pCAL" to "校准信息",
    )

    private fun parsePng(ra: RandomAccessFile, out: StructOut) {
        ra.seek(8)
        var off = 8L
        var idatCount = 0
        var idatSize = 0L
        while (off + 8 <= ra.length()) {
            ra.seek(off)
            val hdr = ByteArray(8)
            if (off + 8 > ra.length()) break
            ra.readFully(hdr)
            val len = u32be(hdr, 0)
            val type = String(hdr, 4, 4, Charsets.US_ASCII)
            if (len > 0x7FFFFFFFL || off + 12 + len > ra.length()) { out.warnings.add("PNG chunk 长度异常，解析终止"); break }
            if (type == "IDAT") { idatCount++; idatSize += len }
            else out.chunks.add(ChunkInfo(type, off, len, PNG_DESC[type] ?: "未知块"))
            if (type == "IHDR" && len >= 13) {
                val d = ByteArray(13); ra.readFully(d)
                val w = u32be(d, 0).toInt(); val h = u32be(d, 4).toInt()
                val depth = d[8].toInt() and 0xFF
                val colorType = d[9].toInt() and 0xFF
                val ctNames = mapOf(0 to "灰度", 2 to "真彩色 RGB", 3 to "索引色", 4 to "灰度+透明", 6 to "真彩色 RGBA")
                val channels = mapOf(0 to 1, 2 to 3, 3 to 1, 4 to 2, 6 to 4)
                out.extra.add(MetaEntry("色彩类型", "${ctNames[colorType] ?: colorType}（${channels[colorType] ?: "?"} 通道 × $depth 位）", important = true))
                out.extra.add(MetaEntry("像素位深", "$depth 位/通道", important = true))
                out.extra.add(MetaEntry("隔行扫描", if (d[12].toInt() == 1) "Adam7 隔行" else "否"))
                out.extra.add(MetaEntry("色彩总位深", "${(channels[colorType] ?: 0) * depth} 位/像素", important = true))
            }
            // v17 · cICP：HDR 传递函数标识（PQ/HLG）
            if (type == "cICP" && len >= 3) {
                val dd = ByteArray(minOf(len.toInt(), 4)); ra.readFully(dd)
                val transfer = dd[1].toInt() and 0xFF
                val tName = when (transfer) {
                    16 -> "PQ（SMPTE 2084 · HDR10）"
                    18 -> "HLG（混合对数 · HDR）"
                    13 -> "sRGB 传递函数"
                    8 -> "线性"
                    else -> "0x${"%02X".format(transfer)}"
                }
                out.extra.add(MetaEntry(
                    "HDR 传递函数",
                    "$tName（cICP 色彩标识，_primaries=${dd[0].toInt() and 0xFF}）",
                    important = transfer == 16 || transfer == 18,
                ))
            }
            // v17 · acTL：APNG 动画帧数
            if (type == "acTL" && len >= 8) {
                val dd = ByteArray(8); ra.readFully(dd)
                val frames = u32be(dd, 0).toInt()
                val plays = u32be(dd, 4).toInt()
                out.extra.add(MetaEntry("APNG 动画", "$frames 帧 · ${if (plays == 0) "无限循环" else "循环 $plays 次"}", important = true))
            }
            // v17 · gAMA 数值 / sRGB 意向 / iCCP 大小
            if (type == "gAMA" && len == 4L) {
                val dd = ByteArray(4); ra.readFully(dd)
                val gamma = u32be(dd, 0) / 100000.0
                out.extra.add(MetaEntry("伽马值", fmtGamma(gamma)))
            }
            if (type == "sRGB" && len >= 1) {
                val dd = ByteArray(1); ra.readFully(dd)
                val names = mapOf(0 to "感光度", 1 to "色度", 2 to "参考显示", 3 to "真实色域")
                out.extra.add(MetaEntry("sRGB 渲染意向", names[dd[0].toInt() and 0xFF] ?: "0x${"%02X".format(dd[0].toInt())}"))
            }
            if (type == "iCCP") {
                out.extra.add(MetaEntry("ICC 色彩配置", "内嵌压缩配置（$len 字节）"))
            }
            if (type == "tIME" && len >= 7) {
                val d = ByteArray(7); ra.readFully(d)
                out.extra.add(MetaEntry("元数据修改时间", "%04d-%02d-%02d %02d:%02d:%02d".format(
                    u16be(d, 0), d[2].toInt() and 0xFF, d[3].toInt() and 0xFF, d[4].toInt() and 0xFF, d[5].toInt() and 0xFF, d[6].toInt() and 0xFF)))
            }
            if (type == "pHYs" && len >= 9) {
                val d = ByteArray(9); ra.readFully(d)
                val x = u32be(d, 0); val unit = d[8].toInt()
                if (unit == 1 && x > 0) out.extra.add(MetaEntry("印刷密度", "${(x * 0.0254).toInt()} DPI"))
            }
            if (type == "tEXt" && len < 1 shl 20) {
                val d = ByteArray(len.toInt()); ra.readFully(d)
                val nul = d.indexOf(0)
                if (nul > 0) {
                    val key = String(d, 0, nul, Charsets.ISO_8859_1)
                    val value = String(d, nul + 1, d.size - nul - 1, Charsets.UTF_8)
                    out.texts.add(key to value)
                    if (key.equals("Software", true) || key.equals("Description", true) || key.equals("Comment", true)) {
                        out.extra.add(MetaEntry(if (key.equals("Software", true)) "软件标注" else "文本注释", value.take(200)))
                    }
                }
            }
            if (type == "zTXt" && len < 1 shl 20) {
                val d = ByteArray(len.toInt()); ra.readFully(d)
                val nul = d.indexOf(0)
                if (nul > 0 && nul + 2 < d.size) {
                    val key = String(d, 0, nul, Charsets.ISO_8859_1)
                    val value = inflate(d, nul + 2)
                    out.texts.add(key to value)
                }
            }
            if (type == "iTXt" && len < 1 shl 22) {
                val d = ByteArray(len.toInt()); ra.readFully(d)
                val parsed = parseITxt(d)
                if (parsed != null) {
                    val (key, value) = parsed
                    out.texts.add(key to value)
                    if (key.equals("XML:com.adobe.xmp", true)) out.xmp = value
                }
            }
            if (type == "eXIf" && len < 1 shl 22) {
                val d = ByteArray(len.toInt()); ra.readFully(d)
                out.tiffPayloads.add(d)
            }
            off += 12 + len
            if (type == "IEND") break
            if (out.chunks.size > 500) { out.warnings.add("块数超限，仅显示前 500 个"); break }
        }
        if (idatCount > 0) out.chunks.add(ChunkInfo("IDAT ×$idatCount", 0, idatSize, "图像数据流（合并统计）"))
        if (out.texts.isNotEmpty()) out.chunks.add(ChunkInfo("文本块合计", 0, out.texts.size.toLong(), "${out.texts.size} 个文本块（含 AI 参数线索）"))
    }

    private fun parseITxt(d: ByteArray): Pair<String, String>? {
        var i = d.indexOf(0)
        if (i < 0) return null
        val key = String(d, 0, i, Charsets.ISO_8859_1)
        i += 1
        if (i >= d.size) return null
        val compFlag = d[i].toInt(); i += 1
        if (i >= d.size) return null
        i += 1 // comp method
        var nul = d.indexOf(0, i)
        if (nul < 0) return null
        // 跳过语言标签与翻译标签
        nul = d.indexOf(0, nul + 1)
        if (nul < 0) return null
        val start = nul + 1
        val value = if (compFlag.toInt() == 1 && start < d.size) inflate(d, start)
        else String(d.copyOfRange(start, d.size), Charsets.UTF_8)
        return key to value
    }

    private fun inflate(d: ByteArray, start: Int): String = try {
        val inflater = Inflater()
        inflater.setInput(d, start, d.size - start)
        val buf = ByteArray(1 shl 22)
        var total = 0
        while (!inflater.finished() && total < buf.size) {
            val n = inflater.inflate(buf, total, buf.size - total)
            if (n == 0 && inflater.needsInput()) break
            total += n
        }
        inflater.end()
        String(buf, 0, total, Charsets.UTF_8)
    } catch (e: Exception) { "" }

    // ---------- JPEG ----------
    private val JPEG_DESC = mapOf(
        0xC0 to "帧头 SOF0（基线 DCT，含尺寸位深）",
        0xC1 to "帧头 SOF1（扩展顺序 DCT）",
        0xC2 to "帧头 SOF2（渐进 DCT）",
        0xC4 to "霍夫曼编码表 DHT",
        0xD8 to "图像起始 SOI",
        0xD9 to "图像结束 EOI",
        0xDA to "扫描数据开始 SOS",
        0xDB to "量化表 DQT",
        0xDC to "重启间隔 DNL",
        0xDD to "重启间隔定义 DRI",
        0xE0 to "APP0（JFIF 标准头）",
        0xE1 to "APP1（EXIF / XMP 元数据）",
        0xE2 to "APP2（ICC 色彩配置）",
        0xE3 to "APP3",
        0xE5 to "APP5",
        0xE6 to "APP6",
        0xE7 to "APP7",
        0xE8 to "APP8",
        0xE9 to "APP9",
        0xEA to "APP10",
        0xEB to "APP11",
        0xEC to "APP12（Ducky/ Photoshop）",
        0xED to "APP13（Photoshop 资源块 / IPTC）",
        0xEE to "APP14（Adobe 色彩变换标记）",
        0xEF to "APP15",
        0xFE to "注释段 COM",
        0x01 to "TEM 临时标记",
    )

    private fun parseJpeg(ra: RandomAccessFile, out: StructOut) {
        ra.seek(2)
        var off = 2L
        var sofSeen = false
        while (off + 4 <= ra.length()) {
            ra.seek(off)
            var b = ra.read()
            if (b < 0) break
            if (b != 0xFF) { // 填充字节
                off += 1; continue
            }
            var marker = ra.read()
            if (marker < 0) break
            if (marker == 0xFF) { off += 1; continue } // 填充
            val segOff = off
            when (marker) {
                0xD8, 0x01 -> { off += 2; continue }
                0xD9 -> { out.chunks.add(ChunkInfo("EOI", segOff, 2, JPEG_DESC[0xD9]!!)); break }
            }
            val lenHi = ra.read(); val lenLo = ra.read()
            if (lenHi < 0 || lenLo < 0) break
            val segLen = (lenHi shl 8) or lenLo
            if (segLen < 2 || segOff + 2 + segLen > ra.length()) { out.warnings.add("JPEG 段长度异常，解析终止"); break }
            val name = "0x%02X".format(marker)
            val desc = JPEG_DESC[marker] ?: "保留段"
            out.chunks.add(ChunkInfo(name, segOff, segLen + 2L, desc))
            try {
                if (marker in 0xC0..0xCF && marker !in intArrayOf(0xC4, 0xC8, 0xCC) && !sofSeen) {
                    sofSeen = true
                    val d = ByteArray(minOf(segLen, 17))
                    ra.seek(segOff + 2); ra.readFully(d)
                    if (d.size >= 8) {
                        val depth = d[2].toInt() and 0xFF
                        val comps = d[7].toInt() and 0xFF
                        out.extra.add(MetaEntry("编码方式", if (marker == 0xC2) "渐进式（Progressive）" else "基线（Baseline）", important = true))
                        out.extra.add(MetaEntry("采样精度", "$depth 位"))
                        out.extra.add(MetaEntry("色彩通道", "$comps 通道" + if (comps == 3) "（YCbCr）" else if (comps == 1) "（灰度）" else ""))
                        if (d.size >= 10 && comps in 1..4) {
                            val samp = d[9].toInt() and 0xFF
                            val hz = (samp shr 4); val vt = (samp and 0xF)
                            out.extra.add(MetaEntry("色度采样", "Y 通道 ${hz}×$vt（${if (hz == 1 && vt == 1) "4:4:4 全采样" else if (hz == 2) "4:2:0 / 4:2:2 常见" else "自定义"}）"))
                        }
                    }
                }
                if (marker == 0xE0 && segLen >= 14) {
                    val d = ByteArray(minOf(segLen, 14))
                    ra.seek(segOff + 4); ra.readFully(d)
                    if (d.size >= 10 && String(d, 2, 5, Charsets.US_ASCII).startsWith("JFIF")) {
                        val ver = "${d[7]}.${d[8]}"
                        out.extra.add(MetaEntry("JFIF 版本", ver))
                        out.extra.add(MetaEntry("密度单位", when (d[9].toInt()) { 0 -> "无单位"; 1 -> "点/英寸"; 2 -> "点/厘米"; else -> "${d[9]}" }))
                        if (d.size >= 12) {
                            val xd = ((d[10].toInt() and 0xFF) shl 8) or (d[11].toInt() and 0xFF)
                            if (d[9].toInt() == 1 && xd > 0) out.extra.add(MetaEntry("印刷密度", "$xd DPI"))
                        }
                    }
                }
                if (marker == 0xE1 && segLen in 7..(1 shl 22)) {
                    val d = ByteArray(segLen - 2)
                    ra.seek(segOff + 4); ra.readFully(d)
                    val id = String(d, 0, minOf(29, d.size), Charsets.US_ASCII)
                    if (id.startsWith("http://ns.adobe.com/xap/1.0/") && d.size >= 29) {
                        out.xmp = String(d.copyOfRange(29, d.size), Charsets.UTF_8)
                        // v17 · HDR 增益图检测（Adobe Gain Map）
                        if (out.xmp!!.contains("gainmap", ignoreCase = true)) {
                            out.extra.add(MetaEntry("HDR 增益图", "检测到 Adobe Gain Map XMP（HDR 增强照片）", important = true))
                        }
                    } else if (id.startsWith("Exif")) {
                        out.extra.add(MetaEntry("EXIF 段", "存在（${segLen - 2} 字节）"))
                        if (d.size >= 6) out.tiffPayloads.add(d.copyOfRange(6, d.size))
                    }
                }
                if (marker == 0xE2 && segLen in 16..(1 shl 22)) {
                    val d = ByteArray(minOf(segLen - 2, 40))
                    ra.seek(segOff + 4); ra.readFully(d)
                    if (d.size >= 11 && String(d, 0, 11, Charsets.US_ASCII) == "ICC_PROFILE") {
                        out.chunks.removeLastOrNull()
                        out.chunks.add(ChunkInfo(name, segOff, segLen + 2L, "APP2（ICC 色彩配置文件）"))
                        out.extra.add(MetaEntry("ICC 配置", "内嵌（${segLen - 2} 字节）", important = true))
                    }
                }
                if (marker == 0xED && segLen in 20..(1 shl 22)) {
                    val d = ByteArray(segLen - 2)
                    ra.seek(segOff + 4); ra.readFully(d)
                    val iptc = IptcParser.parse8Bim(d)
                    if (iptc.isNotEmpty()) {
                        out.extra.addAll(iptc)
                        out.extra.add(MetaEntry("IPTC 记录", "${iptc.size} 项（Photoshop 资源块）", important = true))
                    }
                }
                if (marker == 0xEE && segLen >= 9) {
                    val d = ByteArray(minOf(segLen - 2, 12))
                    ra.seek(segOff + 4); ra.readFully(d)
                    if (d.size >= 5 && String(d, 0, 5, Charsets.US_ASCII) == "Adobe") {
                        out.extra.add(MetaEntry("Adobe 标记", "存在（通常由 Photoshop 导出）"))
                    }
                }
                if (marker == 0xFE && segLen in 4..(1 shl 20)) {
                    val d = ByteArray(segLen - 2)
                    ra.seek(segOff + 4); ra.readFully(d)
                    val comment = String(d, Charsets.UTF_8)
                    out.texts.add("COM" to comment)
                    out.extra.add(MetaEntry("注释段", comment.take(300).replace("\n", " "), important = true))
                }
            } catch (e: Exception) {
                out.warnings.add("JPEG 段 $name 处理中断：${e.message ?: e.javaClass.simpleName}")
            }
            off = segOff + 2 + segLen
            if (marker == 0xDA) { out.chunks.add(ChunkInfo("SOS 之后", off, ra.length() - off, "熵编码图像数据（至 EOI）")); break }
            if (out.chunks.size > 300) break
        }
    }

    // ---------- WebP ----------
    private val WEBP_DESC = mapOf(
        "VP8X" to "扩展头（画布尺寸/特性标志）",
        "VP8 " to "有损图像数据",
        "VP8L" to "无损图像数据",
        "ALPH" to "透明通道数据",
        "ANIM" to "动画参数",
        "ANMF" to "动画帧",
        "ICCP" to "ICC 色彩配置文件",
        "EXIF" to "EXIF 元数据",
        "XMP " to "XMP 元数据",
    )

    private fun parseWebp(ra: RandomAccessFile, out: StructOut) {
        ra.seek(12)
        var off = 12L
        var frames = 0
        while (off + 8 <= ra.length()) {
            ra.seek(off)
            val hdr = ByteArray(8)
            if (off + 8 > ra.length()) break
            ra.readFully(hdr)
            val len = u32le(hdr, 0)
            val type = String(hdr, 4, 4, Charsets.US_ASCII)
            out.chunks.add(ChunkInfo(type.trim(), off, len + 8, WEBP_DESC[type] ?: "未知块"))
            when {
                type == "VP8X" && len >= 10 -> {
                    val d = ByteArray(10); ra.readFully(d)
                    val flags = d[0].toInt() and 0xFF
                    val feats = mutableListOf<String>()
                    if (flags and 0x20 != 0) feats.add("ICC 配置")
                    if (flags and 0x10 != 0) feats.add("ALPH 透明")
                    if (flags and 0x08 != 0) feats.add("EXIF")
                    if (flags and 0x04 != 0) feats.add("XMP")
                    if (flags and 0x02 != 0) feats.add("动画")
                    out.extra.add(MetaEntry("特性标志", if (feats.isEmpty()) "无" else feats.joinToString("、"), important = true))
                    val w = (1 + (d[4].toInt() and 0xFF) + ((d[5].toInt() and 0xFF) shl 8) + ((d[6].toInt() and 0xFF) shl 16))
                    val h = (1 + (d[7].toInt() and 0xFF) + ((d[8].toInt() and 0xFF) shl 8) + ((d[9].toInt() and 0xFF) shl 16))
                    out.extra.add(MetaEntry("画布尺寸", "$w × $h px", important = true))
                }
                type == "ANMF" -> frames++
                type == "EXIF" && len < 1 shl 22 -> { val d = ByteArray(len.toInt()); ra.readFully(d); out.tiffPayloads.add(d) }
                type == "XMP " && len < 1 shl 22 -> { val d = ByteArray(len.toInt()); ra.readFully(d); out.xmp = String(d, Charsets.UTF_8) }
            }
            off += 8 + len + (len and 1)
            if (out.chunks.size > 200) break
        }
        if (frames > 0) out.extra.add(MetaEntry("动画帧数", "$frames 帧", important = true))
    }

    // ---------- GIF ----------
    private fun parseGif(ra: RandomAccessFile, out: StructOut) {
        ra.seek(3) // "GIF" 之后
        val ver = ByteArray(3); ra.readFully(ver) // "87a"/"89a"
        val hdr = ByteArray(7); ra.readFully(hdr) // 逻辑屏幕描述符
        val w = u16le(hdr, 0); val h = u16le(hdr, 2)
        val flags = hdr[4].toInt() and 0xFF
        val gctEntries = if (flags and 0x80 != 0) 2 shl (flags and 0x07) else 0
        val gctBytes = gctEntries * 3
        out.extra.add(MetaEntry("GIF 版本", "GIF${String(ver, Charsets.US_ASCII)}"))
        out.extra.add(MetaEntry("逻辑画布", "$w × $h px", important = true))
        out.extra.add(MetaEntry("全局色表", if (gctEntries > 0) "存在（$gctEntries 色）" else "无"))
        var off = 13L + gctBytes
        var frames = 0
        var loops = 0
        val comments = mutableListOf<String>()
        while (off < ra.length()) {
            ra.seek(off)
            val b = ra.read()
            if (b < 0) break
            when (b) {
                0x21 -> { // 扩展
                    val label = ra.read()
                    var p = off + 2L
                    if (label == 0xF9 && p + 5 <= ra.length()) {
                        val d = ByteArray(4); ra.seek(p + 1); ra.readFully(d) // 越过长度字节
                        val delay = u16le(d, 1)
                        val transparent = (d[3].toInt() and 0xFF) != 0
                        if (frames == 0) out.extra.add(MetaEntry("帧延时", "${delay * 10} ms"))
                        if (frames == 0) out.extra.add(MetaEntry("透明通道", if (transparent) "存在" else "无"))
                        p += 5 // 4 字节数据块 + 长度字节
                    } else if (label == 0xFF) {
                        var sz = ra.read()
                        val block = ByteArray(sz); ra.readFully(block)
                        if (sz >= 11 && String(block, 0, 11, Charsets.US_ASCII) == "NETSCAPE2.0") loops = 1
                        p += 1 + sz
                    } else if (label == 0xFE) {
                        val sb = StringBuilder()
                        var sz = ra.read()
                        while (sz > 0 && sb.length < 1000) {
                            val d = ByteArray(sz); ra.readFully(d); sb.append(String(d, Charsets.UTF_8))
                            sz = ra.read()
                        }
                        if (sb.isNotBlank()) comments.add(sb.toString())
                        // p 已被上面循环推进到终止符
                        ra.seek(p)
                        var sz2 = ra.read()
                        while (sz2 > 0 && p + 1 + sz2 < ra.length()) { p += 1 + sz2; ra.seek(p); sz2 = ra.read() }
                    }
                    // 通用子块跳过
                    ra.seek(p)
                    var sz = ra.read()
                    while (sz > 0 && p + 1 + sz < ra.length()) { p += 1 + sz; ra.seek(p); sz = ra.read() }
                    off = p + 1
                }
                0x2C -> { // 图像描述符
                    frames++
                    val desc = ByteArray(9); ra.readFully(desc)
                    val lctFlags = desc[8].toInt() and 0xFF
                    val lctSize = if (lctFlags and 0x80 != 0) 2 shl (lctFlags and 0x07) else 0
                    off += 1 + 9 + lctSize * 3
                    ra.seek(off)
                    off += 1 // LZW 最小码长字节
                    // 跳过 LZW 数据子块
                    ra.seek(off)
                    var sz = ra.read()
                    while (sz > 0 && off + 1 + sz < ra.length()) { off += 1 + sz; ra.seek(off); sz = ra.read() }
                    off += 1
                }
                0x3B -> { break }
                else -> { out.warnings.add("GIF 块标记异常 0x${"%02X".format(b)}"); break }
            }
            if (out.chunks.size > 100) break
        }
        out.extra.add(MetaEntry("帧数", "$frames 帧" + if (frames > 1) "（动画）" else "", important = true))
        if (loops > 0) out.extra.add(MetaEntry("循环播放", "无限循环"))
        if (comments.isNotEmpty()) {
            out.extra.add(MetaEntry("嵌入注释", comments.joinToString(" / ").take(300), important = true))
            comments.forEach { out.texts.add("GIF注释" to it) }
        }
        out.chunks.add(ChunkInfo("逻辑屏幕描述符", 6, 7, "画布尺寸 + 全局色表标志"))
        if (gctBytes > 0) out.chunks.add(ChunkInfo("全局色表", 13, gctBytes.toLong(), "$gctEntries 色 × 3 字节"))
        out.chunks.add(ChunkInfo("数据流", 13L + gctBytes, ra.length() - 13 - gctBytes, "$frames 个图像帧 + 控制扩展"))
    }

    // ---------- BMP ----------
    private fun parseBmp(ra: RandomAccessFile, out: StructOut) {
        ra.seek(14)
        val hdr = ByteArray(40)
        ra.readFully(hdr)
        val hdrSize = u32le(hdr, 0).toInt()
        val w = u32le(hdr, 4)
        val hAbs = u32le(hdr, 8)
        val h = if (hAbs > 0x7FFFFFFF) (hAbs - (1L shl 32)) * -1 else hAbs
        val planes = u16le(hdr, 12)
        val bpp = u16le(hdr, 14)
        val compression = u32le(hdr, 16).toInt()
        val compNames = mapOf(0 to "BI_RGB（不压缩）", 1 to "BI_RLE8", 2 to "BI_RLE4", 3 to "BI_BITFIELDS", 6 to "BI_ALPHABITFIELDS", 5 to "BI_JPEG")
        out.extra.add(MetaEntry("头版本", if (hdrSize >= 124) "BITMAPV5HEADER（$hdrSize 字节）" else if (hdrSize >= 108) "BITMAPV4HEADER（$hdrSize 字节）" else "BITMAPINFOHEADER（$hdrSize 字节）"))
        out.extra.add(MetaEntry("色深", "$bpp 位/像素", important = true))
        out.extra.add(MetaEntry("压缩方式", compNames[compression] ?: "0x${"%X".format(compression)}"))
        if (planes > 0) out.extra.add(MetaEntry("颜色平面", "$planes"))
        if (bpp >= 8) {
            val colorsUsed = u32le(hdr, 32)
            out.extra.add(MetaEntry("调色板颜色数", if (colorsUsed == 0L) "全部（${1 shl bpp}）" else "$colorsUsed"))
        }
        val xppm = u32le(hdr, 24)
        if (xppm > 0) out.extra.add(MetaEntry("印刷密度", "${Math.round(xppm * 0.0254)} DPI"))
        out.chunks.add(ChunkInfo("文件头 BITMAPFILEHEADER", 0, 14, "BM 标识 + 文件大小 + 数据偏移"))
        out.chunks.add(ChunkInfo("信息头", 14, hdrSize.toLong(), "位图信息头"))
        ra.seek(2)
        val fsz = ByteArray(4); ra.readFully(fsz)
        val fileSize = u32le(fsz, 0)
        out.chunks.add(ChunkInfo("像素数据", 14L + hdrSize, (if (fileSize in 15..ra.length()) fileSize else ra.length()) - 14 - hdrSize, "像素阵列（自底向上存储）"))
    }

    // ---------- ISO-BMFF：HEIC / AVIF / CR3 ----------
    private val BMFF_BOX: Map<String, String> = mapOf(
        "ftyp" to "文件类型标识",
        "mdat" to "媒体数据（图像码流）",
        "meta" to "元数据容器",
        "moov" to "轨道容器",
        "free" to "保留空间",
        "skip" to "可跳过空间",
        "uuid" to "扩展私有数据（佳能为相机元数据）",
    )

    private fun parseIsoBmff(ra: RandomAccessFile, fmt: Fmt, out: StructOut) {
        walkBoxes(ra, 0, ra.length(), depth = 0, out = out, cr3 = fmt == Fmt.CR3)
        if (fmt == Fmt.CR3) {
            out.extra.add(MetaEntry("容器类型", "ISO Base Media（佳能 CR3，与 HEIC 同源）", important = true))
        } else {
            out.extra.add(MetaEntry("容器类型", "ISO Base Media（HEIF 家族，可内嵌多图与缩略图）", important = true))
        }
    }

    private fun walkBoxes(ra: RandomAccessFile, start: Long, end: Long, depth: Int, out: StructOut, cr3: Boolean) {
        var off = start
        var count = 0
        while (off + 8 <= end && count < 100) {
            count++
            ra.seek(off)
            val hdr = ByteArray(8)
            if (off + 8 > ra.length()) break
            ra.readFully(hdr)
            var size = u32be(hdr, 0)
            val type = String(hdr, 4, 4, Charsets.US_ASCII)
            var hdrSize = 8L
            if (size == 1L && off + 16 <= end) {
                val ext = ByteArray(8); ra.readFully(ext)
                size = (u32be(ext, 0) shl 32) or u32be(ext, 4)
                hdrSize = 16L
            } else if (size == 0L) {
                size = end - off
            }
            if (size < 8 || off + size > end) { out.warnings.add("box 长度异常（$type），解析终止"); break }
            val indent = if (depth == 0) "" else "└ "
            when {
                type == "meta" -> {
                    out.chunks.add(ChunkInfo("$indent meta", off, size, BMFF_BOX[type]!!))
                    walkBoxes(ra, off + hdrSize + 4, off + size, depth + 1, out, cr3) // full box 4 字节版本标志
                }
                type == "uuid" && cr3 && size < 1 shl 24 -> {
                    val d = ByteArray(size.toInt() - 8)
                    ra.seek(off + 8); ra.readFully(d)
                    // CanonUuid: 16 字节 USCT + fourcc
                    if (d.size >= 20) {
                        val fourcc = String(d, 16, 4, Charsets.US_ASCII)
                        if (fourcc.startsWith("CMT")) {
                            val name = when (fourcc) {
                                "CMT1" -> "佳能元数据 · TIFF IFD0"
                                "CMT2" -> "佳能元数据 · EXIF 子IFD"
                                "CMT3" -> "佳能元数据 · GPS 子IFD"
                                "CMT4" -> "佳能元数据 · 厂商注释"
                                else -> fourcc
                            }
                            out.chunks.add(ChunkInfo("uuid/$fourcc", off, size, name))
                            if (fourcc != "CMT4") out.tiffPayloads.add(d.copyOfRange(20, d.size))
                        } else {
                            out.chunks.add(ChunkInfo("uuid", off, size, "厂商扩展（$fourcc）"))
                        }
                    }
                }
                depth == 0 -> out.chunks.add(ChunkInfo(type, off, size, BMFF_BOX[type] ?: "数据框"))
            }
            off += size
        }
    }

    // ---------- TIFF 家族底片 ----------
    private val TIFF_TAG: Map<Int, Pair<String, String>> = mapOf(
        0x0100 to ("图像宽度" to "px"),
        0x0101 to ("图像高度" to "px"),
        0x0102 to ("位深" to "位/通道"),
        0x0103 to ("压缩方式" to ""),
        0x0106 to ("光度解释" to ""),
        0x010F to ("相机制造商" to ""),
        0x0110 to ("相机型号" to ""),
        0x0112 to ("方向" to ""),
        0x0115 to ("采样数" to ""),
        0x011A to ("水平分辨率" to ""),
        0x011B to ("垂直分辨率" to ""),
        0x011C to ("平面配置" to ""),
        0x0128 to ("分辨率单位" to ""),
        0x0131 to ("固件/软件" to ""),
        0x0132 to ("修改时间" to ""),
        0x013B to ("作者" to ""),
        0x8298 to ("版权" to ""),
        0x8800 to ("GPS 定位信息" to ""),
        0x829A to ("快门速度" to "s"),
        0x829D to ("光圈值" to ""),
        0x8769 to ("EXIF 子目录" to ""),
        0x8822 to ("曝光模式" to ""),
        0x8825 to ("GPS 子目录" to ""),
        0x8827 to ("ISO 感光度" to ""),
        0x9003 to ("拍摄时间" to ""),
        0x9004 to ("数字化时间" to ""),
        0x9102 to ("压缩比特率" to ""),
        0x9201 to ("快门速度值(APEX)" to ""),
        0x9202 to ("光圈值(APEX)" to ""),
        0x9204 to ("曝光补偿" to ""),
        0x9205 to ("最大光圈" to ""),
        0x9207 to ("场景类型" to ""),
        0x9209 to ("闪光灯" to ""),
        0x920A to ("焦距" to "mm"),
        0x9214 to ("_subjectDistance" to "m"),
        0x927C to ("厂商注释" to ""),
        0x9286 to ("用户注释" to ""),
        0xA002 to ("有效像素宽" to ""),
        0xA003 to ("有效像素高" to ""),
        0xA402 to ("图像模式" to ""),
        0xA403 to ("白平衡" to ""),
        0xA405 to ("等效焦距" to "mm"),
        0xA406 to ("场景类型" to ""),
        0xC612 to ("DNG 版本" to ""),
        0xC614 to ("唯一相机型号" to ""),
        0xC615 to ("CFA 布局" to ""),
        0xC619 to ("校准光源1" to ""),
        0xC61A to ("黑电平" to ""),
        0xC61D to ("白电平" to ""),
        0xC621 to ("色彩矩阵1" to ""),
        0xC622 to ("色彩矩阵2" to ""),
        0xC628 to ("线性化表" to ""),
        0xC717 to (" UniqueModelLong" to ""),
    )

    private fun parseTiffFile(ra: RandomAccessFile, fmt: Fmt, out: StructOut) {
        val size = ra.length()
        val head = ByteArray(minOf(size, 64 * 1024).toInt().coerceAtLeast(8))
        ra.seek(0); ra.readFully(head)
        if (fmt == Fmt.RAF) {
            out.extra.add(MetaEntry("RAF 头标识", String(head, 0, 16, Charsets.US_ASCII).replace("\u0000", ""), important = true))
            out.extra.add(MetaEntry("富士原始格式", "RAF 专有容器（元数据由 EXIF 引擎提取）"))
            out.chunks.add(ChunkInfo("RAF 头", 0, 340, "富士 CCD-RAW 文件头"))
            out.chunks.add(ChunkInfo("数据区", 340, size - 340, "JPEG 预览 + CFA 原始数据"))
            return
        }
        val entries = TiffReader.readIfd(head, 0, out.warnings)
        entries.forEach { (tag, value) ->
            val named = TIFF_TAG[tag]
            val label = named?.first ?: "TIFF 0x${"%04X".format(tag)}"
            val suffix = named?.second ?: ""
            out.extra.add(MetaEntry(label, if (suffix.isEmpty()) value else "$value $suffix", important = tag in setOf(0x0110, 0x010F, 0x9003, 0x8827)))
        }
        out.chunks.add(ChunkInfo("IFD0 图像文件目录", 0, 8, "TIFF 头 + 第 0 级元数据目录"))
        out.chunks.add(ChunkInfo("原始数据区", 8, size - 8, "CFA 传感器数据 / 缩略图 / 条带数据"))
    }
}

// ---------- 通用字节读取与 TIFF IFD 解析 ----------

fun u16be(d: ByteArray, off: Int): Int = ((d[off].toInt() and 0xFF) shl 8) or (d[off + 1].toInt() and 0xFF)
fun u32be(d: ByteArray, off: Int): Long =
    ((d[off].toLong() and 0xFF) shl 24) or ((d[off + 1].toLong() and 0xFF) shl 16) or
        ((d[off + 2].toLong() and 0xFF) shl 8) or (d[off + 3].toLong() and 0xFF)
fun u16le(d: ByteArray, off: Int): Int = (d[off].toInt() and 0xFF) or ((d[off + 1].toInt() and 0xFF) shl 8)
fun u32le(d: ByteArray, off: Int): Long =
    (d[off].toLong() and 0xFF) or ((d[off + 1].toLong() and 0xFF) shl 8) or
        ((d[off + 2].toLong() and 0xFF) shl 16) or ((d[off + 3].toLong() and 0xFF) shl 24)

fun ByteArray.indexOf(b: Int, from: Int = 0): Int {
    for (i in from until size) if (this[i].toInt() == b) return i
    return -1
}

object TiffReader {
    /** 读一个 TIFF 数据块（可为独立文件、PNG eXIf、CR3 CMT），返回 (tagId → 渲染值) 列表 */
    fun readIfd(data: ByteArray, tiffStart: Int, warnings: MutableList<String>): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        try {
            if (tiffStart + 8 > data.size) return result
            val bo = if (data[tiffStart].toInt() == 'I'.code && data[tiffStart + 1].toInt() == 'I'.code) true
            else if (data[tiffStart].toInt() == 'M'.code && data[tiffStart + 1].toInt() == 'M'.code) false
            else return result
            val magic = if (bo) u16le(data, tiffStart + 2) else u16be(data, tiffStart + 2)
            if (magic != 42) { warnings.add("TIFF 魔数异常（$magic）"); return result }
            fun u16(off: Int): Int = if (bo) u16le(data, off) else u16be(data, off)
            fun u32(off: Int): Long = if (bo) u32le(data, off) else u32be(data, off)
            fun u64(off: Int): Long {
                var v = 0L
                for (i in 0 until 8) v = v or ((data[off + i].toLong() and 0xFF) shl (if (bo) (8 * i) else (8 * (7 - i))))
                return v
            }

            val ifd0 = u32(tiffStart + 4).toInt()
            walkIfd(data, tiffStart, ifd0, bo, u16 = ::u16, u32 = ::u32, u64 = ::u64, result = result, warnings = warnings, depth = 0)
        } catch (e: Exception) {
            warnings.add("TIFF 解析中断：${e.message ?: e.javaClass.simpleName}")
        }
        return result
    }

    private fun walkIfd(
        data: ByteArray, tiffStart: Int, ifdOff0: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long, u64: (Int) -> Long,
        result: MutableList<Pair<Int, String>>, warnings: MutableList<String>, depth: Int,
    ) {
        var ifdOff = ifdOff0
        var guard = 0
        while (ifdOff > 0 && ifdOff + 2 <= data.size && guard < 8) {
            guard++
            val count = u16(tiffStart + ifdOff)
            if (count > 1024) { warnings.add("IFD 条目数异常"); return }
            var subExif = -1; var subGps = -1
            for (i in 0 until count) {
                val e = tiffStart + ifdOff + 2 + i * 12
                if (e + 12 > data.size) return
                val tag = u16(e)
                val type = u16(e + 2)
                val cnt = u32(e + 4).toInt()
                if (cnt > 100000) continue
                val typeSizes = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8)
                if (type <= 0 || type >= typeSizes.size) continue
                val total = typeSizes[type] * cnt
                val vOff = if (total <= 4) e + 8 else (tiffStart + u32(e + 8).toInt())
                if (vOff < 0 || vOff + total > data.size) continue
                if (tag == 0x8769) { subExif = u32(e + 8).toInt(); continue }
                if (tag == 0x8825) { subGps = u32(e + 8).toInt(); continue }
                result.add(tag to renderValue(data, vOff, type, cnt, bo, u16, u32, u64))
            }
            val next = u32(tiffStart + ifdOff + 2 + count * 12).toInt()
            if (subExif > 0 && depth < 2) walkIfd(data, tiffStart, subExif, bo, u16, u32, u64, result, warnings, depth + 1)
            if (subGps > 0 && depth < 2) {
                val gps = TiffGpsReader.read(data, tiffStart + subGps, tiffStart, bo)
                gps.forEach { result.add(0x8800 to it) } // 以 0x8800 携带 GPS 文本
            }
            ifdOff = next
        }
    }

    private fun renderValue(
        data: ByteArray, off: Int, type: Int, cnt: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long, u64: (Int) -> Long,
    ): String = try {
        when (type) {
            2 -> { // ASCII
                var end = off
                var i = 0
                while (i < cnt && end < data.size && data[end] != 0.toByte()) { end++; i++ }
                String(data, off, end - off, Charsets.UTF_8).replace("\u0000", "")
            }
            1, 6 -> (0 until cnt).joinToString(", ") { (data[off + it].toInt() and 0xFF).toString() }
            3 -> (0 until cnt).joinToString(", ") { u16(off + it * 2).toString() }
            4 -> (0 until cnt).joinToString(", ") { u32(off + it * 4).toString() }
            5 -> (0 until cnt).joinToString(", ") { rat(data, off + it * 8, bo, false) }
            10 -> (0 until cnt).joinToString(", ") { rat(data, off + it * 8, bo, true) }
            7 -> "二进制（$cnt 字节）"
            9 -> (0 until cnt).joinToString(", ") { u32(off + it * 4).toInt().toString() }
            else -> "类型 $type"
        }
    } catch (e: Exception) { "?" }

    private fun rat(data: ByteArray, off: Int, bo: Boolean, signed: Boolean): String {
        val num = if (bo) u32le(data, off) else u32be(data, off)
        val den = if (bo) u32le(data, off + 4) else u32be(data, off + 4)
        var n = num
        if (signed && num > 0x7FFFFFFF) n = num - (1L shl 32)
        if (den == 0L) return "∞"
        val v = n.toDouble() / den
        return if (v % 1.0 == 0.0) "${v.toLong()}" else String.format(java.util.Locale.US, "%.2f", v)
    }
}

/** 极简 GPS IFD 文本渲染 */
private object TiffGpsReader {
    fun read(data: ByteArray, ifdOff: Int, tiffStart: Int, bo: Boolean): List<String> {
        val out = mutableListOf<String>()
        try {
            fun u16(off: Int): Int = if (bo) u16le(data, off) else u16be(data, off)
            fun u32(off: Int): Long = if (bo) u32le(data, off) else u32be(data, off)
            if (ifdOff + 2 > data.size) return out
            val count = u16(tiffStart + ifdOff)
            var latRef = ""; var lonRef = ""; var lat: Double? = null; var lon: Double? = null; var alt: Double? = null
            for (i in 0 until count) {
                val e = tiffStart + ifdOff + 2 + i * 12
                if (e + 12 > data.size) break
                val tag = u16(e)
                val type = u16(e + 2)
                val cnt = u32(e + 4).toInt()
                val typeSizes = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8)
                if (type <= 0 || type >= typeSizes.size) continue
                val total = typeSizes[type] * cnt
                val vOff = if (total <= 4) e + 8 else (tiffStart + u32(e + 8).toInt())
                if (vOff < 0 || vOff + total > data.size) continue
                when (tag) {
                    0x0001 -> latRef = String(data, vOff, 1, Charsets.US_ASCII)
                    0x0002 -> lat = dms(data, vOff, cnt, bo)
                    0x0003 -> lonRef = String(data, vOff, 1, Charsets.US_ASCII)
                    0x0004 -> lon = dms(data, vOff, cnt, bo)
                    0x0006 -> alt = run {
                        val num = u32(vOff); val den = u32(vOff + 4)
                        if (den == 0L) null else num.toDouble() / den
                    }
                }
            }
            if (lat != null && lon != null) {
                val latS = if (latRef == "S") -lat else lat
                val lonS = if (lonRef == "W") -lon else lon
                out.add("GPS 定位：${"%.6f".format(latS)}, ${"%.6f".format(lonS)}（${if (latRef == "S") "南纬" else "北纬"} ${if (lonRef == "W") "西经" else "东经"}）")
            }
            if (alt != null) out.add("GPS 海拔：${"%.1f".format(alt)} m")
        } catch (e: Exception) { }
        return out
    }

    private fun dms(data: ByteArray, off: Int, cnt: Int, bo: Boolean): Double {
        fun u32(o: Int) = if (bo) u32le(data, o) else u32be(data, o)
        var v = 0.0
        for (i in 0 until minOf(cnt, 3)) {
            val num = u32(off + i * 8); val den = u32(off + i * 8 + 4)
            if (den != 0L) v += num.toDouble() / den / Math.pow(60.0, i.toDouble())
        }
        return v
    }
}
