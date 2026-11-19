package com.lumen.gallery.ui

import android.graphics.BitmapFactory
import android.graphics.Color as GColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.data.MetaEntry
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Danger
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.Ok
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid

/**
 * v25 · 双图对比模式：左右并排 + 元数据 diff（相同项绿、不同项橙）
 */
@Composable
fun CompareScreen(itemA: MediaItem, itemB: MediaItem, reportA: List<MetaEntry>, reportB: List<MetaEntry>, onExit: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", color = TextHi, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable(onClick = onExit).padding(10.dp))
            Text("双图对比", style = MaterialTheme.typography.titleLarge, color = TextHi, modifier = Modifier.weight(1f))
        }
        // 双图并排
        Row(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(itemA, itemB).forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg1)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                            }
                        },
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale),
                    )
                    Text(
                        item.displayName.take(16),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextHi,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // diff 列表
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("元数据 diff", style = MaterialTheme.typography.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
            // v57 · 复制 diff 报告
            var copiedDiff by remember { mutableStateOf(false) }
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            Text(
                if (copiedDiff) "已复制 ✓" else "📋 复制报告",
                color = Cyan, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clickable {
                        val sb = StringBuilder(
                            "【光析 LUMEN 对比报告】A=${itemA.displayName} / B=${itemB.displayName}\n",
                        )
                        val la = reportA.associate { it.label to it.value }
                        val lb = reportB.associate { it.label to it.value }
                        (la.keys + lb.keys).distinct().sorted().forEach { l ->
                            val va = la[l] ?: "（无）"; val vb = lb[l] ?: "（无）"
                            sb.appendLine("$l: A=$va" + if (va != vb) " | B=$vb" else "")
                        }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(sb.toString()))
                        copiedDiff = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val labelsA = reportA.associate { it.label to it.value }
            val labelsB = reportB.associate { it.label to it.value }
            val allLabels = (labelsA.keys + labelsB.keys).distinct().sorted()
            allLabels.forEach { label ->
                val va = labelsA[label] ?: "（无）"
                val vb = labelsB[label] ?: "（无）"
                val same = va == vb
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                va == "（无）" || vb == "（无）" -> Danger.copy(alpha = 0.08f)
                                !same -> com.lumen.gallery.ui.theme.Amber.copy(alpha = 0.08f)
                                else -> Ok.copy(alpha = 0.06f)
                            }
                        )
                        .padding(8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text("A: $va", style = MaterialTheme.typography.bodySmall, color = TextMid,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!same) {
                        Text("B: $vb", style = MaterialTheme.typography.bodySmall,
                            color = if (same) TextMid else com.lumen.gallery.ui.theme.Amber,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 从两张图的 report 提取元数据条目 */
fun extractReportEntries(result: com.lumen.gallery.data.AnalysisResult): List<MetaEntry> =
    result.sections.flatMap { it.entries }
