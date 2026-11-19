package com.lumen.gallery.data

import org.json.JSONObject

/**
 * AI 绘图参数侦探：
 *  1) AUTOMATIC1111 / WebUI 风格 "parameters" 文本
 *  2) ComfyUI "prompt"/"workflow" JSON（PNG tEXt 常见）
 *  3) InvokeAI "Dream" 文本
 *  4) JPEG COM 注释中的 WebUI 参数
 */
object AiParser {

    fun detect(texts: List<Pair<String, String>>, userComment: String?): AiParams? {
        // ComfyUI
        for ((k, v) in texts) {
            if (k.equals("prompt", true) && v.trimStart().startsWith("{")) {
                parseComfyPrompt(v)?.let { return it }
            }
        }
        for ((k, v) in texts) {
            if (k.equals("parameters", true)) {
                parseWebUi(v)?.let { return it }
            }
        }
        for ((k, v) in texts) {
            if (k.equals("Dream", true)) {
                parseInvoke(v)?.let { return it }
            }
        }
        // workflow JSON 兜底（仅有 workflow 无 prompt 时给出摘要）
        for ((k, v) in texts) {
            if (k.equals("workflow", true) && v.trimStart().startsWith("{")) {
                parseComfyWorkflowOnly(v)?.let { return it }
            }
        }
        // JPEG COM / UserComment 中的 WebUI 参数
        for ((_, v) in texts) {
            if (v.contains("Steps:") && v.contains("Sampler:", true)) {
                parseWebUi(v)?.let { return it }
            }
        }
        userComment?.let {
            if (it.contains("Steps:") && it.contains("Sampler:", true)) {
                parseWebUi(it)?.let { p -> return p }
            }
        }
        return null
    }

    // ---------- AUTOMATIC1111 ----------
    private fun parseWebUi(text: String): AiParams? {
        val negIdx = text.indexOf("Negative prompt:")
        val stepsIdx = text.lastIndexOf("Steps:")
        if (stepsIdx < 0) return null
        val positive = if (negIdx > 0) text.substring(0, negIdx).trim() else text.substring(0, stepsIdx).trim()
        val negative = if (negIdx > 0) text.substring(negIdx + "Negative prompt:".length, stepsIdx).trim() else null
        val kvText = text.substring(stepsIdx)
        val entries = mutableListOf<MetaEntry>()
        Regex("([A-Za-z][A-Za-z ]*?):\\s*\"([^\"]*)\"|([A-Za-z][A-Za-z ]*?):\\s*([^,]+)").findAll(kvText).forEach { m ->
            val key = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
            val value = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim().trim('"', ' ')
            if (key.isNotBlank() && value.isNotBlank()) {
                entries.add(MetaEntry(webUiZh(key), value.take(120), important = key in setOf("Model", "Sampler", "Steps", "CFG scale", "Seed", "Size")))
            }
        }
        if (entries.isEmpty() && positive.isBlank() && negative.isNullOrBlank()) return null
        return AiParams(
            engine = "Stable Diffusion（WebUI / AUTOMATIC1111）",
            raw = text,
            entries = entries,
            positive = positive.takeIf { it.isNotBlank() },
            negative = negative?.takeIf { it.isNotBlank() },
        )
    }

    private fun webUiZh(key: String): String = when (key.lowercase()) {
        "steps" -> "生成步数"
        "sampler" -> "采样器"
        "schedule type" -> "调度类型"
        "cfg scale" -> "提示词引导 CFG"
        "seed" -> "随机种子"
        "size" -> "生成尺寸"
        "model hash" -> "模型哈希"
        "model" -> "底模"
        "denoising strength" -> "去噪强度"
        "clip skip" -> "CLIP 跳过层"
        "lora hashes" -> "LoRA 哈希"
        "version" -> "工具版本"
        "vae" -> "VAE"
        "hires upscale" -> "高清放大倍率"
        "hires steps" -> "高清修复步数"
        "hires upscaler" -> "高清放大器"
        "hires resize" -> "高清修复尺寸"
        else -> key
    }

