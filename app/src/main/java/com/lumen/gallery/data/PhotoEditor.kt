package com.lumen.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.provider.MediaStore
import kotlin.math.max

/**
 * v19 · 照片编辑器引擎：旋转/镜像/亮度/对比度/饱和度/色温 + 保存副本
 * 纯像素运算（无 GPU 依赖），编辑结果写入 Pictures/LUMEN 编辑
 */
object PhotoEditor {

    data class EditState(
        val rotation: Int = 0,        // 0/90/180/270
        val mirrorH: Boolean = false,
        val brightness: Float = 0f,   // -0.5..0.5
        val contrast: Float = 1f,     // 0.5..1.5
        val saturation: Float = 1f,   // 0..2
        val temperature: Float = 0f,  // -0.5(冷)..0.5(暖)
    ) {
        val isEdited: Boolean get() = rotation != 0 || mirrorH || brightness != 0f ||
            contrast != 1f || saturation != 1f || temperature != 0f
    }

    fun decode(path: String, maxSide: Int = 2048): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 应用几何变换 + 像素滤镜，返回新 Bitmap */
    fun apply(src: Bitmap, st: EditState): Bitmap {
        // 1. 几何
        var bmp = src
        if (st.rotation != 0 || st.mirrorH) {
            val m = Matrix()
            m.postRotate(st.rotation.toFloat())
            if (st.mirrorH) m.postScale(-1f, 1f)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        if (!st.isEdited) return bmp
        // 2. 像素滤镜
        val w = bmp.width; val h = bmp.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val bOff = st.brightness * 255f
        val cFactor = st.contrast
        val tempR = if (st.temperature > 0) st.temperature * 60f else 0f
        val tempB = if (st.temperature < 0) -st.temperature * 60f else 0f
        val tempG = kotlin.math.abs(st.temperature) * 12f
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = Color.red(p).toFloat(); var g = Color.green(p).toFloat(); var b = Color.blue(p).toFloat()
            // 亮度
            r += bOff; g += bOff; b += bOff
            // 对比度（围绕 128）
            r = (r - 128f) * cFactor + 128f
            g = (g - 128f) * cFactor + 128f
            b = (b - 128f) * cFactor + 128f
            // 饱和度
            val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = lum + (r - lum) * st.saturation
            g = lum + (g - lum) * st.saturation
            b = lum + (b - lum) * st.saturation
            // 色温
            r += tempR - tempG * 0.3f; b += tempB - tempG * 0.3f; g += tempG * 0.15f
            pixels[i] = Color.rgb(
                r.toInt().coerceIn(0, 255),
                g.toInt().coerceIn(0, 255),
                b.toInt().coerceIn(0, 255),
            )
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 保存副本到 Pictures/LUMEN 编辑（MediaStore 免权限） */
    fun save(context: Context, edited: Bitmap, sourceName: String): Pair<Boolean, String> {
        return try {
            val base = sourceName.substringBeforeLast('.')
            val ext = sourceName.substringAfterLast('.', "jpg")
            val name = "${base}_edited.$ext"
            val mime = if (ext == "png") "image/png" else "image/jpeg"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 编辑")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Pair(false, "副本创建失败")
            context.contentResolver.openOutputStream(uri)?.use { os ->
                if (ext == "png") edited.compress(Bitmap.CompressFormat.PNG, 100, os)
                else edited.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }
            Pair(true, "已保存编辑副本：$name")
        } catch (e: Exception) {
            Pair(false, "保存失败：${e.message}")
        }
    }
}
