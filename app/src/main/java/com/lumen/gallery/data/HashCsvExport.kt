package com.lumen.gallery.data

import android.content.Context

/**
 * v31 · 批量哈希校验 CSV：选中项逐个算 MD5/SHA-1/SHA-256，落成电子表格友好的 CSV
 * 输出：应用外部私有 Documents/lumen_hashes_<时间戳>.csv（FileProvider 分享）
 */
object HashCsvExport {

    data class Result(val ok: Boolean, val path: String?, val count: Int, val message: String)

    fun export(context: Context, items: List<MediaItem>): Result = try {
        val dir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "hash_reports")
            .apply { mkdirs() }
        val out = java.io.File(dir, "lumen_hashes_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder("\uFEFF文件名,大小(字节),MD5,SHA-1,SHA-256\n") // BOM 兼容 Excel
        var n = 0
        items.filter { !it.isVideo }.forEach { item ->
            val hashes = try { ColorHash.hashes(item.path) } catch (e: Exception) { emptyList() }
            fun h(label: String) = hashes.firstOrNull { it.label.startsWith(label) }?.value ?: ""
            sb.append(csvCell(item.displayName)).append(",")
                .append(item.size).append(",")
                .append(csvCell(h("MD5"))).append(",")
                .append(csvCell(h("SHA-1"))).append(",")
                .append(csvCell(h("SHA-256"))).append("\n")
            n++
        }
        out.writeText(sb.toString())
        Result(true, out.absolutePath, n, "已导出 $n 项哈希清单")
    } catch (e: Exception) {
        Result(false, null, 0, "导出失败：${e.message ?: e.javaClass.simpleName}")
    }

    private fun csvCell(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
