package com.lumen.gallery.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.lumen.gallery.data.FormatUi
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet

private enum class FormatFilter(val label: String, val exts: Set<String>, val video: Boolean = false) {
    ALL("全部", emptySet()),
    RAW("RAW 底片", setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "orf", "raf", "rw2", "pef", "srw", "x3f", "tif", "tiff")),
    JPEG("JPEG", setOf("jpg", "jpeg")),
    PNG("PNG", setOf("png")),
    GIF("GIF", setOf("gif")),
    WEBP("WebP", setOf("webp")),
    HEIC("HEIC", setOf("heic", "heif", "avif")),
    VIDEO("视频", emptySet(), video = true),
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(vm: LumenViewModel, bucketFilter: String? = null, onClearBucket: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    var formatFilter by remember { mutableStateOf(FormatFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var onlyFav by remember { mutableStateOf(false) }
    var minMpText by remember { mutableStateOf("") }
    var minMp by remember { mutableStateOf<Double?>(null) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    val effectiveFolder = bucketFilter ?: folderFilter
    // v25 · 组合筛选器：全部文件夹（格式 × 文件夹双维过滤）
    val allFolders = remember(state.items) {
        state.items.map { it.path.substringBeforeLast('/') }.distinct().sorted()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.openExternal(uri)
    }

    val items = remember(state.items, formatFilter, effectiveFolder, searchQuery, onlyFav, vm.favoritePaths, minMp) {
        state.items.filter { item ->
            val folderOk = effectiveFolder == null || item.path.substringBeforeLast('/') == effectiveFolder
            val formatOk = when (formatFilter) {
                FormatFilter.ALL -> true
                FormatFilter.VIDEO -> item.isVideo
                else -> formatFilter.exts.contains(item.displayName.substringAfterLast('.', "").lowercase())
            }
            val favOk = !onlyFav || item.path in vm.favoritePaths
            // v58 · 最小像素量过滤（宽×高 ≥ N MP）
            val mpOk = minMp == null || item.width * item.height.toLong() >= (minMp!! * 1_000_000).toLong()
            val searchOk = searchQuery.isBlank() ||
                item.displayName.contains(searchQuery, ignoreCase = true) ||
                item.path.contains(searchQuery, ignoreCase = true) ||
                vm.aiMatch(item, searchQuery.trim())
            folderOk && formatOk && favOk && mpOk && searchOk
        }
    }
    val timeline = remember(items) {
        items.groupBy { item ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = item.dateAdded * 1000 }
            "%d年%d月".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var reportCopied by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showAdvancedFilter by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("光析 LUMEN") }
    var renamePrefix by remember { mutableStateOf("LUMEN") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        // v58 · 高级筛选弹窗
        if (showAdvancedFilter) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAdvancedFilter = false },
                title = { Text("高级筛选", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        Text("只显示像素量 ≥ 输入值的媒体（例：12 或 0.5）", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = minMpText,
                            onValueChange = { v -> minMpText = v.filter { c -> c.isDigit() || c == '.' } },
                            singleLine = true,
                            label = { Text("最小像素量（MP）· 留空 = 不过滤") },
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "应用", color = Cyan, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable {
                                showAdvancedFilter = false
                                minMp = minMpText.toDoubleOrNull()
                            }
                            .padding(10.dp),
                    )
                },
                dismissButton = {
                    Text("清除", color = TextMid, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable {
                                showAdvancedFilter = false
                                minMp = null
                                minMpText = ""
                            }
                            .padding(10.dp))
                },
            )
        }
        // v36 · 水印弹窗
        if (showWatermarkDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showWatermarkDialog = false },
                title = { Text("添加水印", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        Text("选中 ${vm.selectedIds.size} 项 → 右下角半透明文字水印，副本存到 Pictures/LUMEN 水印", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { if (it.length <= 30) watermarkText = it },
                            singleLine = true,
                            label = { Text("水印文字") },
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "执行", color = Cyan, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable {
                                showWatermarkDialog = false
                                if (watermarkText.isNotBlank()) vm.batchWatermark(watermarkText.trim())
                            }
                            .padding(10.dp),
                    )
                },
                dismissButton = {
                    Text("取消", color = TextMid, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { showWatermarkDialog = false }.padding(10.dp))
                },
            )
        }
        // v35 · 批量缩放弹窗
        if (showResizeDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showResizeDialog = false },
                title = { Text("批量缩放", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text("选中 ${vm.selectedIds.size} 项 → 等比缩放副本存到 Pictures/LUMEN 缩放（原图不动）", style = MaterialTheme.typography.bodySmall)
                },
                confirmButton = {},
                dismissButton = {
                    Column(Modifier.padding(8.dp)) {
                        com.lumen.gallery.data.ResizeExport.Size.entries.forEach { s ->
                            Text(
                                "最长边 ${s.maxSide} · ${s.label}", color = Cyan, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clickable {
                                        showResizeDialog = false
                                        vm.batchResize(s)
                                    }
                                    .padding(10.dp),
                            )
                        }
                        Text("取消", color = TextMid, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { showResizeDialog = false }.padding(10.dp))
                    }
                },
            )
        }
        // v30 · 批量重命名弹窗
        if (showConvertDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConvertDialog = false },
                title = { Text("格式转换", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text("选中 ${vm.selectedIds.size} 项 → 副本保存到 Pictures/LUMEN 转换（原图不动）", style = MaterialTheme.typography.bodySmall)
                },
                confirmButton = {},
                dismissButton = {
                    Column(Modifier.padding(8.dp)) {
                        com.lumen.gallery.data.ConvertExport.Fmt.entries.forEach { fmt ->
                            Text(
                                "转为 ${fmt.name}（${fmt.ext}）", color = Cyan, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clickable {
                                        showConvertDialog = false
                                        vm.batchConvert(fmt)
                                    }
                                    .padding(10.dp),
                            )
                        }
                        Text("取消", color = TextMid, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { showConvertDialog = false }.padding(10.dp))
                    }
                },
            )
        }
        if (showRenameDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("批量重命名", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        Text("选中 ${vm.selectedIds.size} 项 → 前缀_001、前缀_002 …（保留原扩展名）", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = renamePrefix,
                            onValueChange = { if (it.length <= 24) renamePrefix = it.filter { c -> !c.isWhitespace() } },
                            singleLine = true,
                            label = { Text("文件名前缀") },
                        )
                    }
                },
                confirmButton = {
                    Text(
                        "执行", color = Cyan, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable {
                                showRenameDialog = false
                                if (renamePrefix.isNotBlank()) vm.batchRename(renamePrefix.trim())
                            }
                            .padding(10.dp),
                    )
                },
                dismissButton = {
                    Text("取消", color = TextMid, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { showRenameDialog = false }.padding(10.dp))
                },
            )
        }
        // —— 批量选择模式顶栏 ——
        if (vm.selectedIds.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.2f), Violet.copy(alpha = 0.2f))))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("已选 ${vm.selectedIds.size} 项", style = MaterialTheme.typography.titleMedium, color = TextHi)
                Spacer(Modifier.weight(1f))
                Text("全选", color = Cyan, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { vm.selectAllVisible() }.padding(horizontal = 8.dp))
                Text("取消", color = TextMid, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { vm.clearSelection() }.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        // —— 顶部品牌区（选择模式下隐藏） ——
        androidx.compose.animation.AnimatedVisibility(visible = vm.selectedIds.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 14.dp)) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(listOf(Cyan, Violet, Magenta))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◉", color = Color(0xFF070B14), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("光析 LUMEN", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (bucketFilter != null) "相册集 · ${bucketFilter.substringAfterLast('/')}" else "照片元数据透视仪 · 把底裤扒干净",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatChip("${items.size}", "显示")
                if (bucketFilter != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "✕", color = Cyan, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { onClearBucket() }.padding(6.dp),
                    )
                }
            }
        }

        // —— 批量操作栏 ——
        androidx.compose.animation.AnimatedVisibility(visible = vm.selectedIds.isNotEmpty()) {
            Column {
                if (vm.batchMsg != null) {
                    Text(vm.batchMsg!!, style = MaterialTheme.typography.bodySmall, color = Cyan, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) {
                                vm.batchCleanExport()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (vm.batchBusy) "脱敏中…" else "🧹 批量脱敏",
                            style = MaterialTheme.typography.labelMedium, color = TextHi,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable {
                                val uris = ArrayList(vm.selectedItems().map { it.uri })
                                if (uris.isNotEmpty()) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = "image/*"
                                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(android.content.Intent.createChooser(intent, "分享所选媒体"))
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📤 分享", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable {
                                scope.launch {
                                    val report = vm.buildBatchReport()
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                                    reportCopied = true
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (reportCopied) "已复制 ✓" else "📋 合并报告",
                            style = MaterialTheme.typography.labelMedium, color = TextHi,
                        )
                    }
                }
                // v30 · 批量重命名入口
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { showRenameDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✏️ 批量重命名", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    // v31 · 哈希 CSV 导出 + 分享
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) {
                                vm.batchHashCsv()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🧾 哈希CSV", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    // v32 · 合成 PDF
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) {
                                vm.batchPdfMerge()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📄 合成PDF", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                }
                // v33 · ZIP 打包
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg1)
                        .clickable(enabled = !vm.batchBusy) { vm.batchZip() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🗜 ZIP 打包分享（原文件名入压缩包）", style = MaterialTheme.typography.labelMedium, color = TextHi)
                }
                // v34 · 格式转换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { showConvertDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔁 格式转换", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    // v35 · 批量缩放
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { showResizeDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📐 批量缩放", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                }
                // v36 · 水印
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { showWatermarkDialog = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("💧 加水印", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    // v53 · 批量收藏
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { vm.batchFavorite(true) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★ 全部收藏", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                    // v53 · 批量取消收藏
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Bg1)
                            .clickable(enabled = !vm.batchBusy) { vm.batchFavorite(false) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("☆ 取消收藏", style = MaterialTheme.typography.labelMedium, color = TextHi)
                    }
                }
                // v31/v32/v33 · 产物分享行
                listOfNotNull(
                    vm.hashCsvPath?.let { it to ("🧾 分享哈希报告 (CSV)" to "text/csv") },
                    vm.pdfPath?.let { it to ("📄 分享合成文档 (PDF)" to "application/pdf") },
                    vm.zipPath?.let { it to ("🗜 分享压缩包 (ZIP)" to "application/zip") },
                ).forEach { (path, desc) ->
                    val (label, mime) = desc
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "📤 $label", color = Cyan, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable {
                                val f = java.io.File(path)
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    ctx, "${ctx.packageName}.fileprovider", f,
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(intent, label.drop(2)))
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        // —— 主入口：从相册选图（液态玻璃主卡） ——
        var pickPressed by remember { mutableStateOf(false) }
        val pickScale by animateFloatAsState(
            if (pickPressed) 0.97f else 1f,
            spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "pickScale",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pickScale; scaleY = pickScale }
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.14f), Violet.copy(alpha = 0.14f), Magenta.copy(alpha = 0.12f))))
                .border(
                    1.2.dp,
                    Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.5f), Violet.copy(alpha = 0.3f), Magenta.copy(alpha = 0.4f))),
                    RoundedCornerShape(16.dp),
                )
                .graphicsLayer { shadowElevation = 6f }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) {
                    pickPressed = true
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📂", fontSize = MaterialTheme.typography.displaySmall.fontSize)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("从相册选图 · 即刻透视", style = MaterialTheme.typography.titleMedium, color = TextHi)
                    Text("跳过翻找，任意位置的照片直接进来解剖", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(1f))
                Text("→", color = Cyan, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(10.dp))

        // —— 快捷入口（液态玻璃） ——
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickCard("⚡ 随机透视", Modifier.weight(1f)) { items.randomOrNull()?.let { vm.open(it) } }
            QuickCard(if (showSearch) "✕ 关闭搜索" else "🔍 搜索", Modifier.weight(1f), accent = if (showSearch) Magenta else Violet) {
                showSearch = !showSearch
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(visible = showSearch, enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜文件名 / 路径…", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(10.dp))

        // —— 格式筛选 chips ——
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // v28 · 收藏筛选
            FilterChip2(if (onlyFav) "★ 已收藏" else "★ 收藏", onlyFav, onClick = { onlyFav = !onlyFav })
            FormatFilter.entries.forEach { f ->
                val selected = formatFilter == f
                FilterChip2(f.label, selected) { formatFilter = f }
            }
            // v58 · 高级筛选
            FilterChip2(
                if (minMp != null) "⚙ ≥${if (minMp!! >= 1) "%.0f".format(minMp!!) else "%.1f".format(minMp!!)}MP" else "⚙ 高级",
                minMp != null,
                onClick = { showAdvancedFilter = true },
            )
        }
        // —— v25 · 文件夹第二维筛选（多文件夹时显示） ——
        if (allFolders.size > 1 && bucketFilter == null) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip2("📁 全部相册", folderFilter == null) { folderFilter = null }
                allFolders.forEach { folder ->
                    val name = "📁 ${folder.substringAfterLast('/')}"
                    FilterChip2(name, folderFilter == folder) {
                        folderFilter = if (folderFilter == folder) null else folder
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        AnimatedVisibility(visible = items.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (state.loading) "正在扫描媒体库..." else "该筛选下没有媒体",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (items.isNotEmpty()) {
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val showTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 9 } }
            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                timeline.forEach { (month, list) ->
                    item(key = "h_$month", span = { GridItemSpan(3) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Brush.verticalGradient(listOf(Cyan, Violet))),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(month, style = MaterialTheme.typography.titleMedium.copy(color = TextHi))
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Cyan.copy(alpha = 0.35f), Line, Color.Transparent),
                                        ),
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${list.size} 张", style = MaterialTheme.typography.labelMedium, color = TextLow)
                        }
                    }
                    items(list, key = { "${if (it.isVideo) "v" else "i"}${it.id}" }) { item ->
                        val isSelected = item.id in vm.selectedIds
                        ThumbCell(
                            item,
                            selectionMode = vm.selectedIds.isNotEmpty(),
                            isSelected = isSelected,
                            isFav = item.path in vm.favoritePaths,
                            onClick = {
                                if (vm.selectedIds.isNotEmpty()) vm.toggleSelect(item) else vm.open(item)
                            },
                            onLongClick = { vm.toggleSelect(item) },
                        )
                    }
                }
                }
                // v43 · 回顶部悬浮按钮
                if (showTop) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 12.dp, end = 12.dp)
                            .size(46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(Cyan, Violet)))
                            .clickable { scope.launch { gridState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "↑", color = Color(0xFF070B14),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ThumbCell(
    item: com.lumen.gallery.data.MediaItem,
    selectionMode: Boolean,
    isSelected: Boolean,
    isFav: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        if (appeared) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "thumbIn",
    )
    LaunchedEffect(Unit) { appeared = true }
    // v18 · 按压弹性回馈
    val pressInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "thumbPress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                this.alpha = alpha
                val s = (0.94f + alpha * 0.06f) * pressScale
                this.scaleX = s; this.scaleY = s
            }
            .clip(RoundedCornerShape(10.dp))
            .background(Bg1)
            .border(
                if (isSelected) 2.dp else 0.dp,
                if (isSelected) Cyan else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .combinedClickable(
                interactionSource = pressInteraction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // v14 · 缩略图限尺寸解码：大 RAW 不再全图解码，网格滚动流畅
        val thumbCtx = androidx.compose.ui.platform.LocalContext.current
        val thumbRequest = remember(item.uri) {
            coil.request.ImageRequest.Builder(thumbCtx)
                .data(item.uri)
                .size(300)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = thumbRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Cyan),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color(0xFF070B14), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (item.isVideo) {
            Box(
                Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .align(Alignment.TopStart),
            ) {
                Text("▶ ${FormatUi.fmtDuration(item.durationMs ?: 0)}", color = TextHi, style = MaterialTheme.typography.labelMedium)
            }
        }
        // v59 · 收藏星标角标
        if (isFav && !isSelected) {
            Text(
                "★", color = Color(0xFFFBBF24),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.labelMedium.copy(color = TextHi),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterChip2(text: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.0f else 0.96f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "chip",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.25f), Violet.copy(alpha = 0.25f))) else Brush.verticalGradient(listOf(Bg1.copy(alpha = 0.9f), Bg1.copy(alpha = 0.7f))))
            .border(
                1.dp,
                if (selected) Brush.horizontalGradient(listOf(Cyan, Violet))
                else Brush.linearGradient(listOf(Color(0xFF1E2A44), Color(0xFF1E2A44))),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) TextHi else TextMid,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.10f), Bg1.copy(alpha = 0.9f))))
            .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = Cyan))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickCard(text: String, modifier: Modifier = Modifier, accent: Color = Cyan, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "quickScale",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.10f), Bg1.copy(alpha = 0.92f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(color = TextMid))
    }
}
