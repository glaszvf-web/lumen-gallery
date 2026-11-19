package com.lumen.gallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val mime: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val isVideo: Boolean,
    val durationMs: Long? = null,
)

data class MetaEntry(
    val label: String,
    val value: String,
    val important: Boolean = false,
)

data class MetaSection(
    val title: String,
    val subtitle: String = "",
    val entries: List<MetaEntry> = emptyList(),
)

data class ChunkInfo(
    val name: String,
    val offset: Long,
    val size: Long,
    val desc: String,
)

data class HistogramData(
    val r: IntArray,
    val g: IntArray,
    val b: IntArray,
) {
    val max: Int by lazy { maxOf(r.max(), g.max(), b.max()).coerceAtLeast(1) }
}

data class AiParams(
    val engine: String,
    val entries: List<MetaEntry>,
    val positive: String? = null,
    val negative: String? = null,
    val raw: String? = null,
)

data class AnalysisResult(
    val formatName: String,
    val sections: List<MetaSection>,
    val chunks: List<ChunkInfo>,
    val histogram: HistogramData?,
    val palette: List<Int>,
    val aiParams: AiParams?,
    val privacyIssues: List<String>,
    val hashes: List<MetaEntry>,
    val parseWarnings: List<String> = emptyList(),
    val bitDepth: String? = null,   // v17 · 最高位深（重点标注）
    val hdr: String? = null,        // v17 · HDR 标识（null=SDR）
    val rawPreview: android.graphics.Bitmap? = null, // v27 · RAW 显影预览（内嵌 JPEG 显影）
    val rawPreviewMeta: String? = null,              // v27 · 预览来源说明
)
