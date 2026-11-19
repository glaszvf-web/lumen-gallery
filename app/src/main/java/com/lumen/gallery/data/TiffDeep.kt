package com.lumen.gallery.data

/**
 * TiffDeep —— TIFF/DNG/CR2/NEF 深度解析引擎
 * 按 IFD 分组输出（IFD0 / SubIFD 原始数据 / ExifIFD / GPS / IFD1 缩略图），
 * 全量 tag 中文字典覆盖：TIFF 基线 + Exif + DNG 全套（含 CFA/色彩矩阵/校准光源/黑电平）。
 * tag 码表对齐 dng_sdk dng_tag_codes.h 与 ExifTool DNG.pm。
 */

data class TiffEntry(
    val tag: Int,
    val label: String,
    val value: String,
    val important: Boolean = false,
)

data class TiffIfd(
    val title: String,
    val subtitle: String,
    val entries: List<TiffEntry>,
)

object TiffDeep {

    private const val MAX_ARRAY_ITEMS = 16

    // ---------- tag 字典 ----------
    private val TAGS: Map<Int, String> = mapOf(
        // TIFF 基线
        0x00FE to "子文件类型", 0x0100 to "图像宽度", 0x0101 to "图像高度",
        0x0102 to "位深", 0x0103 to "压缩方式", 0x0106 to "光度解释", 0x010D to "文档名",
        0x010E to "图像描述", 0x010F to "相机制造商", 0x0110 to "相机型号", 0x0111 to "条带数据偏移",
        0x0112 to "方向", 0x0115 to "采样数", 0x0116 to "每条带行数", 0x0117 to "条带字节数",
        0x011A to "水平分辨率", 0x011B to "垂直分辨率", 0x011C to "平面配置", 0x0128 to "分辨率单位",
        0x0131 to "固件/软件", 0x0132 to "修改时间", 0x013B to "作者", 0x013C to "主机电脑",
        0x0142 to "Tile 宽度", 0x0143 to "Tile 高度", 0x0144 to "Tile 偏移", 0x0145 to "Tile 字节数",
        0x014A to "子 IFD 指针", 0x0201 to "缩略图偏移", 0x0202 to "缩略图字节数", 0x0213 to "YCbCr 定位",
        0x828D to "CFA 重复阵列维数", 0x828E to "CFA 滤色阵列", 0x8773 to "ICC 色彩配置",
        // Exif 拍摄参数
        0x829A to "快门速度", 0x829D to "光圈", 0x8822 to "曝光模式", 0x8824 to "光谱灵敏度",
        0x8827 to "ISO 感光度", 0x8830 to "灵敏度类型", 0x8832 to "标准输出感光度",
        0x9000 to "Exif 版本", 0x9003 to "拍摄时间", 0x9004 to "数字化时间",
        0x9010 to "时区偏移", 0x9011 to "拍摄时区", 0x9012 to "数字化时区",
        0x9101 to "分量配置", 0x9102 to "压缩比特率", 0x9201 to "快门速度值(APEX)", 0x9202 to "光圈值(APEX)",
        0x9203 to "亮度值(APEX)", 0x9204 to "曝光补偿", 0x9205 to "最大光圈", 0x9206 to "对焦距离",
        0x9207 to "测光方式", 0x9208 to "光源类型", 0x9209 to "闪光灯", 0x920A to "焦距",
        0x9214 to "主体区域", 0x927C to "厂商注释区", 0x9286 to "用户注释",
        0x9290 to "亚秒", 0x9291 to "拍摄亚秒", 0x9292 to "数字化亚秒",
        0xA000 to "Flashpix 版本", 0xA001 to "色彩空间", 0xA002 to "有效像素宽", 0xA003 to "有效像素高",
        0xA20B to "闪光能量", 0xA20E to "感光面横向分辨率", 0xA210 to "感光面分辨率单位",
        0xA215 to "曝光指数", 0xA217 to "感光方式", 0xA300 to "文件来源", 0xA301 to "场景类型",
        0xA401 to "自定义渲染", 0xA402 to "曝光程序", 0xA403 to "白平衡", 0xA404 to "数码变焦",
        0xA405 to "等效焦距", 0xA406 to "场景类型", 0xA407 to "增益控制", 0xA408 to "对比度",
        0xA409 to "饱和度", 0xA40A to "锐度", 0xA40C to "对焦距离范围", 0xA420 to "图像唯一 ID",
        0xA430 to "相机所有者", 0xA431 to "机身序列号", 0xA432 to "镜头规格", 0xA434 to "镜头制造商",
        0xA435 to "镜头型号", 0xA436 to "镜头序列号",
        // DNG 全套（对齐 dng_sdk dng_tag_codes.h）
        0xC612 to "DNG 版本", 0xC613 to "DNG 向后兼容版本", 0xC614 to "唯一相机型号",
        0xC615 to "CFA 平面颜色", 0xC616 to "CFA 布局", 0xC617 to "线性化表",
        0xC618 to "黑电平重复维数", 0xC619 to "黑电平", 0xC61A to "黑电平横向增量",
        0xC61B to "黑电平纵向增量", 0xC61C to "白电平", 0xC61D to "默认缩放",
        0xC61E to "默认裁剪原点", 0xC61F to "默认裁剪尺寸", 0xC620 to "默认裁剪宽高比",
        0xC621 to "色彩矩阵 1", 0xC622 to "色彩矩阵 2", 0xC623 to "相机校准矩阵 1",
        0xC624 to "相机校准矩阵 2", 0xC625 to "还原矩阵 1", 0xC626 to "还原矩阵 2",
        0xC627 to "模拟增益平衡", 0xC628 to "拍摄中性色", 0xC629 to "拍摄白点坐标",
        0xC62A to "基准曝光", 0xC62B to "基准噪声", 0xC62C to "基准锐度", 0xC62D to "拜耳绿通道分离",
        0xC62E to "线性响应上限", 0xC62F to "相机序列号", 0xC630 to "镜头信息",
        0xC631 to "色度模糊半径", 0xC632 to "抗锯齿强度", 0xC633 to "阴影缩放",
        0xC634 to "DNG 私有数据", 0xC635 to "厂商注释安全标记",
        0xC63A to "校准光源 1", 0xC63B to "校准光源 2", 0xC63C to "最佳质量缩放",
        0xC63D to "有效区域", 0xC63E to "遮罩区域", 0xC63F to "拍摄 ICC 配置",
        0xC640 to "拍摄前配置矩阵", 0xC641 to "当前 ICC 配置", 0xC642 to "当前前配置矩阵",
        0xC643 to "色度参考", 0xC644 to "拍摄配置名", 0xC645 to "原始 RAW 文件名",
        0xC646 to "原始 RAW 文件数据", 0xC647 to "色相饱和映射维数", 0xC648 to "色相饱和映射数据 1",
        0xC649 to "色相饱和映射数据 2", 0xC64A to "配置色调曲线", 0xC64B to "配置嵌入策略",
        0xC64C to "配置版权", 0xC64D to "前向矩阵 1", 0xC64E to "前向矩阵 2",
        0xC64F to "预览应用名", 0xC650 to "预览应用版本", 0xC653 to "预览色彩空间",
        0xC654 to "预览时间", 0xC655 to "RAW 图像摘要", 0xC656 to "原始 RAW 摘要",
        0xC657 to "子 Tile 块大小", 0xC658 to "行交错因子",
        0xC740 to "镜头矫正指令 1", 0xC741 to "镜头矫正指令 2", 0xC742 to "镜头矫正指令 3",
        0xC743 to "噪声模型", 0xC744 to "配置增益表映射",
    )

