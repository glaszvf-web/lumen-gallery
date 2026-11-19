package com.lumen.gallery.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import kotlinx.coroutines.delay

/**
 * v23 · 幻灯片放映：Ken Burns 缓推 + 交叉淡入 + 液态玻璃控制条 + 设为壁纸
 */
@Composable
fun SlideshowScreen(items: List<MediaItem>, startIndex: Int, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    var intervalSec by remember { mutableIntStateOf(4) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(playing, intervalSec, items) {
        while (playing && items.isNotEmpty()) {
            delay(intervalSec * 1000L)
            index = (index + 1) % items.size
        }
    }

    val item = items.getOrNull(index) ?: items.firstOrNull() ?: return

    // v23 · Ken Burns：每张缓慢 1.0→1.12 缩放 + 微量平移（方向按 index 交替）
    val kb = rememberInfiniteTransition(label = "kenburns")
    val zoom by kb.animateFloat(
        1f, 1.12f,
        infiniteRepeatable(tween(intervalSec * 1000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "zoom",
    )
    val drift by kb.animateFloat(
        -10f, 10f,
        infiniteRepeatable(tween(intervalSec * 1000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val dir = if (index % 2 == 0) 1f else -1f
    // 交叉淡入
    var fade by remember { mutableStateOf(false) }
    val fadeAlpha by animateFloatAsState(
        if (fade) 1f else 0f,
        tween(600), label = "slideFade",
    )
    LaunchedEffect(item.uri) { fade = false; fade = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = fadeAlpha
                    scaleX = zoom; scaleY = zoom
                    translationX = drift * dir
                },
        )
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable(onClick = onExit).padding(10.dp))
            Spacer(Modifier.weight(1f))
            Text(
                "${index + 1} / ${items.size}",
                color = Color.White, style = MaterialTheme.typography.titleMedium,
            )
        }
        // v23 · 液态玻璃控制条
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.75f)),
                    ),
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.45f), Cyan.copy(alpha = 0.12f))),
                    RoundedCornerShape(18.dp),
                )
                .padding(16.dp),
        ) {
            Text(item.displayName, color = Color.White, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (playing) "⏸ 暂停" else "▶ 播放",
                    color = Cyan,
                    modifier = Modifier.clickable { playing = !playing }.padding(vertical = 6.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text("间隔 ${intervalSec}s", color = TextLow)
                Slider(
                    value = intervalSec.toFloat(),
                    onValueChange = { intervalSec = it.toInt() },
                    valueRange = 1f..15f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan),
                )
                Text("🖼 壁纸", color = Cyan,
                    modifier = Modifier.clickable {
                        val wm = WallpaperManager.getInstance(ctx)
                        val bmp = loadBitmapForWallpaper(ctx, item)
                        if (bmp != null) {
                            runCatching { wm.setBitmap(bmp) }
                            bmp.recycle()
                        }
                    }.padding(6.dp))
            }
        }
    }
}

private fun loadBitmapForWallpaper(ctx: Context, item: MediaItem): Bitmap? = try {
    ctx.contentResolver.openInputStream(item.uri)?.use { ins ->
        android.graphics.BitmapFactory.decodeStream(ins, null, android.graphics.BitmapFactory.Options().apply {
            inSampleSize = 2
        })
    }
} catch (e: Exception) { null }

/** v21 入口：从统计页/长按菜单进入幻灯片 */
@Composable
fun SlideshowLauncherRow(items: List<MediaItem>, onStart: (Int) -> Unit) {
    if (items.size < 2) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(com.lumen.gallery.ui.theme.Bg1)
            .clickable { onStart(0) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎞", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("幻灯片放映（${items.size} 张）", style = MaterialTheme.typography.titleMedium, color = TextHi)
            Text("全屏轮播 · 可调间隔 · 设为壁纸", style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        Text("→", color = Cyan)
    }
}
