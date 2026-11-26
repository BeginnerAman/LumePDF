package com.lumepdf.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight RecyclerView with smooth pinch-to-zoom, double-tap zoom, and pan.
 */
class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var onSingleTap: (() -> Unit)? = null

    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                val newScale = (scaleFactor * detector.scaleFactor).coerceIn(1f, 3.5f)
                if (newScale != oldScale) {
                    val ratio = newScale / oldScale
                    panX = detector.focusX - (detector.focusX - panX) * ratio
                    panY = detector.focusY - (detector.focusY - panY) * ratio
                    scaleFactor = newScale
                    clampPan()
                    invalidate()
                }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap?.invoke()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val oldScale = scaleFactor
                val targetScale = if (scaleFactor > 1.25f) 1f else 2.2f
                val ratio = targetScale / oldScale
                panX = e.x - (e.x - panX) * ratio
                panY = e.y - (e.y - panY) * ratio
                scaleFactor = targetScale
                clampPan()
                invalidate()
                return true
            }
        }
    )

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        gestureDetector.onTouchEvent(e)

        if (scaleFactor > 1.02f && !scaleDetector.isInProgress) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = e.x
                    lastTouchY = e.y
                    isPanning = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isPanning && e.pointerCount == 1) {
                        val dx = e.x - lastTouchX
                        val dy = e.y - lastTouchY
                        lastTouchX = e.x
                        lastTouchY = e.y

                        val prevPanY = panY
                        panX += dx
                        panY += dy
                        clampPan()
                        invalidate()

                        // Only scroll the RecyclerView vertically if vertical pan reached its edge
                        val consumedY = panY - prevPanY
                        val remainingDy = dy - consumedY
                        if (kotlin.math.abs(remainingDy) > 0.5f) {
                            scrollBy(0, (-remainingDy).toInt())
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPanning = false
                }
            }
        } else if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchX = e.x
            lastTouchY = e.y
        }

        return super.onTouchEvent(e)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (scaleFactor > 1.001f) {
            canvas.save()
            canvas.translate(panX, panY)
            canvas.scale(scaleFactor, scaleFactor)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
    }

    private fun clampPan() {
        if (scaleFactor <= 1.001f) {
            scaleFactor = 1f
            panX = 0f
            panY = 0f
            return
        }
        val minPanX = -(width * (scaleFactor - 1f))
        val minPanY = -(height * (scaleFactor - 1f))
        panX = max(minPanX, min(0f, panX))
        panY = max(minPanY, min(0f, panY))
    }
}
