package com.lumen.gallery.ui

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.gallery.data.AnalysisResult
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.data.MetaEntry
import com.lumen.gallery.data.MetaSection
import com.lumen.gallery.ui.theme.Amber
import com.lumen.gallery.ui.theme.Bg0
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Danger
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.Ok
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SECTION_ACCENTS = listOf(Cyan, Violet, Magenta, Ok, Amber)

@Composable
fun AnalysisScreen(vm: LumenViewModel, item: MediaItem) {
    var refreshed by remember(item.id) { mutableStateOf(vm.resultOf(item)) }
    LaunchedEffect(vm.analyzing, item.id) {
        if (!vm.analyzing) refreshed = vm.resultOf(item)
    }
    var fullscreen by remember { mutableStateOf(false) }
    var videoPlaying by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // P0 修复：系统返回（含侧滑手势）先回相册，绝不允许直接杀 app
    androidx.activity.compose.BackHandler {
        when {
            fullscreen -> fullscreen = false
            videoPlaying -> videoPlaying = false
            editing -> editing = false
            else -> vm.closeCurrent()
        }
    }

    Box(Modifier.fillMaxSize().background(Bg0)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg0)
                .statusBarsPadding(),
        ) {
            TopBar(item, vm) { vm.closeCurrent() }
            if (!videoPlaying && !editing) {
                ZoomableImage(item.uri, item.path, item.isVideo) { fullscreen = true }
            }
            AnalysisBody(item, refreshed, vm.analyzing, onPlayVideo = { videoPlaying = true }, onEdit = { editing = true }, onCompare = { comparing = true })
        }
        if (fullscreen) {
            FullscreenViewer(
                item.uri, item.isVideo, item.path, result = refreshed,
                onExit = { fullscreen = false },
                onPrev = { vm.gotoPrev() },
                onNext = { vm.gotoNext() },
            )
        }
        if (videoPlaying) {
            VideoPlayerScreen(item.uri, item.displayName) { videoPlaying = false }
        }
        if (editing) {
            EditorScreen(item) { editing = false }
        }
        if (comparing) {
            // v25 · 与下一张对比
            val idx = vm.currentList.indexOfFirst { it.id == item.id }
            val other = if (idx >= 0 && idx < vm.currentList.size - 1) vm.currentList[idx + 1] else vm.currentList.firstOrNull()
            other?.let { otherItem ->
                val reportA = refreshed?.sections?.flatMap { it.entries } ?: emptyList()
                val reportB = vm.resultOf(otherItem)?.sections?.flatMap { it.entries }
                    ?: run {
                        val r = com.lumen.gallery.data.MetadataEngine.analyze(
                            ctx.applicationContext, otherItem, withColor = false,
                        )
                        r.sections.flatMap { it.entries }
                    }
                CompareScreen(item, otherItem, reportA, reportB) { comparing = false }
            }
        }
    }
}

/** V6 · 沉浸式全屏查看器：黑底缩放 + 快速参数条 + 单击显隐 + v42 横滑切图 */
@Composable
private fun FullscreenViewer(
    uri: Uri, isVideo: Boolean, path: String, result: AnalysisResult?,
    onExit: () -> Unit,
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    androidx.activity.compose.BackHandler { onExit() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var showInfo by remember { mutableStateOf(true) }
    val videoFrame = if (isVideo) rememberVideoFrame(path) else null
    val all = result?.sections?.flatMap { it.entries } ?: emptyList()
    fun find(label: String) = all.firstOrNull { it.label == label }?.value ?: "—"
    // v42 · 横滑累计量（双指缩放时不触发切换）
    var swipeAcc by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 8f)
                    offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                }
            }
            .pointerInput(Unit) {
                // v42 · 未缩放时横滑切换上一张/下一张
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    swipeAcc = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val drag = ev.changes.firstOrNull()?.positionChange()?.x ?: 0f
                        if (scale <= 1.05f) swipeAcc += drag
                    } while (ev.changes.any { it.pressed })
                    if (scale <= 1.05f) {
                        if (swipeAcc < -140f) onNext()
                        else if (swipeAcc > 140f) onPrev()
                    }
                }
            }
            .clickable { showInfo = !showInfo },
    ) {
        if (videoFrame != null) {
            androidx.compose.foundation.Image(
                bitmap = videoFrame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showInfo,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // v19 · 液态玻璃快速参数浮岛
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.82f))))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.5f), Violet.copy(alpha = 0.35f), Magenta.copy(alpha = 0.4f))),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(
                    "光圈" to find("光圈"),
                    "快门" to find("快门速度"),
                    "ISO" to find("ISO 感光度"),
                    "焦距" to find("焦距"),
                ).forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Cyan.copy(alpha = 0.8f))
                        Text(value.take(12), style = MaterialTheme.typography.titleMedium.copy(color = TextHi))
                    }
                }
            }
        }
        Text(
            "✕", color = TextHi, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .clickable(onClick = onExit)
                .padding(18.dp),
        )
        Text(
            "×%.1f".format(scale), color = TextLow,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(18.dp),
        )
        androidx.compose.material3.TextButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 60.dp),
        ) { Text("⤢ 退出全屏", color = TextMid) }
    }
}

