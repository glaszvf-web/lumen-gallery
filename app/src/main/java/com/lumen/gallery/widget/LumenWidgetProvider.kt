package com.lumen.gallery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.RemoteViews
import com.lumen.gallery.MainActivity
import com.lumen.gallery.R

/**
 * v27 · 桌面小部件：最新底片速览
 * 显示媒体库最新一张照片 + 总数；点击直达透视仪主界面。
 * 半小时自动刷新 + 应用每次打开时主动推送。
 */
class LumenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildRemoteViews(context))
    }

    companion object {
        /** 构建小部件视图：最新照片 + 总数 + 打开入口 */
        fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.lumen_widget)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            try {
                val cr = context.contentResolver
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                )
                // 总数
                var total = 0L
                cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null, null)
                    ?.use { c -> total = c.count.toLong() }
                // 最新一张（跳过系统解不了的 RAW/DNG，最多往前找 8 张）
                cr.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    var shown = 0
                    var found = 0
                    while (c.moveToNext() && found < 8 && shown == 0) {
                        found++
                        val id = c.getLong(0)
                        val name = c.getString(1) ?: ""
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        val bmp = decodeSmall(cr, uri)
                        if (bmp != null) {
                            views.setImageViewBitmap(R.id.widget_photo, bmp)
                            views.setTextViewText(R.id.widget_title, name)
                            shown = 1
                        }
                    }
                    views.setTextViewText(R.id.widget_stats, "图库共 $total 张 · 点击进入透视仪")
                }
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_stats, "媒体库暂不可读")
            }
            return views
        }

        /** 限尺寸解码（widget 用不了 27MB 的原图） */
        private fun decodeSmall(cr: android.content.ContentResolver, uri: Uri): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) null else {
                var sample = 1
                while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (_: Exception) { null }

        /** 应用打开时主动刷新全部小部件实例 */
        fun pushAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, LumenWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val views = buildRemoteViews(context)
                    ids.forEach { manager.updateAppWidget(it, views) }
                }
            } catch (_: Exception) { }
        }
    }
}
