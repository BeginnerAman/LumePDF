package com.lumepdf.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe, low-RAM wrapper over Android's native PdfRenderer.
 * Supports crisp landscape rendering and fast optional whitespace margin cropping.
 */
class PdfPageRenderer private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    val pageCount: Int = renderer.pageCount

    private val mutex = Mutex()
    private val pageAspectRatios = arrayOfNulls<Float>(pageCount)

    private val maxCacheBytes: Int = run {
        val maxMem = Runtime.getRuntime().maxMemory()
        (maxMem / 6L).coerceIn(16L * 1024L * 1024L, 48L * 1024L * 1024L).toInt()
    }

    private val cache = object : LruCache<Int, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    fun getCachedPage(pageIndex: Int): Bitmap? = cache.get(pageIndex)

    fun getAspectRatio(pageIndex: Int): Float? = pageAspectRatios.getOrNull(pageIndex)

    fun clearCache() {
        cache.evictAll()
        pageAspectRatios.fill(null)
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        cropMargins: Boolean
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null
        cache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        mutex.withLock {
            cache.get(pageIndex)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }

            runCatching {
                renderer.openPage(pageIndex).use { page ->
                    val rawRatio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val renderWidth = targetWidthPx.coerceIn(600, 2200)
                    val renderHeight = (renderWidth * rawRatio).toInt().coerceIn(200, 3400)

                    val rawBitmap = Bitmap.createBitmap(
                        renderWidth,
                        renderHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(rawBitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(
                        rawBitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    val finalBitmap = if (cropMargins) {
                        trimWhiteMargins(rawBitmap)
                    } else {
                        rawBitmap
                    }

                    pageAspectRatios[pageIndex] =
                        finalBitmap.height.toFloat() / finalBitmap.width.coerceAtLeast(1).toFloat()
                    cache.put(pageIndex, finalBitmap)
                    finalBitmap
                }
            }.getOrNull()
        }
    }

    private fun trimWhiteMargins(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        // Scan row-by-row with a tiny single-row buffer (~8 KB) to avoid multi-MB OOM spikes
        val rowPixels = IntArray(w)

        val step = 8
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        var y = 0
        while (y < h) {
            src.getPixels(rowPixels, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = rowPixels[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < 245 || g < 245 || b < 245) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }

        if (maxX <= minX || maxY <= minY) return src

        val padX = (w * 0.025f).toInt().coerceAtLeast(12)
        val padY = (h * 0.02f).toInt().coerceAtLeast(12)

        val left = (minX - padX).coerceAtLeast(0)
        val top = (minY - padY).coerceAtLeast(0)
        val right = (maxX + step + padX).coerceAtMost(w)
        val bottom = (maxY + step + padY).coerceAtMost(h)

        val cropW = right - left
        val cropH = bottom - top

        // Only crop if it removes meaningful blank margin (> 6% saved)
        if (cropW < 120 || cropH < 120 || (cropW > w * 0.94f && cropH > h * 0.94f)) {
            return src
        }

        val cropped = Bitmap.createBitmap(src, left, top, cropW, cropH)
        if (cropped !== src) {
            src.recycle()
        }
        return cropped
    }

    fun close() {
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        suspend fun open(context: Context, uri: Uri, filePath: String?): Result<PdfPageRenderer> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val pfd = if (!filePath.isNullOrBlank() && File(filePath).canRead()) {
                        ParcelFileDescriptor.open(
                            File(filePath),
                            ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?: error("Cannot open PDF file")
                    }
                    val renderer = PdfRenderer(pfd)
                    PdfPageRenderer(pfd, renderer)
                }
            }
    }
}