    // ---------- ComfyUI ----------
    private fun parseComfyPrompt(json: String): AiParams? = try {
        val root = JSONObject(json)
        var seed: String? = null; var steps: String? = null; var cfg: String? = null
        var sampler: String? = null; var scheduler: String? = null; var model: String? = null
        var loras = mutableListOf<String>()
        val texts = mutableListOf<String>()
        val keyIter = root.keys()
        var samplerNodes = 0
        while (keyIter.hasNext()) {
            val nodeKey = keyIter.next()
            val node = root.optJSONObject(nodeKey) ?: continue
            val ct = node.optString("class_type", "")
            val inputs = node.optJSONObject("inputs") ?: continue
            when {
                ct.contains("KSampler") || ct.contains("SamplerCustom") -> {
                    samplerNodes++
                    seed = seed ?: arrayOf("seed", "noise_seed").firstNotNullOfOrNull { k ->
                        inputs.opt(k)?.let { nodeRef(root, it) }
                    }?.toString()
                    steps = steps ?: inputs.opt("steps")?.toString()
                    cfg = cfg ?: inputs.opt("cfg")?.toString()
                    sampler = sampler ?: inputs.optString("sampler_name").ifBlank { null }
                    scheduler = scheduler ?: inputs.optString("scheduler").ifBlank { null }
                }
                ct.contains("CheckpointLoader") || ct.contains("UNETLoader") || ct.contains("VAELoader") -> {
                    val n = inputs.optString("ckpt_name").ifBlank { inputs.optString("unet_name") }
                    if (n.isNotBlank()) model = model ?: n
                }
                ct.contains("LoraLoader") -> {
                    inputs.optString("lora_name").takeIf { it.isNotBlank() && !ct.contains("ModelOnly") || ct.contains("LoraLoaderModelOnly") }?.let { loras.add(it) }
                }
                ct.contains("CLIPTextEncode") || ct.contains("TextEncode") -> {
                    val t = inputs.opt("text")?.let { nodeRef(root, it) }
                    if (!t.isNullOrBlank() && t.length > 1) texts.add(t)
                }
            }
        }
        val entries = mutableListOf<MetaEntry>()
        samplerNodes.takeIf { it > 0 }?.let { entries.add(MetaEntry("采样节点数", "$it 个 KSampler 节点")) }
        // EmptyLatent 尺寸：再扫一遍
        var size: String? = null
        for (k in root.keys()) {
            val node = root.optJSONObject(k) ?: continue
            if (node.optString("class_type").contains("EmptyLatent")) {
                val inputs = node.optJSONObject("inputs") ?: continue
                size = "${inputs.opt("width")} × ${inputs.opt("height")}" + (inputs.opt("batch_size")?.let { "（批量 $it）" } ?: "")
            }
        }
        model?.let { entries.add(MetaEntry("模型/底模", it.take(120), important = true)) }
        loras.distinct().take(5).forEachIndexed { i, l -> entries.add(MetaEntry("LoRA ${i + 1}", l.take(120))) }
        sampler?.let { entries.add(MetaEntry("采样器", it, important = true)) }
        scheduler?.let { entries.add(MetaEntry("调度器", it)) }
        steps?.let { entries.add(MetaEntry("生成步数", it, important = true)) }
        cfg?.let { entries.add(MetaEntry("提示词引导 CFG", it, important = true)) }
        seed?.let { entries.add(MetaEntry("随机种子", it, important = true)) }
        size?.let { entries.add(MetaEntry("生成尺寸", it, important = true)) }
        entries.add(MetaEntry("工作流节点", "${root.length()} 个节点"))
        if (entries.isEmpty() && texts.isEmpty()) null
        else AiParams(
            engine = "ComfyUI 节点工作流",
            raw = json,
            entries = entries,
            positive = texts.firstOrNull()?.take(1200),
            negative = texts.getOrNull(1)?.take(1200),
        )
    } catch (e: Exception) { null }

    /** 输入可能是引用 [nodeId, slot]，则解析该节点输出名 */
    private fun nodeRef(root: JSONObject, v: Any?): String? = when {
        v == null -> null
        v is String -> v
        v is Number -> v.toString()
        v is org.json.JSONArray && v.length() == 2 -> {
            val nodeId = v.optString(0)
            val slot = v.optInt(1)
            val target = root.optJSONObject(nodeId)
            val ct = target?.optString("class_type") ?: ""
            val inner = target?.optJSONObject("inputs")?.let { tin ->
                arrayOf("seed", "noise_seed", "value").firstNotNullOfOrNull { k -> tin.opt(k) }
            }
            if (inner is Number) inner.toString() else "$ct#$slot"
        }
        else -> v.toString()
    }

    private fun parseComfyWorkflowOnly(json: String): AiParams? = try {
        val root = JSONObject(json)
        val nodes = root.optJSONArray("nodes") ?: return null
        var nodeCount = 0
        var models = mutableListOf<String>()
        var samplers = mutableListOf<String>()
        for (i in 0 until nodes.length()) {
            nodeCount++
            val n = nodes.optJSONObject(i) ?: continue
            val type = n.optString("type", "")
            if (type.contains("CheckpointLoader") || type.contains("LoraLoader")) {
                val wv = n.optJSONArray("widgets_values") ?: continue
                for (j in 0 until wv.length()) {
                    val s = wv.optString(j)
                    if (s.endsWith(".safetensors") || s.endsWith(".ckpt") || s.endsWith(".pt")) models.add(s)
                }
            }
            if (type.contains("KSampler")) samplers.add(type)
        }
        if (nodeCount == 0) null
        else AiParams(
            engine = "ComfyUI 工作流（仅节点图）",
            raw = json,
            entries = buildList {
                add(MetaEntry("工作流节点", "$nodeCount 个"))
                models.distinct().take(5).forEachIndexed { idx, m -> add(MetaEntry("模型 ${idx + 1}", m.take(120), important = idx == 0)) }
                samplers.take(1).forEach { add(MetaEntry("采样节点", it)) }
            },
        )
    } catch (e: Exception) { null }

    // ---------- InvokeAI ----------
    private fun parseInvoke(text: String): AiParams? {
        val m = Regex("^(.*?)\\s+(-[A-Za-z]\\s+\\S+.*)$").find(text) ?: return null
        val prompt = m.groupValues[1].trim()
        val flagZh = mapOf("S" to "生成步数", "W" to "生成宽度", "H" to "生成高度", "C" to "提示词引导 CFG", "s" to "随机种子", "A" to "采样器", "m" to "模型")
        val entries = mutableListOf<MetaEntry>()
        Regex("-(\\w)\\s+(\"[^\"]*\"|\\S+)").findAll(m.groupValues[2]).forEach { f ->
            val zh = flagZh[f.groupValues[1]] ?: return@forEach
            entries.add(MetaEntry(zh, f.groupValues[2].trim('"'), important = true))
        }
        if (entries.isEmpty()) return null
        return AiParams(engine = "InvokeAI", entries = entries, positive = prompt.takeIf { it.isNotBlank() }, raw = text)
    }
}