    // ---------- 枚举转译 ----------
    private val COMPRESSION = mapOf(
        1 to "未压缩", 2 to "CCITT RLE", 5 to "LZW", 6 to "JPEG（旧式）", 7 to "JPEG",
        8 to "Deflate", 32773 to "PackBits", 34887 to "LZMA", 34892 to "有损 JPEG",
    )
    private val PHOTOMETRIC = mapOf(
        0 to "白底黑字（MinIsWhite）", 1 to "黑底白字（灰度 MinIsBlack）", 2 to "RGB 真彩", 3 to "调色板索引色", 5 to "CMYK 印刷色",
        6 to "YCbCr", 8 to "CIELab", 32803 to "CFA 滤色阵列（RAW 原始数据）", 34892 to "线性 RAW",
        34896 to "深度图",
    )
    private val SUBFILE_TYPE = mapOf(
        0 to "全分辨率主图", 1 to "缩略/降采样图", 2 to "多页文档单页", 3 to "透明替代页", 4 to "降采样多页",
    )
    private val PLANAR = mapOf(1 to "紧凑排列（Chunky）", 2 to "平面分离（Planar）")
    private val RES_UNIT = mapOf(1 to "无单位", 2 to "英寸", 3 to "厘米", 4 to "毫米", 5 to "微米")
    private val COLOR_SPACE = mapOf(1 to "sRGB", 2 to "Adobe RGB", 0xFFFF to "未校准")
    private val YCBCR_POS = mapOf(1 to "居中（视频常用）", 2 to "共定位（JPEG 默认）")
    private val LIGHT_SOURCE = mapOf(
        0 to "未知", 1 to "日光", 2 to "荧光灯", 3 to "钨丝灯", 4 to "闪光灯", 9 to "晴天",
        10 to "多云", 11 to "阴影", 12 to "日光荧光(D 5700-7100K)", 13 to "日白荧光(N 4600-5500K)",
        14 to "冷白荧光(W 3800-4500K)", 15 to "白荧光(WW 3250-3800K)", 16 to "暖白荧光(L 2600-3250K)",
        17 to "标准光源 A", 18 to "标准光源 B", 19 to "标准光源 C", 20 to "D55", 21 to "D65", 22 to "D75",
        23 to "D50", 24 to "ISO 摄影钨丝灯", 32 to "扩展光源 D55", 33 to "扩展光源 D65",
        34 to "扩展光源 D75", 35 to "扩展光源 D50", 255 to "其他光源",
    )
    private val METERING = mapOf(
        0 to "未知", 1 to "平均测光", 2 to "中央重点平均", 3 to "点测光", 4 to "多点测光",
        5 to "评价测光", 6 to "局部测光", 255 to "其他",
    )
    private val EXPOSURE_PROGRAM = mapOf(
        0 to "未定义", 1 to "手动", 2 to "程序自动", 3 to "光圈优先", 4 to "快门优先",
        5 to "创意程序", 6 to "动作程序", 7 to "人像模式", 8 to "风景模式", 9 to "夜景", 10 to "手持夜景",
    )
    private val SENSING_METHOD = mapOf(
        1 to "未定义", 2 to "单芯片彩色传感器", 3 to "双芯片彩色传感器", 4 to "三芯片彩色传感器",
        5 to "顺序色彩传感器", 7 to "三线传感器", 8 to "顺序线色传感器",
    )
    private val CFA_LAYOUT = mapOf(
        1 to "方阵（拜耳 RGGB 类）", 2 to "1/2 行交替阵列", 3 to "4 色阵列", 4 to "1/3 行交替阵列",
        5 to "1/4 行交替阵列", 6 to "2.7K 阵列（Sony）",
    )
    private val CFA_COLOR = mapOf(0 to "红", 1 to "绿", 2 to "蓝", 3 to "青", 4 to "品红", 5 to "黄", 6 to "白")
    private val FILE_SOURCE = mapOf(1 to "胶片扫描", 2 to "反射打印件扫描", 3 to "数码相机直拍", 4 to "其他")