@Composable
private fun AnalysisBody(item: MediaItem, r: AnalysisResult?, analyzing: Boolean, onPlayVideo: () -> Unit = {}, onEdit: () -> Unit = {}, onCompare: () -> Unit = {}) {
    var query by remember(item.id) { mutableStateOf("") }
    var showSearch by remember(item.id) { mutableStateOf(false) }
    // v43 · 一键展开/折叠全部分组（null=默认折叠）
    var forceExpand by remember(item.id) { mutableStateOf<Boolean?>(null) }

    Column(Modifier.fillMaxSize()) {
        // —— 工具条：搜索过滤 ——
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSearch) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索元数据关键字…", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(52.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "取消", color = Cyan, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { query = ""; showSearch = false }.padding(6.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
                // v43 · 一键展开/折叠全部分组
                Text(
                    when (forceExpand) {
                        true -> "▣ 全部折叠"
                        false -> "▣ 全部展开"
                        null -> "▤ 展开全部"
                    },
                    color = if (forceExpand == null) TextMid else Cyan,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { forceExpand = when (forceExpand) { true -> false; false -> null; null -> true } }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.Search, "搜索", tint = TextMid,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showSearch = true }
                        .padding(8.dp)
                        .size(22.dp),
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (analyzing || r == null) {
                item { GlassCard(accent = Cyan) { ScanIndicator("⚡ 深度扫描中 · EXIF / IFD 结构 / AI 指纹 / 编码色彩科学") } }
            }
            r?.let { result ->
                val q = query.trim()
                // v17 · 位深/规格重点条（用户要求：色深重点标出）
                if (q.isEmpty()) item { SpecStrip(result) }
                // v27 · RAW 显影预览（底片内嵌 JPEG 显影）
                if (q.isEmpty() && result.rawPreview != null) {
                    item { RawPreviewCard(result.rawPreview!!, result.rawPreviewMeta ?: "") }
                }
                // v18 · 视频播放入口（v19 液态玻璃）
                if (q.isEmpty() && item.isVideo) {
                    item {
                        QuickActionButton("▶ 播放视频（ExoPlayer 硬解）", Cyan) { onPlayVideo() }
                    }
                }
                // v19 · 编辑入口
                if (q.isEmpty() && !item.isVideo) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                QuickActionButton("🎨 编辑", Violet) { onEdit() }
                            }
                            Box(Modifier.weight(1f)) {
                                // v25 · 双图对比
                                QuickActionButton("⇆ 对比", Magenta) { onCompare() }
                            }
                        }
                    }
                }
                // Hero 曝光速览
                if (q.isEmpty()) item { HeroCard(result) }
                // v48 · 术语速读卡：本图涉及的专业词白话解释（缩写后缀保留）
                if (q.isEmpty()) {
                    val joined = result.sections.joinToString(" ") { s ->
                        s.title + " " + s.subtitle + " " + s.entries.joinToString(" ") { it.label + " " + it.value }
                    } + " " + (result.bitDepth ?: "") + " " + (result.hdr ?: "") + " " + result.formatName
                    val hits = com.lumen.gallery.data.Terms.matchIn(joined)
                    if (hits.isNotEmpty()) item { TermsCard(hits) }
                }
                if (result.parseWarnings.isNotEmpty() && q.isEmpty()) {
                    item {
                        GlassCard(accent = Amber) {
                            Text("解析备注", style = MaterialTheme.typography.titleMedium, color = Amber)
                            Spacer(Modifier.height(6.dp))
                            result.parseWarnings.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = TextMid) }
                        }
                    }
                }
                // AI 档案置顶
                if (q.isEmpty()) result.aiParams?.let { ai -> item { AiCard(ai) } }
                // 隐私体检置顶
                if (q.isEmpty() && result.privacyIssues.isNotEmpty()) {
                    item { PrivacyCard(item, result) }
                }
                // 定位卡（地图跳转 + 逆地理）
                if (q.isEmpty()) {
                    val gpsSection = result.sections.firstOrNull { it.subtitle == "GEOTAG" }
                    if (gpsSection != null) item { LocationCard(gpsSection) }
                }
                // 各分组（可折叠）
                result.sections.forEachIndexed { idx, section ->
                    item {
                        CollapsibleSectionCard(
                            section = section,
                            accent = SECTION_ACCENTS[idx % SECTION_ACCENTS.size],
                            expandedDefault = section.subtitle == "SUMMARY" || q.isNotEmpty(),
                            query = q.ifEmpty { null },
                            forceExpand = forceExpand,
                        )
                    }
                }
                // 色彩可视化
                if (q.isEmpty() && (result.histogram != null || result.palette.isNotEmpty())) {
                    item { ColorWaveCard(result) }
                }
                // 容器结构
                if (q.isEmpty() && result.chunks.isNotEmpty()) {
                    item { ChunkCard(result) }
                }
                // 哈希
                if (result.hashes.isNotEmpty()) {
                    item { HashCard(result, q) }
                }
                // 导出
                if (q.isEmpty()) item { ExportCard(item, result) }
            }
        }
    }
}

