package com.lumen.gallery

import org.junit.Test
import java.io.File

class TiffDeepTest {

    @Test
    fun parseHuaweiDng() {
        val f = File("testimgs/huawei_mate80.dng")
        val data = f.readBytes()
        val warnings = mutableListOf<String>()
        val ifds = com.lumen.gallery.data.TiffDeep.parse(data, warnings)
        println("warnings=$warnings")
        println("IFD count=${ifds.size}")
        ifds.forEach { ifd ->
            println("== ${ifd.title} (${ifd.subtitle}) ${ifd.entries.size} 项")
            ifd.entries.forEach { e ->
                println("   [${if (e.important) "*" else " "}] ${e.label} = ${e.value.take(80)}")
            }
        }
        assert(ifds.size >= 4) { "应至少有 IFD0/Exif/GPS/SubIFD 四组，实际 ${ifds.size}" }
        val all = ifds.flatMap { it.entries }
        assert(all.any { it.label == "DNG 版本" && it.value.startsWith("1.4") }) { "DNG 版本应转译" }
        assert(all.any { it.label == "CFA 滤色阵列" && it.value.contains("拜耳") }) { "CFA 应转译为拜耳" }
        assert(all.any { it.label == "校准光源 1" && it.value.contains("D65") }) { "校准光源 33 应转译 D65" }
        assert(all.any { it.label == "厂商注释区" && (it.value.contains("逆向命中") || it.value.contains("头 64 字节") || it.value.contains("头 48 字节")) }) { "MakerNote 深挖应输出 hexdump/启发式结果" }
        val gps = ifds.first { it.title.startsWith("GPS") }
        assert(gps.entries.any { it.label == "GPS 纬度" && it.value.contains("24°22′") }) { "GPS 度分秒" }
    }

    /** v27 · MakerNote 全量逆向：真实 IFD 结构的华为 MakerNote 应"逆向命中"并全解码 */
    @Test
    fun parseHuaweiMakerNote() {
        val f = File("testimgs/huawei_maker.dng")
        if (!f.exists()) return
        val warnings = mutableListOf<String>()
        val ifds = com.lumen.gallery.data.TiffDeep.parse(data(f), warnings)
        val all = ifds.flatMap { it.entries }
        val maker = all.firstOrNull { it.label == "厂商注释区" }
        println("MakerNote = ${maker?.value?.take(300)}")
        assert(maker != null) { "应解析出厂商注释区" }
        assert(maker!!.value.contains("逆向命中")) { "标准 IFD 结构 MakerNote 应逆向命中，实际: ${maker.value.take(120)}" }
        assert(maker.value.contains("0x0003")) { "应含 MakerNote 内条目 tag" }
        // RAW 显影预览提取
        val previews = com.lumen.gallery.data.TiffDeep.extractPreviews(data(f))
        println("previews = $previews")
        assert(previews.isNotEmpty()) { "应提取出内嵌 JPEG 预览" }
        assert(previews[0].source == "IFD0") { "预览应来自 IFD0" }
    }

    private fun data(f: File) = f.readBytes()
}
