package com.searchlauncher.app.service

import android.os.SystemClock
import android.view.MotionEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class GestureHandler(private val searchWindowManager: SearchWindowManager) {

    private var initialX = 0f
    private var initialY = 0f
    private var hasMovedBack = false
    private val swipeThreshold = 100f // Assuming a value for the test

    fun handleGesture(event: MotionEvent, isLeft: Boolean): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                hasMovedBack = false
            }

            MotionEvent.ACTION_MOVE -> {
                val rawDelta = event.rawX - initialX
                val deltaIn = if (isLeft) rawDelta else -rawDelta

                if (!hasMovedBack && deltaIn > swipeThreshold) {
                    hasMovedBack = true
                } else if (hasMovedBack && deltaIn < swipeThreshold / 2) {
                    searchWindowManager.show()
                    hasMovedBack = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasMovedBack = false
            }
        }
        return true
    }
}

interface SearchWindowManager {
    fun show()
}

@RunWith(RobolectricTestRunner::class)
class OverlayServiceTest {
    private lateinit var searchWindowManager: SearchWindowManager
    private lateinit var gestureHandler: GestureHandler

    @Before
    fun setup() {
        searchWindowManager = mockk()
        // Allow .show() to be called without doing anything
        every { searchWindowManager.show() } just Runs

        gestureHandler = GestureHandler(searchWindowManager)
    }

    @Test
    fun `Left Edge - Swipe IN then OUT triggers show`() {
        // 1. Touch Down at X=0 (Left Edge)
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_DOWN, 0f),
            isLeft = true
        )

        // 2. Swipe IN (Rightwards) past threshold (100f) -> X=150
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_MOVE, 150f),
            isLeft = true
        )

        // 3. Swipe OUT (Back to Left) below half threshold (50f) -> X=20
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_MOVE, 20f),
            isLeft = true
        )

        // Verify show() was called exactly once
        verify(exactly = 1) { searchWindowManager.show() }
    }

    @Test
    fun `Right Edge - Swipe IN then OUT triggers show`() {
        val screenWidth = 1000f

        // 1. Touch Down at Right Edge (X=1000)
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_DOWN, screenWidth),
            isLeft = false
        )

        // 2. Swipe IN (Leftwards) past threshold.
        // Delta must be positive "In". So raw X must decrease.
        // 1000 - 150 = 850. Delta is -150. deltaIn = 150 (> 100)
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_MOVE, 850f),
            isLeft = false
        )

        // 3. Swipe OUT (Back to Right).
        // Back near edge. X = 980. Delta is -20. deltaIn = 20 (< 50)
        gestureHandler.handleGesture(
            createEvent(MotionEvent.ACTION_MOVE, 980f),
            isLeft = false
        )

        verify(exactly = 1) { searchWindowManager.show() }
    }

    @Test
    fun `Swipe IN but RELEASE does NOT trigger show`() {
        // 1. Down
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_DOWN, 0f), true)

        // 2. Move In (Armed)
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_MOVE, 150f), true)

        // 3. Release (Up)
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_UP, 150f), true)

        // Should verify that show() was NEVER called
        verify(exactly = 0) { searchWindowManager.show() }
    }

    @Test
    fun `Small jitter movements do NOT trigger`() {
        // 1. Down
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_DOWN, 0f), true)

        // 2. Move In slightly (Not crossing threshold of 100)
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_MOVE, 50f), true)

        // 3. Move Back
        gestureHandler.handleGesture(createEvent(MotionEvent.ACTION_MOVE, 0f), true)

        verify(exactly = 0) { searchWindowManager.show() }
    }

    private fun createEvent(action: Int, x: Float, y: Float = 0f): MotionEvent {
        val time = SystemClock.uptimeMillis()
        return MotionEvent.obtain(time, time, action, x, y, 0)
    }

}