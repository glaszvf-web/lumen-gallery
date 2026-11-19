package com.lumen.gallery.data

import android.content.Context

/**
 * v12 · 全库 AI 提示词索引器
 * 扫描 PNG（tEXt/iTXt/zTXt）与 JPEG（COM/UserComment）中的 AI 参数，
 * 产出 path → AiInfo 映射，供相册搜索覆盖提示词/模型名。
 */
object AiIndexer {

    data class AiInfo(
        val engine: String,
        val model: String?,
        val prompt: String?,
        val raw: String,
    )

    private const val MAX_SCAN_BYTES = 4L * 1024 * 1024   // 只读头部 4MB
    private const val MAX_FILE = 80L * 1024 * 1024        // 跳过超大文件

    fun indexFile(path: String): AiInfo? {
        val f = java.io.File(path)
        if (!f.exists() || f.length() > MAX_FILE) return null
        val head = try {
            f.inputStream().use { ins ->
                val b = ByteArray(MAX_SCAN_BYTES.toInt())
                var off = 0
                while (off < b.size) {
                    val r = ins.read(b, off, b.size - off)
                    if (r < 0) break
                    off += r
                }
                if (off == 0) null else b.copyOf(off)
            }
        } catch (e: Exception) { null } ?: return null

        return when {
            head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> indexPng(head)
            head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> indexJpeg(head, path)
            else -> null
        }
    }

    // ---- PNG：遍历 chunk 找 tEXt/iTXt/zTXt ----
    private fun indexPng(d: ByteArray): AiInfo? {
        val texts = mutableListOf<Pair<String, String>>()
        var off = 8
        var guard = 0
        while (off + 8 <= d.size && guard < 2000) {
            guard++
            val len = ((d[off].toInt() and 0xFF) shl 24) or ((d[off + 1].toInt() and 0xFF) shl 16) or
                ((d[off + 2].toInt() and 0xFF) shl 8) or (d[off + 3].toInt() and 0xFF)
            if (len < 0 || off + 12 + len > d.size) break
            val type = String(d, off + 4, 4, Charsets.US_ASCII)
            if (type == "tEXt" && len < 1 shl 20) {
                val chunk = d.copyOfRange(off + 8, off + 8 + len)
                val nul = chunk.indexOf(0)
                if (nul > 0) {
                    texts.add(String(chunk, 0, nul, Charsets.ISO_8859_1) to
                        String(chunk, nul + 1, chunk.size - nul - 1, Charsets.UTF_8))
                }
            } else if (type == "iTXt" && len < 1 shl 22) {
                val chunk = d.copyOfRange(off + 8, off + 8 + len)
                parseITxt(chunk)?.let { texts.add(it) }
            } else if (type == "zTXt" && len < 1 shl 20) {
                val chunk = d.copyOfRange(off + 8, off + 8 + len)
                val nul = chunk.indexOf(0)
                if (nul > 0 && nul + 2 < chunk.size) {
                    try {
                        val inflater = java.util.zip.Inflater()
                        inflater.setInput(chunk, nul + 2, chunk.size - nul - 2)
                        val buf = ByteArray(1 shl 20)
                        val total = inflater.inflate(buf)
                        inflater.end()
                        texts.add(String(chunk, 0, nul, Charsets.ISO_8859_1) to String(buf, 0, total, Charsets.UTF_8))
                    } catch (e: Exception) { }
                }
            } else if (type == "IHDR" && len >= 13) { /* skip */ }
            if (type == "IDAT") break // 元数据都在 IDAT 前
            off += 12 + len
        }
        if (texts.isEmpty()) return null
        val ai = AiParser.detect(texts, null) ?: return null
        return toInfo(ai, texts)
    }

