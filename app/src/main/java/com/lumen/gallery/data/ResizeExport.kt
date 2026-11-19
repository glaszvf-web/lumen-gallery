package com.lumen.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore

/**
 * v35 · 批量缩放导出：等比缩到最长边目标值，副本入 Pictures/LUMEN 缩放
 */
object ResizeExport {

    enum class Size(val label: String, val maxSide: Int) {
        S1080("1080P（社交分享）", 1080),
        S2048("2048（高清档）", 2048),
        S4096("4096（近原档）", 4096),
    }

    data class Result(val ok: Int, val fail: Int, val message: String)

    fun resize(context: Context, items: List<MediaItem>, size: Size): Result {
        var ok = 0; var fail = 0
        items.filter { !it.isVideo }.forEach { item ->
            try {
                val src = BitmapFactory.decodeFile(item.path) ?: run { fail++; return@forEach }
                val scale = minOf(1f, size.maxSide.toFloat() / maxOf(src.width, src.height))
                val bmp = if (scale < 1f) {
                    val w = (src.width * scale).toInt().coerceAtLeast(1)
                    val h = (src.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(src, w, h, true)
                    if (scaled != src) src.recycle()
                    scaled
                } else src
                val name = item.displayName.substringBeforeLast('.') + "_${size.maxSide}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 缩放")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) { fail++; bmp.recycle(); return@forEach }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 90, os)) throw IllegalStateException("compress 失败")
                } ?: throw IllegalStateException("输出流失败")
                bmp.recycle()
                ok++
            } catch (e: Exception) {
                fail++
            }
        }
        return Result(ok, fail, "缩放完成：$ok 成功 / $fail 失败 → Pictures/LUMEN 缩放（最长边 ${size.maxSide}）")
    }
}
