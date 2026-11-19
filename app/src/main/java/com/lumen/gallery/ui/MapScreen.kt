package com.lumen.gallery.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.Magenta
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow
import com.lumen.gallery.ui.theme.TextMid
import com.lumen.gallery.ui.theme.Violet

/**
 * v20 · 地图模式：等距圆柱投影世界地图 + GPS 照片打点 + 位置聚类列表
 * 离线工作：经纬网自绘 + 内置大城市坐标标注
 */
data class CityPin(val name: String, val lat: Double, val lon: Double)

val CITIES = listOf(
    CityPin("北京", 39.9, 116.4), CityPin("上海", 31.2, 121.5), CityPin("广州", 23.1, 113.3),
    CityPin("深圳", 22.5, 114.1), CityPin("成都", 30.7, 104.1), CityPin("杭州", 30.3, 120.2),
    CityPin("西安", 34.3, 108.9), CityPin("武汉", 30.6, 114.3), CityPin("重庆", 29.6, 106.5),
    CityPin("香港", 22.3, 114.2), CityPin("台北", 25.0, 121.6), CityPin("东京", 35.7, 139.7),
    CityPin("首尔", 37.6, 127.0), CityPin("新加坡", 1.35, 103.8), CityPin("曼谷", 13.8, 100.5),
    CityPin("伦敦", 51.5, -0.13), CityPin("巴黎", 48.9, 2.35), CityPin("柏林", 52.5, 13.4),
    CityPin("莫斯科", 55.8, 37.6), CityPin("纽约", 40.7, -74.0), CityPin("洛杉矶", 34.05, -118.2),
    CityPin("旧金山", 37.8, -122.4), CityPin("多伦多", 43.7, -79.4), CityPin("悉尼", -33.9, 151.2),
    CityPin("墨尔本", -37.8, 145.0), CityPin("迪拜", 25.2, 55.3), CityPin("新德里", 28.6, 77.2),
    CityPin("开罗", 30.0, 31.2), CityPin("开普敦", -33.9, 18.4), CityPin("圣保罗", -23.6, -46.6),
    CityPin("墨西哥城", 19.4, -99.1), CityPin("罗马", 41.9, 12.5), CityPin("阿姆斯特丹", 52.4, 4.9),
    CityPin("梅州", 24.3, 116.1), CityPin("汕头", 23.35, 116.7), CityPin("厦门", 24.5, 118.1),
    CityPin("福州", 26.1, 119.3), CityPin("长沙", 28.2, 113.0), CityPin("昆明", 25.0, 102.7),
    CityPin("拉萨", 29.7, 91.1), CityPin("乌鲁木齐", 43.8, 87.6), CityPin("哈尔滨", 45.8, 126.5),
)

/** 简化大陆轮廓点阵（equirectangular 采样，经纬度）——只做示意 */
val LANDMASSES = listOf(
    // 欧亚大陆
    listOf(doubleArrayOf(60.0, 5.0), doubleArrayOf(70.0, 60.0), doubleArrayOf(75.0, 100.0), doubleArrayOf(70.0, 140.0),
        doubleArrayOf(60.0, 160.0), doubleArrayOf(45.0, 135.0), doubleArrayOf(30.0, 122.0), doubleArrayOf(20.0, 110.0),
        doubleArrayOf(10.0, 100.0), doubleArrayOf(8.0, 80.0), doubleArrayOf(20.0, 70.0), doubleArrayOf(35.0, 45.0),
        doubleArrayOf(40.0, 30.0), doubleArrayOf(45.0, 15.0), doubleArrayOf(38.0, -8.0), doubleArrayOf(50.0, -5.0)),
    // 非洲
    listOf(doubleArrayOf(35.0, -6.0), doubleArrayOf(32.0, 20.0), doubleArrayOf(30.0, 32.0), doubleArrayOf(12.0, 40.0),
        doubleArrayOf(0.0, 42.0), doubleArrayOf(-15.0, 40.0), doubleArrayOf(-30.0, 32.0), doubleArrayOf(-34.0, 20.0),
        doubleArrayOf(-20.0, 15.0), doubleArrayOf(-5.0, 10.0), doubleArrayOf(5.0, -5.0), doubleArrayOf(20.0, -15.0)),
    // 北美
    listOf(doubleArrayOf(70.0, -160.0), doubleArrayOf(70.0, -100.0), doubleArrayOf(60.0, -70.0), doubleArrayOf(48.0, -60.0),
        doubleArrayOf(30.0, -80.0), doubleArrayOf(25.0, -97.0), doubleArrayOf(18.0, -95.0), doubleArrayOf(15.0, -92.0),
        doubleArrayOf(30.0, -115.0), doubleArrayOf(48.0, -125.0), doubleArrayOf(60.0, -145.0)),
    // 南美
    listOf(doubleArrayOf(12.0, -72.0), doubleArrayOf(8.0, -60.0), doubleArrayOf(-5.0, -40.0), doubleArrayOf(-20.0, -40.0),
        doubleArrayOf(-35.0, -55.0), doubleArrayOf(-52.0, -70.0), doubleArrayOf(-30.0, -72.0), doubleArrayOf(-10.0, -78.0),
        doubleArrayOf(0.0, -80.0)),
    // 澳洲
    listOf(doubleArrayOf(-12.0, 131.0), doubleArrayOf(-18.0, 122.0), doubleArrayOf(-33.0, 115.0), doubleArrayOf(-35.0, 138.0),
        doubleArrayOf(-30.0, 153.0), doubleArrayOf(-20.0, 149.0), doubleArrayOf(-14.0, 142.0)),
)

