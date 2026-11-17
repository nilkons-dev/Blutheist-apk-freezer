package com.ankan.netfreeze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/** Loads app icons in the background as small bitmaps and caches them. */
object IconLoader {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun get(pkg: String): Bitmap? = cache.get(pkg)

    fun load(ctx: Context, pkg: String, sizePx: Int, onLoaded: (Bitmap) -> Unit) {
        val app = ctx.applicationContext
        pool.execute {
            try {
                val bmp: Bitmap = cache.get(pkg) ?: render(app, pkg, sizePx).also { cache.put(pkg, it) }
                main.post { onLoaded(bmp) }
            } catch (e: Throwable) {
                // ignore, the placeholder stays
            }
        }
    }

    private fun render(ctx: Context, pkg: String, size: Int): Bitmap {
        val pm = ctx.packageManager
        val d: Drawable = try {
            pm.getApplicationIcon(pkg)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }
}
