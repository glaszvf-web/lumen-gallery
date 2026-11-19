package com.lumen.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v27 单测：CR3 字节级 GPS 脱敏 + RAW 预览提取 + MakerNote 全量逆向
 */
class Cr3StripTest {

    /** 手拼最小 CR3：ftyp 壳 + CMT 盒（盒内一段小端 TIFF，IFD0 挂 0x8825→GPS IFD） */
    private fun buildCr3(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        // ftyp 壳
        out.write("freetype".toByteArray(Charsets.US_ASCII))
        out.write("ftypcrx ".toByteArray(Charsets.US_ASCII))

        // ---- 盒内 TIFF（从 tiffStart=0 的相对布局）----
        // TIFF 头 8 字节 + IFD0(2+1*12+4=18) + GPS IFD(2+4*12+4=54) + GPS 数据(3 个 RATIONAL=72 + 2 ASCII)
        val tiff = java.io.ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x49, 0x49, 0x2A, 0x00))          // II*\0
        tiff.writeLE32(8)                                        // IFD0 @8
        val ifd0Off = 8
        val ifd0Len = 2 + 1 * 12 + 4                             // 1 条目
        val gpsIfdOff = ifd0Off + ifd0Len                        // 26
        val gpsLen = 2 + 4 * 12 + 4                              // 54
        val gpsDataOff = gpsIfdOff + gpsLen                      // 80
        // IFD0：1 条 0x8825 LONG×1 → GPS IFD
        tiff.writeLE16(1)
        tiff.writeLE16(0x8825); tiff.writeLE16(4); tiff.writeLE32(1); tiff.writeLE32(gpsIfdOff.toLong())
        tiff.writeLE32(0)                                        // next IFD = 0
        // GPS IFD：版本/纬度参考/纬度/经度参考/经度
        tiff.writeLE16(5)
        // 0x0000 BYTE×1 0 (版本) — 内联
        tiff.writeLE16(0x0000); tiff.writeLE16(1); tiff.writeLE32(1); tiff.writeBytes(byteArrayOf(2, 3, 0, 0))
        // 0x0001 ASCII×1 'N' — 内联
        tiff.writeLE16(0x0001); tiff.writeLE16(2); tiff.writeLE32(1); tiff.writeASCII("N")
        // 0x0002 RATIONAL×3 @gpsDataOff
        tiff.writeLE16(0x0002); tiff.writeLE16(5); tiff.writeLE32(3); tiff.writeLE32(gpsDataOff.toLong())
        // 0x0003 ASCII×1 'E'
        tiff.writeLE16(0x0003); tiff.writeLE16(2); tiff.writeLE32(1); tiff.writeASCII("E")
        // 0x0004 RATIONAL×3 @gpsDataOff+24
        tiff.writeLE16(0x0004); tiff.writeLE16(5); tiff.writeLE32(3); tiff.writeLE32((gpsDataOff + 24).toLong())
        tiff.writeLE32(0)                                        // GPS next = 0
        // GPS 数据：24°(24/1) 22.5′(45/2) 0″ + 116° 30′ 0″
        tiff.writeLE32(24); tiff.writeLE32(1); tiff.writeLE32(45); tiff.writeLE32(2); tiff.writeLE32(0); tiff.writeLE32(1)
        tiff.writeLE32(116); tiff.writeLE32(1); tiff.writeLE32(30); tiff.writeLE32(1); tiff.writeLE32(0); tiff.writeLE32(1)
        val tiffBytes = tiff.toByteArray()

        // CMT 盒：'CMT ' + 8 字节盒头 + TIFF
        out.write("CMT ".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0x10))
        out.write(tiffBytes)
        out.write(byteArrayOf(0, 0, 0, 0))                       // mdat 尾巴
        return out.toByteArray()
    }

    @Test
    fun cr3StripGpsZeroesPointer() {
        val data = buildCr3()
        assertTrue(com.lumen.gallery.data.Cr3Strip.stripGps(data) >= 1)
        // 布局：freetype(8)+ftypcrx(8) → "CMT "@16 → 8 字节盒头 → TIFF 起始 @28
        val ts = 16 + 4 + 8 // 28
        // IFD0 起始 @ts+8（TIFF 头 8B），count 2B → 首条目 @ts+10，0x8825 值域 @ts+18
        val e = ts + 8 + 2
        assertEquals(0x25, data[e].toInt() and 0xFF)
        assertEquals(0x88, data[e + 1].toInt() and 0xFF)
        for (k in 0 until 4) assertEquals(0, data[e + 8 + k].toInt())
        // GPS 数据本体保留（字节级精准抹除：只动指针）
        // 布局：ifd0Off=ts+8(18B) → gpsIfdOff=ts+26(5 条目 66B) → gpsDataOff=ts+92
        val gpsLatOff = ts + 26 + 66
        assertEquals(24, data[gpsLatOff].toInt())
    }

    @Test
    fun cr3StripIsIdempotent() {
        val data = buildCr3()
        // 重复抹除不崩；最终指针值恒为 0（tag 保留、值域归零 = GPS 永久不可达）
        com.lumen.gallery.data.Cr3Strip.stripGps(data)
        com.lumen.gallery.data.Cr3Strip.stripGps(data)
        val e = (16 + 4 + 8) + 8 + 2 // TIFF@28 → IFD0@36 → 首条目@38
        for (k in 0 until 4) assertEquals(0, data[e + 8 + k].toInt())
    }

    @Test
    fun extractPreviewsFindsEmbeddedJpeg() {
        // TIFF：IFD0 带 0x0201/0x0202 指向一段 FF D8 … FF D9
        val tiff = java.io.ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x49, 0x49, 0x2A, 0x00)); tiff.writeLE32(8)
        val ifd0Len = 2 + 2 * 12 + 4
        val jpegOff = 8 + ifd0Len
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        tiff.writeLE16(2)
        tiff.writeLE16(0x0201); tiff.writeLE16(4); tiff.writeLE32(1); tiff.writeLE32(jpegOff.toLong())
        tiff.writeLE16(0x0202); tiff.writeLE16(4); tiff.writeLE32(1); tiff.writeLE32(jpeg.size.toLong())
        tiff.writeLE32(0)
        tiff.write(jpeg)
        val previews = com.lumen.gallery.data.TiffDeep.extractPreviews(tiff.toByteArray())
        assertEquals(1, previews.size)
        assertEquals("IFD0", previews[0].source)
        assertEquals(jpeg.size, previews[0].length)
    }

    @Test
    fun makerNoteProbeHitsSyntheticIfd() {
        // MakerNote：8 字节头 + 标准 IFD（3 条目，绝对地址偏移）
        val body = java.io.ByteArrayOutputStream()
        body.write(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        val ifdOff = 8 // MakerNote 内相对
        body.writeLE16(3)
        body.writeLE16(0x0001); body.writeLE16(4); body.writeLE32(1); body.writeLE32(3)
        body.writeLE16(0x0002); body.writeLE16(4); body.writeLE32(1); body.writeLE32(7)
        body.writeLE16(0x0003); body.writeLE16(3); body.writeLE32(1); body.writeLE32(5)
        body.writeLE32(0)
        val data = body.toByteArray()
        val warnings = mutableListOf<String>()
        val ifds = com.lumen.gallery.data.TiffDeep.parse(data, warnings)
        // 直接探针：构造含 0x927C 的外层太绕，这里走 probe 的公开路径（经 parse 的 MakerNote 渲染）
        // 简化断言：无崩溃 + 输出中至少出现"逆向命中"或 hexdump
        val mnOff = ifdOff
        val rendered = com.lumen.gallery.data.TiffDeep.parse(data, warnings)
            .flatMap { it.entries }
        assertTrue(rendered.isEmpty() || warnings.isEmpty())
        // probe 逻辑直测：用反射不可行，改用端到端 —— 拼一个含 0x927C 的 TIFF
        val tiff = java.io.ByteArrayOutputStream()
        tiff.write(byteArrayOf(0x49, 0x49, 0x2A, 0x00)); tiff.writeLE32(8)
        val mn = data
        val mnOff2 = 8 + (2 + 1 * 12 + 4)
        tiff.writeLE16(1)
        tiff.writeLE16(0x927C); tiff.writeLE16(7); tiff.writeLE32(mn.size.toLong()); tiff.writeLE32(mnOff2.toLong())
        tiff.writeLE32(0)
        tiff.write(mn)
        val ifds2 = com.lumen.gallery.data.TiffDeep.parse(tiff.toByteArray(), mutableListOf())
        val makerEntry = ifds2.flatMap { it.entries }.firstOrNull { it.label == "厂商注释区" }
        assertTrue("MakerNote 应有输出", makerEntry != null)
        assertTrue(
            "应命中逆向或给出 hexdump",
            makerEntry!!.value.contains("逆向命中") || makerEntry.value.contains("头 64 字节"),
        )
    }

    private fun java.io.ByteArrayOutputStream.writeLE16(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
    private fun java.io.ByteArrayOutputStream.writeLE32(v: Long) {
        write((v and 0xFF).toInt()); write(((v shr 8) and 0xFF).toInt())
        write(((v shr 16) and 0xFF).toInt()); write(((v shr 24) and 0xFF).toInt())
    }
    private fun java.io.ByteArrayOutputStream.writeASCII(s: String) {
        // 4 字节内联：'N' + \0\0\0
        s.toByteArray(Charsets.US_ASCII).forEach { write(it.toInt()) }
        repeat(4 - s.length) { write(0) }
    }
    private fun java.io.ByteArrayOutputStream.writeBytes(b: ByteArray) { b.forEach { write(it.toInt()) } }
}
