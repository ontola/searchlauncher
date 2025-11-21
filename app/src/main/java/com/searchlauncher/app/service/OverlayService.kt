package com.searchlauncher.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.searchlauncher.app.R
import com.searchlauncher.app.SearchLauncherApp
import com.searchlauncher.app.ui.MainActivity

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var leftEdgeView: View? = null
    private var rightEdgeView: View? = null

    private var initialX = 0f
    private var initialY = 0f
    private var hasMovedBack = false
    private var isTapCandidate = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SearchLauncherApp.NOTIFICATION_ID, createNotification())

        if (intent?.action == ACTION_SHOW_SEARCH) {
            launchSearchActivity()
        } else {
            setupEdgeDetector()
        }

        return START_STICKY
    }

    private fun launchSearchActivity() {
        val intent = Intent(this, com.searchlauncher.app.ui.SearchActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun setupEdgeDetector() {
        if (leftEdgeView != null) return

        leftEdgeView = createEdgeView(Gravity.START or Gravity.TOP)
        rightEdgeView = createEdgeView(Gravity.END or Gravity.TOP)
    }

    private fun createEdgeView(gravity: Int): View {
        val view = View(this)

        val params =
                WindowManager.LayoutParams(
                                60, // Width - increased to 60px for better detection
                                WindowManager.LayoutParams.MATCH_PARENT,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                } else {
                                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                                },
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply { this.gravity = gravity }

        val isLeft =
                (gravity and Gravity.START) == Gravity.START ||
                        (gravity and Gravity.LEFT) == Gravity.LEFT

        view.setOnTouchListener { _, event ->
            handleGesture(event, isLeft)
            true // consume event
        }

        windowManager.addView(view, params)
        return view
    }

    private fun handleGesture(event: MotionEvent, isLeft: Boolean): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                initialY = event.rawY
                hasMovedBack = false
                isTapCandidate = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTapCandidate) {
                    val dx = event.rawX - initialX
                    val dy = event.rawY - initialY
                    if (kotlin.math.hypot(dx, dy) > TAP_THRESHOLD_PX) {
                        isTapCandidate = false
                    }
                }

                val rawDelta = event.rawX - initialX
                // Normalize delta: Positive means moving "in" (away from edge)
                // For Left edge: +x is in
                // For Right edge: -x is in
                val deltaIn = if (isLeft) rawDelta else -rawDelta

                // 1. Swipe IN (away from edge)
                if (!hasMovedBack && deltaIn > SWIPE_THRESHOLD) {
                    hasMovedBack = true
                    isTapCandidate = false
                }
                // 2. Swipe OUT (back to edge)
                else if (hasMovedBack && deltaIn < SWIPE_THRESHOLD / 2) {
                    // User moved back to starting position
                    launchSearchActivity()
                    hasMovedBack = false
                    isTapCandidate = false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isTapCandidate) {
                    handleTap(event.rawX, event.rawY)
                }
                hasMovedBack = false
                isTapCandidate = false
            }
            MotionEvent.ACTION_CANCEL -> {
                hasMovedBack = false
                isTapCandidate = false
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        val scheduled = GestureAccessibilityService.performClick(x, y) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                setUntouchable(false)
            }, 200)
        }

        if (scheduled) {
            setUntouchable(true)
        }
    }

    private fun setUntouchable(untouchable: Boolean) {
        leftEdgeView?.let { updateViewFlags(it, untouchable) }
        rightEdgeView?.let { updateViewFlags(it, untouchable) }
    }

    private fun updateViewFlags(view: View, untouchable: Boolean) {
        val params = view.layoutParams as WindowManager.LayoutParams
        val flags = params.flags
        if (untouchable) {
            params.flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (params.flags != flags) {
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, SearchLauncherApp.NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(getString(R.string.service_notification_title))
                    .setContentText(getString(R.string.service_notification_desc))
                    .setSmallIcon(android.R.drawable.ic_menu_search)
                    .setContentIntent(pendingIntent)
                    .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                    .setContentTitle(getString(R.string.service_notification_title))
                    .setContentText(getString(R.string.service_notification_desc))
                    .setSmallIcon(android.R.drawable.ic_menu_search)
                    .setContentIntent(pendingIntent)
                    .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        leftEdgeView?.let { windowManager.removeView(it) }
        rightEdgeView?.let { windowManager.removeView(it) }
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning = false
        const val ACTION_SHOW_SEARCH = "com.searchlauncher.SHOW_SEARCH"
        const val ACTION_HIDE_SEARCH = "com.searchlauncher.HIDE_SEARCH"
        private const val SWIPE_THRESHOLD = 100f
        private const val TAP_THRESHOLD_PX = 20f
    }
}