@Composable
fun MapScreen(vm: LumenViewModel) {
    val state by vm.state.collectAsState()
    // 从缓存/快速 EXIF 拿 GPS（分析过的照片才有）
    val geoPhotos = remember(state.items) {
        state.items.mapNotNull { item ->
            vm.resultOf(item)?.sections
                ?.firstOrNull { it.subtitle == "GEOTAG" }
                ?.entries?.firstOrNull { it.label == "GPS 纬度" }?.value?.let { latStr ->
                    val lat = Regex("（(-?[0-9.]+)）").find(latStr)?.groupValues?.get(1)?.toDoubleOrNull()
                    vm.resultOf(item)?.sections?.firstOrNull { it.subtitle == "GEOTAG" }
                        ?.entries?.firstOrNull { it.label == "GPS 经度" }?.value?.let { lonStr ->
                            val lon = Regex("（(-?[0-9.]+)）").find(lonStr)?.groupValues?.get(1)?.toDoubleOrNull()
                            if (lat != null && lon != null) Triple(item, lat, lon) else null
                        }
                }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // v22 · 液态玻璃标题面板
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Cyan.copy(alpha = 0.12f), Violet.copy(alpha = 0.10f), Color.Transparent),
                    ),
                )
                .border(1.dp, Cyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("地图模式", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${geoPhotos.size} 张已解析 GPS 照片 · 打开透视页可累积坐标",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("🌍", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(4.dp))
        // 世界地图画布（v22 · 打点呼吸脉冲）
        val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "mapPulse").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1800),
                androidx.compose.animation.core.RepeatMode.Restart,
            ),
            label = "pulse",
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Bg1)
                .border(1.dp, Line, RoundedCornerShape(16.dp)),
        ) {
            val w = size.width; val h = size.height
            fun project(lat: Double, lon: Double): Offset = Offset(
                ((lon + 180.0) / 360.0 * w).toFloat(),
                ((90.0 - lat) / 180.0 * h).toFloat(),
            )
            // 经纬网
            for (lon in -180..180 step 30) {
                val x = ((lon + 180.0) / 360.0 * w).toFloat()
                drawLine(Line, Offset(x, 0f), Offset(x, h), 1f)
            }
            for (lat in -90..90 step 30) {
                val y = ((90.0 - lat) / 180.0 * h).toFloat()
                drawLine(Line, Offset(0f, y), Offset(w, y), 1f)
            }
            // 大陆轮廓
            val paint = androidx.compose.ui.graphics.drawscope.Stroke(2f)
            LANDMASSES.forEach { poly ->
                val path = androidx.compose.ui.graphics.Path()
                poly.forEachIndexed { i, ll ->
                    val p = project(ll[0], ll[1])
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, TextLow.copy(alpha = 0.6f), style = paint)
            }
            // 大城市
            CITIES.forEach { city ->
                val p = project(city.lat, city.lon)
                drawCircle(TextLow, 3f, p)
            }
            // GPS 照片打点（洋红光点 + 呼吸脉冲光环）
            geoPhotos.forEach { (_, lat, lon) ->
                val p = project(lat, lon)
                drawCircle(Magenta.copy(alpha = (1f - pulse) * 0.45f), 12f + pulse * 16f, p)
                drawCircle(Magenta, 12f, p)
                drawCircle(Color.White, 4f, p)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 位置聚类列表
        val clusters = remember(geoPhotos) {
            geoPhotos.groupBy { (_, lat, lon) ->
                CITIES.minByOrNull { c ->
                    val dx = c.lon - lon; val dy = c.lat - lat
                    dx * dx + dy * dy
                }?.name ?: "%.0f°,%.0f°".format(lat, lon)
            }.toList().sortedByDescending { it.second.size }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
            if (clusters.isEmpty()) {
                item {
                    Text(
                        "还没有带 GPS 的已解析照片 —— 在透视页打开带定位的照片即可累积",
                        style = MaterialTheme.typography.bodySmall, color = TextLow,
                    )
                }
            }
            clusters.forEach { (key, list) ->
                item(key = key) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Cyan.copy(alpha = 0.10f), Bg1.copy(alpha = 0.92f)),
                                ),
                            )
                            .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .clickable { vm.open(list.first().first) }
                            .padding(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Cyan, Magenta)),
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(key, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${list.size} 张 · ${"%.3f".format(list.first().second)}, ${"%.3f".format(list.first().third)}",
                                style = MaterialTheme.typography.bodySmall, color = TextLow,
                            )
                        }
                        Text("${list.size} ▸", color = Cyan, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}


