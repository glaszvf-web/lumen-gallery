package com.lumen.gallery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumen.gallery.ui.theme.Bg2
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow

data class BottomTab(val key: Int, val icon: String, val label: String)

/** v17 · 液态玻璃浮岛底部导航 */
@Composable
fun BottomBar(current: Int, onSelect: (Int) -> Unit, tabs: List<BottomTab>) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Bg2.copy(alpha = 0.85f), Bg2.copy(alpha = 0.95f)),
                ),
            )
            .graphicsLayer { shadowElevation = 12f }
            .padding(vertical = 6.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            val selected = tab.key == current
            val scale by animateFloatAsState(
                if (selected) 1.12f else 0.92f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
                label = "tabScale",
            )
            val color by animateColorAsState(if (selected) Cyan else TextLow, label = "tabColor")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab.key) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                // 选中指示器（液态光条）
                Box(
                    Modifier
                        .width(if (selected) 24.dp else 0.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) Brush.horizontalGradient(listOf(Cyan, Cyan.copy(alpha = 0.3f)))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)),
                        ),
                )
                Text(tab.icon, style = MaterialTheme.typography.titleLarge, color = color)
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = color,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}
