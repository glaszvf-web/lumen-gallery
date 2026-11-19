package com.lumen.gallery.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.gallery.data.AnalysisResult
import com.lumen.gallery.data.FormatUi
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.data.MediaStoreScanner
import com.lumen.gallery.data.MetadataEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val permissionGranted: Boolean = false,
)

class LumenViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** 当前选中的照片 */
    var current by mutableStateOf<MediaItem?>(null)
        private set

    /** 分析结果缓存 uri → result */
    private val cache = mutableMapOf<Uri, AnalysisResult>()

    /** 分析进行中 */
    var analyzing by mutableStateOf(false)
        private set

    /** 页面路由：0 相册 / 1 统计 */
    var route by mutableStateOf(0)

    fun loadLibrary() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val items = try {
                MediaStoreScanner.scan(getApplication())
            } catch (e: SecurityException) {
                emptyList()
            }
            _state.value = _state.value.copy(loading = false, items = items, permissionGranted = true)
            // v28 · 收藏星标同步加载
            favoritePaths = com.lumen.gallery.data.FavoritesStore.load(getApplication())
            // v17 · 库变化后自动增量重建 AI 索引
            buildAiIndex(items)
        }
        registerObserver()
    }

    // ---------- v17 · MediaStore 实时监听：新拍/新存照片立即出现 ----------
    private var observerRegistered = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingScan: Runnable? = null

    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        val cr = getApplication<Application>().contentResolver
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // 防抖：连续插入只触发一次扫描
                pendingScan?.let { mainHandler.removeCallbacks(it) }
                pendingScan = Runnable { rescanQuiet() }.also { mainHandler.postDelayed(it, 600) }
            }
        }
        runCatching {
            cr.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            cr.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        }
    }

    /** 静默重扫：不打断当前浏览，只更新列表 */
    private fun rescanQuiet() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = try {
                MediaStoreScanner.scan(getApplication())
            } catch (e: SecurityException) {
                return@launch
            }
            _state.value = _state.value.copy(items = items, loading = false, permissionGranted = true)
            buildAiIndex(items)
        }
    }

    /** v12 · AI 提示词索引：path → 信息（JSON 持久化，搜索可覆盖提示词/模型） */
    var aiIndex by androidx.compose.runtime.mutableStateOf(emptyMap<String, com.lumen.gallery.data.AiIndexer.AiInfo>())
        private set
    var aiIndexBuilding by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun buildAiIndex(items: List<MediaItem>) {
        if (aiIndexBuilding) return
        aiIndexBuilding = true
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val existing = com.lumen.gallery.data.AiIndexStore.load(app).toMutableMap()
            val validPaths = items.map { it.path }.toSet()
            existing.keys.retainAll(validPaths) // 清掉已删除文件的条目
            var scanned = 0
            items.filter { !it.isVideo && it.path !in existing }.take(400).forEach { item ->
                com.lumen.gallery.data.AiIndexer.indexFile(item.path)?.let {
                    existing[item.path] = com.lumen.gallery.data.AiIndexer.AiInfo(
                        engine = it.engine, model = it.model, prompt = it.prompt, raw = it.raw,
                    )
                    scanned++
                }
            }
            com.lumen.gallery.data.AiIndexStore.save(app, existing.mapValues { (_, e) ->
                com.lumen.gallery.data.AiIndexer.AiInfo(e.engine, e.model, e.prompt, e.raw)
            })
            aiIndex = existing.mapValues { (_, e) ->
                com.lumen.gallery.data.AiIndexer.AiInfo(e.engine, e.model, e.prompt, e.raw)
            }
            aiIndexBuilding = false
        }
    }

    /** 强制重建（清缓存全量重扫） */
    fun rebuildAiIndex() {
        val app = getApplication<Application>()
        com.lumen.gallery.data.AiIndexStore.clear(app)
        aiIndex = emptyMap()
        buildAiIndex(_state.value.items)
    }

    /** 搜索命中：文件名/路径 或 AI 提示词/模型 */
    fun aiMatch(item: MediaItem, query: String): Boolean {
        val info = aiIndex[item.path] ?: return false
        return info.engine.contains(query, true) ||
            (info.model?.contains(query, true) == true) ||
            (info.prompt?.contains(query, true) == true) ||
            info.raw.contains(query, true)
    }

    fun resultOf(item: MediaItem): AnalysisResult? = cache[item.uri]

    /** v13 · 清空分析缓存（AI 索引由设置页单独重建） */
    fun clearCaches() {
        cache.clear()
    }

    // ---------- v28 · 收藏夹 ----------
    var favoritePaths by androidx.compose.runtime.mutableStateOf(emptySet<String>())
        private set

    fun loadFavorites() {
        favoritePaths = com.lumen.gallery.data.FavoritesStore.load(getApplication())
    }

    fun isFavorite(item: MediaItem): Boolean = item.path in favoritePaths

    fun toggleFavorite(item: MediaItem) {
        val next = favoritePaths.toMutableSet()
        if (item.path in next) next.remove(item.path) else next.add(item.path)
        favoritePaths = next
        com.lumen.gallery.data.FavoritesStore.save(getApplication(), next)
    }

    /** v53 · 批量收藏 / 取消收藏 */
    fun batchFavorite(add: Boolean) {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        val next = favoritePaths.toMutableSet()
        targets.forEach { if (add) next.add(it.path) else next.remove(it.path) }
        favoritePaths = next
        com.lumen.gallery.data.FavoritesStore.save(getApplication(), next)
        batchMsg = if (add) "已收藏 ${targets.size} 项 ★" else "已取消 ${targets.size} 项收藏"
        clearSelection()
    }

    // ---------- v11 · 批量选择 ----------
    var selectedIds by androidx.compose.runtime.mutableStateOf(emptySet<Long>())
        private set
    var batchMsg by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var batchBusy by androidx.compose.runtime.mutableStateOf(false)
        private set

    fun toggleSelect(item: MediaItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
    }

    fun selectAllVisible() {
        selectedIds = state.value.items.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
        batchMsg = null
    }

    fun selectedItems(): List<MediaItem> = state.value.items.filter { it.id in selectedIds }

    /** 批量脱敏导出 */
    fun batchCleanExport() {
        val targets = selectedItems().filter { !it.isVideo }
        if (targets.isEmpty()) { batchMsg = "选中项中没有可脱敏的图片"; return }
        batchBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            var ok = 0
            var lastMsg = ""
            targets.forEach { item ->
                val r = com.lumen.gallery.data.CleanExport.export(app, item)
                if (r.ok) ok++
                lastMsg = r.message
            }
            batchMsg = "批量脱敏完成：$ok/${targets.size} 张已导出到 Pictures/LUMEN 脱敏（$lastMsg）"
            batchBusy = false
            clearSelection()
        }
    }

    /** v30 · 批量重命名：前缀_001.ext 顺序编号（MediaStore UPDATE，失败项跳过并计数） */
    fun batchRename(prefix: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val cr = getApplication<Application>().contentResolver
            var ok = 0
            val failed = mutableListOf<String>()
            targets.forEachIndexed { idx, item ->
                try {
                    val ext = item.displayName.substringAfterLast('.', "jpg")
                    val newName = "${prefix}_${"%03d".format(idx + 1)}.$ext"
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, newName)
                    }
                    val updated = cr.update(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        values,
                        "${android.provider.MediaStore.MediaColumns._ID}=?",
                        arrayOf(item.id.toString()),
                    )
                    if (updated > 0) ok++ else failed.add(item.displayName)
                } catch (e: Exception) {
                    failed.add(item.displayName)
                }
            }
            batchMsg = buildString {
                append("批量重命名完成：$ok/${targets.size} 项 → ${prefix}_001…")
                if (failed.isNotEmpty()) append("（${failed.size} 项被系统拒绝，多为非本应用写入的媒体）")
            }
            batchBusy = false
            clearSelection()
            rescanQuiet()
        }
    }

    /** v31 · 批量哈希 CSV 导出（返回报告文件路径供分享） */
    var hashCsvPath by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun batchHashCsv() {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        hashCsvPath = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.HashCsvExport.export(getApplication(), targets)
            hashCsvPath = r.path
            batchMsg = r.message + if (r.ok) "（已可分享）" else ""
            batchBusy = false
        }
    }

    // ---------- v32 · 多图合成 PDF ----------
    var pdfPath by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun batchPdfMerge() {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        pdfPath = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.PdfMergeExport.export(getApplication(), targets)
            pdfPath = r.path
            batchMsg = r.message + if (r.ok) "（点击下方分享）" else ""
            batchBusy = false
        }
    }

    // ---------- v34 · 格式转换器 ----------
    fun batchConvert(fmt: com.lumen.gallery.data.ConvertExport.Fmt) {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.ConvertExport.convert(getApplication(), targets, fmt)
            batchMsg = r.message
            batchBusy = false
            clearSelection()
        }
    }

    // ---------- v35 · 批量缩放导出 ----------
    fun batchResize(size: com.lumen.gallery.data.ResizeExport.Size) {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.ResizeExport.resize(getApplication(), targets, size)
            batchMsg = r.message
            batchBusy = false
            clearSelection()
        }
    }

    // ---------- v36 · 水印添加器 ----------
    fun batchWatermark(text: String) {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.WatermarkExport.apply(getApplication(), targets, text)
            batchMsg = r.message
            batchBusy = false
            clearSelection()
        }
    }
    var zipPath by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun batchZip() {
        val targets = selectedItems()
        if (targets.isEmpty()) { batchMsg = "未选中任何项"; return }
        batchBusy = true
        zipPath = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = com.lumen.gallery.data.ZipExport.export(getApplication(), targets)
            zipPath = r.path
            batchMsg = r.message + if (r.ok) "（点击下方分享）" else ""
            batchBusy = false
        }
    }

    /** 批量合并报告（最多 30 项，未分析的现算） */
    suspend fun buildBatchReport(): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val sb = StringBuilder("【光析 LUMEN 批量元数据报告】共 ${selectedIds.size} 项\n")
        selectedItems().take(30).forEach { item ->
            val r = cache[item.uri] ?: MetadataEngine.analyze(app, item, withColor = false)
            sb.appendLine("\n════ ${item.displayName} ════")
            sb.appendLine("格式：${r.formatName} · ${FormatUi.fmtSize(item.size)}")
            r.aiParams?.let { ai ->
                sb.appendLine("AI：${ai.engine}")
                ai.entries.forEach { sb.appendLine("  ${it.label}：${it.value}") }
            }
            r.sections.forEach { s ->
                sb.appendLine("〔${s.title}〕")
                s.entries.filter { it.important }.forEach { sb.appendLine("  ${it.label}：${it.value}") }
            }
            if (r.privacyIssues.isNotEmpty()) r.privacyIssues.forEach { sb.appendLine("  ⚠ $it") }
        }
        sb.toString()
    }

    /** V8 · 连续浏览：当前媒体在列表中的位置与相邻跳转 */
    var currentList: List<MediaItem> = emptyList()
    var currentIndex: Int = -1

    fun canPrev(): Boolean = currentIndex > 0
    fun canNext(): Boolean = currentIndex in currentList.indices && currentIndex < currentList.size - 1
    fun gotoPrev() { if (canPrev()) open(currentList[currentIndex - 1]) }
    fun gotoNext() { if (canNext()) open(currentList[currentIndex + 1]) }

    fun open(item: MediaItem, force: Boolean = false) {
        if (current == null || current?.id != item.id) {
            // 记录列表位置（首次打开或切换目标时同步）
            if (currentList.isEmpty() || currentList.none { it.id == item.id }) {
                currentList = state.value.items
            }
            currentIndex = currentList.indexOfFirst { it.id == item.id }
        }
        current = item
        if (!force && cache.containsKey(item.uri)) return
        analyzing = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                MetadataEngine.analyze(getApplication(), item)
            } catch (e: Throwable) {
                AnalysisResult(
                    formatName = "解析失败",
                    sections = emptyList(),
                    chunks = emptyList(),
                    histogram = null,
                    palette = emptyList(),
                    aiParams = null,
                    privacyIssues = emptyList(),
                    hashes = emptyList(),
                    parseWarnings = listOf("深度解析异常：${e.message ?: e.javaClass.simpleName}"),
                )
            }
            cache[item.uri] = result
            analyzing = false
        }
    }

    /** 从系统相册选图：解析 content Uri（必要时拷贝到缓存再深挖） */
    fun openExternal(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val cr = app.contentResolver
                var name = "已选图片"
                var size = 0L
                var w = 0; var h = 0
                var added = System.currentTimeMillis() / 1000
                var mime = "image/*"
                var path = ""
                val proj = arrayOf(
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.WIDTH,
                    android.provider.MediaStore.MediaColumns.HEIGHT,
                    android.provider.MediaStore.MediaColumns.DATE_ADDED,
                    android.provider.MediaStore.MediaColumns.MIME_TYPE,
                    android.provider.MediaStore.MediaColumns.DATA,
                )
                runCatching { cr.query(uri, proj, null, null, null) }.getOrNull()?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0) ?: name
                        size = if (!c.isNull(1)) c.getLong(1) else 0L
                        w = if (!c.isNull(2)) c.getInt(2) else 0
                        h = if (!c.isNull(3)) c.getInt(3) else 0
                        added = if (!c.isNull(4)) c.getLong(4) else added
                        mime = c.getString(5) ?: mime
                        path = c.getString(6) ?: ""
                    }
                }
                if (path.isBlank() || !java.io.File(path).exists()) {
                    val ext = name.substringAfterLast('.', "bin").take(8)
                    val tmp = java.io.File(app.cacheDir, "picked_${System.currentTimeMillis()}.$ext")
                    cr.openInputStream(uri)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                    path = tmp.absolutePath
                    if (size == 0L) size = tmp.length()
                }
                val isVideo = mime.startsWith("video/")
                val item = MediaItem(
                    id = -System.nanoTime(), uri = uri, path = path, displayName = name, mime = mime,
                    size = size, width = w, height = h, dateAdded = added, dateModified = added, isVideo = isVideo,
                )
                current = item
                analyzing = true
                val result = try {
                    MetadataEngine.analyze(app, item)
                } catch (e: Throwable) {
                    AnalysisResult(
                        formatName = "解析失败", sections = emptyList(), chunks = emptyList(), histogram = null,
                        palette = emptyList(), aiParams = null, privacyIssues = emptyList(), hashes = emptyList(),
                        parseWarnings = listOf("外部图片解析异常：${e.message ?: e.javaClass.simpleName}"),
                    )
                }
                cache[item.uri] = result
                analyzing = false
            } catch (e: Throwable) {
                analyzing = false
            }
        }
    }

    fun closeCurrent() {
        current = null
        analyzing = false
    }

    /** 全库统计（相机型号走 EXIF 快扫，限量防卡顿） */
    suspend fun computeStats(): Stats = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val items = _state.value.items
        val images = items.filter { !it.isVideo }
        val byFormat = images.groupBy { it.displayName.substringAfterLast('.', "").lowercase().ifEmpty { "?" } }
            .map { (k, v) -> k to v.size }
            .sortedByDescending { it.second }
        val totalBytes = items.sumOf { it.size }
        val byMonth = images.groupBy {
            val d = java.util.Calendar.getInstance().apply { timeInMillis = it.dateAdded * 1000 }
            "%04d-%02d".format(d.get(java.util.Calendar.YEAR), d.get(java.util.Calendar.MONTH) + 1)
        }.map { (k, v) -> k to v.size }.sortedByDescending { it.first }
        val cameras = mutableMapOf<String, Int>()
        images.take(300).forEach { item ->
            try {
                val model = androidx.exifinterface.media.ExifInterface(item.path)
                    .getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)
                val key = model?.replace("\u0000", "")?.trim()?.takeIf { it.isNotBlank() } ?: "无 EXIF（已剥离/网络图）"
                cameras[key] = (cameras[key] ?: 0) + 1
            } catch (e: Exception) {
                cameras["无 EXIF（已剥离/网络图）"] = (cameras["无 EXIF（已剥离/网络图）"] ?: 0) + 1
            }
        }
        val byCamera = cameras.map { (k, v) -> k to v }.sortedByDescending { it.second }
        val maxDim = images.maxOfOrNull { maxOf(it.width, it.height) } ?: 0
        // v38 · 文件夹体积 Top（全库，不分图片视频）
        val byFolder = items.groupBy { it.path.substringBeforeLast('/').substringAfterLast('/') }
            .map { (k, v) -> k to v.sumOf { it.size } }
            .sortedByDescending { it.second }
        Stats(items.size, images.size, items.size - images.size, byFormat, byMonth, byCamera, totalBytes, maxDim, byFolder)
    }

    data class Stats(
        val total: Int,
        val images: Int,
        val videos: Int,
        val byFormat: List<Pair<String, Int>>,
        val byMonth: List<Pair<String, Int>>,
        val byCamera: List<Pair<String, Int>>,
        val totalBytes: Long,
        val maxDim: Int,
        val byFolder: List<Pair<String, Long>> = emptyList(),
    )

    /** V4 · 深度扫描结果：隐私雷达 + 疑似重复 */
    data class DeepScan(
        val gpsCount: Int,
        val serialCount: Int,
        val aiCount: Int,
        val scanned: Int,
        val radarSamples: List<Pair<String, String>>, // 文件名 → 风险描述
        val duplicateGroups: List<List<MediaItem>>,   // 疑似重复组
    )

    /** dHash 感知哈希（9×8 灰度 → 64bit），汉明距离 ≤ 6 判疑似重复 */
    private fun dHash(item: MediaItem): Long? = try {
        val bmp = ColorHashDecodeSmallPath(item.path, 72) ?: return null
        val w = bmp.width; val h = bmp.height
        val gray = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = bmp.getPixel(x, y)
            gray[y * w + x] = (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000
        }
        bmp.recycle()
        var hash = 0L
        val cols = 9; val rows = 8
        for (row in 0 until rows) {
            for (col in 0 until cols - 1) {
                val x0 = col * (w - 1) / (cols - 1)
                val x1 = (col + 1) * (w - 1) / (cols - 1)
                val y = row * (h - 1) / (rows - 1)
                val left = gray[y * w + x0]
                val right = gray[y * w + x1]
                hash = (hash shl 1) or (if (left > right) 1L else 0L)
            }
        }
        hash
    } catch (e: Exception) { null }

    private fun ColorHashDecodeSmallPath(path: String, target: Int): android.graphics.Bitmap? =
        com.lumen.gallery.data.ColorHash.decodeSmall(path, target)

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    suspend fun deepScan(): DeepScan = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val items = _state.value.items.filter { !it.isVideo }
        var gps = 0; var serial = 0; var ai = 0
        val samples = mutableListOf<Pair<String, String>>()
        val hashes = mutableListOf<Pair<MediaItem, Long>>()
        items.take(300).forEach { item ->
            try {
                val exif = androidx.exifinterface.media.ExifInterface(item.path)
                val hasGps = exif.latLong != null
                val serialVal = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_BODY_SERIAL_NUMBER)
                    ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_CAMERA_OWNER_NAME)
                if (hasGps) gps++
                if (!serialVal.isNullOrBlank()) serial++
                if (hasGps && samples.size < 6) samples.add(item.displayName to "GPS 定位暴露")
                else if (!serialVal.isNullOrBlank() && samples.size < 6) samples.add(item.displayName to "序列号/所有者暴露")
            } catch (e: Exception) { }
            dHash(item)?.let { hashes.add(item to it) }
        }
        // 重复分组
        val groups = mutableListOf<List<MediaItem>>()
        val used = mutableSetOf<Int>()
        for (i in hashes.indices) {
            if (i in used) continue
            val group = mutableListOf(hashes[i].first)
            for (j in i + 1 until hashes.size) {
                if (j in used) continue
                if (hamming(hashes[i].second, hashes[j].second) <= 6) {
                    group.add(hashes[j].first); used.add(j)
                }
            }
            if (group.size > 1) { groups.add(group); used.add(i) }
        }
        DeepScan(gps, serial, ai, minOf(items.size, 300), samples, groups.take(5))
    }
}
