/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.bined.android.basic

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Default scroll pane for binary component.
 *
 * @author ExBin Project (https://exbin.org)
 */
open class DefaultCodeAreaScrollPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    @Volatile
    protected var scrollingByUser = false
    
    @Volatile
    protected var scrollingUpdate = false

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    init {
        init()
    }

    private fun init() {
        isScrollContainer = true
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
    }

    override fun getVerticalScrollbarPosition(): Int = scrollY

    fun getHorizontalScrollbarPosition(): Int = scrollX

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastTouchX = ev.x
            lastTouchY = ev.y
            isDragging = !scroller.isFinished
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            return false
        }

        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = kotlin.math.abs(ev.x - lastTouchX)
            val dy = kotlin.math.abs(ev.y - lastTouchY)
            if (dx > touchSlop || dy > touchSlop) {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }

        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker = (velocityTracker ?: VelocityTracker.obtain()).also { it.addMovement(event) }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                scrollingByUser = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y
                if (!isDragging) {
                    if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                        isDragging = true
                        scrollingByUser = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (isDragging) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    scrollBy(dx.toInt(), dy.toInt())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val vx = velocityTracker?.xVelocity?.toInt() ?: 0
                    val vy = velocityTracker?.yVelocity?.toInt() ?: 0
                    if (kotlin.math.abs(vx) > minimumFlingVelocity || kotlin.math.abs(vy) > minimumFlingVelocity) {
                        fling(-vx, -vy)
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                scrollingByUser = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                scrollingByUser = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun fling(velocityX: Int, velocityY: Int) {
        val child = getChildAt(0)
        val maxX = kotlin.math.max(0, (child?.width ?: 0) - width)
        val maxY = kotlin.math.max(0, (child?.height ?: 0) - height)
        scroller.fling(scrollX, scrollY, velocityX, velocityY, 0, maxX, 0, maxY)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollingUpdate = true
            scrollTo(scroller.currX, scroller.currY)
            scrollingUpdate = false
            postInvalidateOnAnimation()
        }
    }

    override fun scrollTo(x: Int, y: Int) {
        val child = getChildAt(0)
        val maxX = kotlin.math.max(0, (child?.width ?: 0) - width)
        val maxY = kotlin.math.max(0, (child?.height ?: 0) - height)
        val clampedX = x.coerceIn(0, maxX)
        val clampedY = y.coerceIn(0, maxY)
        super.scrollTo(clampedX, clampedY)
    }

    fun updateScrollBars(verticalScrollValue: Int, horizontalScrollValue: Int) {
        scrollingUpdate = true
        scrollTo(horizontalScrollValue, verticalScrollValue)
//        awakenScrollBars()
        scrollingUpdate = false
    }

    fun isScrollingByUser(): Boolean = scrollingByUser

    fun isScrollingUpdate(): Boolean = scrollingUpdate
}