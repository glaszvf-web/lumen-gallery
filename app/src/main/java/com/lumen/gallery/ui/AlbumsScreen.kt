package com.lumen.gallery.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.gallery.data.MediaItem
import com.lumen.gallery.ui.theme.Bg1
import com.lumen.gallery.ui.theme.Cyan
import com.lumen.gallery.ui.theme.Line
import com.lumen.gallery.ui.theme.TextHi
import com.lumen.gallery.ui.theme.TextLow

data class Bucket(val folder: String, val name: String, val count: Int, val cover: MediaItem?)

@Composable
fun AlbumsScreen(items: List<MediaItem>, onOpen: (String) -> Unit) {
    val buckets = remember2(items)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        Text("相册集", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 14.dp))
        Text("按文件夹归类 · 点进即达", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(buckets, key = { it.folder }) { b ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Bg1)
                        .clickable { onOpen(b.folder) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .background(Brush.linearGradient(listOf(Color(0xFF16233F), Bg1))),
                    ) {
                        b.cover?.let {
                            AsyncImage(
                                model = it.uri,
                                contentDescription = b.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("${b.count} 项", style = MaterialTheme.typography.labelMedium, color = TextHi)
                        }
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b.folder, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextLow)
                    }
                }
            }
        }
    }
}

@Composable
private fun remember2(items: List<MediaItem>): List<Bucket> = androidx.compose.runtime.remember(items) {
    items.groupBy { it.path.substringBeforeLast('/') }
        .map { (folder, list) ->
            Bucket(folder, folder.substringAfterLast('/'), list.size, list.firstOrNull())
        }
        .sortedByDescending { it.count }
}
