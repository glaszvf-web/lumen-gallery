package com.lumen.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore

/**
 * v34 · 格式转换器：JPEG / PNG / WebP 三向互转，副本入 Pictures/LUMEN 转换
 */
object ConvertExport {

    enum class Fmt(val ext: String, val mime: String, val compress: Bitmap.CompressFormat, val quality: Int) {
        JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92),
        PNG("png", "image/png", Bitmap.CompressFormat.PNG, 100),
        WEBP("webp", "image/webp", Bitmap.CompressFormat.WEBP, 88),
    }

    data class Result(val ok: Int, val fail: Int, val message: String)

    fun convert(context: Context, items: List<MediaItem>, fmt: Fmt): Result {
        var ok = 0; var fail = 0
        items.filter { !it.isVideo }.forEach { item ->
            try {
                val bmp = decodeFit(item.path, 4096) ?: run { fail++; return@forEach }
                val name = item.displayName.substringBeforeLast('.') + "_converted." + fmt.ext
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, fmt.mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 转换")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) { fail++; bmp.recycle(); return@forEach }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    if (!bmp.compress(fmt.compress, fmt.quality, os)) throw IllegalStateException("compress 返回 false")
                } ?: throw IllegalStateException("输出流打开失败")
                bmp.recycle()
                ok++
            } catch (e: Exception) {
                fail++
            }
        }
        return Result(ok, fail, "格式转换完成：$ok 成功 / $fail 失败 → Pictures/LUMEN 转换")
    }

    private fun decodeFit(path: String, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) null else {
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) { null }
}