/** v48 · 术语速读卡：本图涉及的专业词 → 白话解释（专业缩写后缀保留） */
@Composable
private fun TermsCard(hits: List<com.lumen.gallery.data.Terms.Term>) {
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) hits else hits.take(4)
    GlassCard(accent = Violet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Violet))
            Spacer(Modifier.width(8.dp))
            Text("术语速读", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("GLOSSARY", style = MaterialTheme.typography.labelMedium, color = Violet.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(8.dp))
        shown.forEach { t ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("› ", color = Violet)
                Text(
                    t.plain,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (hits.size > 4) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (showAll) "收起 ▲" else "还有 ${hits.size - 4} 条 · 展开全部 ▼",
                color = Cyan, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAll = !showAll }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** v27 · RAW 显影预览卡：底片内嵌 JPEG 显影 + 点击放大 */
@Composable
private fun RawPreviewCard(bmp: android.graphics.Bitmap, meta: String) {
    var zoomed by remember { mutableStateOf(false) }
    GlassCard(accent = Ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Ok))
            Spacer(Modifier.width(8.dp))
            Text("RAW 显影预览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("DEVELOP", style = MaterialTheme.typography.labelMedium, color = Ok.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "RAW 显影预览",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { zoomed = true },
        )
        Spacer(Modifier.height(6.dp))
        Text(meta, style = MaterialTheme.typography.bodySmall, color = TextLow)
        Text("点击图片放大查看 · RAW 主数据为未显影 CFA，此图来自底片内嵌 JPEG", style = MaterialTheme.typography.bodySmall, color = TextLow)
    }
    if (zoomed) {
        androidx.activity.compose.BackHandler { zoomed = false }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable { zoomed = false },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
            Text(
                "✕", color = TextHi, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .clickable { zoomed = false }
                    .padding(18.dp),
            )
        }
    }
}

/** v19 · 液态玻璃快捷操作按钮（按压弹性） */
@Composable
private fun QuickActionButton(text: String, accent: Color, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "qab",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.08f))))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(color = TextHi))
    }
}

