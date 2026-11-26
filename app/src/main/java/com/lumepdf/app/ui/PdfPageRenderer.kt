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
 * Uses a bounded LruCache so scrolling stays smooth without bloating memory.
 */
class PdfPageRenderer private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    val pageCount: Int = renderer.pageCount

    private val mutex = Mutex()
    private val pageAspectRatios = arrayOfNulls<Float>(pageCount)

    private val maxCacheBytes: Int = run {
        val maxMem = Runtime.getRuntime().maxMemory().toInt()
        (maxMem / 10).coerceIn(6 * 1024 * 1024, 24 * 1024 * 1024)
    }

    private val cache = object : LruCache<Int, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    fun getCachedPage(pageIndex: Int): Bitmap? = cache.get(pageIndex)

    fun getAspectRatio(pageIndex: Int): Float? = pageAspectRatios.getOrNull(pageIndex)

    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
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
                        val ratio = page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        pageAspectRatios[pageIndex] = ratio

                        val renderWidth = targetWidthPx.coerceIn(480, 1440)
                        val renderHeight = (renderWidth * ratio).toInt().coerceIn(200, 2600)

                        val bitmap = Bitmap.createBitmap(
                            renderWidth,
                            renderHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        cache.put(pageIndex, bitmap)
                        bitmap
                    }
                }.getOrNull()
            }
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
