package com.statusbar.ios

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Owns the single transparent overlay window. Uses TYPE_ACCESSIBILITY_OVERLAY,
 * which an AccessibilityService is allowed to add without the SYSTEM_ALERT_WINDOW
 * permission or a "drawing over other apps" prompt.
 *
 * Shade detection: systemui's window class name changes when the notification
 * shade expands/collapses. Class names below are the AOSP names as of Android
 * 15; OEM skins (Motorola's MyUX included) can rename these, so this is a
 * heuristic, not a guaranteed API. If it misbehaves on a given device, capture
 * `adb shell dumpsys accessibility` while pulling the shade down and update
 * SHADE_CLASS_HINTS / STATUSBAR_CLASS_HINTS to match what you see.
 */
class StatusBarAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: StatusBarOverlayView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var stateProvider: SystemStateProvider
    private var config: StatusBarConfig = Presets.iphone16()

    private var overlayAdded = false
    private var shadeExpanded = false

    private val handler = Handler(Looper.getMainLooper())

    private val tickRunnable = object : Runnable {
        override fun run() {
            // Cheap: only repaints the clock text region's containing view.
            // Real value changes (battery/wifi/signal) arrive via SystemStateProvider,
            // this just keeps the on-screen minute/second display current.
            if (overlayAdded) overlayView.invalidate()
            val intervalMs = if (config.time.showSeconds) 1000L else 15_000L
            handler.postDelayed(this, intervalMs)
        }
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON && PrefsManager.isEnabled(ctx) && !shadeExpanded) {
                showOverlay()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        config = PrefsManager.getActiveConfig(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayView = StatusBarOverlayView(this).apply {
            this.config = this@StatusBarAccessibilityService.config
            editMode = false // never intercepts touches on the real overlay
        }

        stateProvider = SystemStateProvider(this).apply {
            onChanged = { newState -> handler.post { overlayView.state = newState } }
        }
        stateProvider.start()

        buildParams()
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))

        if (PrefsManager.isEnabled(this)) showOverlay()
        handler.post(tickRunnable)
    }

    private fun buildParams() {
        val heightPx = dpToPx(config.barHeight).toInt().coerceAtLeast(1)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or   // touches pass through
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Draw in the very top strip only -- underlying app layout is untouched
            // because this is a separate overlay window, not a layout inset.
        }
    }

    fun showOverlay() {
        if (overlayAdded) return
        try {
            windowManager.addView(overlayView, params)
            overlayAdded = true
        } catch (_: Exception) { /* window not attachable yet (e.g. service still initializing) */ }
    }

    fun hideOverlay() {
        if (!overlayAdded) return
        try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        overlayAdded = false
    }

    /** Call after the user edits settings so the live overlay reflects the change immediately. */
    fun refreshConfig() {
        config = PrefsManager.getActiveConfig(this)
        overlayView.config = config
        buildParams()
        if (overlayAdded) {
            try { windowManager.updateViewLayout(overlayView, params) } catch (_: Exception) {}
        }
    }

    fun setEnabled(enabled: Boolean) {
        PrefsManager.setEnabled(this, enabled)
        if (enabled && !shadeExpanded) showOverlay() else if (!enabled) hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != "com.android.systemui") return
        val cls = event.className?.toString() ?: return

        if (SHADE_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }) {
            shadeExpanded = true
            hideOverlay() // real Android status bar + shade take over immediately
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            STATUSBAR_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }
        ) {
            if (shadeExpanded) {
                shadeExpanded = false
                if (PrefsManager.isEnabled(this)) showOverlay()
            }
        }
    }

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / density change -> recompute geometry once layout settles.
        handler.postDelayed({ refreshConfig() }, 200)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        stateProvider.stop()
        try { unregisterReceiver(screenOnReceiver) } catch (_: Exception) {}
        hideOverlay()
        instance = null
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    companion object {
        var instance: StatusBarAccessibilityService? = null
        fun isRunning(): Boolean = instance != null

        private val SHADE_CLASS_HINTS = listOf(
            "NotificationShadeWindowView", "NotificationPanelView",
            "NotificationShadeWindow", "QSContainer", "ShadeWindowView"
        )
        private val STATUSBAR_CLASS_HINTS = listOf(
            "StatusBarWindowView", "PhoneStatusBarView", "CollapsedStatusBarFragment"
        )
    }
}