/** v17 · 规格 重点条：位深大字 + HDR 徽章 + 分辨率 */
@Composable
private fun SpecStrip(result: AnalysisResult) {
    val all = result.sections.flatMap { it.entries }
    val res = all.firstOrNull { it.label == "分辨率" }?.value ?: "—"
    val mp = all.firstOrNull { it.label == "像素量" }?.value ?: ""
    val bit = result.bitDepth ?: "—"
    val isHdr = result.hdr != null
    GlassCard(accent = if (isHdr) Magenta else Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 位深大字（重点）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    bit.split(" ").firstOrNull() ?: "—",
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = if (result.bitDepth?.startsWith("16") == true ||
                            result.bitDepth?.startsWith("14") == true ||
                            result.bitDepth?.startsWith("10") == true
                        ) Violet else TextHi,
                        fontWeight = FontWeight.Black,
                    ),
                )
                Text("位深 bit", style = MaterialTheme.typography.labelMedium, color = TextLow)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHdr) Magenta.copy(alpha = 0.25f) else Cyan.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (isHdr) "HDR" else "SDR",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isHdr) Magenta else Cyan, fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$res · $mp", style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                result.hdr?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Hero 曝光速览：光圈/快门/ISO/焦距 大数字卡 */
@Composable
private fun HeroCard(r: AnalysisResult) {
    val all = r.sections.flatMap { it.entries }
    fun find(label: String) = all.firstOrNull { it.label == label }?.value
    val cells = listOf(
        "⌀" to (find("光圈") ?: find("光圈值(APEX)") ?: "—"),
        "⏱" to (find("快门速度") ?: find("快门速度值(APEX)") ?: "—"),
        "ISO" to (find("ISO 感光度") ?: "—"),
        "⌖" to (find("焦距") ?: "—"),
    )
    if (cells.all { it.second == "—" }) return
    GlassCard(accent = Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Cyan))
            Spacer(Modifier.width(8.dp))
            Text("曝光速览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("EXPOSURE", style = MaterialTheme.typography.labelMedium, color = Cyan.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.forEachIndexed { i, (icon, value) ->
                val accent = SECTION_ACCENTS[i % SECTION_ACCENTS.size]
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Bg1.copy(alpha = 0.9f))))
                        .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(icon, style = MaterialTheme.typography.bodySmall, color = accent)
                    Text(
                        value.take(14),
                        style = MaterialTheme.typography.titleMedium.copy(color = TextHi),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        find("分辨率")?.let {
            Spacer(Modifier.height(8.dp))
            Text("分辨率 $it · ${find("像素量") ?: ""}", style = MaterialTheme.typography.bodySmall, color = TextMid)
        }
    }
}

@Composable
private fun TopBar(item: MediaItem, vm: LumenViewModel, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextHi,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(8.dp)
                .size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.path.substringBeforeLast('/'), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // v28 · 收藏星标
        val fav = vm.isFavorite(item)
        Text(
            if (fav) "★" else "☆",
            color = if (fav) Amber else TextMid,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { vm.toggleFavorite(item) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        // V8 · 连续浏览 ‹ ›
        val prevEnabled = vm.canPrev()
        val nextEnabled = vm.canNext()
        Text(
            "‹", color = if (prevEnabled) Cyan else TextLow,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = prevEnabled) { vm.gotoPrev() }
                .padding(horizontal = 10.dp),
        )
        Text(
            "›", color = if (nextEnabled) Cyan else TextLow,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = nextEnabled) { vm.gotoNext() }
                .padding(horizontal = 10.dp),
        )
    }
}

/** V9 · 视频取帧：视频条目用 MediaMetadataRetriever 提取代表帧 */
@Composable
fun rememberVideoFrame(path: String): android.graphics.Bitmap? =
    remember(path) {
        try {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(path)
            val frame = r.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            frame
        } catch (e: Exception) { null }
    }

@Composable
private fun ZoomableImage(uri: Uri, path: String, isVideo: Boolean = false, onEnterFullscreen: () -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val videoFrame = if (isVideo) rememberVideoFrame(path) else null
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Bg1)
            .border(1.dp, Line, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
                }
            }
            .clickable {
                if (scale > 1f) {
                    scale = 1f; offset = androidx.compose.ui.geometry.Offset.Zero
                } else {
                    onEnterFullscreen()
                }
            },
    ) {
        if (videoFrame != null) {
            androidx.compose.foundation.Image(
                bitmap = videoFrame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
        }
        // RAW/DNG 用系统缩略图通道展示
        val model: Any = remember(path) {
            if (path.endsWith(".dng", true) || path.endsWith(".cr2", true) || path.endsWith(".nef", true) ||
                path.endsWith(".arw", true) || path.endsWith(".raf", true) || path.endsWith(".cr3", true) ||
                path.endsWith(".orf", true) || path.endsWith(".rw2", true)
            ) uri else uri
        }
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("🔍 捏合缩放 ×%.1f".format(scale), style = MaterialTheme.typography.labelMedium, color = TextMid)
        }
    }
}

