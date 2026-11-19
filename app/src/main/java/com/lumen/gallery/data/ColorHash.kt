package com.lumen.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.InputStream
import java.security.MessageDigest

object ColorHash {

    /**
     * 文件指纹：MD5/SHA-1 仅用于去重比对与文件溯源（业界通用做法，与 ExifTool 一致），
     * 完整性校验以 SHA-256/SHA-512 为准。
     */
    fun hashes(path: String): List<MetaEntry> {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sha512 = MessageDigest.getInstance("SHA-512")
        java.io.File(path).inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                for (d in listOf(md5, sha1, sha256, sha512)) d.update(buf, 0, n)
            }
        }
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        return listOf(
            MetaEntry("MD5（去重指纹）", hex(md5.digest())),
            MetaEntry("SHA-1（去重指纹）", hex(sha1.digest())),
            MetaEntry("SHA-256", hex(sha256.digest()), important = true),
            MetaEntry("SHA-512", hex(sha512.digest())),
        )
    }

    /** 降采样解码，避免大图 OOM */
    fun decodeSmall(path: String, target: Int = 256): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= target && opts.outHeight / (sample * 2) >= target) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, o2)
    }

    fun histogram(bmp: Bitmap): HistogramData {
        val r = IntArray(256); val g = IntArray(256); val b = IntArray(256)
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (p in pixels) {
            r[Color.red(p)]++; g[Color.green(p)]++; b[Color.blue(p)]++
        }
        return HistogramData(r, g, b)
    }

    data class ColorStats(val brightness: Double, val saturation: Double, val palette: List<Int>, val isGray: Boolean)

    fun analyze(bmp: Bitmap): ColorStats {
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var lum = 0.0; var sat = 0.0; var grayCount = 0
        val step = maxOf(1, pixels.size / 20000)
        var sampled = 0
        val samples = mutableListOf<Int>()
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val rr = Color.red(p); val gg = Color.green(p); val bb = Color.blue(p)
            lum += 0.2126 * rr + 0.7152 * gg + 0.0722 * bb
            val mx = maxOf(rr, gg, bb); val mn = minOf(rr, gg, bb)
            sat += if (mx == 0) 0.0 else (mx - mn).toDouble() / mx
            if (mx - mn < 12) grayCount++
            if (i % step == 0 && samples.size < 4096) samples.add(p)
            sampled++
            i += step
        }
        val brightness = if (sampled == 0) 0.0 else lum / sampled / 255.0
        val saturation = if (sampled == 0) 0.0 else sat / sampled
        val isGray = grayCount.toDouble() / maxOf(1, sampled) > 0.95
        return ColorStats(brightness, saturation, kmeans(samples, 5), isGray)
    }

    /** 简易 k-means 主色调提取 */
    private fun kmeans(pixels: List<Int>, k: Int, iterations: Int = 10): List<Int> {
        if (pixels.isEmpty()) return emptyList()
        // 初始中心：均匀取样
        val centers = (0 until k).map { i -> pixels[(i.toLong() * pixels.size / k).toInt().coerceIn(0, pixels.size - 1)] }.toMutableList()
        val buckets = IntArray(k)
        val sums = Array(k) { LongArray(3) }
        repeat(iterations) {
            java.util.Arrays.fill(buckets, 0)
            for (s in sums) java.util.Arrays.fill(s, 0L)
            for (p in pixels) {
                var best = 0; var bestD = Long.MAX_VALUE
                for (c in centers.indices) {
                    val cr = Color.red(centers[c]); val cg = Color.green(centers[c]); val cb = Color.blue(centers[c])
                    val d = (Color.red(p) - cr).toLong() * (Color.red(p) - cr) +
                        (Color.green(p) - cg).toLong() * (Color.green(p) - cg) +
                        (Color.blue(p) - cb).toLong() * (Color.blue(p) - cb)
                    if (d < bestD) { bestD = d; best = c }
                }
                buckets[best]++
                sums[best][0] += Color.red(p); sums[best][1] += Color.green(p); sums[best][2] += Color.blue(p)
            }
            for (c in centers.indices) {
                if (buckets[c] > 0) {
                    centers[c] = Color.rgb(
                        (sums[c][0] / buckets[c]).toInt().coerceIn(0, 255),
                        (sums[c][1] / buckets[c]).toInt().coerceIn(0, 255),
                        (sums[c][2] / buckets[c]).toInt().coerceIn(0, 255),
                    )
                }
            }
        }
        // 按占比排序
        return centers.indices
            .filter { buckets[it] > 0 }
            .sortedByDescending { buckets[it] }
            .map { centers[it] }
    }
}
