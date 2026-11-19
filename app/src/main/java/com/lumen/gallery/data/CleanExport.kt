package com.lumen.gallery.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

/**
 * V10 · 一键脱敏导出：复制副本到 Pictures/LUMEN脱敏 并抹除 GPS / 序列号 / 所有者 / 用户注释
 */
object CleanExport {

    data class Result(val ok: Boolean, val message: String)

    fun export(context: Context, item: MediaItem): Result {
        if (item.isVideo) return Result(false, "视频脱敏暂不支持，请用图片条目")
        return try {
            val cr = context.contentResolver
            val name = item.displayName.substringBeforeLast('.') + "_脱敏." + item.displayName.substringAfterLast('.', "jpg")
            // 1) 经 MediaStore 建副本（应用自建无需写权限）
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime.ifBlank { "image/jpeg" })
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 脱敏")
            }
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Result(false, "副本创建失败（MediaStore 拒绝）")
            // 2) 拷贝原始字节
            val pfd = cr.openFileDescriptor(uri, "rw") ?: return Result(false, "副本打开失败")
            pfd.use { descriptor ->
                java.io.FileOutputStream(descriptor.fileDescriptor).use { out ->
                    java.io.File(item.path).inputStream().use { it.copyTo(out) }
                }
            }
            // 3) 抹除敏感字段：JPEG/PNG/WebP 走 ExifInterface；TIFF 家族走字节级 GPS 指针清零
            val realPath = queryRealPath(cr, uri)
            val ext = item.displayName.substringAfterLast('.', "").lowercase()
            val exifWritable = ext in setOf("jpg", "jpeg", "png", "webp")
            var stripped = 0
            if (exifWritable && realPath != null) {
                val exifWriter = androidx.exifinterface.media.ExifInterface(realPath)
                val removed = mutableListOf<String>()
                listOf(
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_IMG_DIRECTION,
                    androidx.exifinterface.media.ExifInterface.TAG_BODY_SERIAL_NUMBER,
                    androidx.exifinterface.media.ExifInterface.TAG_CAMERA_OWNER_NAME,
                    androidx.exifinterface.media.ExifInterface.TAG_LENS_SERIAL_NUMBER,
                    androidx.exifinterface.media.ExifInterface.TAG_ARTIST,
                ).forEach { tag ->
                    if (exifWriter.getAttribute(tag) != null) {
                        removed.add(tag)
                        exifWriter.setAttribute(tag, null)
                    }
                }
                exifWriter.saveAttributes()
                stripped = removed.size
            } else if (ext == "cr3") {
                // v27 · CR3 佳能容器：字节级定位 CMT 盒 → 盒内 TIFF 的 GPS 指针（CMT3）清零
                val buf = java.io.File(item.path).let { f ->
                    val n = minOf(f.length(), 256L * 1024 * 1024).toInt()
                    f.inputStream().use { ins ->
                        val b = ByteArray(n); var off = 0
                        while (off < n) { val r = ins.read(b, off, n - off); if (r < 0) break; off += r }
                        b.copyOf(off)
                    }
                }
                stripped = Cr3Strip.stripGps(buf)
                if (realPath != null) java.io.File(realPath).outputStream().use { it.write(buf) }
            } else if (ext in setOf("dng", "tif", "tiff", "cr2", "nef", "arw", "orf", "rw2", "pef", "srw")) {
                // TIFF 家族：字节级把 GPS IFD 指针（0x8825）清零，保留其他元数据
                val buf = java.io.File(item.path).let { f ->
                    val n = minOf(f.length(), 64L * 1024 * 1024).toInt()
                    f.inputStream().use { ins ->
                        val b = ByteArray(n); var off = 0
                        while (off < n) { val r = ins.read(b, off, n - off); if (r < 0) break; off += r }
                        b.copyOf(off)
                    }
                }
                stripped = stripTiffGpsPointers(buf)
                if (realPath != null) java.io.File(realPath).outputStream().use { it.write(buf) }
            }
            Result(
                true,
                "已导出脱敏副本：$name（抹除 $stripped 处 GPS 指针/敏感字段" +
                    (if (!exifWritable) "，RAW 为字节级抹除" + if (ext == "cr3") "（CR3 CMT 盒）" else "" else "") + "）",
            )
        } catch (e: Exception) {
            Result(false, "脱敏导出失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun queryRealPath(cr: android.content.ContentResolver, uri: android.net.Uri): String? =
        runCatching {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    /** 字节级抹除 TIFF 家族的 GPS IFD 指针（0x8825），返回抹除数 */
    private fun stripTiffGpsPointers(data: ByteArray): Int {
        var stripped = 0
        return try {
            if (data.size < 8) return 0
            val little = data[0].toInt() == 'I'.code && data[1].toInt() == 'I'.code
            fun u16(o: Int) = if (little) u16le(data, o) else u16be(data, o)
            fun u32(o: Int) = if (little) u32le(data, o) else u32be(data, o)
            var off = u32(4).toInt()
            var guard = 0
            val visited = mutableSetOf<Int>()
            while (off > 0 && off + 2 <= data.size && guard < 8 && off !in visited) {
                guard++; visited.add(off)
                val cnt = u16(off)
                if (cnt > 1024) break
                for (i in 0 until cnt) {
                    val e = off + 2 + i * 12
                    if (e + 12 > data.size) break
                    if (u16(e) == 0x8825) {
                        data[e + 8] = 0; data[e + 9] = 0; data[e + 10] = 0; data[e + 11] = 0
                        stripped++
                    }
                }
                off = u32(off + 2 + cnt * 12).toInt()
            }
            stripped
        } catch (e: Exception) { stripped }
    }
}