/** 可折叠分组卡 */
@Composable
private fun CollapsibleSectionCard(
    section: MetaSection, accent: Color, expandedDefault: Boolean, query: String?,
    forceExpand: Boolean? = null,
) {
    var expanded by remember(section.title, query != null, forceExpand) {
        mutableStateOf(forceExpand ?: expandedDefault)
    }
    val q = query?.trim()?.lowercase()
    val filtered = if (q.isNullOrBlank()) section.entries else section.entries.filter {
        it.label.lowercase().contains(q) || it.value.lowercase().contains(q)
    }
    if (filtered.isEmpty()) return
    GlassCard(modifier = Modifier.animateContentSize(animationSpec = tween(260, easing = FastOutSlowInEasing)), accent = accent) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "${filtered.size} 项 ▲" else "${filtered.size} 项 ▼",
                style = MaterialTheme.typography.labelMedium,
                color = accent.copy(alpha = 0.85f),
            )
        }
        if (section.subtitle.isNotBlank()) {
            Text(section.subtitle, style = MaterialTheme.typography.labelMedium, color = TextLow)
        }
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column { filtered.forEach { DataRow(it) } }
        }
        if (!expanded) {
            val preview = filtered.take(3)
            preview.forEach { DataRow(it) }
            if (filtered.size > 3) {
                Text("… 共 ${filtered.size} 项，点击展开", style = MaterialTheme.typography.bodySmall, color = TextLow)
            }
        }
    }
}

