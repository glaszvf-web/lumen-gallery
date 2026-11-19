package com.lumen.gallery.data

import java.io.InputStream

enum class Fmt(val zhName: String, val ext: String) {
    JPEG("JPEG 图像", "jpg"),
    PNG("PNG 图像", "png"),
    GIF("GIF 动图", "gif"),
    BMP("BMP 位图", "bmp"),
    WEBP("WebP 图像", "webp"),
    HEIF("HEIF/HEIC 容器", "heic"),
    TIFF("TIFF 图像", "tif"),
    DNG("DNG 数字底片", "dng"),
    CR2("佳能 CR2 底片", "cr2"),
    CR3("佳能 CR3 底片", "cr3"),
    NEF("尼康 NEF 底片", "nef"),
    ARW("索尼 ARW 底片", "arw"),
    ORF("奥林巴斯 ORF 底片", "orf"),
    RAF("富士 RAF 底片", "raf"),
    RW2("松下 RW2 底片", "rw2"),
    PEF("宾得 PEF 底片", "pef"),
    SRW("三星 SRW 底片", "srw"),
    X3F("适马 X3F 底片", "x3f"),
    AVIF("AVIF 图像", "avif"),
    RAW_UNKNOWN("未知图像格式", "bin"),
}

object Format {

    /** 通过文件头魔数识别真实格式，不信任扩展名 */
    fun sniff(head: ByteArray, size: Long): Fmt {
        fun u32LE(off: Int): Long =
            (head[off].toLong() and 0xFF) or ((head[off + 1].toLong() and 0xFF) shl 8) or
                ((head[off + 2].toLong() and 0xFF) shl 16) or ((head[off + 3].toLong() and 0xFF) shl 24)
        return when {
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() -> Fmt.JPEG
            head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte() -> Fmt.PNG
            head.size >= 6 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() && head[3] == '8'.code.toByte() -> Fmt.GIF
            head.size >= 2 && head[0] == 'B'.code.toByte() && head[1] == 'M'.code.toByte() -> Fmt.BMP
            head.size >= 12 && head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() && head[3] == 'F'.code.toByte() &&
                head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() && head[10] == 'B'.code.toByte() && head[11] == 'P'.code.toByte() -> Fmt.WEBP
            head.size >= 12 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> {
                val brand = String(head, 8, 4, Charsets.US_ASCII).trim()
                when {
                    brand.startsWith("crx") -> Fmt.CR3
                    brand.startsWith("he") || brand.startsWith("mif") || brand == "heim" || brand == "mif1" -> Fmt.HEIF
                    brand.startsWith("avi") -> Fmt.AVIF
                    else -> Fmt.HEIF
                }
            }
            head.size >= 4 && ((head[0] == 'I'.code.toByte() && head[1] == 'I'.code.toByte()) || (head[0] == 'M'.code.toByte() && head[1] == 'M'.code.toByte())) -> {
                // TIFF 家族：需要进一步区分 DNG / CR2 / NEF / ARW 等（由 TIFFParser 通过 DNGVersion / Make 判断）
                Fmt.TIFF
            }
            head.size >= 16 && String(head, 0, 8, Charsets.US_ASCII) == "FUJIFILM" -> Fmt.RAF
            head.size >= 4 && String(head, 0, 3, Charsets.US_ASCII) == "IIU" -> Fmt.RW2
            head.size >= 8 && String(head, 0, 4, Charsets.US_ASCII) == "FOVb" -> Fmt.X3F
            else -> Fmt.RAW_UNKNOWN
        }
    }

    fun sniff(stream: InputStream, size: Long): Pair<Fmt, ByteArray> {
        val head = ByteArray(64)
        val n = stream.read(head)
        stream.close()
        val h = if (n < 0) ByteArray(0) else head.copyOf(n)
        return sniff(h, size) to h
    }
}
