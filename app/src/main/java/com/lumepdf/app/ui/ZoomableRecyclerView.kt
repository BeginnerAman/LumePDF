package com.lumepdf.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight RecyclerView with jump-free pinch-to-zoom, double-tap zoom, and pan.
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

    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false
    private var wasPinching = false
    private var cancelledChildScroll = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                wasPinching = true
                isPanning = false
                return true
            }

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

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                wasPinching = true
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

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        if (scaleFactor > 1.02f || e.pointerCount > 1) {
            return true
        }
        return super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        gestureDetector.onTouchEvent(e)

        // While pinching with 2+ fingers, lock RecyclerView scrolling so it never jerks
        if (scaleDetector.isInProgress || e.pointerCount > 1) {
            if (!cancelledChildScroll) {
                val cancel = MotionEvent.obtain(e).apply { action = MotionEvent.ACTION_CANCEL }
                super.onTouchEvent(cancel)
                cancel.recycle()
                cancelledChildScroll = true
            }
            wasPinching = true
            isPanning = false
            return true
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = e.getPointerId(0)
                lastTouchX = e.x
                lastTouchY = e.y
                isPanning = scaleFactor > 1.02f
                wasPinching = false
                cancelledChildScroll = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = e.actionIndex
                val liftedId = e.getPointerId(liftedIndex)
                if (liftedId == activePointerId && e.pointerCount > 1) {
                    val newIndex = if (liftedIndex == 0) 1 else 0
                    activePointerId = e.getPointerId(newIndex)
                    lastTouchX = e.getX(newIndex)
                    lastTouchY = e.getY(newIndex)
                }
                wasPinching = true
                isPanning = false
                if (scaleFactor > 1.02f) return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = e.findPointerIndex(activePointerId).let {
                    if (it >= 0) it else 0
                }
                val x = e.getX(pointerIndex)
                val y = e.getY(pointerIndex)

                // First single-finger move after pinch simply syncs coordinates with zero jump
                if (wasPinching) {
                    lastTouchX = x
                    lastTouchY = y
                    wasPinching = false
                    isPanning = scaleFactor > 1.02f
                    if (scaleFactor > 1.02f) return true
                }

                if (scaleFactor > 1.02f) {
                    if (!isPanning) {
                        isPanning = true
                        lastTouchX = x
                        lastTouchY = y
                        return true
                    }

                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y

                    val prevPanY = panY
                    panX += dx
                    panY += dy
                    clampPan()
                    invalidate()

                    val consumedY = panY - prevPanY
                    val remainingDy = dy - consumedY
                    if (abs(remainingDy) > 0.5f) {
                        scrollBy(0, (-remainingDy / scaleFactor).toInt())
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                wasPinching = false
                cancelledChildScroll = false
                if (scaleFactor > 1.02f) return true
            }
        }

        return super.onTouchEvent(e)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampPan()
        invalidate()
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
        if (scaleFactor <= 1.001f || width <= 0 || height <= 0) {
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
