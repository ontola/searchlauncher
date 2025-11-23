package com.searchlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class GestureAccessibilityService : AccessibilityService() {

    private var backPressCount = 0
    private var lastBackPressTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This service works in conjunction with the overlay service
        // to detect back gestures and trigger the search UI

        // Note: The actual gesture detection is primarily handled by the OverlayService
        // This accessibility service provides additional context about the current app state
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    companion object {
        private const val DOUBLE_BACK_TIME_DELTA = 500L // milliseconds
        private var instance: WeakReference<GestureAccessibilityService>? = null

        fun isConnected(): Boolean = instance?.get() != null

        fun performClick(x: Float, y: Float, onComplete: () -> Unit): Boolean {
            val service = instance?.get() ?: return false

            val path = Path()
            path.moveTo(x, y)

            // Create a tap gesture (short duration stroke)
            val builder = GestureDescription.Builder()
            val gestureDescription = builder
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()

            return service.dispatchGesture(gestureDescription, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    onComplete()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    onComplete()
                }
            }, null)
        }

        fun openNotifications(): Boolean {
            val service = instance?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }

        fun openQuickSettings(): Boolean {
            val service = instance?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        }
    }
}
