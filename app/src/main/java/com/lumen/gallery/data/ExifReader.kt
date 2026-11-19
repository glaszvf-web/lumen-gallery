package com.lumen.gallery.data

import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExifReader {

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun read(exif: ExifInterface): Triple<List<MetaEntry>, List<MetaEntry>, List<String>> {
        return run(exif)
    }

    private fun run(exif: ExifInterface): Triple<List<MetaEntry>, List<MetaEntry>, List<String>> {
        val entries = mutableListOf<MetaEntry>()
        val privacy = mutableListOf<String>()

        fun raw(tag: String): String? = exif.getAttribute(tag)?.takeIf { it.isNotBlank() }

        // ---- 相机与镜头 ----
        val make = raw(ExifInterface.TAG_MAKE)
        val model = raw(ExifInterface.TAG_MODEL)
        if (make != null) entries.add(MetaEntry("相机制造商", cleanText(make)))
        if (model != null) entries.add(MetaEntry("相机型号", cleanText(model), important = true))
        raw(ExifInterface.TAG_BODY_SERIAL_NUMBER)?.let { entries.add(MetaEntry("机身序列号", it, important = true)); privacy.add("机身序列号暴露：$it") }
        raw(ExifInterface.TAG_LENS_MAKE)?.let { entries.add(MetaEntry("镜头制造商", cleanText(it))) }
        raw(ExifInterface.TAG_LENS_MODEL)?.let { entries.add(MetaEntry("镜头型号", cleanText(it), important = true)) }
        raw(ExifInterface.TAG_LENS_SERIAL_NUMBER)?.let { entries.add(MetaEntry("镜头序列号", it)); privacy.add("镜头序列号暴露") }
        raw(ExifInterface.TAG_SOFTWARE)?.let { entries.add(MetaEntry("处理软件", cleanText(it))) }
        raw(ExifInterface.TAG_ARTIST)?.let { entries.add(MetaEntry("作者/摄影师", cleanText(it), important = true)); privacy.add("作者信息暴露：${cleanText(it)}") }
        raw(ExifInterface.TAG_IMAGE_DESCRIPTION)?.let { entries.add(MetaEntry("图像描述", cleanText(it))) }
        raw(ExifInterface.TAG_COPYRIGHT)?.let { entries.add(MetaEntry("版权声明", cleanText(it))) }
        raw(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { entries.add(MetaEntry("拍摄时间", fmtDate(it), important = true)) }
        raw(ExifInterface.TAG_DATETIME_DIGITIZED)?.let { entries.add(MetaEntry("数字化时间", fmtDate(it))) }
        raw(ExifInterface.TAG_DATETIME)?.let { entries.add(MetaEntry("修改时间", fmtDate(it))) }

        // ---- 曝光参数 ----
        raw(ExifInterface.TAG_F_NUMBER)?.let {
            val v = parseRational(it)
            if (v != null) entries.add(MetaEntry("光圈", "f/${fmtNum(v, 1)}", important = true))
        }
        raw(ExifInterface.TAG_EXPOSURE_TIME)?.let {
            val v = parseRational(it)
            if (v != null) entries.add(MetaEntry("快门速度", fmtShutter(v), important = true))
        }
        raw(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { entries.add(MetaEntry("ISO 感光度", it, important = true)) }
        raw(ExifInterface.TAG_FOCAL_LENGTH)?.let {
            val v = parseRational(it)
            if (v != null) {
                var text = "${fmtNum(v, 1)} mm"
                raw(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)?.let { eq ->
                    text += "（等效 ${eq} mm）"
                }
                entries.add(MetaEntry("焦距", text, important = true))
            }
        }
        raw(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)?.let {
            parseRational(it)?.let { v -> entries.add(MetaEntry("曝光补偿", "${if (v > 0) "+" else ""}${fmtNum(v, 2)} EV")) }
        }
        raw(ExifInterface.TAG_MAX_APERTURE_VALUE)?.let {
            parseRational(it)?.let { v -> entries.add(MetaEntry("最大光圈", "f/${fmtNum(v, 1)}")) }
        }
        raw(ExifInterface.TAG_FLASH_ENERGY)?.let { entries.add(MetaEntry("闪光能量", it)) }

        // ---- 枚举类参数（转译） ----
        raw(ExifInterface.TAG_EXPOSURE_PROGRAM)?.let { code ->
            val v = code.toIntOrNull()
            val names = listOf("未定义", "手动", "程序自动", "光圈优先", "快门优先", "创意程序", "动作程序", "人像模式", "风景模式")
            if (v != null && v in names.indices) entries.add(MetaEntry("曝光模式", names[v]))
        }
        raw(ExifInterface.TAG_METERING_MODE)?.let { code ->
            val v = code.toIntOrNull()
            val names = listOf("未知", "平均测光", "中央重点平均", "点测光", "多点测光", "评价测光", "局部测光")
            if (v != null && v in names.indices) entries.add(MetaEntry("测光方式", names[v]))
        }
        raw(ExifInterface.TAG_WHITE_BALANCE)?.let { code ->
            entries.add(MetaEntry("白平衡", if (code == "0") "自动" else "手动", important = true))
        }
        raw(ExifInterface.TAG_FLASH)?.let { code ->
            val v = code.toIntOrNull()
            if (v != null) entries.add(MetaEntry("闪光灯", translateFlash(v), important = true))
        }
        raw(ExifInterface.TAG_EXPOSURE_MODE)?.let { code ->
            val v = code.toIntOrNull()
            val names = listOf("自动曝光", "手动曝光", "自动包围曝光")
            if (v != null && v in names.indices) entries.add(MetaEntry("曝光模式", names[v]))
        }
        raw(ExifInterface.TAG_SCENE_CAPTURE_TYPE)?.let { code ->
            val v = code.toIntOrNull()
            val names = listOf("标准", "风景", "人像", "夜景")
            if (v != null && v in names.indices) entries.add(MetaEntry("场景类型", names[v]))
        }
        raw(ExifInterface.TAG_SCENE_TYPE)?.let { if (it == "1") entries.add(MetaEntry("场景类型", "直拍照片")) }
        raw(ExifInterface.TAG_SENSING_METHOD)?.let { code ->
            val v = code.toIntOrNull()
            val names = mapOf(1 to "未定义", 2 to "单芯片彩色传感器", 3 to "双芯片彩色传感器", 4 to "三芯片彩色传感器", 5 to "顺序色彩传感器", 7 to "三线传感器", 8 to "顺序线色传感器")
            names[v]?.let { entries.add(MetaEntry("感光方式", it)) }
        }
        raw(ExifInterface.TAG_LIGHT_SOURCE)?.let { code ->
            val v = code.toIntOrNull()
            val names = mapOf(1 to "日光", 2 to "荧光灯", 3 to "钨丝灯", 4 to "闪光灯", 9 to "晴天", 10 to "多云", 11 to "阴影", 12 to "日光荧光灯", 13 to "白荧光灯", 17 to "标准光A", 18 to "标准光B", 19 to "标准光C", 20 to "D55", 21 to "D65", 22 to "D75", 255 to "其他光源")
            names[v]?.let { entries.add(MetaEntry("光源类型", it)) }
        }
        raw(ExifInterface.TAG_COLOR_SPACE)?.let { code ->
            entries.add(MetaEntry("色彩空间", if (code == "1") "sRGB" else if (code == "65535") "未校准" else code))
        }
        raw(ExifInterface.TAG_SUBJECT_DISTANCE)?.let {
            parseRational(it)?.let { v -> if (v > 0) entries.add(MetaEntry("对焦距离", "${fmtNum(v, 2)} m")) }
        }
        raw(ExifInterface.TAG_DIGITAL_ZOOM_RATIO)?.let {
            parseRational(it)?.let { v -> if (v > 1) entries.add(MetaEntry("数码变焦", "${fmtNum(v, 1)} ×")) }
        }
        raw(ExifInterface.TAG_CONTRAST)?.let { code -> val v = code.toIntOrNull(); if (v != null) entries.add(MetaEntry("对比度", listOf("正常", "低", "高").getOrElse(v) { "$v" })) }
        raw(ExifInterface.TAG_SATURATION)?.let { code -> val v = code.toIntOrNull(); if (v != null) entries.add(MetaEntry("饱和度", listOf("正常", "低", "高").getOrElse(v) { "$v" })) }
        raw(ExifInterface.TAG_SHARPNESS)?.let { code -> val v = code.toIntOrNull(); if (v != null) entries.add(MetaEntry("锐度", listOf("正常", "柔", "硬").getOrElse(v) { "$v" })) }
        raw(ExifInterface.TAG_BRIGHTNESS_VALUE)?.let { parseRational(it)?.let { v -> entries.add(MetaEntry("亮度值", fmtNum(v, 2))) } }

        // ---- 方向与图像 ----
        raw(ExifInterface.TAG_ORIENTATION)?.let { code ->
            code.toIntOrNull()?.takeIf { it != 0 }?.let { v -> entries.add(MetaEntry("方向", translateOrientation(v), important = true)) }
        }
        raw(ExifInterface.TAG_X_RESOLUTION)?.let { entries.add(MetaEntry("水平分辨率", it)) }
        raw(ExifInterface.TAG_Y_RESOLUTION)?.let { entries.add(MetaEntry("垂直分辨率", it)) }
        raw(ExifInterface.TAG_RESOLUTION_UNIT)?.let { code ->
            entries.add(MetaEntry("分辨率单位", if (code == "2") "英寸" else if (code == "3") "厘米" else code))
        }

        // ---- GPS（隐私重点） ----
        val gps = readGps(exif)
        entries.addAll(gps.entries)
        privacy.addAll(gps.privacy)

        // ---- UserComment（可能是 AI 参数） ----
        raw(ExifInterface.TAG_USER_COMMENT)?.let {
            val t = decodeUserComment(it)
            if (t.isNotBlank()) entries.add(MetaEntry("用户注释", t.take(400), important = true))
        }

        return Triple(entries, gps.coords, privacy)
    }

    data class GpsData(val entries: List<MetaEntry>, val coords: List<MetaEntry>, val privacy: List<String>)

    private fun readGps(exif: ExifInterface): GpsData {
        val lat = exif.latLong
        val entries = mutableListOf<MetaEntry>()
        val privacy = mutableListOf<String>()
        val coords = mutableListOf<MetaEntry>()
        if (lat != null && lat.size >= 2) {
            val latD = lat[0]; val lonD = lat[1]
            val latHemi = if (latD >= 0) "北纬" else "南纬"
            val lonHemi = if (lonD >= 0) "东经" else "西经"
            val dms = fun(deg: Double): String {
                val a = kotlin.math.abs(deg); val d = a.toInt(); val mF = (a - d) * 60
                val m = mF.toInt(); val s = (mF - m) * 60
                return "$d°$m′${"%.1f".format(s)}″"
            }
            coords.add(MetaEntry("GPS 纬度", "$latHemi ${dms(latD)}（${fmtNum(latD, 6)}）", important = true))
            coords.add(MetaEntry("GPS 经度", "$lonHemi ${dms(lonD)}（${fmtNum(lonD, 6)}）", important = true))
            exif.getAltitude(0.0).takeIf { it != 0.0 }?.let { coords.add(MetaEntry("GPS 海拔", "${fmtNum(it, 1)} m")) }
            exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)?.let { d ->
                parseRational(d)?.let { coords.add(MetaEntry("拍摄朝向", "${fmtNum(it, 1)}°（罗盘方位）")) }
            }
            entries.add(MetaEntry("定位状态", "已记录 GPS 位置 ⚠", important = true))
            privacy.add("照片带有 GPS 定位（${fmtNum(latD, 4)}, ${fmtNum(lonD, 4)}），公开发布前建议抹除")
        } else {
            entries.add(MetaEntry("定位状态", "未记录 GPS"))
        }
        return GpsData(entries, coords, privacy)
    }

    fun translateFlash(v: Int): String {
        val fired = (v and 0x1) != 0
        val base = if (fired) "已闪光" else "未闪光"
        val mode = when {
            (v and 0x8) != 0 -> "，强制模式"
            (v and 0x10) != 0 -> "，禁止模式"
            else -> ""
        }
        val returned = (v shr 6) and 0x3
        val ret = when (returned) {
            2 -> "，回闪确认"
            3 -> "，回闪检测失败"
            else -> ""
        }
        val redEye = if ((v and 0x40) != 0) "，防红眼" else ""
        return base + mode + ret + redEye
    }

    fun translateOrientation(v: Int): String = when (v) {
        1 -> "正常（横拍）"
        2 -> "水平镜像"
        3 -> "旋转 180°"
        4 -> "垂直镜像"
        5 -> "转置（镜像+旋转）"
        6 -> "顺时针 90°（竖拍）"
        7 -> "反转置"
        8 -> "逆时针 90°（竖拍）"
        else -> "未知（$v）"
    }

    fun fmtShutter(sec: Double): String = when {
        sec >= 1.0 -> "${fmtNum(sec, 1)}″"
        else -> "1/${kotlin.math.round(1.0 / sec).toInt()}″"
    }

    fun fmtDate(raw: String): String = try {
        df.format(SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)!!)
    } catch (e: Exception) {
        raw
    }

    fun parseRational(s: String): Double? {
        if (s.contains(",")) return null // 多值取不了
        val parts = s.split("/")
        return try {
            when (parts.size) {
                1 -> parts[0].toDouble()
                2 -> {
                    val den = parts[1].toDouble()
                    if (den == 0.0) null else parts[0].toDouble() / den
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun fmtNum(v: Double, digits: Int): String =
        String.format(Locale.US, "%.${digits}f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

    private fun cleanText(s: String) = s.replace("\u0000", "").trim()

    private fun decodeUserComment(raw: String): String {
        // ExifInterface 已解出大部分；残余 UNO/ASCII 前缀清理
        return raw.replace("\u0000", "").removePrefix("ASCII\u0000\u0000\u0000").removePrefix("UNICODE\u0000").trim()
    }
}
