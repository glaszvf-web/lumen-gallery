package com.lumen.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.data.PhotoEditor
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v21 · 照片编辑器：滤镜预设 + 旋转/镜像/亮度/对比度/饱和度/色温 + 保存副本
 */
private data class FilterPreset(val name: String, val state: PhotoEditor.EditState)

private val FILTER_PRESETS = listOf(
    FilterPreset("原图", PhotoEditor.EditState()),
    FilterPreset("黑白", PhotoEditor.EditState(saturation = 0f)),
    FilterPreset("复古棕", PhotoEditor.EditState(saturation = 0.6f, temperature = 0.3f, brightness = 0.05f)),
    FilterPreset("冷调", PhotoEditor.EditState(temperature = -0.35f)),
    FilterPreset("暖阳", PhotoEditor.EditState(temperature = 0.35f)),
    FilterPreset("鲜艳", PhotoEditor.EditState(saturation = 1.5f, contrast = 1.1f)),
    FilterPreset("电影", PhotoEditor.EditState(saturation = 0.85f, contrast = 1.2f, temperature = -0.15f, brightness = -0.03f)),
)

@Composable
fun EditorScreen(item: MediaItem, onExit: () -> Unit) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(PhotoEditor.EditState()) }
    var presetName by remember { mutableStateOf("原图") }
    var baseBmp by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveMsg by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(item.path) {
        baseBmp = withContext(Dispatchers.IO) { PhotoEditor.decode(item.path, 1600) }
        loading = false
    }
    val preview = remember(state, baseBmp) {
        baseBmp?.let { if (state.isEdited) PhotoEditor.apply(it, state) else it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clickable(onClick = onExit).padding(12.dp))
            Column(Modifier.weight(1f)) {
                Text("编辑 · ${item.displayName}", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("修改不会覆盖原图，另存副本", color = TextMid, style = MaterialTheme.typography.bodySmall)
            }
        }
        // 预览
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> Text("解码中…", color = TextMid)
                preview != null -> Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Text("无法解码此图（RAW 请先导出 JPEG）", color = TextMid)
            }
        }
        // 控制区（v21 液态玻璃面板）
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            com.lumen.gallery.ui.theme.Bg2.copy(alpha = 0.9f),
                            Color(0xFF0B1120),
                        ),
                    ),
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Cyan.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                    androidx.compose.ui.graphics.RectangleShape,
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // v21 · 滤镜预设胶囊行
            Text("滤镜预设", color = com.lumen.gallery.ui.theme.TextLow, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FILTER_PRESETS.forEach { p ->
                    LiquidChip(
                        text = p.name,
                        selected = presetName == p.name,
                        onClick = {
                            presetName = p.name
                            state = p.state
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 几何按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { state = state.copy(rotation = (state.rotation + 90) % 360) },
                    modifier = Modifier.weight(1f),
                ) { Text("↻ 旋转 90°", color = TextHi) }
                androidx.compose.material3.OutlinedButton(
                    onClick = { state = state.copy(mirrorH = !state.mirrorH) },
                    modifier = Modifier.weight(1f),
                ) { Text("⇋ 镜像", color = TextHi) }
            }
            Spacer(Modifier.height(6.dp))
            SliderRow("亮度", state.brightness / 1f, -0.5f..0.5f) {
                presetName = "自定义"
                state = state.copy(brightness = it)
            }
            SliderRow("对比度", state.contrast, 0.5f..1.5f) {
                presetName = "自定义"
                state = state.copy(contrast = it)
            }
            SliderRow("饱和度", state.saturation, 0f..2f) {
                presetName = "自定义"
                state = state.copy(saturation = it)
            }
            SliderRow("色温", state.temperature, -0.5f..0.5f) {
                presetName = "自定义"
                state = state.copy(temperature = it)
            }
            TextButton(onClick = {
                presetName = "原图"
                state = PhotoEditor.EditState()
            }) { Text("↺ 重置全部", color = TextMid) }
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Button(
                onClick = {
                    if (saving) return@Button
                    saving = true
                    val bmp = preview
                    val app = ctx.applicationContext
                    Thread {
                        val src = bmp ?: PhotoEditor.apply(
                            PhotoEditor.decode(item.path, 4096) ?: return@Thread, state,
                        )
                        val (ok, message) = PhotoEditor.save(app, src, item.displayName)
                        saveMsg = message
                        saving = false
                    }.start()
                },
                enabled = !saving && baseBmp != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "保存中…" else "💾 保存编辑副本", style = MaterialTheme.typography.titleMedium)
            }
            saveMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = if (it.startsWith("已保存")) com.lumen.gallery.ui.theme.Ok else com.lumen.gallery.ui.theme.Danger,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = TextMid, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
    }
    Spacer(Modifier.height(2.dp))
}
