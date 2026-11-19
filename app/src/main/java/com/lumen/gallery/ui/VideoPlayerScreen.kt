package com.lumen.gallery.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lumen.gallery.ui.theme.Bg2
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextMid
import kotlinx.coroutines.delay

/**
 * v20 · 液态玻璃视频播放器（Media3 ExoPlayer）
 * 自绘控制层：玻璃浮岛 · 中央弹性播放键 · 双区手势（左亮度/右音量）· 自动隐藏
 */
@Composable
fun VideoPlayerScreen(uri: Uri, name: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsShown by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableLongStateOf(0L) }
    // 手势浮标
    var gestureHint by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // 位置轮询
    LaunchedEffect(player) {
        while (true) {
            if (!seeking) position = player.currentPosition
            delay(500)
        }
    }
    // 自动隐藏控制层
    LaunchedEffect(controlsShown, isPlaying) {
        if (controlsShown && isPlaying) {
            delay(3200)
            controlsShown = false
        }
    }

    val fmt: (Long) -> String = { ms ->
        val s = ms / 1000
        "%d:%02d".format(s / 60, s % 60)
    }

    // v20 · 单手势层：单击显隐 · 双击暂停 · 横向快进 · 左半上滑亮度/右半上滑音量
    var gestureMode by remember { mutableStateOf<Char?>(null) } // 'S'=seek 'B'=brightness 'V'=volume
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragAccX by remember { mutableFloatStateOf(0f) }
    var dragAccY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsShown = !controlsShown },
                    onDoubleTap = { if (isPlaying) player.pause() else player.play() },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (controlsShown) return@detectDragGestures
                        gestureMode = null
                        dragStartX = offset.x
                        dragAccX = 0f
                        dragAccY = 0f
                    },
                    onDragEnd = {
                        if (gestureMode == 'S') player.seekTo(seekTarget)
                        gestureMode = null
                        gestureHint = null
                        seeking = false
                    },
                    onDrag = { change, dragAmount ->
                        if (controlsShown) return@detectDragGestures
                        dragAccX += dragAmount.x
                        dragAccY += dragAmount.y
                        if (gestureMode == null) {
                            gestureMode = when {
                                kotlin.math.abs(dragAccX) > kotlin.math.abs(dragAccY) * 1.4f -> 'S'
                                else -> if (dragStartX < size.width / 2f) 'B' else 'V'
                            }
                            if (gestureMode == 'S') {
                                seeking = true
                                seekTarget = position
                            }
                        }
                        when (gestureMode) {
                            'S' -> {
                                seekTarget = (seekTarget + (dragAmount.x * 80L).toLong()).coerceIn(0L, duration)
                                gestureHint = "⏩ ${fmt(seekTarget)} / ${fmt(duration)}"
                            }
                            'B' -> {
                                val win = (view.context as? Activity)?.window
                                if (win != null) {
                                    val cur = win.attributes.screenBrightness
                                    val next = (if (cur < 0f) 0.5f else cur) - dragAmount.y / 2200f
                                    val clamped = next.coerceIn(0.02f, 1f)
                                    win.attributes = win.attributes.apply { screenBrightness = clamped }
                                    gestureHint = "☀ ${(clamped * 100).toInt()}%"
                                }
                            }
                            'V' -> {
                                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = (-dragAmount.y / 900f).toInt()
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (cur + delta).coerceIn(0, maxVol), 0)
                                val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                gestureHint = "🔊 $now/$maxVol"
                            }
                        }
                        change.consume()
                    },
                )
            },
    ) {
        // 播放画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // v20 自绘液态控制层
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 手势提示浮标
        gestureHint?.let { hint ->
            if (hint.isNotEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // 液态玻璃控制层
        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.78f))))
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.5f), Cyan.copy(alpha = 0.15f))),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // v41 · 截帧保存（当前画面入 Pictures/LUMEN 截帧）
                var frameMsg by remember { mutableStateOf<String?>(null) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "📸 截取当前帧保存", color = Cyan, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                frameMsg = "截帧中…"
                                Thread {
                                    val msg = try {
                                        val r = android.media.MediaMetadataRetriever()
                                        r.setDataSource(context, uri)
                                        val bmp = r.getFrameAtTime(position * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                                        r.release()
                                        if (bmp == null) "取帧失败"
                                        else {
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "LUMEN_frame_${System.currentTimeMillis()}.jpg")
                                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/LUMEN 截帧")
                                            }
                                            val cr = context.contentResolver
                                            val u = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                            if (u == null) "保存被拒绝"
                                            else {
                                                cr.openOutputStream(u)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it) }
                                                bmp.recycle()
                                                "已保存到 Pictures/LUMEN 截帧 ✓"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        "截帧失败：${e.message ?: e.javaClass.simpleName}"
                                    }
                                    frameMsg = msg
                                }.start()
                            }
                            .padding(vertical = 6.dp),
                    )
                    frameMsg?.let { Text(it, color = TextMid, style = MaterialTheme.typography.labelMedium) }
                }
                Slider(
                    value = if (seeking) seekTarget.toFloat() else position.toFloat(),
                    onValueChange = {
                        seeking = true
                        seekTarget = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(seekTarget)
                        position = seekTarget
                        seeking = false
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Cyan,
                        activeTrackColor = Cyan,
                        inactiveTrackColor = Bg2,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(if (seeking) seekTarget else position), style = MaterialTheme.typography.labelMedium, color = Cyan)
                    Text(fmt(duration), style = MaterialTheme.typography.labelMedium, color = TextMid)
                }
            }
        }

        // 中央播放键（点击画面区域唤出控制层后仍然可见的弹性播放态）
        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable { if (isPlaying) player.pause() else player.play() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isPlaying) "⏸" else "▶", color = Color.White, style = MaterialTheme.typography.displaySmall)
            }
        }

        // 顶栏
        AnimatedVisibility(
            visible = controlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "✕", color = Color.White, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .clickable {
                            player.stop()
                            onExit()
                        }
                        .padding(14.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    Text("v20 液态玻璃播放 · 左亮度 右音量 双击暂停", style = MaterialTheme.typography.bodySmall, color = TextMid)
                }
            }
        }
    }
}
