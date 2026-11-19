package com.lumen.gallery.data

import android.content.Context
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v33 · 批量打包 ZIP：选中媒体按原文件名打入压缩包（同名自动加序号）
 * 输出：应用外部私有 Documents/lumen_zip/<时间戳>.zip
 */
object ZipExport {

    data class Result(val ok: Boolean, val path: String?, val count: Int, val message: String)

    fun export(context: Context, items: List<MediaItem>): Result {
        if (items.isEmpty()) return Result(false, null, 0, "未选中任何项")
        return try {
            val dir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "lumen_zip")
                .apply { mkdirs() }
            val out = java.io.File(dir, "lumen_${System.currentTimeMillis()}.zip")
            val used = mutableMapOf<String, Int>()
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                items.forEach { item ->
                    val f = java.io.File(item.path)
                    if (!f.exists()) return@forEach
                    // 同名冲突加序号
                    var name = item.displayName
                    val n = used[name] ?: 0
                    used[name] = n + 1
                    if (n > 0) {
                        val base = item.displayName.substringBeforeLast('.')
                        val ext = item.displayName.substringAfterLast('.', "jpg")
                        name = "${base}_$n.$ext"
                    }
                    zip.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            Result(true, out.absolutePath, items.size, "已打包 ${items.size} 项")
        } catch (e: Exception) {
            Result(false, null, 0, "打包失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