/** 隐私体检卡 + 一键脱敏导出 */
@Composable
private fun PrivacyCard(item: MediaItem, result: AnalysisResult) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    GlassCard(accent = Danger) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("隐私体检", style = MaterialTheme.typography.titleMedium, color = Danger)
            Spacer(Modifier.weight(1f))
            Text("PRIVACY", style = MaterialTheme.typography.labelMedium, color = Danger.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(8.dp))
        result.privacyIssues.forEach { PrivacyBadge(it); Spacer(Modifier.height(6.dp)) }
        if (!item.isVideo) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    busy = true
                    val app = ctx.applicationContext
                    Thread {
                        val res = com.lumen.gallery.data.CleanExport.export(app, item)
                        msg = res.message
                        busy = false
                    }.start()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (busy) "脱敏导出中…" else "🧹 一键导出脱敏副本（抹 GPS/序列号）",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            msg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("已导出")) Ok else TextMid)
            }
        }
    }
}
@Composable
private fun LocationCard(section: MetaSection) {
    val ctx = LocalContext.current
    var address by remember { mutableStateOf<String?>(null) }
    val lat = section.entries.firstOrNull { it.label.contains("纬度") }?.value
        ?.let { Regex("（(-?[0-9.]+)）").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
    val lon = section.entries.firstOrNull { it.label.contains("经度") }?.value
        ?.let { Regex("（(-?[0-9.]+)）").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

    LaunchedEffect(lat, lon) {
        if (lat != null && lon != null) {
            withContext(Dispatchers.IO) {
                try {
                    if (Geocoder.isPresent()) {
                        Geocoder(ctx, java.util.Locale.CHINA)
                            .getFromLocation(lat, lon, 1)
                            ?.firstOrNull()?.let { a ->
                                address = listOfNotNull(a.adminArea, a.locality, a.subLocality, a.thoroughfare)
                                    .filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null }
                            }
                    }
                } catch (e: Exception) { }
            }
        }
    }
    GlassCard(accent = Ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Ok))
            Spacer(Modifier.width(8.dp))
            Text("位置速览", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("LOCATION", style = MaterialTheme.typography.labelMedium, color = Ok.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(8.dp))
        if (lat != null && lon != null) {
            Text(
                "%.5f, %.5f".format(lat, lon),
                style = MaterialTheme.typography.titleMedium.copy(color = TextHi),
            )
            Text(
                address ?: "正在尝试离线逆地理…（失败可用下方按钮跳转地图）",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(
                    onClick = {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?z=16"))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(Intent.createChooser(i, "在地图中查看"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("🗺 在地图中查看", style = MaterialTheme.typography.labelMedium) }
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("coords", "$lat,$lon"))
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("复制坐标", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun AiCard(ai: com.lumen.gallery.data.AiParams) {
    GlassCard(accent = Magenta) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Magenta))
            Spacer(Modifier.width(8.dp))
            Text("AI 绘图档案", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("GENERATIVE", style = MaterialTheme.typography.labelMedium, color = Magenta)
        }
        Spacer(Modifier.height(4.dp))
        Text(ai.engine, style = MaterialTheme.typography.bodySmall, color = TextLow)
        Spacer(Modifier.height(10.dp))
        ai.entries.forEach { DataRow(it) }
        ai.positive?.let {
            Spacer(Modifier.height(8.dp))
            PromptBlock("正向提示词", it, Ok)
        }
        ai.negative?.let {
            Spacer(Modifier.height(6.dp))
            PromptBlock("负向提示词", it, Danger)
        }
        ai.raw?.let { rawText ->
            Spacer(Modifier.height(8.dp))
            val clipboard = LocalClipboardManager.current
            var copiedWf by remember { mutableStateOf(false) }
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(rawText.take(20000)))
                    copiedWf = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (copiedWf) "工作流原文已复制 ✓" else "📋 复制工作流/参数原文", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PromptBlock(label: String, text: String, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Bg1)
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = TextMid))
    }
}

@Composable
private fun ColorWaveCard(r: AnalysisResult) {
    GlassCard(accent = Ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Ok))
            Spacer(Modifier.width(8.dp))
            Text("色彩波形", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("RGB HISTOGRAM", style = MaterialTheme.typography.labelMedium, color = Ok.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(10.dp))
        r.histogram?.let { HistogramCanvas(it) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("主色调", style = MaterialTheme.typography.bodySmall)
            PaletteRow(r.palette, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChunkCard(r: AnalysisResult) {
    GlassCard(accent = Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Cyan))
            Spacer(Modifier.width(8.dp))
            Text("容器结构解剖", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("BINWALK", style = MaterialTheme.typography.labelMedium, color = Cyan.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(10.dp))
        r.chunks.take(60).forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    c.name, style = MaterialTheme.typography.labelMedium.copy(color = TextHi),
                    modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Column(Modifier.weight(1f)) {
                    Text(c.desc, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "@ 0x${java.lang.Long.toHexString(c.offset)} · ${if (c.size >= 1024) "%.1f KB".format(c.size / 1024.0) else "${c.size} B"}",
                        style = MaterialTheme.typography.labelMedium.copy(color = TextLow),
                    )
                }
            }
        }
        if (r.chunks.size > 60) Text("…另有 ${r.chunks.size - 60} 项省略", style = MaterialTheme.typography.bodySmall, color = TextLow)
    }
}

@Composable
private fun HashCard(r: AnalysisResult, query: String) {
    val q = query.trim().lowercase()
    val hashes = r.hashes.filter { q.isBlank() || it.label.lowercase().contains(q) || it.value.lowercase().contains(q) }
    if (hashes.isEmpty()) return
    GlassCard(accent = Violet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(4.dp)).background(Violet))
            Spacer(Modifier.width(8.dp))
            Text("指纹与校验", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("HASH", style = MaterialTheme.typography.labelMedium, color = Violet.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(8.dp))
        hashes.forEach { h ->
            Column(Modifier.padding(vertical = 3.dp)) {
                Text(h.label, style = MaterialTheme.typography.bodySmall)
                Text(
                    h.value, style = MaterialTheme.typography.labelMedium.copy(color = TextMid),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(item: MediaItem, r: AnalysisResult) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var making by remember { mutableStateOf(false) }
    GlassCard(accent = Amber) {
        Text("报告导出", style = MaterialTheme.typography.titleMedium, color = Amber)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(
                onClick = {
                    clipboard.setText(AnnotatedString(buildReport(item, r)))
                    copied = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (copied) "已复制 ✓" else "复制全文", style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, buildReport(item, r))
                    }
                    ctx.startActivity(Intent.createChooser(intent, "分享元数据报告"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("分享文本", style = MaterialTheme.typography.labelMedium)
            }
        }
        // v56 · 导出 JSON（机器可读全量档案）
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = {
                try {
                    val json = buildJsonReport(item, r)
                    val f = java.io.File(ctx.cacheDir, "lumen_meta_${System.currentTimeMillis()}.json")
                    f.writeText(json)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        ctx, "${ctx.packageName}.fileprovider", f,
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "导出元数据 JSON"))
                } catch (e: Exception) { }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("{ } 导出元数据 JSON（机器可读）", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        // v54 · 模板选择
        var template by remember { mutableStateOf(true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "📋 完整卡", false to "✨ 极简卡").forEach { (d, label) ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (template == d) Cyan.copy(alpha = 0.22f) else com.lumen.gallery.ui.theme.Bg1)
                        .border(
                            1.dp,
                            if (template == d) Cyan else com.lumen.gallery.ui.theme.Line,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { template = d }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (template == d) Cyan else TextMid)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(
            onClick = {
                android.util.Log.d("LUMEN", "share-card clicked")
                making = true
                val context = ctx.applicationContext
                val tpl = template
                Thread {
                    try {
                        android.util.Log.d("LUMEN", "render start")
                        val card = com.lumen.gallery.data.ShareCard.render(context, item, r, tpl)
                        android.util.Log.d("LUMEN", "card saved: ${card.absolutePath} size=${card.length()}")
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", card,
                        )
                        android.util.Log.d("LUMEN", "uri=$uri")
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(intent, "分享元数据卡片").apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                        android.util.Log.d("LUMEN", "chooser started")
                    } catch (e: Throwable) {
                        android.util.Log.e("LUMEN", "share failed", e)
                        try {
                            java.io.File(context.cacheDir, "share_error.txt")
                                .writeText(e.stackTraceToString().take(4000))
                        } catch (e2: Exception) { }
                    }
                    making = false
                }.start()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (making) "生成中…" else "🎨 生成分享卡（PNG）", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** v56 · 元数据 JSON 导出（org.json 逐层构建，字段名保留英文专业标识） */
private fun buildJsonReport(item: MediaItem, r: AnalysisResult): String {
    val root = org.json.JSONObject()
    root.put("file", item.displayName)
    root.put("path", item.path)
    root.put("size_bytes", item.size)
    root.put("format", r.formatName)
    root.put("bit_depth", r.bitDepth ?: "")
    root.put("hdr", r.hdr ?: "")
    val sections = org.json.JSONArray()
    r.sections.forEach { s ->
        val so = org.json.JSONObject()
        so.put("title", s.title)
        so.put("key", s.subtitle)
        val arr = org.json.JSONArray()
        s.entries.forEach { e ->
            val eo = org.json.JSONObject()
            eo.put("label", e.label)
            eo.put("value", e.value)
            eo.put("important", e.important)
            arr.put(eo)
        }
        so.put("entries", arr)
        sections.put(so)
    }
    root.put("sections", sections)
    r.aiParams?.let { ai ->
        val aio = org.json.JSONObject()
        aio.put("engine", ai.engine)
        ai.entries.firstOrNull { it.label.contains("模型", ignoreCase = true) || it.label.contains("model", ignoreCase = true) }
            ?.let { aio.put("model", it.value) }
        ai.positive?.let { aio.put("positive", it) }
        ai.negative?.let { aio.put("negative", it) }
        root.put("ai_params", aio)
    }
    root.put("privacy_issues", org.json.JSONArray(r.privacyIssues))
    return root.toString(2)
}

private fun buildReport(item: MediaItem, r: AnalysisResult): String {
    val sb = StringBuilder()
    sb.appendLine("【光析 LUMEN 元数据报告】")
    sb.appendLine("文件：${item.displayName}")
    sb.appendLine("格式：${r.formatName}")
    r.aiParams?.let { ai ->
        sb.appendLine()
        sb.appendLine("== AI 绘图档案（${ai.engine}）==")
        ai.entries.forEach { sb.appendLine("${it.label}：${it.value}") }
        ai.positive?.let { sb.appendLine("正向提示词：$it") }
        ai.negative?.let { sb.appendLine("负向提示词：$it") }
    }
    r.sections.forEach { s ->
        sb.appendLine()
        sb.appendLine("== ${s.title} ==")
        s.entries.forEach { sb.appendLine("${it.label}：${it.value}") }
    }
    if (r.hashes.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("== 指纹 ==")
        r.hashes.forEach { sb.appendLine("${it.label}：${it.value}") }
    }
    if (r.privacyIssues.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("== 隐私提示 ==")
        r.privacyIssues.forEach { sb.appendLine("⚠ $it") }
    }
    return sb.toString()
}
