package com.lumen.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet
import java.util.Calendar

/**
 * v29 · 回忆页：那年今天（跨年同月同日聚合，无命中自动回退到"这些年这个月"）
 */
@Composable
fun MemoriesScreen(vm: LumenViewModel) {
    val state by vm.state.collectAsState()
    // v60 · 回忆一键放映
    var showSlideshow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val day = now.get(Calendar.DAY_OF_MONTH)
    val year = now.get(Calendar.YEAR)

    val sameDay = remember(state.items, year, month, day) {
        state.items.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateAdded * 1000 }
            c.get(Calendar.MONTH) == month && c.get(Calendar.DAY_OF_MONTH) == day && c.get(Calendar.YEAR) < year
        }.sortedByDescending { it.dateAdded }
    }
    val sameMonth = remember(state.items, year, month) {
        state.items.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateAdded * 1000 }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) < year
        }.sortedByDescending { it.dateAdded }
    }
    val showFallback = sameDay.isEmpty()
    val list = if (showFallback) sameMonth else sameDay
    val byYear = remember(list) {
        list.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.dateAdded * 1000 }.get(Calendar.YEAR)
        }.toList().sortedByDescending { it.first }
    }

    if (showSlideshow && list.isNotEmpty()) {
        SlideshowScreen(items = list, startIndex = 0) { showSlideshow = false }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 液态玻璃头
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Violet.copy(alpha = 0.14f), Magenta.copy(alpha = 0.10f), Color.Transparent),
                    ),
                )
                .border(1.dp, Violet.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showFallback) "这些年 · ${month + 1} 月" else "那年今天 · ${month + 1} 月 ${day} 日",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (showFallback) "今天没有往年同日回忆，先翻翻这个月" else "${sameDay.size} 张来自往年的今天",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // v60 · 一键放映回忆
            Text(
                "▶ 放映",
                color = Violet, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = list.isNotEmpty()) { showSlideshow = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("🕰", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(4.dp))

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有往年回忆，拍下去就有了", color = TextLow, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            byYear.forEach { (y, photos) ->
                item(key = "y_$y", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brush.verticalGradient(listOf(Violet, Magenta))),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$y 年",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextHi, fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(Violet.copy(alpha = 0.35f), Color.Transparent)),
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${photos.size} 张", style = MaterialTheme.typography.labelMedium, color = TextLow)
                    }
                }
                items(photos, key = { "m_${it.id}" }) { item ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(com.lumen.gallery.ui.theme.Bg1)
                            .clickable { vm.open(item) },
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "${month + 1}.${day}",
                                color = TextHi,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