    private val IMPORTANT_TAGS = setOf(
        0x010F, 0x0110, 0x829A, 0x829D, 0x8827, 0x920A, 0xA405, 0x0102, 0x0103, 0x0106,
        0xC612, 0xC614, 0xC628, 0xC63D, 0x0117, 0x0111, 0xA435, 0x0112, 0x9003, 0xC61F,
    )

    private val TYPE_SIZES = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8)

    private val GPS_TAGS = mapOf(
        0x0000 to "GPS 版本", 0x0001 to "纬度参考", 0x0002 to "GPS 纬度", 0x0003 to "经度参考",
        0x0004 to "GPS 经度", 0x0005 to "海拔参考", 0x0006 to "GPS 海拔", 0x0007 to "测量基准面（大地水准）",
        0x0008 to "测量时间", 0x0009 to "定位状态", 0x000A to "差分站 ID（DGPS 基站）", 0x000B to "定位精度 HDOP",
        0x000C to "测速参考", 0x000D to "GPS 速度", 0x000E to "运动方向", 0x0010 to "成像方向",
        0x0012 to "地图基准", 0x001B to "GPS 处理方法",
    )

    // ---------- 解析入口 ----------
    fun parse(data: ByteArray, warnings: MutableList<String>): List<TiffIfd> {
        val out = mutableListOf<TiffIfd>()
        try {
            if (data.size < 8) return out
            val bo = when {
                data[0].toInt() == 'I'.code && data[1].toInt() == 'I'.code -> true
                data[0].toInt() == 'M'.code && data[1].toInt() == 'M'.code -> false
                else -> return out
            }
            val magic = if (bo) u16le(data, 2) else u16be(data, 2)
            if (magic != 42) { warnings.add("TIFF 魔数异常（$magic）"); return out }
            fun u16(off: Int) = if (bo) u16le(data, off) else u16be(data, off)
            fun u32(off: Int) = if (bo) u32le(data, off) else u32be(data, off)

            val visited = mutableSetOf<Int>()
            var chain = u32(4).toInt()
            var chainIdx = 0
            while (chain > 0 && chain + 2 <= data.size && chainIdx < 4 && chain !in visited) {
                visited.add(chain)
                val (ifd, chained) = parseOneIfd(
                    data, chain, bo, ::u16, ::u32, warnings, visited,
                    title = if (chainIdx == 0) "IFD0 · 主元数据" else "IFD$chainIdx · 缩略图",
                    subtitle = if (chainIdx == 0) "IFD0" else "IFD$chainIdx",
                )
                if (ifd != null) out.add(ifd)
                out.addAll(chained)
                chain = u32(chain + 2 + u16(chain) * 12).toInt()
                chainIdx++
            }
        } catch (e: Exception) {
            warnings.add("TIFF 深度解析中断：${e.message ?: e.javaClass.simpleName}")
        }
        return out
    }

    /** 返回 本 IFD + 其挂链的 Exif/GPS/SUB IFD（顺序：本 → SubIFDs → Exif → GPS） */
    private fun parseOneIfd(
        data: ByteArray, ifdOff: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
        warnings: MutableList<String>, visited: MutableSet<Int>,
        title: String, subtitle: String,
    ): Pair<TiffIfd?, List<TiffIfd>> {
        val chained = mutableListOf<TiffIfd>()
        try {
            if (ifdOff + 2 > data.size) return null to chained
            val count = u16(ifdOff)
            if (count > 1024) { warnings.add("IFD 条目数异常（$count）"); return null to chained }
            val entries = mutableListOf<TiffEntry>()
            val subPointers = mutableListOf<Int>()
            var subExif = -1; var subGps = -1
            var subFileTypeName: String? = null
            for (i in 0 until count) {
                val e = ifdOff + 2 + i * 12
                if (e + 12 > data.size) break
                val tag = u16(e)
                val type = u16(e + 2)
                val cnt = u32(e + 4).toInt()
                if (type <= 0 || type >= TYPE_SIZES.size || cnt > 200000) continue
                val total = TYPE_SIZES[type] * cnt
                val vOff = if (total <= 4) e + 8 else u32(e + 8).toInt()
                if (vOff < 0 || vOff + total > data.size) continue
                when (tag) {
                    0x8769 -> { subExif = u32(e + 8).toInt(); continue }
                    0x8825 -> { subGps = u32(e + 8).toInt(); continue }
                    0x014A -> {
                        val base = if (total <= 4) e + 8 else vOff
                        for (k in 0 until minOf(cnt, 8)) {
                            val p = u32(base + k * 4).toInt()
                            if (p > 0 && p !in visited) subPointers.add(p)
                        }
                        continue
                    }
                    0x00FE -> subFileTypeName = SUBFILE_TYPE[u32(e + 8).toInt()]
                }
                val label = TAGS[tag] ?: "TIFF 0x%04X".format(tag)
                val value = prettify(tag, renderValue(data, vOff, type, cnt, bo, u16, u32), cnt, data, vOff, type, bo, u16, u32)
                entries.add(TiffEntry(tag, label, value, important = tag in IMPORTANT_TAGS))
            }
            // 子 IFD（DNG RAW 数据 / 多页）
            subPointers.forEachIndexed { idx, subOff ->
                if (subOff !in visited) {
                    visited.add(subOff)
                    val (sub, subChained) = parseOneIfd(
                        data, subOff, bo, u16, u32, warnings, visited,
                        title = if (subFileTypeName?.contains("全分辨率") == true || isCfaIfd(data, subOff, bo, u16, u32))
                            "SubIFD · RAW 原始数据（CFA）" else "SubIFD · 补充数据 ${idx + 1}",
                        subtitle = "SubIFD[$idx]",
                    )
                    sub?.let { chained.add(it) }
                    chained.addAll(subChained)
                }
            }
            if (subExif > 0 && subExif !in visited) {
                visited.add(subExif)
                val (exif, exifChained) = parseOneIfd(data, subExif, bo, u16, u32, warnings, visited, "Exif · 拍摄参数", "ExifIFD")
                exif?.let { chained.add(it) }
                chained.addAll(exifChained)
            }
            if (subGps > 0 && subGps !in visited) {
                visited.add(subGps)
                parseGpsIfd(data, subGps, bo)?.let { chained.add(it) }
            }
            return TiffIfd(title, subtitle, entries) to chained
        } catch (e: Exception) {
            warnings.add("IFD 解析中断（$title）：${e.message ?: e.javaClass.simpleName}")
            return null to chained
        }
    }

    private fun isCfaIfd(data: ByteArray, off: Int, bo: Boolean, u16: (Int) -> Int, u32: (Int) -> Long): Boolean {
        return try {
            if (off + 2 > data.size) return false
            val count = u16(off)
            for (i in 0 until count) {
                val e = off + 2 + i * 12
                if (e + 12 > data.size) break
                if (u16(e) == 0x0106) {
                    val v = u32(e + 8)
                    return v == 32803L
                }
            }
            false
        } catch (e: Exception) { false }
    }

    private fun prettify(
        tag: Int, raw: String, cnt: Int, data: ByteArray, off: Int, type: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
    ): String = when (tag) {
        0x0103 -> COMPRESSION[raw.trim().toIntOrNull()] ?: raw
        0x0106 -> PHOTOMETRIC[raw.trim().toIntOrNull()] ?: raw
        0x00FE -> SUBFILE_TYPE[raw.trim().toIntOrNull()] ?: raw
        0x011C -> PLANAR[raw.trim().toIntOrNull()] ?: raw
        0x0128 -> RES_UNIT[raw.trim().toIntOrNull()] ?: raw
        0xA001 -> COLOR_SPACE[raw.trim().toIntOrNull()] ?: raw
        0x0213 -> YCBCR_POS[raw.trim().toIntOrNull()] ?: raw
        0x9207 -> METERING[raw.trim().toIntOrNull()] ?: raw
        0x8822 -> EXPOSURE_PROGRAM[raw.trim().toIntOrNull()] ?: raw
        0x9208 -> LIGHT_SOURCE[raw.trim().toIntOrNull()] ?: raw
        0xA217 -> SENSING_METHOD[raw.trim().toIntOrNull()] ?: raw
        0xC63A, 0xC63B -> LIGHT_SOURCE[raw.trim().toIntOrNull()] ?: raw
        0xC616 -> CFA_LAYOUT[raw.trim().toIntOrNull()] ?: raw
        0x828E -> cfaPattern(raw)
        0xC615 -> cfaColors(raw)
        0xA300 -> FILE_SOURCE[raw.trim().toIntOrNull()] ?: raw
        0x0117, 0x0202 -> raw + "（约 ${FormatUi.fmtSize(raw.trim().toLongOrNull() ?: 0)}）"
        0x927C -> makerNoteProbe(data, off, cnt, bo, u16, u32)
        0xC634 -> "[$cnt 字节厂商私有数据]"
        0xC740, 0xC741, 0xC742 -> "[$cnt 项镜头矫正指令（畸变/色散/噪点修正参数）]"
        0xC744 -> "[$cnt 项增益表映射（镜头到成像的画质修正大表）]"
        0xC617 -> "[$cnt 项线性化表]"
        0xC612, 0xC613 -> raw.split(",").mapNotNull { it.trim().toIntOrNull() }.joinToString(".")
        0x9000 -> raw.split(",").mapNotNull { it.trim().toIntOrNull() }.map { it.toChar() }.joinToString("")
        0x829A -> {
            // 快门速度：RATIONAL 秒 → 1/N″
            val den = if (bo) u32le(data, off + 4) else u32be(data, off + 4)
            val num = if (bo) u32le(data, off) else u32be(data, off)
            if (den == 0L) raw else {
                val v = num.toDouble() / den
                if (v >= 1.0) "${fmtNum(v, 1)}″" else "1/${kotlin.math.round(1.0 / v).toInt()}″"
            }
        }
        0x829D -> {
            // 光圈：RATIONAL → f/x.x
            val den = if (bo) u32le(data, off + 4) else u32be(data, off + 4)
            val num = if (bo) u32le(data, off) else u32be(data, off)
            if (den == 0L) raw else "f/${fmtNum(num.toDouble() / den, 1)}"
        }
        else -> raw
    }

    /** v15→v27 · 厂商注释区全量逆向入口（实现在文件尾部 makerNoteProbe） */

    private fun renderMakerValue(
        data: ByteArray, off: Int, type: Int, cnt: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
    ): String = try {
        when (type) {
            2 -> {
                var end = off
                var i = 0
                while (i < cnt && end < data.size && data[end] != 0.toByte()) { end++; i++ }
                String(data, off, end - off, Charsets.UTF_8).replace("\u0000", "").ifBlank { "…" }
            }
            3 -> (0 until minOf(cnt, 4)).joinToString(",") { u16(off + it * 2).toString() }
            4 -> (0 until minOf(cnt, 4)).joinToString(",") { u32(off + it * 4).toString() }
            5, 10 -> (0 until minOf(cnt, 2)).joinToString(",") {
                val n = if (bo) u32le(data, off + it * 8) else u32be(data, off + it * 8)
                val de = if (bo) u32le(data, off + it * 8 + 4) else u32be(data, off + it * 8 + 4)
                if (de == 0L) "∞" else "${n}/${de}"
            }
            else -> (0 until minOf(cnt, 4)).joinToString(",") { (data[off + it].toInt() and 0xFF).toString() }
        }
    } catch (e: Exception) { "…" }

    private fun cfaPattern(raw: String): String {
        val nums = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (nums.isEmpty() || nums.any { CFA_COLOR[it] == null }) return raw
        val zh = nums.joinToString("") { CFA_COLOR[it]!!.substring(0, 1) }
        return if (nums.size == 4) {
            "[$zh]（2×2 拜耳滤色阵列）"
        } else {
            "[$zh]（滤色阵列）"
        }
    }

    private fun cfaColors(raw: String): String {
        val nums = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (nums.isEmpty()) return raw
        return nums.joinToString("、") { CFA_COLOR[it] ?: "$it" } + "（按传感器平面顺序）"
    }

    private fun renderValue(
        data: ByteArray, off: Int, type: Int, cnt: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
    ): String = try {
        when (type) {
            2 -> { // ASCII
                var end = off
                var i = 0
                while (i < cnt && end < data.size && data[end] != 0.toByte()) { end++; i++ }
                String(data, off, end - off, Charsets.UTF_8).replace("\u0000", "")
            }
            1, 6 -> {
                val n = minOf(cnt, MAX_ARRAY_ITEMS)
                (0 until n).joinToString(", ") { (data[off + it].toInt() and 0xFF).toString() } +
                    if (cnt > MAX_ARRAY_ITEMS) " …（共 $cnt 字节）" else ""
            }
            3 -> (0 until minOf(cnt, MAX_ARRAY_ITEMS)).joinToString(", ") { u16(off + it * 2).toString() } +
                if (cnt > MAX_ARRAY_ITEMS) " …（共 $cnt 项）" else ""
            4 -> (0 until minOf(cnt, MAX_ARRAY_ITEMS)).joinToString(", ") { u32(off + it * 4).toString() } +
                if (cnt > MAX_ARRAY_ITEMS) " …（共 $cnt 项）" else ""
            5 -> (0 until minOf(cnt, MAX_ARRAY_ITEMS)).joinToString(" ") { frac(data, off + it * 8, bo) } +
                if (cnt > MAX_ARRAY_ITEMS) " …（共 $cnt 项）" else ""
            7 -> "二进制（$cnt 字节）"
            9 -> (0 until minOf(cnt, MAX_ARRAY_ITEMS)).joinToString(", ") { u32(off + it * 4).toInt().toString() }
            10 -> (0 until minOf(cnt, MAX_ARRAY_ITEMS)).joinToString(" ") { frac(data, off + it * 8, bo) } +
                if (cnt > MAX_ARRAY_ITEMS) " …（共 $cnt 项）" else ""
            else -> "未常规类型 TIFF Type $type"
        }
    } catch (e: Exception) { "?" }

    /** 有理数：整数显示整数，否则保留精确分数（对齐参考软件的 1094/1024 风格） */
    private fun frac(data: ByteArray, off: Int, bo: Boolean): String {
        val num = if (bo) u32le(data, off) else u32be(data, off)
        val den = if (bo) u32le(data, off + 4) else u32be(data, off + 4)
        if (den == 0L) return "∞"
        val signed = if (num > 0x7FFFFFFFL) num - (1L shl 32) else num
        return if (signed % den == 0L) "${signed / den}" else "${signed}/${den}"
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

    private fun fmtDms(deg: Double): String {
        val a = kotlin.math.abs(deg)
        val d = a.toInt()
        val mF = (a - d) * 60
        val m = mF.toInt()
        val s = (mF - m) * 60
        return "${d}°$m′${"%.2f".format(s)}″"
    }

    private fun parseGpsIfd(data: ByteArray, ifdOff: Int, bo: Boolean): TiffIfd? {
        return try {
            fun u16(off: Int) = if (bo) u16le(data, off) else u16be(data, off)
            fun u32(off: Int) = if (bo) u32le(data, off) else u32be(data, off)
            if (ifdOff + 2 > data.size) return null
            val count = u16(ifdOff)
            val entries = mutableListOf<TiffEntry>()
            var latDeg = 0.0; var lonDeg = 0.0
            for (i in 0 until count) {
                val e = ifdOff + 2 + i * 12
                if (e + 12 > data.size) break
                val tag = u16(e)
                val type = u16(e + 2)
                val cnt = u32(e + 4).toInt()
                if (type <= 0 || type >= TYPE_SIZES.size || cnt > 200000) continue
                val total = TYPE_SIZES[type] * cnt
                val vOff = if (total <= 4) e + 8 else u32(e + 8).toInt()
                if (vOff < 0 || vOff + total > data.size) continue
                val label = GPS_TAGS[tag] ?: "GPS 0x%04X".format(tag)
                val value: String = when (tag) {
                    0x0000 -> (0 until minOf(cnt, 4)).joinToString(".") { (data[vOff + it].toInt() and 0xFF).toString() }
                    0x0001 -> if (String(data, vOff, 1, Charsets.US_ASCII) == "N") "北纬" else "南纬"
                    0x0002 -> { latDeg = dms(data, vOff, cnt, bo); "${fmtDms(latDeg)}（${fmtNum(latDeg, 6)}）" }
                    0x0003 -> if (String(data, vOff, 1, Charsets.US_ASCII) == "E") "东经" else "西经"
                    0x0004 -> { lonDeg = dms(data, vOff, cnt, bo); "${fmtDms(lonDeg)}（${fmtNum(lonDeg, 6)}）" }
                    0x0005 -> if ((data[vOff].toInt() and 0xFF) == 1) "海平面以下" else "海平面以上"
                    0x0006 -> "${fmtNum(ratD(data, vOff, bo), 1)} m"
                    0x0007 -> when (data[vOff].toInt() and 0xFF) { 1 -> "GPS 判定"; 2 -> "地面测量"; 3 -> "卫星定位"; else -> "其他" }
                    0x0009 -> if (data[vOff].toInt() == 'A'.code) "有效定位" else "信号缺失"
                    0x000B -> fmtNum(ratD(data, vOff, bo), 2)
                    0x0012 -> "WGS-84 世界大地坐标系"
                    else -> renderValue(data, vOff, type, cnt, bo, ::u16, ::u32)
                }
                entries.add(TiffEntry(tag, label, value, important = tag in setOf(0x0002, 0x0004, 0x0006)))
            }
            if (entries.isEmpty()) null else TiffIfd("GPS · 定位档案", "GPS IFD", entries)
        } catch (e: Exception) { null }
    }

    private fun ratD(data: ByteArray, off: Int, bo: Boolean): Double {
        val num = if (bo) u32le(data, off) else u32be(data, off)
        val den = if (bo) u32le(data, off + 4) else u32be(data, off + 4)
        return if (den == 0L) 0.0 else num.toDouble() / den
    }

    fun fmtNum(v: Double, digits: Int): String =
        String.format(java.util.Locale.US, "%.${digits}f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    // ══════════════════════════════════════════════════════════
    //  v27 · RAW 显影预览：提取底片内嵌 JPEG（IFD0/SubIFD 的 0x0201/0x0202）
    // ══════════════════════════════════════════════════════════

    data class RawPreview(val offset: Int, val length: Int, val source: String)

    /**
     * 快速扫描 TIFF 家族字节流，收集全部内嵌 JPEG 预览（缩略图/降采样图）。
     * 不产出 IFD 卡，只为显影预览服务；返回按字节数降序的候选列表。
     */
    fun extractPreviews(data: ByteArray): List<RawPreview> {
        val out = mutableListOf<RawPreview>()
        try {
            if (data.size < 8) return out
            val bo = data[0].toInt() == 'I'.code && data[1].toInt() == 'I'.code
            fun u16(o: Int) = if (bo) u16le(data, o) else u16be(data, o)
            fun u32(o: Int) = if (bo) u32le(data, o) else u32be(data, o)
            val visited = mutableSetOf<Int>()

            fun scanIfd(ifdOff: Int, source: String) {
                if (ifdOff <= 0 || ifdOff + 2 > data.size || ifdOff in visited) return
                visited.add(ifdOff)
                val count = u16(ifdOff)
                if (count <= 0 || count > 1024) return
                var jpegOff = -1; var jpegLen = -1
                for (i in 0 until count) {
                    val e = ifdOff + 2 + i * 12
                    if (e + 12 > data.size) break
                    when (u16(e)) {
                        0x0201 -> jpegOff = u32(e + 8).toInt()
                        0x0202 -> jpegLen = u32(e + 8).toInt()
                        0x014A -> { // SubIFD 指针（降采样图常在 SubIFD）
                            val total = TYPE_SIZES[4] * u32(e + 4).toInt()
                            val base = if (total <= 4) e + 8 else u32(e + 8).toInt()
                            val n = minOf(u32(e + 4).toInt(), 8)
                            for (k in 0 until n) {
                                val p = u32(base + k * 4).toInt()
                                if (p > 0) scanIfd(p, "SubIFD[$k]")
                            }
                        }
                    }
                }
                if (jpegOff > 0 && jpegLen > 0 && jpegOff + jpegLen <= data.size) {
                    // 验明正身：JPEG 必须以 FF D8 开头
                    if ((data[jpegOff].toInt() and 0xFF) == 0xFF && (data[jpegOff + 1].toInt() and 0xFF) == 0xD8) {
                        out.add(RawPreview(jpegOff, jpegLen, source))
                    }
                }
            }
            scanIfd(u32(4).toInt(), "IFD0")
        } catch (_: Exception) { }
        return out.sortedByDescending { it.length }
    }

    // ══════════════════════════════════════════════════════════
    //  v27 · 厂商注释区全量逆向（升级 v15 启发式）：
    //  多基址 × 双偏移基准 × 全条目输出，命中即给完整解码
    // ══════════════════════════════════════════════════════════

    private class MakerHit(val base: Int, val mode: String, val entries: List<Pair<Int, String>>) {
        val score: Int get() = entries.size
    }

    private fun makerProbeAll(
        data: ByteArray, off: Int, cnt: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
    ): MakerHit? {
        // 候选基址：0 是标准 IFD 直起；6/8/10/12/16/22/26 覆盖各厂商常见头长度
        val bases = intArrayOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28)
        var best: MakerHit? = null
        for (base in bases) {
            val ifd = off + base
            if (ifd + 2 > data.size) continue
            val count = u16(ifd)
            if (count !in 3..300) continue
            // 偏移基准两种：相对 MakerNote 起点 / 相对 TIFF 头（绝对地址）
            for (mode in listOf("相对基址", "绝对地址")) {
                val parsed = mutableListOf<Pair<Int, String>>()
                var valid = 0
                for (i in 0 until minOf(count, 80)) {
                    val e = ifd + 2 + i * 12
                    if (e + 12 > data.size) break
                    val tag = u16(e)
                    val type = u16(e + 2)
                    if (type !in 1..13) continue
                    val c = u32(e + 4).toInt()
                    if (c > 200000) continue
                    val sizes = intArrayOf(0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 8)
                    val total = sizes[type] * c
                    val rawPtr = u32(e + 8).toInt()
                    val vOff = if (total <= 4) e + 8
                    else when (mode) {
                        "相对基址" -> off + rawPtr
                        else -> rawPtr
                    }
                    if (vOff < 0 || vOff + total > data.size) continue
                    valid++
                    if (parsed.size < 48) {
                        parsed.add(tag to renderMakerValue(data, vOff, type, c, bo, u16, u32))
                    }
                }
                // 命中标准：合法条目占比高（防误命中二进制区）
                if (valid >= 5 && valid >= count * 6 / 10) {
                    val hit = MakerHit(base, mode, parsed)
                    if (best == null || hit.score > best!!.score) best = hit
                }
            }
        }
        return best
    }

    private fun makerNoteProbe(
        data: ByteArray, off: Int, cnt: Int, bo: Boolean,
        u16: (Int) -> Int, u32: (Int) -> Long,
    ): String {
        val sb = StringBuilder()
        val hit = makerProbeAll(data, off, cnt, bo, u16, u32)
        if (hit != null && hit.entries.isNotEmpty()) {
            sb.append("逆向命中（基址 +${hit.base}，偏移${hit.mode}，${hit.entries.size} 项全解码）：")
            hit.entries.forEach { (tag, v) -> sb.append(" ␟0x${"%04X".format(tag)}=${v.take(48)}") }
        } else {
            sb.append("头 64 字节：")
            for (i in 0 until minOf(64, cnt)) sb.append("%02X".format(data[off + i].toInt() and 0xFF))
            sb.append(" · 该厂商注释区不符合标准 IFD 结构（已试 15 种基址×2 种偏移基准），华为未公开此区规范，保留原始字节供取证")
        }
        return sb.toString().take(2400)
    }
}
