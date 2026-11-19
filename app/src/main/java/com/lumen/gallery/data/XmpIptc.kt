package com.lumen.gallery.data

object XmpParser {

    /** 轻量提取 XMP 关键字段（不做完整 RDF 解析，性能友好） */
    fun extract(xmp: String): List<MetaEntry> {
        val out = mutableListOf<MetaEntry>()
        if (xmp.isBlank()) return out

        fun firstTag(tag: String): String? {
            val re = Regex("<(?:\\w+:)?$tag(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$tag>", RegexOption.DOT_MATCHES_ALL)
            return re.find(xmp)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
        fun attr(tag: String, attr: String): String? {
            val re = Regex("<(?:\\w+:)?$tag\\b[^>]*\\b$attr=\"([^\"]*)\"", RegexOption.DOT_MATCHES_ALL)
            return re.find(xmp)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }

        firstTag("title")?.let { out.add(MetaEntry("XMP 标题", stripXml(it).take(120))) }
        firstTag("description")?.let { out.add(MetaEntry("XMP 描述", stripXml(it).take(200))) }
        firstTag("creator")?.let { out.add(MetaEntry("XMP 创作者", stripXml(it).take(80))) }
        firstTag("CreatorTool")?.let { out.add(MetaEntry("创建工具", stripXml(it).take(80), important = true)) }
        firstTag("Rating")?.let { out.add(MetaEntry("XMP 评分", stripXml(it))) }
            ?: attr("Rating", "xmp:Rating")?.let { out.add(MetaEntry("XMP 评分", it)) }
        firstTag("Label")?.let { out.add(MetaEntry("XMP 标签", stripXml(it))) }
        // 关键词集合
        val kw = Regex("<(?:\\w+:)?subject>(.*?)</(?:\\w+:)?subject>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xmp).map { stripXml(it.groupValues[1]) }
            .filter { it.isNotBlank() && it != "/" }.distinct().take(10).joinToString("、")
        if (kw.isNotBlank()) out.add(MetaEntry("XMP 关键词", kw))
        // photoshop 命名空间
        firstTag("Credit")?.let { out.add(MetaEntry("XMP 信用", stripXml(it).take(80))) }
        firstTag("Source")?.let { out.add(MetaEntry("XMP 来源", stripXml(it).take(80))) }
        if (xmp.contains("GenerativeAI", true) || xmp.contains("ignorableEdit", true)) {
            out.add(MetaEntry("AI 生成声明", "XMP 中含有生成式 AI 声明（C2PA/Content Credentials 线索）", important = true))
        }
        return out
    }

    private fun stripXml(s: String): String =
        s.replace(Regex("<[^>]*>"), "").trim().replace(Regex("\\s+"), " ")

    private fun String.trimToNull(): String? = if (isBlank()) null else this
    private fun MatchResult.trimToNull(g: Int = 0): String? = groupValues[g].trimToNull()
}

object IptcParser {

    private val IPTC_TAGS = mapOf(
        0x05 to "IPTC 标题",
        0x19 to "IPTC 关键词",
        0x1E to "IPTC 参考编号",
        0x37 to "IPTC 日期",
        0x3C to "IPTC 时间",
        0x50 to "IPTC 信用",
        0x55 to "IPTC 来源",
        0x5F to "IPTC 修改循环ID",
        0x64 to "IPTC 版权通知",
        0x6E to "IPTC 联系信息",
        0x78 to "IPTC 摘要",
        0x7C to "IPTC 作者",
        0x80 to "IPTC 说明",
        0x86 to "IPTC 传输参考",
        0x87 to "IPTC 对象类型",
        0x8C to "IPTC 分类",
    )

    /** 解析 JPEG APP13 (Photoshop 8BIM) 中的 IPTC IIM 记录 */
    fun parse8Bim(data: ByteArray): List<MetaEntry> {
        val out = mutableListOf<MetaEntry>()
        try {
            var i = 0
            while (i + 12 <= data.size) {
                if (data[i] != '8'.code.toByte() || data[i + 1] != 'B'.code.toByte() ||
                    data[i + 2] != 'I'.code.toByte() || data[i + 3] != 'M'.code.toByte()
                ) { i++; continue }
                val rsrcLen = u16be(data, i + 10)
                val rsrcEnd = i + 12 + rsrcLen
                if (rsrcEnd > data.size) break
                // 找 IPTC (0x0404)
                if (u16be(data, i + 4) == 0x0404) {
                    var p = i + 12
                    while (p + 5 <= rsrcEnd) {
                        if (data[p].toInt() != 0x1C) break
                        val recNo = data[p + 1].toInt() and 0xFF
                        val dataType = data[p + 2].toInt() and 0xFF
                        val len = ((data[p + 3].toInt() and 0xFF) shl 8) or (data[p + 4].toInt() and 0xFF)
                        if (recNo == 2 && p + 5 + len <= rsrcEnd) {
                            val text = String(data, p + 5, len, Charsets.ISO_8859_1)
                            val decoded = fixLatin1(text)
                            IPTC_TAGS[dataType]?.let { out.add(MetaEntry(it, decoded.take(200))) }
                        }
                        p += 5 + len
                    }
                }
                i = rsrcEnd
            }
        } catch (e: Exception) { }
        return out
    }

    /** IPTC IIM 使用 Latin-1，尽量还原中文（PSD 常以 UTF-8 误存） */
    private fun fixLatin1(s: String): String {
        val hasHighByte = s.any { it.code in 0x80..0xFF }
        if (!hasHighByte) return s
        val bytes = ByteArray(s.length) { i -> (s[i].code and 0xFF).toByte() }
        val utf8 = String(bytes, Charsets.UTF_8)
        // 若按 UTF-8 解出大量替换符则保留原样
        return if (utf8.contains('\uFFFD')) s else utf8
    }
}