    // ---- JPEG：COM 段 + UserComment ----
    private fun indexJpeg(d: ByteArray, path: String): AiInfo? {
        val texts = mutableListOf<Pair<String, String>>()
        var off = 2
        var guard = 0
        while (off + 4 <= d.size && guard < 500) {
            guard++
            if (d[off] != 0xFF.toByte()) { off++; continue }
            val marker = d[off + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01) { off += 2; continue }
            if (marker == 0xD9) break
            if (off + 4 > d.size) break
            val segLen = ((d[off + 2].toInt() and 0xFF) shl 8) or (d[off + 3].toInt() and 0xFF)
            if (segLen < 2 || off + 2 + segLen > d.size) break
            if (marker == 0xFE && segLen in 4..(1 shl 20)) {
                texts.add("COM" to String(d, off + 4, segLen - 2, Charsets.UTF_8))
            }
            if (marker == 0xDA) break
            off += 2 + segLen
        }
        var userComment: String? = null
        try {
            userComment = androidx.exifinterface.media.ExifInterface(path)
                .getAttribute(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT)
        } catch (e: Exception) { }
        if (texts.isEmpty() && userComment.isNullOrBlank()) return null
        val ai = AiParser.detect(texts, userComment) ?: return null
        return toInfo(ai, texts)
    }

    private fun toInfo(ai: AiParams, texts: List<Pair<String, String>>): AiInfo {
        val model = ai.entries.firstOrNull {
            it.label.contains("模型") || it.label.contains("底模")
        }?.value
        return AiInfo(
            engine = ai.engine,
            model = model,
            prompt = ai.positive?.take(400) ?: ai.entries.firstOrNull { it.label.contains("提示词") }?.value,
            raw = texts.joinToString("\n") { "${it.first}: ${it.second.take(2000)}" }.take(6000),
        )
    }

    /** iTXt：key\0 compFlag\0 compMethod\0 lang\0 translated\0 text */
    private fun parseITxt(d: ByteArray): Pair<String, String>? = try {
        var i = d.indexOf(0)
        if (i < 0) null
        else {
            val key = String(d, 0, i, Charsets.ISO_8859_1)
            i += 1
            if (i >= d.size) null
            else {
                val compFlag = d[i].toInt()
                i += 2 // compFlag + compMethod
                // lang\0 translated\0
                var nul = d.indexOf(0, i)
                if (nul < 0) null
                else {
                    nul = d.indexOf(0, nul + 1)
                    if (nul < 0) null
                    else {
                        val start = nul + 1
                        val value = if (compFlag == 1 && start < d.size) {
                            try {
                                val inflater = java.util.zip.Inflater()
                                inflater.setInput(d, start, d.size - start)
                                val buf = ByteArray(1 shl 20)
                                val total = inflater.inflate(buf)
                                inflater.end()
                                String(buf, 0, total, Charsets.UTF_8)
                            } catch (e: Exception) { "" }
                        } else {
                            String(d.copyOfRange(start, d.size), Charsets.UTF_8)
                        }
                        key to value
                    }
                }
            }
        }
    } catch (e: Exception) { null }

    private fun ByteArray.indexOf(b: Int, from: Int = 0): Int {
        for (i in from until size) if (this[i].toInt() == b) return i
        return -1
    }
}

/** v12 · 索引持久化（filesDir/ai_index.json） */
object AiIndexStore {

    data class Entry(val engine: String, val model: String?, val prompt: String?, val raw: String)

    fun save(context: Context, index: Map<String, AiIndexer.AiInfo>) {
        try {
            val arr = org.json.JSONArray()
            index.forEach { (path, info) ->
                arr.put(org.json.JSONObject().apply {
                    put("path", path)
                    put("engine", info.engine)
                    put("model", info.model ?: "")
                    put("prompt", info.prompt ?: "")
                    put("raw", info.raw)
                })
            }
            java.io.File(context.filesDir, "ai_index.json")
                .writeText(org.json.JSONObject().put("items", arr).toString())
        } catch (e: Exception) { }
    }

    fun load(context: Context): Map<String, AiIndexer.AiInfo> = try {
        val f = java.io.File(context.filesDir, "ai_index.json")
        if (!f.exists()) emptyMap()
        else {
            val obj = org.json.JSONObject(f.readText())
            val arr = obj.optJSONArray("items") ?: org.json.JSONArray()
            val map = mutableMapOf<String, AiIndexer.AiInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.getString("path")] = AiIndexer.AiInfo(
                    engine = o.optString("engine"),
                    model = o.optString("model").ifBlank { null },
                    prompt = o.optString("prompt").ifBlank { null },
                    raw = o.optString("raw"),
                )
            }
            map
        }
    } catch (e: Exception) { emptyMap() }

    fun clear(context: Context) {
        runCatching { java.io.File(context.filesDir, "ai_index.json").delete() }
    }
}
