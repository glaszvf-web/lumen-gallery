package com.lumen.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.gallery.data.FormatUi
import com.lumen.gallery.ui.theme.Amber
import com.lumen.gallery.ui.theme.Bg0
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.Ok
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet

@Composable
fun StatsScreen(vm: LumenViewModel, onBack: () -> Unit = {}) {
    var stats by remember { mutableStateOf<LumenViewModel.Stats?>(null) }
    var deep by remember { mutableStateOf<LumenViewModel.DeepScan?>(null) }
    LaunchedEffect(Unit) {
        stats = vm.computeStats()
        deep = vm.deepScan()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg0)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextHi,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBack() }
                    .padding(8.dp)
                    .size(22.dp),
            )
            Text("库内统计", style = MaterialTheme.typography.titleLarge)
        }

        if (stats == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScanIndicator("⚡ 遍历全库 EXIF，正在统计……")
            }
            return@Column
        }
        val s = stats!!
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigNum("${s.images}", "张图片", Cyan, Modifier.weight(1f))
                    BigNum("${s.videos}", "个视频", Magenta, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigNum(FormatUi.fmtSize(s.totalBytes), "总占用", Ok, Modifier.weight(1f))
                    BigNum("${s.maxDim}", "最大边长", Violet, Modifier.weight(1f))
                }
            }
            // 格式分布
            item {
                GlassCard(accent = Cyan) {
                    Text("格式分布", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val maxV = s.byFormat.maxOfOrNull { it.second } ?: 1
                    s.byFormat.take(10).forEach { (fmt, cnt) ->
                        BarRow(fmt.uppercase(), cnt, maxV, Cyan)
                    }
                    if (s.byFormat.isEmpty()) Text("暂无图片", style = MaterialTheme.typography.bodySmall)
                }
            }
            // v50 · 格式占比环形图
            item {
                GlassCard(accent = Violet) {
                    Text("格式占比环", style = MaterialTheme.typography.titleMedium)
                    Text("图片格式数量占比（前 7 名）", style = MaterialTheme.typography.bodySmall, color = TextLow)
                    Spacer(Modifier.height(10.dp))
                    MiniDonut(s.byFormat, Violet)
                }
            }
            // 时间线
            item {
                GlassCard(accent = Ok) {
                    Text("拍摄月历", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val maxV = s.byMonth.maxOfOrNull { it.second } ?: 1
                    s.byMonth.take(12).forEach { (m, cnt) ->
                        BarRow(m, cnt, maxV, Ok)
                    }
                    if (s.byMonth.isEmpty()) Text("暂无数据", style = MaterialTheme.typography.bodySmall)
                }
            }
            // v44 · 月度活跃趋势（Canvas 柱状图，生长动画）
            item {
                GlassCard(accent = Cyan) {
                    Text("月度活跃趋势", style = MaterialTheme.typography.titleMedium)
                    Text("最近 12 个月拍摄量柱状图（新 → 旧，从右往左）", style = MaterialTheme.typography.bodySmall, color = TextLow)
                    Spacer(Modifier.height(10.dp))
                    val months = s.byMonth.take(12).reversed()
                    if (months.isEmpty()) {
                        Text("暂无数据", style = MaterialTheme.typography.bodySmall)
                    } else {
                        MiniBarChart(months, Cyan)
                    }
                }
            }
            // v38 · 存储空间分析（按文件夹体积占比）
            item {
                GlassCard(accent = Magenta) {
                    Text("存储空间分析（文件夹体积 Top 10）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val byFolder = s.byFolder
                    val maxB = byFolder.maxOfOrNull { it.second } ?: 1L
                    byFolder.forEach { (folder, bytes) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                folder, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(0.42f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Box(Modifier.weight(0.4f).height(10.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((bytes.toFloat() / maxB).coerceIn(0.01f, 1f))
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                listOf(Magenta.copy(alpha = 0.45f), Magenta),
                                            ),
                                        ),
                                )
                            }
                            Text(
                                FormatUi.fmtSize(bytes),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(0.18f).padding(start = 6.dp),
                                color = TextMid,
                            )
                        }
                    }
                    if (byFolder.isEmpty()) Text("暂无数据", style = MaterialTheme.typography.bodySmall)
                }
            }
            // 相机分布
            item {
                GlassCard(accent = Amber) {
                    Text("设备分布（EXIF 采样前 300 张）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    val maxV = s.byCamera.maxOfOrNull { it.second } ?: 1
                    s.byCamera.take(8).forEach { (cam, cnt) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                cam, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                Modifier
                                    .weight(0.5f)
                                    .height(10.dp),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(cnt.toFloat() / maxV)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Amber.copy(alpha = 0.85f)),
                                )
                            }
                            Text(
                                "×$cnt", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(44.dp),
                                color = TextMid,
                            )
                        }
                    }
                    if (s.byCamera.isEmpty()) Text("暂无相机信息", style = MaterialTheme.typography.bodySmall)
                }
            }
            // 隐私雷达
            deep?.let { d ->
                item {
                    GlassCard(accent = com.lumen.gallery.ui.theme.Danger) {
                        Text("隐私雷达", style = MaterialTheme.typography.titleMedium, color = com.lumen.gallery.ui.theme.Danger)
                        Text("扫描 ${d.scanned} 张图片的敏感元数据", style = MaterialTheme.typography.bodySmall, color = TextLow)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RadarCell("${d.gpsCount}", "GPS 暴露", com.lumen.gallery.ui.theme.Danger, Modifier.weight(1f))
                            RadarCell("${d.serialCount}", "序列号暴露", Amber, Modifier.weight(1f))
                        }
                        if (d.radarSamples.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            d.radarSamples.take(4).forEach { (name, risk) ->
                                Text(
                                    "⚠ $name · $risk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMid,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (d.gpsCount == 0 && d.serialCount == 0) {
                            Text("✓ 未发现敏感元数据暴露", style = MaterialTheme.typography.bodySmall, color = com.lumen.gallery.ui.theme.Ok)
                        }
                    }
                }
                // 疑似重复
                item {
                    GlassCard(accent = Violet) {
                        Text("重复照片侦探", style = MaterialTheme.typography.titleMedium, color = com.lumen.gallery.ui.theme.Violet)
                        Text("dHash 感知哈希聚类（汉明距离 ≤ 6）", style = MaterialTheme.typography.bodySmall, color = TextLow)
                        Spacer(Modifier.height(10.dp))
                        if (d.duplicateGroups.isEmpty()) {
                            Text("✓ 未发现疑似重复的照片", style = MaterialTheme.typography.bodySmall, color = com.lumen.gallery.ui.theme.Ok)
                        } else {
                            // v39 · 缩略图分组浏览：每行一组，点缩略图直接透视
                            d.duplicateGroups.forEach { group ->
                                Text(
                                    "相似组 · ${group.size} 张", style = MaterialTheme.typography.labelMedium, color = Violet,
                                )
                                Spacer(Modifier.height(6.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(group.size) { i ->
                                        val item = group[i]
                                        Box(
                                            Modifier
                                                .size(96.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Bg1)
                                                .border(1.dp, Violet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                                .clickable { vm.open(item) },
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = item.uri,
                                                contentDescription = item.displayName,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Text("点缩略图直接透视 · 处理建议：脱敏后分享或批量清理", style = MaterialTheme.typography.bodySmall, color = TextLow)
                        }
                    }
                }
            } ?: item {
                GlassCard(accent = Violet) { ScanIndicator("⚡ 隐私雷达与重复检测扫描中…") }
            }
            // AI 索引
            item {
                GlassCard(accent = Magenta) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI 提示词索引", style = MaterialTheme.typography.titleMedium, color = com.lumen.gallery.ui.theme.Magenta)
                        Spacer(Modifier.weight(1f))
                        Text("${vm.aiIndex.size} 张", style = MaterialTheme.typography.labelMedium, color = TextMid)
                    }
                    Text(
                        if (vm.aiIndexBuilding) "后台扫描 PNG/JPEG 的生成参数中…" else "已建索引：相册搜索可直接搜提示词/模型名",
                        style = MaterialTheme.typography.bodySmall, color = TextLow,
                    )
                    Spacer(Modifier.height(8.dp))
                    vm.aiIndex.entries.take(4).forEach { (path, info) ->
                        Text(
                            "🎨 ${path.substringAfterLast('/')} · ${info.model ?: info.engine}",
                            style = MaterialTheme.typography.bodySmall, color = TextMid,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { vm.rebuildAiIndex() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🔄 重建全库 AI 索引", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun RadarCell(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.10f), Bg1.copy(alpha = 0.92f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.displaySmall.copy(color = accent))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BigNum(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier, accent) {
        Text(value, style = MaterialTheme.typography.displaySmall.copy(color = accent))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** v50 · 格式占比环形图（Canvas 圆环，动画扫入，图例并列） */
@Composable
private fun MiniDonut(data: List<Pair<String, Int>>, accent: Color) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    val colors = listOf(Cyan, Violet, Magenta, Ok, Amber, Color(0xFF60A5FA), Color(0xFFF87171))
    val sweep by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(800),
        label = "donut",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
            val stroke = 22f
            val inset = stroke
            var start = -90f
            data.take(7).forEachIndexed { i, (_, cnt) ->
                val frac = cnt.toFloat() / total
                val s = frac * 360f * sweep
                drawArc(
                    color = colors[i % colors.size],
                    startAngle = start,
                    sweepAngle = s,
                    useCenter = false,
                    style = Stroke(width = stroke),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                )
                start += s
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            data.take(7).forEachIndexed { i, (name, cnt) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors[i % colors.size]),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${name.uppercase()} · $cnt（${(cnt * 100 / total)}%）",
                        style = MaterialTheme.typography.labelMedium, color = TextMid,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/** v44 · 月度趋势柱状图（Canvas + 生长动画 + 峰值高亮） */
@Composable
private fun MiniBarChart(months: List<Pair<String, Int>>, accent: Color) {
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "chartGrow",
    )
    val maxV = months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val peakIdx = months.indexOfFirst { it.second == months.maxOf { m -> m.second } }
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height
        val n = months.size.coerceAtLeast(1)
        val slot = w / n
        val barW = slot * 0.55f
        // 基线与横线
        for (i in 1..3) drawLine(
            com.lumen.gallery.ui.theme.Line, androidx.compose.ui.geometry.Offset(0f, h * i / 4f),
            androidx.compose.ui.geometry.Offset(w, h * i / 4f), 1f,
        )
        months.forEachIndexed { i, (_, cnt) ->
            val frac = (cnt.toFloat() / maxV) * 0.92f * progress
            val bh = h * frac
            val x = slot * i + (slot - barW) / 2
            val isPeak = i == peakIdx
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(accent, accent.copy(alpha = 0.35f)),
                ),
                topLeft = androidx.compose.ui.geometry.Offset(x, h - bh),
                size = androidx.compose.ui.geometry.Size(barW, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f),
                alpha = if (isPeak) 1f else 0.72f,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(months.firstOrNull()?.first?.substring(2) ?: "", style = MaterialTheme.typography.labelMedium, color = TextLow)
        Text(
            "峰值 ${months.maxOfOrNull { it.second } ?: 0} 张/月",
            style = MaterialTheme.typography.labelMedium, color = accent,
        )
        Text(months.lastOrNull()?.first?.substring(2) ?: "", style = MaterialTheme.typography.labelMedium, color = TextLow)
    }
}

@Composable
private fun BarRow(label: String, count: Int, max: Int, accent: Color) {
    // v26 · 条形生长动画
    val frac by androidx.compose.animation.core.animateFloatAsState(
        targetValue = count.toFloat() / max,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "bar",
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(10.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent)),
                    ),
            )
        }
        Text(
            "$count", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(44.dp).padding(start = 6.dp),
            color = TextMid,
        )
    }
}
