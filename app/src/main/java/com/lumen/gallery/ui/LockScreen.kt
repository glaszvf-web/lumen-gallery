package com.lumen.gallery.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Danger
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import java.security.MessageDigest

/**
 * v22 · 应用锁（PIN，SHA-256 存储）
 * 首次启动设置 PIN，之后每次冷启动验证
 */
object PinStore {
    private const val PREFS = "lumen_lock"
    fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

    fun hasPin(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("pin")

    fun save(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("pin", hash(pin)).apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pin", "") ?: ""
        return hash(pin) == saved
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pin").apply()
    }
}

/**
 * v24 · 液态玻璃 PIN 锁屏：数字玻璃键盘 + 弹性点阵指示 + 错误抖动
 * firstTime 两段式：先输 PIN（4~8 位）→ 点"→"进确认段 → 长度对齐即校验
 */
@Composable
fun LockScreen(context: Context, firstTime: Boolean, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(0) } // 0=输入 pin，1=确认
    var error by remember { mutableStateOf<String?>(null) }
    var shaking by remember { mutableStateOf(false) }
    val shake by animateFloatAsState(if (shaking) 1f else 0f, tween(400), label = "shake")
    LaunchedEffect(shaking) {
        if (shaking) {
            kotlinx.coroutines.delay(420)
            shaking = false
            pin = ""; confirm = ""; phase = 0
        }
    }

    fun submit() {
        if (firstTime) {
            when {
                pin.length < 4 -> error = "PIN 至少 4 位"
                pin != confirm -> { error = "两次输入不一致"; shaking = true }
                else -> { PinStore.save(context, pin); onUnlocked() }
            }
        } else {
            if (PinStore.verify(context, pin)) onUnlocked()
            else { error = "PIN 错误"; shaking = true }
        }
    }

    fun onDigit(d: String) {
        error = null
        if (firstTime && phase == 1) {
            if (confirm.length < pin.length) {
                confirm += d
                if (confirm.length == pin.length) submit()
            }
        } else if (pin.length < 8) {
            pin += d
            if (!firstTime && pin.length >= 4) submit()
        }
    }

    fun onBackspace() {
        error = null
        if (firstTime && phase == 1 && confirm.isNotEmpty()) confirm = confirm.dropLast(1)
        else if (firstTime && phase == 1 && confirm.isEmpty()) phase = 0
        else pin = pin.dropLast(1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0B1120), Color(0xFF111A2E))),
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .graphicsLayer { translationX = kotlin.math.sin(shake * 6.28f * 3f) * 14f * shake },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔒", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                if (firstTime) (if (phase == 0) "设置 4~8 位 PIN" else "再次输入确认")
                else "输入 PIN 解锁",
                style = MaterialTheme.typography.titleLarge, color = TextHi,
            )
            Text("光析 LUMEN 隐私保护", style = MaterialTheme.typography.bodySmall, color = TextLow)
            Spacer(Modifier.height(22.dp))
            // 点阵指示（确认段用紫色区分）
            val shownLen = if (firstTime && phase == 1) confirm.length else pin.length
            val dotAccent = if (firstTime && phase == 1) com.lumen.gallery.ui.theme.Violet else Cyan
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(if (firstTime && phase == 1) pin.length else 8) { i ->
                    val filled = i < shownLen
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (filled) dotAccent else Color.Transparent)
                            .border(1.dp, if (filled) dotAccent else TextLow.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(22.dp))
            // 玻璃数字键盘（按压弹性）
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (firstTime) "→" else "·", "0", "⌫")
            keys.chunked(3).forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowKeys.forEach { k ->
                        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            if (pressed) 0.88f else 1f,
                            spring(dampingRatio = 0.5f, stiffness = 700f), label = "key",
                        )
                        Box(
                            Modifier
                                .size(64.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Cyan.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape)
                                .clickable(interactionSource = interaction, indication = null) {
                                    when (k) {
                                        "→" -> if (firstTime && pin.length >= 4) { phase = 1; error = null }
                                        "⌫" -> onBackspace()
                                        "·" -> {}
                                        else -> onDigit(k)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k,
                                style = if (k.length == 1) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.titleMedium,
                                color = when {
                                    k.length > 1 && k != "⌫" && k != "·" -> com.lumen.gallery.ui.theme.Violet
                                    k.length == 1 -> TextHi
                                    else -> TextLow
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (firstTime) {
                TextButton(onClick = onUnlocked) { Text("跳过（之后可在设置开启）", color = TextLow) }
            }
        }
    }
}
