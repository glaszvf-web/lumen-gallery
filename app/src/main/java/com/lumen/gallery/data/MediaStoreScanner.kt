package com.lumen.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaStoreScanner {

    fun scan(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DURATION,
        )
        // 图片
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val size = c.getLong(4)
                if (size <= 0) continue
                items.add(MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    path = c.getString(2) ?: "",
                    displayName = c.getString(1) ?: "未命名",
                    mime = c.getString(3) ?: "image/*",
                    size = size,
                    width = c.getInt(5),
                    height = c.getInt(6),
                    dateAdded = c.getLong(7),
                    dateModified = c.getLong(8),
                    isVideo = false,
                ))
            }
        }
        // 视频
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val size = c.getLong(4)
                if (size <= 0) continue
                val dur = if (!c.isNull(9)) c.getLong(9) else null
                items.add(MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    path = c.getString(2) ?: "",
                    displayName = c.getString(1) ?: "未命名",
                    mime = c.getString(3) ?: "video/*",
                    size = size,
                    width = c.getInt(5),
                    height = c.getInt(6),
                    dateAdded = c.getLong(7),
                    dateModified = c.getLong(8),
                    isVideo = true,
                    durationMs = dur,
                ))
            }
        }
        items.sortByDescending { it.dateAdded }
        return items
    }
}

object FormatUi {
    fun fmtSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
        bytes >= 1L shl 20 -> "%.2f MB".format(bytes / 1048576.0)
        bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun fmtDuration(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }

    fun aspectRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "未知"
        val g = gcd(w, h)
        val a = w / g; val b = h / g
        return if (a <= 64 && b <= 64) "$a : $b" else "%.2f : 1".format(w.toDouble() / h)
    }

    fun megapixels(w: Int, h: Int): String = "%.1f 百万像素".format(w * h / 1000000.0)

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
