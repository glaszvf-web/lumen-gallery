package com.lumen.gallery.data

import android.content.Context
import android.content.SharedPreferences

/**
 * v28 · 收藏夹：本地星标（path 集合，JSON 持久化）
 * 收藏以文件路径为键——MediaStore id 会因重扫变化，路径在同文件场景最稳
 */
object FavoritesStore {
    private const val PREFS = "lumen_favorites"
    private const val KEY = "fav_paths"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    fun save(context: Context, paths: Set<String>) {
        prefs(context).edit().putStringSet(KEY, paths).apply()
    }
}
