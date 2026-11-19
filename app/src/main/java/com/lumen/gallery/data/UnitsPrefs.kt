package com.lumen.gallery.data

import android.content.Context

/** v13 · 偏好存储（像素单位等） */
object UnitsPrefs {

    private const val PREFS = "lumen_prefs"
    private const val KEY_WAN = "pixel_unit_wan"

    @Volatile
    var useWan: Boolean = false
        private set

    fun init(context: Context) {
        useWan = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WAN, false)
    }

    fun setWan(context: Context, wan: Boolean) {
        useWan = wan
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WAN, wan).apply()
    }

    /** 像素量文本：默认 MP 简略风（50MP / 12.5MP），可切回万 */
    fun pixels(w: Int, h: Int): String = if (useWan) {
        "${(w.toLong() * h / 10000).coerceAtLeast(1)} 万像素"
    } else {
        val mp = w.toLong() * h / 1_000_000.0
        (if (mp >= 100) "%.0f".format(mp) else if (mp >= 10) "%.1f".format(mp) else "%.2f".format(mp)).trimEnd('.', '0').trimEnd('.') + " MP"
    }
}
