package com.lumen.gallery.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// —— 光析 LUMEN 科技感配色（v16 · 强调色可切换；v37 · OLED 纯黑可切，顶层 var 全局生效）——
var Cyan = Color(0xFF22D3EE)
var CyanDim = Color(0xFF0E7490)
var Violet = Color(0xFFA78BFA)
var Magenta = Color(0xFFF472B6)
val Amber = Color(0xFFFBBF24)
var Bg0 = Color(0xFF070B14)   // 最深层
var Bg1 = Color(0xFF0B1120)   // 卡片底
var Bg2 = Color(0xFF111A2E)   // 悬浮层
var Line = Color(0xFF1E2A44)  // 描边
val TextHi = Color(0xFFE8F1FF)
val TextMid = Color(0xFF9DB0D0)
val TextLow = Color(0xFF5C6B8A)
val Danger = Color(0xFFFB7185)
val Ok = Color(0xFF34D399)

// v37 · OLED 纯黑：背景全黑、描边弱化（OLED 省电 + 对比拉满）
private val Bg0Default = Color(0xFF070B14)
private val Bg1Default = Color(0xFF0B1120)
private val Bg2Default = Color(0xFF111A2E)
private val LineDefault = Color(0xFF1E2A44)

fun applyOledMode(enabled: Boolean) {
    if (enabled) {
        Bg0 = Color(0xFF000000)
        Bg1 = Color(0xFF050505)
        Bg2 = Color(0xFF0A0A0A)
        Line = Color(0xFF222222)
    } else {
        Bg0 = Bg0Default
        Bg1 = Bg1Default
        Bg2 = Bg2Default
        Line = LineDefault
    }
}

val Mono = FontFamily.Monospace

/** v16 · 强调色主题 */
data class AccentTheme(val name: String, val label: String, val accent: Color, val second: Color, val third: Color)

val ACCENT_THEMES = listOf(
    AccentTheme("cyan", "极地青", Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF472B6)),
    AccentTheme("violet", "星云紫", Color(0xFFA78BFA), Color(0xFF22D3EE), Color(0xFFF472B6)),
    AccentTheme("magenta", "霓虹粉", Color(0xFFF472B6), Color(0xFFA78BFA), Color(0xFF22D3EE)),
    AccentTheme("emerald", "量子绿", Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFFFBBF24)),
    AccentTheme("amber", "聚变橙", Color(0xFFFB923C), Color(0xFFFBBF24), Color(0xFFF472B6)),
)

private const val ACCENT_PREFS = "lumen_prefs"
private const val ACCENT_KEY = "accent"

fun currentAccentName(context: Context): String =
    context.getSharedPreferences(ACCENT_PREFS, Context.MODE_PRIVATE).getString(ACCENT_KEY, "cyan") ?: "cyan"

fun currentAccentName(): String = ACCENT_THEMES.first().name

fun saveAccent(context: Context, name: String) {
    context.getSharedPreferences(ACCENT_PREFS, Context.MODE_PRIVATE).edit().putString(ACCENT_KEY, name).apply()
}

fun applyAccent(name: String) {
    val t = ACCENT_THEMES.firstOrNull { it.name == name } ?: ACCENT_THEMES[0]
    Cyan = t.accent
    Violet = t.second
    Magenta = t.third
}

private val LumenScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Bg0,
    secondary = Violet,
    onSecondary = Bg0,
    background = Bg0,
    onBackground = TextHi,
    surface = Bg1,
    onSurface = TextHi,
    surfaceVariant = Bg2,
    onSurfaceVariant = TextMid,
    outline = Line,
    error = Danger,
)

val LumenType = Typography(
    displaySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = TextHi),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextHi),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextHi),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TextMid),
    bodySmall = TextStyle(fontSize = 12.sp, color = TextLow),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = TextLow),
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    // v26 · Material You 动态色（Android 12+ 跟随壁纸，fallback 到深空配色）
    val context = LocalContext.current
    val useDynamic = context.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)
        .getBoolean("material_you", false)

    val scheme = if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = dynamicDarkColorScheme(context)
        LumenScheme.copy(
            primary = dyn.primary,
            secondary = dyn.secondary,
            background = Bg0, onBackground = TextHi,
            surface = Bg1, onSurface = TextHi,
            surfaceVariant = Bg2, onSurfaceVariant = TextMid,
            outline = Line, error = Danger,
        )
    } else LumenScheme

    MaterialTheme(
        colorScheme = scheme,
        typography = LumenType,
        content = content,
    )
}
