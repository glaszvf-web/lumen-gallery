package com.lumen.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument

/**
 * v32 · 多图合成 PDF：每图一页（A4 比例 1240×1754，图按 fit 居中白底）
 * 输出：应用外部私有 Documents/lumen_pdf/<时间戳>.pdf
 */
object PdfMergeExport {

    private const val PAGE_W = 1240
    private const val PAGE_H = 1754

    data class Result(val ok: Boolean, val path: String?, val pages: Int, val message: String)

    fun export(context: Context, items: List<MediaItem>): Result {
        val images = items.filter { !it.isVideo }
        if (images.isEmpty()) return Result(false, null, 0, "没有可合成的图片")
        return try {
            val doc = PdfDocument()
            images.forEachIndexed { idx, item ->
                val bmp = decodeFit(item.path, 2048) ?: return@forEachIndexed
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, idx + 1).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val scale = minOf(PAGE_W.toFloat() / bmp.width, PAGE_H.toFloat() / bmp.height)
                val w = (bmp.width * scale).toInt()
                val h = (bmp.height * scale).toInt()
                val left = (PAGE_W - w) / 2
                val top = (PAGE_H - h) / 2
                canvas.drawBitmap(bmp, null, android.graphics.Rect(left, top, left + w, top + h), null)
                doc.finishPage(page)
                bmp.recycle()
            }
            val dir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "lumen_pdf")
                .apply { mkdirs() }
            val out = java.io.File(dir, "lumen_${System.currentTimeMillis()}.pdf")
            out.outputStream().use { doc.writeTo(it) }
            doc.close()
            Result(true, out.absolutePath, images.size, "已合成 ${images.size} 页 PDF")
        } catch (e: Exception) {
            Result(false, null, 0, "PDF 合成失败：${e.message ?: e.javaClass.simpleName}")
        }
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
