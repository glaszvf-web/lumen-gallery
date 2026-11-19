package com.lumen.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.provider.MediaStore

/**
 * v36 · 水印添加器：右下角半透明文字水印（光析 LUMEN 风格描边），副本入 Pictures/LUMEN 水印
 */
object WatermarkExport {

    data class Result(val ok: Int, val fail: Int, val message: String)

    fun apply(context: Context, items: List<MediaItem>, text: String): Result {
        if (text.isBlank()) return Result(0, 0, "水印文字为空")
        var ok = 0; var fail = 0
        items.filter { !it.isVideo }.forEach { item ->
            try {
                val src = BitmapFactory.decodeFile(item.path) ?: run { fail++; return@forEach }
                val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
                src.recycle()
                val canvas = Canvas(bmp)
                val density = bmp.width.coerceAtLeast(bmp.height) / 24f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = density.coerceIn(28f, 96f)
                    color = Color.WHITE
                    alpha = 190
                    setShadowLayer(textSize / 10f, 0f, 0f, Color.argb(160, 0, 0, 0))
                }
                val pad = paint.textSize * 0.6f
                val x = bmp.width - paint.measureText(text) - pad
                val y = bmp.height - pad
                canvas.drawText(text, x, y, paint)
                val name = item.displayName.substringBeforeLast('.') + "_水印.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 水印")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) { fail++; bmp.recycle(); return@forEach }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, os)) throw IllegalStateException("compress 失败")
                } ?: throw IllegalStateException("输出流失败")
                bmp.recycle()
                ok++
            } catch (e: Exception) {
                fail++
            }
        }
        return Result(ok, fail, "水印完成：$ok 成功 / $fail 失败 → Pictures/LUMEN 水印")
    }
}
