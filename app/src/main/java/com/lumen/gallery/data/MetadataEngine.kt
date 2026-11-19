package com.lumen.gallery.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 元数据总引擎：按魔数分派格式 → 结构解析 + EXIF + AI 参数 + XMP/IPTC + 哈希 + 色彩分析
 */
object MetadataEngine {

    private const val RAW_HEAD_BYTES = 16 * 1024 * 1024 // RAW 元数据区足够覆盖 IFD 表

    fun analyze(context: Context, item: MediaItem, withColor: Boolean = true): AnalysisResult {
        val warnings = mutableListOf<String>()
        val file = File(item.path)
        val headBytes = ByteArray(64)
        var fmt: Fmt = if (file.exists()) {
            file.inputStream().use { ins ->
                val n = ins.read(headBytes)
                Format.sniff(if (n < 0) ByteArray(0) else headBytes.copyOf(n), file.length())
            }
        } else Fmt.RAW_UNKNOWN

        val out = StructOut()
        StructureParsers.parse(item.path, fmt, out)
        warnings.addAll(out.warnings)

        val sections = mutableListOf<MetaSection>()
        var rawPreviewBmp: android.graphics.Bitmap? = null
        var rawPreviewMetaStr: String? = null

        // ---------- 1. 概要 ----------
        val summary = mutableListOf(
            MetaEntry("文件名", item.displayName, important = true),
            MetaEntry("识别格式", fmt.zhName, important = true),
            MetaEntry("文件大小", FormatUi.fmtSize(item.size), important = true),
        )
        if (item.width > 0 && item.height > 0) {
            summary.add(MetaEntry("分辨率", "${item.width} × ${item.height} px", important = true))
            summary.add(MetaEntry("像素量", UnitsPrefs.pixels(item.width, item.height), important = true))
            summary.add(MetaEntry("宽高比", FormatUi.aspectRatio(item.width, item.height)))
        }
        summary.add(MetaEntry("文件路径", item.path))
        summary.add(MetaEntry("所在文件夹", item.path.substringBeforeLast('/').substringAfterLast('/')))
        summary.add(MetaEntry("MIME 类型", item.mime))
        summary.add(MetaEntry("入库时间", ts(item.dateAdded)))
        summary.add(MetaEntry("修改时间", ts(item.dateModified)))
        out.extra.filter { !it.label.startsWith("TIFF") && it.label !in TIFF_META_LABELS }
            .forEach { e -> if (!summary.any { it.label == e.label }) summary.add(e) }

        // ---------- 2. EXIF ----------
        val exifEntries = mutableListOf<MetaEntry>()
        val gpsEntries = mutableListOf<MetaEntry>()
        var userComment: String? = null
        val exifPrivacy = mutableListOf<String>()
        try {
            val (entries, gps, privacy) = ExifReader.read(androidx.exifinterface.media.ExifInterface(item.path))
            exifEntries.addAll(entries)
            gpsEntries.addAll(gps)
            exifPrivacy.addAll(privacy)
            userComment = entries.firstOrNull { it.label == "用户注释" }?.value
        } catch (e: Exception) {
            warnings.add("EXIF 引擎异常：${e.message ?: e.javaClass.simpleName}")
        }

        // ---------- 3. TIFF/RAW 深度 IFD 解析（DNG/CR2/NEF/ARW/TIFF/HEIC 嵌入/CR3） ----------
        val isTiffFamily = fmt in setOf(
            Fmt.TIFF, Fmt.DNG, Fmt.CR2, Fmt.NEF, Fmt.ARW, Fmt.ORF, Fmt.RW2, Fmt.PEF, Fmt.SRW, Fmt.CR3,
        )
        if (isTiffFamily) {
            try {
                val buf = file.takeIf { it.exists() }?.let { f ->
                    val n = minOf(f.length(), RAW_HEAD_BYTES.toLong()).toInt()
                    f.inputStream().use { ins ->
                        val b = ByteArray(n)
                        var off = 0
                        while (off < n) {
                            val r = ins.read(b, off, n - off)
                            if (r < 0) break
                            off += r
                        }
                        b.copyOf(off)
                    }
                }
                if (buf != null) {
                    val ifds = TiffDeep.parse(buf, warnings)
                    // 格式精判：DNGVersion 在场即 DNG
                    val hasDng = ifds.any { s -> s.entries.any { it.tag == 0xC612 } }
                    val make = ifds.flatMap { it.entries }.firstOrNull { it.tag == 0x010F }?.value ?: ""
                    if (hasDng) {
                        fmt = Fmt.DNG
                        summary[1] = MetaEntry("识别格式", "DNG 数字底片（HUAWEI/Adobe RAW）", important = true)
                    } else if (fmt == Fmt.TIFF) {
                        fmt = when {
                            make.contains("Canon", true) -> Fmt.CR2
                            make.contains("NIKON", true) -> Fmt.NEF
                            make.contains("SONY", true) -> Fmt.ARW
                            else -> Fmt.TIFF
                        }
                        summary[1] = MetaEntry("识别格式", fmt.zhName, important = true)
                    }
                    // 机型信息提升到概要
                    val model = ifds.flatMap { it.entries }.firstOrNull { it.tag == 0x0110 }?.value
                    if (model != null && !summary.any { it.label == "相机型号" }) {
                        summary.add(MetaEntry("相机型号", model, important = true))
                    }
                    // 每个 IFD 一张折叠卡（v55 · 极客模式可在标签前显示原始 tag 号）
                    val geekMode = context.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                        .getBoolean("geek_mode", false)
                    ifds.forEach { ifd ->
                        val entries = ifd.entries.map {
                            MetaEntry(
                                if (geekMode) "0x%04X · ${it.label}".format(it.tag) else it.label,
                                it.value, it.important,
                            )
                        }
                        if (entries.isNotEmpty()) sections.add(MetaSection(ifd.title, ifd.subtitle, entries))
                    }
                    // v27 · RAW 显影预览：底片内嵌 JPEG（最大的那张）直接显影成 Bitmap
                    try {
                        val previews = TiffDeep.extractPreviews(buf)
                        val best = previews.firstOrNull { it.length in 4096..(24 * 1024 * 1024) }
                        if (best != null) {
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(buf, best.offset, best.length, bounds)
                            if (bounds.outWidth > 0) {
                                var sample = 1
                                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                                val bmp = android.graphics.BitmapFactory.decodeByteArray(buf, best.offset, best.length, opts)
                                if (bmp != null) {
                                    rawPreviewBmp = bmp
                                    rawPreviewMetaStr = "由底片内嵌 JPEG 显影 · ${best.source} · " +
                                        "${bounds.outWidth}×${bounds.outHeight}（${FormatUi.fmtSize(best.length.toLong())}）"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        warnings.add("RAW 显影预览失败：${e.message ?: e.javaClass.simpleName}")
                    }
                    // GPS 卡（去重：与 ExifReader 的重复）
                    val gpsIfd = ifds.firstOrNull { it.title.startsWith("GPS") }
                    if (gpsIfd != null && gpsEntries.none { it.label.startsWith("GPS") }) {
                        gpsIfd.entries.forEach { gpsEntries.add(MetaEntry(it.label, it.value, it.important)) }
                        exifPrivacy.add("照片带有 GPS 定位，公开发布前建议抹除")
                    }
                }
            } catch (e: Exception) {
                warnings.add("RAW 深度解析异常：${e.message ?: e.javaClass.simpleName}")
            }
        } else {
            out.extra.filter { it.label.startsWith("TIFF") || it.label in TIFF_META_LABELS }
                .forEach { exifEntries.add(it) }
        }

        // ---------- GPS 兜底（JPEG APP1 / PNG eXIf / WebP EXIF 载荷） ----------
        if (gpsEntries.none { it.label.startsWith("GPS") }) {
            for (payload in out.tiffPayloads) {
                try {
                    val gpsIfd = TiffDeep.parse(payload, warnings).firstOrNull { it.title.startsWith("GPS") }
                    if (gpsIfd != null) {
                        gpsIfd.entries.forEach { gpsEntries.add(MetaEntry(it.label, it.value, it.important)) }
                        exifEntries.add(MetaEntry("定位状态", "已记录 GPS 位置 ⚠", important = true))
                        exifPrivacy.add("照片带有 GPS 定位信息，公开发布前建议抹除")
                        break
                    }
                } catch (e: Exception) { }
            }
        }
        if (exifEntries.isNotEmpty()) {
            sections.add(MetaSection("相机与曝光参数", "EXIF / CAMERA", exifEntries))
        }
        if (gpsEntries.isNotEmpty()) {
            sections.add(MetaSection("地理定位", "GEOTAG", gpsEntries))
        }

        // JPEG 补一张精度卡（8bit 明示）——放在位深提取之前
        if (fmt == Fmt.JPEG && sections.none { s -> s.entries.any { it.label == "位深" } }) {
            sections.add(MetaSection("JPEG 编码精度", "JPEG DECODE", listOf(
                MetaEntry("位深", "8 bit/通道（JPEG 标准上限）", important = true),
                MetaEntry("说明", "JPEG 标准为 8bit/通道；16bit 深度请使用 PNG/TIFF/RAW 存储"),
            )))
        }

        // ---------- v17 · 位深重点提取 + HDR 识别 ----------
        // 位深：从所有"位深/像素位深/色彩总位深"条目取最大值（RAW 看 CFA 位深，PNG 看 IHDR 位深）
        val bitCandidates = mutableListOf<Int>()
        (sections.flatMap { it.entries } + out.extra).forEach { e ->
            if (e.label == "位深" || e.label == "像素位深" || e.label == "色彩总位深" || e.label == "有效位深") {
                Regex("(\\d+)").findAll(e.value).forEach { m ->
                    val v = m.groupValues[1].toIntOrNull() ?: 0
                    if (v in 1..64) bitCandidates.add(v)
                }
            }
        }
        val maxBit = bitCandidates.maxOrNull()
        var bitDepth: String? = maxBit?.let { "$it bit" + if (it >= 10) "（高色深）" else "" }
        var hdr: String? = (out.extra + sections.flatMap { it.entries })
            .firstOrNull { it.label.startsWith("HDR") }?.value
        if (bitDepth == null && fmt == Fmt.HEIF) bitDepth = "8/10 bit（HEIF 常见）"
        if (hdr != null && sections.none { s -> s.entries.any { it.label.startsWith("HDR") } }) {
            sections.add(MetaSection("HDR 高动态范围", "HDR", listOf(
                MetaEntry("HDR 标识", hdr, important = true),
                MetaEntry("说明", "HDR 内容峰值亮度远超 SDR，兼容 SDR 显示时观感可能偏灰"),
            )))
        }
        // PNG 专属深度档案（用户要求：无损截图要有完整解析）
        if (fmt == Fmt.PNG) {
            val pngLabels = setOf(
                "色彩类型", "像素位深", "隔行扫描", "色彩总位深", "伽马值", "sRGB 渲染意向",
                "ICC 色彩配置", "印刷密度", "元数据修改时间", "APNG 动画", "HDR 传递函数",
            )
            val pngEntries = out.extra.filter { it.label in pngLabels }.map { MetaEntry(it.label, it.value, it.important) }
                .toMutableList()
            // 文本块内容全量（AI 线索/游戏引擎标注）
            out.texts.take(6).forEach { (k, v) ->
                pngEntries.add(MetaEntry("文本块 · $k", v.take(120).replace("\n", " ⏎ ")))
            }
            if (pngEntries.isNotEmpty()) {
                sections.add(MetaSection("PNG 深度档案", "PNG DECODE", pngEntries))
            }
        }

        // ---------- 4. AI 生成参数 ----------
        val aiParams = AiParser.detect(out.texts, userComment)

        // ---------- 5. XMP / IPTC ----------
        val xmpEntries = out.xmp?.let { XmpParser.extract(it) } ?: emptyList()
        if (xmpEntries.isNotEmpty()) {
            sections.add(MetaSection("XMP 扩展档案", "XMP", xmpEntries))
        }

        // ---------- 6. 视频深挖 ----------
        if (item.isVideo) {
            sections.add(MetaSection("视频流档案", "VIDEO STREAM", videoEntries(item, warnings)))
            val codec = videoCodecEntries(item, warnings)
            if (codec.isNotEmpty()) sections.add(MetaSection("编码与色彩科学", "CODEC / COLOR", codec))
        }

        // ---------- 7. 哈希 ----------
        val hashes = try {
            ColorHash.hashes(item.path)
        } catch (e: Exception) {
            warnings.add("哈希计算失败"); emptyList()
        }

        // ---------- 8. 色彩分析 ----------
        var histogram: HistogramData? = null
        var palette = emptyList<Int>()
        if (withColor && !item.isVideo) {
            try {
                val decoded = ColorHash.decodeSmall(item.path)
                if (decoded != null) {
                    histogram = ColorHash.histogram(decoded)
                    val stats = ColorHash.analyze(decoded)
                    palette = stats.palette
                    sections.add(MetaSection("色彩分析", "COLOR LAB", colorEntries(stats, false)))
                    decoded.recycle()
                } else {
                    // RAW/DNG 主数据是未显影 CFA，系统解码失败 → 用嵌入缩略图/系统生成缩略图分析
                    val thumb = context.contentResolver.loadThumbnail(item.uri, android.util.Size(512, 512), null)
                    histogram = ColorHash.histogram(thumb)
                    val stats = ColorHash.analyze(thumb)
                    palette = stats.palette
                    sections.add(MetaSection("色彩分析", "COLOR LAB · 预览图", colorEntries(stats, true)))
                    thumb.recycle()
                }
            } catch (e: Exception) {
                warnings.add("色彩分析失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

        // ---------- 9. 隐私体检 ----------
        val privacy = buildList {
            addAll(exifPrivacy)
            if (out.texts.any { it.first.equals("Software", true) && it.second.contains("Photoshop", true) }) {
                add("文件曾被 Photoshop 编辑（历史痕迹）")
            }
            if (xmpEntries.any { it.label == "AI 生成声明" }) {
                add("XMP 声明此图可能由 AI 生成")
            }
            if (aiParams != null) add("图片内嵌 AI 生成参数（可识别出提示词与模型）")
            if (fmt == Fmt.DNG) add("DNG 数字底片：内含完整传感器 RAW 数据，信息量远超普通照片")
        }

        return AnalysisResult(
            formatName = fmt.zhName,
            sections = sections,
            chunks = out.chunks,
            histogram = histogram,
            palette = palette,
            aiParams = aiParams,
            privacyIssues = privacy,
            hashes = hashes,
            parseWarnings = warnings,
            bitDepth = bitDepth,
            hdr = hdr,
            rawPreview = rawPreviewBmp,
            rawPreviewMeta = rawPreviewMetaStr,
        )
    }

    private fun colorEntries(stats: ColorHash.ColorStats, fromThumb: Boolean): List<MetaEntry> = buildList {
        if (fromThumb) add(MetaEntry("分析基准", "嵌入缩略图（RAW 主数据为未显影 CFA，无法直接显像）", important = true))
        add(MetaEntry("平均亮度", "${(stats.brightness * 100).toInt()} %（${brightnessDesc(stats.brightness)}）", important = true))
        add(MetaEntry("平均饱和度", "${(stats.saturation * 100).toInt()} %"))
        add(MetaEntry("色彩属性", if (stats.isGray) "灰度/近黑白图像" else "彩色图像"))
    }

    private val TIFF_META_LABELS = setOf(
        "GPS 定位信息", "黑电平", "白电平", "DNG 版本", "唯一相机型号", "色彩矩阵1", "色彩矩阵2",
        "固件/软件", "有效像素宽", "有效像素高", "压缩方式", "光度解释", "平面配置", "CFA 布局",
    )

    // ---------- 视频：基础（MediaMetadataRetriever） ----------
    private fun videoEntries(item: MediaItem, warnings: MutableList<String>): List<MetaEntry> {
        val entries = mutableListOf<MetaEntry>()
        val r = MediaMetadataRetriever()
        var gotDuration = false
        try {
            r.setDataSource(item.path)
            fun k(key: Int) = r.extractMetadata(key)
            k(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                gotDuration = true
                entries.add(MetaEntry("时长", FormatUi.fmtDuration(it.toLong()), important = true))
            }
            entries.add(MetaEntry("容器/编码", k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "未知"))
            k(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let {
                entries.add(MetaEntry("音轨", if (it != "0" && it != "no") "有" else "无"))
            }
            k(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.let { entries.add(MetaEntry("总帧数", it)) }
            k(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let { entries.add(MetaEntry("录制帧率", "$it fps")) }
            k(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { if (it != "0") entries.add(MetaEntry("旋转", "$it°")) }
            k(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { entries.add(MetaEntry("拍摄地点", it, important = true)) }
            k(MediaMetadataRetriever.METADATA_KEY_WRITER)?.let { entries.add(MetaEntry("封装工具", it)) }
            k(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { s ->
                val pretty = if (s.length >= 15) try {
                    "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)} ${s.substring(9, 11)}:${s.substring(11, 13)}:${s.substring(13, 15)}"
                } catch (e: Exception) { s } else s
                entries.add(MetaEntry("拍摄时间", pretty))
            }
        } catch (e: Exception) {
            warnings.add("视频元数据读取失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            r.release()
        }
        if (!gotDuration) {
            item.durationMs?.let { entries.add(MetaEntry("时长", FormatUi.fmtDuration(it), important = true)) }
        }
        return entries
    }

    // ---------- 视频：编码与色彩科学（MediaExtractor，支持华为 LOG/10bit/HDR 识别） ----------
    private fun videoCodecEntries(item: MediaItem, warnings: MutableList<String>): List<MetaEntry> {
        val entries = mutableListOf<MetaEntry>()
        val ex = MediaExtractor()
        try {
            ex.setDataSource(item.path)
            var trackFormat: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) { trackFormat = f; ex.selectTrack(i); break }
            }
            val f = trackFormat ?: return entries
            val mime = f.getString(MediaFormat.KEY_MIME) ?: "未知"
            entries.add(MetaEntry("视频编码格式", mime.uppercase(), important = true))
            if (f.containsKey(MediaFormat.KEY_BIT_RATE)) {
                val bps = f.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                entries.add(MetaEntry("码率", "%.2f Mbps".format(bps / 1_000_000.0), important = true))
            }
            if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                val fr = try { f.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (e: Exception) { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                entries.add(MetaEntry("目标帧率", "${fmtNumTidy(fr)} fps"))
            }
            if (f.containsKey(MediaFormat.KEY_PROFILE)) {
                val profile = f.getInteger(MediaFormat.KEY_PROFILE)
                entries.add(MetaEntry("编码 Profile", profileName(mime, profile), important = true))
            }
            if (f.containsKey(MediaFormat.KEY_LEVEL)) {
                entries.add(MetaEntry("编码规格", "Level ${f.getInteger(MediaFormat.KEY_LEVEL)}（档位越高支持的分辨率/码率上限越高）"))
            }
            var transferName = "未声明"
            var standardName = "未声明"
            if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                val t = f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
                transferName = when (t) {
                    MediaFormat.COLOR_TRANSFER_LINEAR -> "线性（Linear）"
                    MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR 视频 Gamma"
                    MediaFormat.COLOR_TRANSFER_ST2084 -> "ST 2084（PQ / HDR10）"
                    MediaFormat.COLOR_TRANSFER_HLG -> "HLG（混合对数 HDR）"
                    else -> "未知（$t）"
                }
                entries.add(MetaEntry("色彩传递函数", transferName, important = true))
            }
            if (f.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                val s = f.getInteger(MediaFormat.KEY_COLOR_STANDARD)
                standardName = when (s) {
                    MediaFormat.COLOR_STANDARD_BT709 -> "BT.709（SDR 常规）"
                    MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
                    MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
                    MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020（广色域）"
                    else -> "未知（$s）"
                }
                entries.add(MetaEntry("色彩空间标准", standardName, important = true))
            }
            if (f.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                val rge = f.getInteger(MediaFormat.KEY_COLOR_RANGE)
                entries.add(MetaEntry("色彩范围", when (rge) {
                    MediaFormat.COLOR_RANGE_FULL -> "Full（全幅 0-255）"
                    MediaFormat.COLOR_RANGE_LIMITED -> "Limited（视频级 16-235）"
                    else -> "未知（$rge）"
                }))
            }
            // 10bit 判定
            val bitDepth = when {
                mime.contains("hevc", true) && f.containsKey(MediaFormat.KEY_PROFILE) &&
                    f.getInteger(MediaFormat.KEY_PROFILE) == 2 -> 10 // HEVC Main10
                mime.contains("av01", true) && f.containsKey(MediaFormat.KEY_PROFILE) &&
                    f.getInteger(MediaFormat.KEY_PROFILE) == 1 -> 10
                mime.contains("vp9", true) && f.containsKey(MediaFormat.KEY_PROFILE) &&
                    f.getInteger(MediaFormat.KEY_PROFILE) == 2 -> 10
                else -> 8
            }
            entries.add(MetaEntry("色彩位深", "${bitDepth} bit" + if (bitDepth > 8) "（高精度 10bit 影调，LOG/HDR 典型特征）" else "", important = true))
            // LOG / HDR 综合判定
            val isHdrTransfer = f.containsKey(MediaFormat.KEY_COLOR_TRANSFER) && (
                f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_HLG ||
                    f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) == MediaFormat.COLOR_TRANSFER_ST2084)
            val isWide = f.containsKey(MediaFormat.KEY_COLOR_STANDARD) &&
                f.getInteger(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT2020
            entries.add(MetaEntry("拍摄模式判定", when {
                isHdrTransfer && isWide && bitDepth >= 10 -> "HDR / LOG 模式（10bit · HLG/PQ · BT.2020 广色域）——华为专业 LOG 视频的典型封装"
                isHdrTransfer && bitDepth >= 10 -> "HDR 模式（10bit · HLG/PQ 传递函数）"
                isWide && bitDepth >= 10 -> "广色域 10bit（疑似 LOG/HLG）"
                else -> "SDR 常规视频（8bit）"
            }, important = true))
            if (f.containsKey("hdr-static-info")) {
                entries.add(MetaEntry("HDR 静态元数据", "内嵌 SMPTE ST 2086（HDR10 亮度/色域描述）", important = true))
            }
        } catch (e: Exception) {
            warnings.add("编码深挖失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            ex.release()
        }
        return entries
    }

    private fun profileName(mime: String, profile: Int): String = when {
        mime.contains("hevc", true) -> when (profile) {
            1 -> "Main（8bit）"; 2 -> "Main 10（10bit HDR/LOG）"; 3 -> "Main Still"; 4 -> "Range Extensions"
            5 -> "High Throughput"; 6 -> "Multiview Main"; else -> "Profile $profile"
        }
        mime.contains("avc", true) -> when (profile) {
            1 -> "Baseline"; 2 -> "Main"; 3 -> "Extended"; 4 -> "High"; 8 -> "High"
            65536 -> "High（厂商标记 65536）"
            0x10002 -> "High 10"; else -> "Profile $profile"
        }
        mime.contains("av01", true) -> when (profile) { 0 -> "Main 8bit"; 1 -> "Main 10bit"; else -> "Profile $profile" }
        mime.contains("vp9", true) -> when (profile) { 0 -> "Profile 0 (8bit)"; 2 -> "Profile 2 (10bit)"; else -> "Profile $profile" }
        else -> "Profile $profile"
    }

    private fun fmtNumTidy(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

    private fun brightnessDesc(v: Double): String = when {
        v < 0.15 -> "偏暗"
        v < 0.35 -> "较暗"
        v < 0.65 -> "适中"
        v < 0.85 -> "较亮"
        else -> "过曝倾向"
    }

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private fun ts(epoch: Long): String = df.format(Date(epoch * 1000))
}
