package com.lumen.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import java.io.File

/**
 * V5 · 元数据分享卡：把透视结果渲染成一张科技感 PNG
 */
object ShareCard {

    // v54 · 模板：detailed=完整版（含关键条目），minimal=极简版（只留曝光+AI）
    fun render(context: Context, item: MediaItem, r: AnalysisResult, detailed: Boolean = true): File {
        val W = 1080
        val H = 1500
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 深空背景 + 渐变
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF070B14.toInt(), 0xFF101B33.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        val cyan = 0xFF22D3EE.toInt()
        val violet = 0xFFA78BFA.toInt()
        val magenta = 0xFFF472B6.toInt()
        val textHi = 0xFFE8F1FF.toInt()
        val textMid = 0xFF9DB0D0.toInt()
        val textLow = 0xFF5C6B8A.toInt()

        // 顶部渐变条
        val bar = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, cyan, violet, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), 10f, bar)

        fun paint(size: Float, color: Int, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                isFakeBoldText = bold
            }

        // 品牌
        c.drawText("◉ 光析 LUMEN", 60f, 120f, paint(52f, cyan, bold = true))
        c.drawText("PHOTO METADATA SCOPE", 60f, 165f, paint(24f, textLow))

        // 缩略图（有则画，限高 500 居中）
        var y = 240f
        val thumb = ColorHash.decodeSmall(item.path, 720)
        if (thumb != null) {
            var tw = 960f
            var th = tw * thumb.height / thumb.width
            if (th > 500f) {
                th = 500f
                tw = th * thumb.width / thumb.height
            }
            val x0 = (W - tw) / 2f
            val scaled = Bitmap.createScaledBitmap(thumb, tw.toInt(), th.toInt(), true)
            val round = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111A2E.toInt() }
            c.drawRoundRect(RectF(x0, y, x0 + tw, y + th), 28f, 28f, round)
            val save = c.save()
            c.clipRoundRect(RectF(x0, y, x0 + tw, y + th), 28f, 28f)
            c.drawBitmap(scaled, x0, y, null)
            c.restoreToCount(save)
            y += th + 50f
            scaled.recycle()
        } else {
            c.drawText(item.displayName, 60f, y + 40f, paint(44f, textHi, bold = true))
            y += 110f
        }

        // 文件名 + 格式
        c.drawText(item.displayName, 60f, y, paint(40f, textHi, bold = true))
        y += 58f
        c.drawText("${r.formatName} · ${FormatUi.fmtSize(item.size)}", 60f, y, paint(30f, textMid))
        y += 90f

        // 曝光大字
        val all = r.sections.flatMap { it.entries }
        fun find(label: String) = all.firstOrNull { it.label == label }?.value
        val cells = listOf(
            "光圈" to (find("光圈") ?: "—"),
            "快门" to (find("快门速度") ?: "—"),
            "ISO" to (find("ISO 感光度") ?: "—"),
            "焦距" to (find("焦距") ?: "—"),
        ).filter { it.second != "—" }
        if (cells.isNotEmpty()) {
            val cw = 960f / cells.size
            cells.forEachIndexed { i, (label, value) ->
                val x0 = 60f + i * cw
                c.drawText(label, x0 + 20f, y, paint(26f, textLow))
                c.drawText(value.take(12), x0 + 20f, y + 64f, paint(if (value.length > 8) 40f else 56f, textHi, bold = true))
            }
            y += 130f
        }

        // AI 档案提示
        r.aiParams?.let { ai ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22F472B6 }
            c.drawRoundRect(RectF(60f, y, W - 60f, y + 100f), 20f, 20f, p)
            c.drawText("AI 绘图档案 · ${ai.engine}", 90f, y + 42f, paint(30f, magenta, bold = true))
            c.drawText(ai.positive?.take(28)?.plus("…") ?: "已提取生成参数", 90f, y + 82f, paint(26f, textMid))
            y += 140f
        }

        // 关键条目（最多 8 行；v54 极简模板跳过）
        val highlights = if (detailed) all.filter { it.important }.take(8) else emptyList()
        highlights.forEach { e ->
            c.drawText(e.label, 60f, y, paint(28f, textLow))
            c.drawText(e.value.take(30), 320f, y, paint(28f, textMid))
            y += 46f
        }

        // 底部
        c.drawText("光析 LUMEN · 把底裤扒干净", 60f, H - 70f, paint(28f, textLow))

        // 保存
        val out = File(context.cacheDir, "lumen_share_${System.currentTimeMillis()}.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bmp.recycle()
        return out
    }

    private fun Canvas.clipRoundRect(r: RectF, rx: Float, ry: Float) {
        val path = android.graphics.Path().apply { addRoundRect(r, rx, ry, android.graphics.Path.Direction.CW) }
        clipPath(path)
    }
}
