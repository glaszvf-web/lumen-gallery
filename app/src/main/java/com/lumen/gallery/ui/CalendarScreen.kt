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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import java.util.Calendar

/**
 * v23 · 日历视图：月历网格，每天显示照片数，点日期跳到当天照片
 */
@Composable
fun CalendarScreen(vm: LumenViewModel) {
    val state by vm.state.collectAsState()
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) // 0-based
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 该月 1 号是星期几（周日=1）
    val firstCal = Calendar.getInstance().apply { set(year, month, 1) }
    val firstDow = firstCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday

    val byDay = remember(state.items) {
        state.items.groupBy { item ->
            val c = Calendar.getInstance().apply { timeInMillis = item.dateAdded * 1000 }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) c.get(Calendar.DAY_OF_MONTH) else 0
        }.filterKeys { it > 0 }
    }
    val dayThumbs = remember(state.items) {
        state.items.groupBy { item ->
            val c = Calendar.getInstance().apply { timeInMillis = item.dateAdded * 1000 }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month) c.get(Calendar.DAY_OF_MONTH) else 0
        }.mapValues { (_, list) -> list.firstOrNull() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(14.dp),
    ) {
        // v24 · 液态玻璃月历头
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Cyan.copy(alpha = 0.12f), com.lumen.gallery.ui.theme.Violet.copy(alpha = 0.10f), Color.Transparent),
                    ),
                )
                .border(1.dp, Cyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$year 年 ${month + 1} 月", style = MaterialTheme.typography.titleLarge)
                Text("${state.items.size} 张媒体 · 本月 ${byDay.values.sumOf { it.size }} 张", style = MaterialTheme.typography.bodySmall)
            }
            Text("📅", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        // 星期表头
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextLow)
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items((0 until firstDow).toList(), key = { "pad$it" }) { Box(Modifier.size(44.dp)) }
            items((1..daysInMonth).toList(), key = { "d$it" }) { day ->
                val count = byDay[day]?.size ?: 0
                val thumb = dayThumbs[day]
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (count > 0) Bg1 else Color.Transparent)
                        .clickable(enabled = count > 0) {
                            // 跳到当天第一张
                            byDay[day]?.firstOrNull()?.let { vm.open(it) }
                        },
                ) {
                    thumb?.let { t ->
                        coil.compose.AsyncImage(
                            model = t.uri,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // 角标计数
                    if (count > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Cyan),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$count", style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF070B14),
                            )
                        }
                    }
                    Text(
                        "$day",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day in 1..5 || (firstDow + day) % 7 in 1..5) TextMid else TextLow,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("点击有照片的日期直接透视当天照片", style = MaterialTheme.typography.bodySmall, color = TextLow)
    }
}
