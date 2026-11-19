package com.lumen.gallery.ui

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.gallery.data.UnitsPrefs
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet

/** v13 · 设置页 */
@Composable
fun SettingsScreen(vm: LumenViewModel) {
    val ctx = LocalContext.current
    var wan by remember { mutableStateOf(UnitsPrefs.useWan) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 14.dp))

        // —— 像素单位 ——
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("像素量单位", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (wan) "当前：1258 万像素（中文习惯）" else "当前：12.5 MP（简略 · 默认）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = wan,
                    onCheckedChange = {
                        wan = it
                        UnitsPrefs.setWan(ctx, it)
                        vm.clearCaches()
                        (ctx as? android.app.Activity)?.recreate()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— v37 · OLED 纯黑模式 ——
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OLED 纯黑模式", style = MaterialTheme.typography.titleMedium)
                    Text("背景全黑 · 省电护眼 · 对比拉满（重启生效）", style = MaterialTheme.typography.bodySmall)
                }
                var oled by remember {
                    mutableStateOf(ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE).getBoolean("oled_mode", false))
                }
                Switch(
                    checked = oled,
                    onCheckedChange = {
                        oled = it
                        ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("oled_mode", it).apply()
                        (ctx as? android.app.Activity)?.recreate()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— v45 · 默认启动页 ——
        GlassCard {
            Text("默认启动页", style = MaterialTheme.typography.titleMedium)
            Text("冷启动后直接进入的页面", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            val tabs = listOf("🖼 相册", "📚 相册集", "🕰 回忆")
            var sel by remember {
                mutableStateOf(ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE).getInt("default_tab", 0))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tabs.forEachIndexed { idx, label ->
                    val selected = sel == idx
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Cyan.copy(alpha = 0.30f), Violet.copy(alpha = 0.25f)),
                                ) else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Bg1, Bg1)),
                            )
                            .border(
                                1.dp,
                                if (selected) Cyan else Line,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable {
                                sel = idx
                                ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                                    .edit().putInt("default_tab", idx).apply()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) TextHi else TextMid,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— v55 · 极客模式 ——
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("极客模式", style = MaterialTheme.typography.titleMedium)
                    Text("RAW/EXIF 条目标签前显示原始 tag 号（0x829A · 快门速度）", style = MaterialTheme.typography.bodySmall)
                }
                var geek by remember {
                    mutableStateOf(ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE).getBoolean("geek_mode", false))
                }
                Switch(
                    checked = geek,
                    onCheckedChange = {
                        geek = it
                        ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("geek_mode", it).apply()
                        vm.clearCaches()
                        (ctx as? android.app.Activity)?.recreate()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— 强调色 ——
        GlassCard {
            Text("强调色主题（重启生效）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.lumen.gallery.ui.theme.ACCENT_THEMES.forEach { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(t.accent, t.second, t.third),
                                    ),
                                )
                                .clickable {
                                    com.lumen.gallery.ui.theme.saveAccent(ctx, t.name)
                                    com.lumen.gallery.ui.theme.applyAccent(t.name)
                                    (ctx as? android.app.Activity)?.recreate()
                                }
                                .let { m ->
                                    if (com.lumen.gallery.ui.theme.currentAccentName(ctx) == t.name)
                                        m.border(2.dp, TextHi, CircleShape) else m
                                },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— 缓存管理 ——
        GlassCard {
            Text("缓存管理", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("透视结果与 AI 索引缓存；清空后首次浏览会重新解析。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Bg1)
                        .clickable {
                            vm.clearCaches()
                            com.lumen.gallery.data.AiIndexStore.clear(ctx)
                            vm.rebuildAiIndex()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🧹 清空全部分析缓存", style = MaterialTheme.typography.labelMedium, color = TextHi)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— Material You 动态色 ——
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Material You 动态色", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android 12+ 跟随系统壁纸配色（重启生效）",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                var my by remember {
                    mutableStateOf(
                        ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                            .getBoolean("material_you", false),
                    )
                }
                Switch(
                    checked = my,
                    onCheckedChange = {
                        my = it
                        ctx.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("material_you", it).apply()
                        (ctx as? android.app.Activity)?.recreate()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // —— 隐私与权限说明 ——
        GlassCard {
            Text("隐私与权限", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "· 全程离线工作，不申请 INTERNET 权限，任何数据不出设备。\n" +
                    "· READ_MEDIA_IMAGES/VIDEO：读取媒体库展示与解析。\n" +
                    "· ACCESS_MEDIA_LOCATION：缺少它时系统会在文件层抹除 GPS，透视仪需要真实数据；可在系统设置随时撤销。\n" +
                    "· 脱敏导出的副本存于 Pictures/LUMEN 脱敏，可随时删除。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))

        // —— 关于 ——
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("关于 光析 LUMEN", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("v${com.lumen.gallery.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Cyan)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "中文照片元数据透视仪 · 深度解析 JPEG / PNG / GIF / WebP / BMP / HEIC / AVIF / DNG·CR2·NEF·ARW 等 RAW / CR3 佳能容器 / MP4·HEVC 视频流。\n" +
                    "AI 取证：ComfyUI / Stable Diffusion / InvokeAI。\n" +
                    "工具箱：收藏星标 · 那年今天 · 批量改名/脱敏/缩放/水印/格式转换/收藏管理 · 哈希CSV · PDF合成 · ZIP打包 · JSON导出 · RAW显影预览 · CR3字节级脱敏 · 视频截帧 · 桌面Widget · 外部分享直透 · OLED纯黑 · 默认启动页 · 术语速读/点查 · 极客模式。\n" +
                    "灵感致谢：Aves · ExifTool tag 字典 · dng_sdk 码表。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}
