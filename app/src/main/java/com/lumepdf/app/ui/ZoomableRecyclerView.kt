package com.lumepdf.app.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight RecyclerView with jump-free pinch-to-zoom, double-tap zoom,
 * sub-pixel panning, and smooth 2D OverScroller fling momentum when zoomed.
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
    private var scrollRemainderY = 0f
    private var isPanning = false
    private var wasPinching = false
    private var cancelledChildScroll = false

    private val scroller = OverScroller(context)
    private val viewConfig = ViewConfiguration.get(context)
    private var velocityTracker: VelocityTracker? = null
    private var lastFlingX = 0
    private var lastFlingY = 0

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scroller.forceFinished(true)
                recycleVelocityTracker()
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
                scroller.forceFinished(true)
                scrollRemainderY = 0f
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
            recycleVelocityTracker()
            wasPinching = true
            isPanning = false
            return true
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.abortAnimation()
                stopScroll()
                scrollRemainderY = 0f

                activePointerId = e.getPointerId(0)
                lastTouchX = e.x
                lastTouchY = e.y
                isPanning = scaleFactor > 1.02f
                wasPinching = false
                cancelledChildScroll = false

                if (isPanning) {
                    obtainVelocityTracker().addMovement(e)
                }
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
                recycleVelocityTracker()
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
                    scrollRemainderY = 0f
                    wasPinching = false
                    isPanning = scaleFactor > 1.02f
                    if (isPanning) {
                        obtainVelocityTracker().addMovement(e)
                        return true
                    }
                }

                if (scaleFactor > 1.02f) {
                    if (!isPanning) {
                        isPanning = true
                        lastTouchX = x
                        lastTouchY = y
                        obtainVelocityTracker().addMovement(e)
                        return true
                    }

                    velocityTracker?.addMovement(e)
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y

                    applyZoomedDelta(dx, dy)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (scaleFactor > 1.02f && isPanning) {
                    val vt = velocityTracker
                    if (vt != null) {
                        vt.addMovement(e)
                        vt.computeCurrentVelocity(
                            1000,
                            viewConfig.scaledMaximumFlingVelocity.toFloat()
                        )
                        val vx = vt.getXVelocity(activePointerId)
                        val vy = vt.getYVelocity(activePointerId)
                        val minFling = viewConfig.scaledMinimumFlingVelocity.toFloat()
                        if (abs(vx) > minFling || abs(vy) > minFling) {
                            lastFlingX = 0
                            lastFlingY = 0
                            scroller.fling(
                                0,
                                0,
                                vx.toInt(),
                                vy.toInt(),
                                Int.MIN_VALUE,
                                Int.MAX_VALUE,
                                Int.MIN_VALUE,
                                Int.MAX_VALUE
                            )
                            postInvalidateOnAnimation()
                        }
                    }
                    recycleVelocityTracker()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    isPanning = false
                    wasPinching = false
                    cancelledChildScroll = false
                    return true
                }
                recycleVelocityTracker()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                wasPinching = false
                cancelledChildScroll = false
            }

            MotionEvent.ACTION_CANCEL -> {
                recycleVelocityTracker()
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                wasPinching = false
                cancelledChildScroll = false
                if (scaleFactor > 1.02f) return true
            }
        }

        return super.onTouchEvent(e)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val curX = scroller.currX
            val curY = scroller.currY
            val dx = (curX - lastFlingX).toFloat()
            val dy = (curY - lastFlingY).toFloat()
            lastFlingX = curX
            lastFlingY = curY

            if (scaleFactor > 1.02f) {
                val moved = applyZoomedDelta(dx, dy)
                if (moved) {
                    invalidate()
                    postInvalidateOnAnimation()
                } else {
                    scroller.abortAnimation()
                }
            } else {
                scroller.abortAnimation()
            }
        }
    }

    /**
     * Applies horizontal pan and seamless sub-pixel vertical scroll + edge pan.
     * Returns true if either horizontal pan or vertical scroll changed.
     */
    private fun applyZoomedDelta(dx: Float, dy: Float): Boolean {
        val oldPanX = panX
        val oldPanY = panY

        panX += dx
        panY += dy
        clampPan()

        val consumedX = abs(panX - oldPanX) > 0.05f
        val consumedPanY = panY - oldPanY
        val remainingDy = dy - consumedPanY

        var scrolledY = false
        if (abs(remainingDy) > 0.01f) {
            scrollRemainderY += (-remainingDy / scaleFactor)
            val stepY = scrollRemainderY.toInt()
            if (stepY != 0) {
                val canMoveY = (stepY > 0 && canScrollVertically(1)) ||
                    (stepY < 0 && canScrollVertically(-1))
                if (canMoveY) {
                    scrollBy(0, stepY)
                    scrolledY = true
                }
                scrollRemainderY -= stepY
            } else if ((remainingDy < 0 && canScrollVertically(1)) ||
                (remainingDy > 0 && canScrollVertically(-1))
            ) {
                // Keep flinging while accumulating sub-pixel scroll
                scrolledY = true
            }
        }

        return consumedX || abs(consumedPanY) > 0.05f || scrolledY
    }

    private fun obtainVelocityTracker(): VelocityTracker {
        return velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    fun resetZoom() {
        scroller.forceFinished(true)
        recycleVelocityTracker()
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        scrollRemainderY = 0f
        isPanning = false
        wasPinching = false
        invalidate()
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

    override fun onDetachedFromWindow() {
        scroller.abortAnimation()
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }
}
