package com.lumen.gallery.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.gallery.data.HistogramData
import com.lumen.gallery.data.MetaEntry
import com.lumen.gallery.data.MetaSection
import com.lumen.gallery.ui.theme.Amber
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Bg2
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Danger
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.Mono
import com.lumen.gallery.ui.theme.Ok
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet

/**
 * ═══════════════════════════════════════════════
 *  LUMEN 液态玻璃设计语言 v2
 *  半透明材质 · 内发光 · 弹性动画 · 品牌渐变
 * ═══════════════════════════════════════════════
 */

@Composable
fun BrandGradient(): Brush = Brush.linearGradient(listOf(Cyan, Violet, Magenta))

@Composable
fun BrandAccentGradient(): Brush = Brush.horizontalGradient(
    listOf(Cyan.copy(alpha = 0.35f), Violet.copy(alpha = 0.35f), Magenta.copy(alpha = 0.25f)),
)

/** 液态玻璃材质卡——多层阴影+渐变描边+内发光+弹性 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Cyan,
    glowing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glowPulse = if (glowing) {
        val t = rememberInfiniteTransition(label = "glow")
        val p by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "gp")
        p
    } else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Bg2.copy(alpha = 0.72f),
                        Bg1.copy(alpha = 0.88f),
                    ),
                ),
            )
            .border(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.28f * glowPulse),
                        Color.Transparent,
                        accent.copy(alpha = 0.08f),
                    ),
                ),
                RoundedCornerShape(20.dp),
            )
            .graphicsLayer {
                shadowElevation = 8.dp.toPx()
                shape = RoundedCornerShape(20.dp)
                clip = true
            },
    ) {
        // 品牌渐变顶条（液态光）
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.06f),
                            accent.copy(alpha = 0.9f),
                            accent.copy(alpha = 0.06f),
                        ),
                    ),
                ),
        )
        Column(Modifier.padding(14.dp)) { content() }
    }
}

/** 液态胶囊按钮（弹性缩放） */
@Composable
fun LiquidChip(
    text: String,
    selected: Boolean,
    accent: Color = Cyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "chip",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.3f), accent.copy(alpha = 0.12f)))
                else Brush.linearGradient(listOf(Bg1, Bg1)),
            )
            .border(
                1.2.dp,
                if (selected) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.4f)))
                else Brush.linearGradient(listOf(Line, Line)),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
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
fun SectionCard(section: MetaSection, accent: Color = Cyan, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f)))),
            )
            Spacer(Modifier.width(8.dp))
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(section.subtitle, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(10.dp))
        section.entries.forEachIndexed { idx, entry ->
            val appear = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                appear.animateTo(1f, tween(300, delayMillis = minOf(idx * 40, 400)))
            }
            Box(
                Modifier.graphicsLayer {
                    alpha = appear.value
                    translationX = -24f * (1f - appear.value)
                },
            ) { DataRow(entry) }
        }
    }
}

@Composable
fun DataRow(entry: MetaEntry) {
    // v49 · 术语点查：命中字典的行可点击弹白话解释
    val hit = remember(entry.label, entry.value) {
        com.lumen.gallery.data.Terms.matchIn(entry.label, entry.value).firstOrNull()
    }
    var showTerm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { m -> if (hit != null) m.clickable { showTerm = true } else m },
    ) {
        Text(
            entry.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (hit != null) Cyan else TextMid,
            ),
            modifier = Modifier.width(112.dp),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = if (entry.important) TextHi else TextMid,
                fontWeight = if (entry.important) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.weight(1f),
        )
    }
    if (showTerm && hit != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTerm = false },
            title = { Text("术语解释 · ${entry.label}", style = MaterialTheme.typography.titleMedium) },
            text = { Text(hit.plain, style = MaterialTheme.typography.bodyMedium, color = TextMid) },
            confirmButton = {
                Text("知道了", color = Cyan, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable { showTerm = false }
                        .padding(10.dp))
            },
        )
    }
}

@Composable
fun HistogramCanvas(hist: HistogramData, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width; val h = size.height
        for (i in 1..3) drawLine(Line, Offset(0f, h * i / 4), Offset(w, h * i / 4), 1f)
        for (i in 1..5) drawLine(Line.copy(alpha = 0.5f), Offset(w * i / 6, 0f), Offset(w * i / 6, h), 1f)
        val max = hist.max.toFloat()
        fun path(arr: IntArray, color: Color) {
            val p = Path()
            for (i in arr.indices) {
                val x = w * i / 255f
                val y = h - (arr[i] / max) * h * 0.92f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }
        path(hist.r, Danger.copy(alpha = 0.75f))
        path(hist.g, Ok.copy(alpha = 0.75f))
        path(hist.b, Cyan.copy(alpha = 0.75f))
    }
}

@Composable
fun PaletteRow(colors: List<Int>, modifier: Modifier = Modifier) {
    if (colors.isEmpty()) return
    Row(modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { c ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(c))
                    .border(1.dp, Line, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
fun ScanIndicator(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val progress by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "scanline",
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val w = size.width
            val p = Path()
            for (i in 0..100) {
                val x = w * i / 100f
                val y = size.height / 2 + (kotlin.math.sin((i / 8f) + progress * 6.28f) * 12f).toFloat() * 0.8f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, Cyan.copy(alpha = 0.85f), style = Stroke(1.8f, cap = StrokeCap.Round))
            drawLine(Violet, Offset(w * progress, 6f), Offset(w * progress, size.height - 6f), 2f)
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextLow)
    }
}

@Composable
fun PrivacyBadge(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Danger.copy(alpha = 0.10f))
            .border(1.dp, Danger.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", color = Amber, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = TextMid))
    }
}
