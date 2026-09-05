package com.statusbar.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/** Live system values the view needs; the host (service or editor) feeds these in. */
data class SystemState(
    var batteryPercent: Int = 100,
    var isCharging: Boolean = false,
    var wifiConnected: Boolean = false,
    var wifiLevel: Int = 3,        // 0..4
    var cellularConnected: Boolean = true,
    var cellularLevel: Int = 4,    // 0..4
    var networkTypeLabel: String = "5G"
)

/** Which logical element owns a hit-testable region, used only in editor drag mode. */
enum class ElementId { TIME, BATTERY, BATTERY_PERCENT, WIFI, SIGNAL, NETWORK_TYPE, NOTCH }

class StatusBarOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var config: StatusBarConfig = Presets.iphone16()
        set(value) { field = value; invalidate() }

    var state: SystemState = SystemState()
        set(value) { field = value; invalidate() }

    /** When true, draws selection outlines and accepts drag gestures. False in the live overlay. */
    var editMode: Boolean = false

    var onElementDragged: ((ElementId, dx: Float, dy: Float) -> Unit)? = null
    var onElementSelected: ((ElementId) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DFFEB3B"); style = Paint.Style.STROKE; strokeWidth = 2f
    }

    // Populated every onDraw so touch handling can hit-test against current layout.
    private val hitRects = mutableMapOf<ElementId, RectF>()
    private var dragTarget: ElementId? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = config
        hitRects.clear()

        // Background — transparent unless the user explicitly wants a tint.
        if (c.backgroundOpacity > 0f) {
            bgPaint.color = Color.BLACK
            bgPaint.alpha = (c.backgroundOpacity * 255).roundToInt()
            canvas.drawRect(0f, 0f, width.toFloat(), dp(c.barHeight), bgPaint)
        }

        canvas.save()
        canvas.translate(dp(c.overallX), dp(c.overallY))
        canvas.scale(c.overallScale, c.overallScale, width / 2f, dp(c.barHeight) / 2f)

        drawNotch(canvas, c)
        drawTime(canvas, c)
        drawTrailingCluster(canvas, c)

        canvas.restore()

        if (editMode) {
            hitRects.forEach { (_, rect) -> canvas.drawRoundRect(rect, 8f, 8f, selectionPaint) }
        }
    }

    private fun drawNotch(canvas: Canvas, c: StatusBarConfig) {
        if (!c.notch.enabled) return
        val w = dp(c.notch.width)
        val h = dp(c.notch.height)
        val cx = width / 2f + dp(c.notch.x)
        val top = dp(c.notch.y)
        val rect = RectF(cx - w / 2f, top, cx + w / 2f, top + h)
        iconFillPaint.color = Color.BLACK
        canvas.drawRoundRect(rect, dp(c.notch.cornerRadius), dp(c.notch.cornerRadius), iconFillPaint)
        iconFillPaint.color = Color.WHITE
        registerHit(ElementId.NOTCH, rect)
    }

    private fun drawTime(canvas: Canvas, c: StatusBarConfig) {
        val t = c.time
        if (!t.element.visible) return
        val fmt = if (t.use24Hour) {
            if (t.showSeconds) "HH:mm:ss" else "HH:mm"
        } else {
            if (t.showSeconds) "h:mm:ss" else "h:mm"
        }
        val timeStr = SimpleDateFormat(fmt, Locale.getDefault()).format(Date())

        textPaint.textSize = dp(t.element.fontSize) * t.element.scale
        textPaint.alpha = (t.element.opacity * 255).roundToInt()
        textPaint.typeface = when (t.weight) {
            TimeWeight.REGULAR -> android.graphics.Typeface.DEFAULT
            TimeWeight.MEDIUM, TimeWeight.SEMIBOLD -> android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
            )
            TimeWeight.BOLD -> android.graphics.Typeface.DEFAULT_BOLD
        }
        textPaint.letterSpacing = t.letterSpacing / 100f

        val baseX = dp(c.paddingLeft) + t.element.x
        val baseY = dp(c.barHeight) / 2f - (textPaint.ascent() + textPaint.descent()) / 2f + t.element.y

        val bounds = Rect()
        textPaint.getTextBounds(timeStr, 0, timeStr.length, bounds)
        canvas.drawText(timeStr, baseX, baseY, textPaint)
        registerHit(
            ElementId.TIME,
            RectF(baseX, baseY + bounds.top, baseX + bounds.width(), baseY + bounds.bottom)
        )
    }

    /** Trailing side: network type, signal, wifi, battery pill+percent — right-aligned like iOS. */
    private fun drawTrailingCluster(canvas: Canvas, c: StatusBarConfig) {
        var cursorRight = width - dp(c.paddingRight)

        // Battery (rightmost)
        cursorRight = drawBattery(canvas, c, cursorRight)
        cursorRight -= dp(c.elementSpacing)

        // Wifi
        if (c.connectivity.wifi.visible) {
            cursorRight = drawWifi(canvas, c, cursorRight)
            cursorRight -= dp(c.elementSpacing)
        }

        // Signal bars
        if (c.connectivity.signal.visible) {
            cursorRight = drawSignal(canvas, c, cursorRight)
            cursorRight -= dp(c.elementSpacing)
        }

        // Network type label (4G/5G/LTE)
        if (c.connectivity.showNetworkTypeLabel && c.connectivity.networkType.visible) {
            drawNetworkTypeLabel(canvas, c, cursorRight)
        }
    }

    private fun drawBattery(canvas: Canvas, c: StatusBarConfig, rightEdge: Float): Float {
        val b = c.battery
        if (!b.element.visible) return rightEdge

        val iconW = dp(b.element.iconSize) * b.element.scale
        val iconH = iconW * 0.5f
        val x = rightEdge - iconW + dp(b.iconX)
        val y = dp(c.barHeight) / 2f - iconH / 2f + dp(b.iconY)

        iconPaint.alpha = (b.element.opacity * 255).roundToInt()
        iconPaint.strokeWidth = dp(1.2f)

        when (b.style) {
            BatteryStyle.IOS_PILL, BatteryStyle.IOS_OUTLINE -> {
                val bodyW = iconW - dp(2.5f)
                val bodyRect = RectF(x, y, x + bodyW, y + iconH)
                canvas.drawRoundRect(bodyRect, dp(3f), dp(3f), iconPaint)
                // nub
                canvas.drawRoundRect(
                    RectF(x + bodyW + dp(1f), y + iconH * 0.25f, x + bodyW + dp(2.5f), y + iconH * 0.75f),
                    dp(1f), dp(1f), iconFillPaint
                )
                // fill level
                val level = state.batteryPercent.coerceIn(0, 100) / 100f
                val inset = dp(2f)
                val fillColor = if (b.showLowBatteryWarning && state.batteryPercent <= b.lowBatteryThreshold && !state.isCharging)
                    Color.parseColor("#FF3B30") else Color.WHITE
                iconFillPaint.color = fillColor
                val fillRect = RectF(
                    x + inset, y + inset,
                    x + inset + (bodyW - inset * 2) * level, y + iconH - inset
                )
                canvas.drawRoundRect(fillRect, dp(1.5f), dp(1.5f), iconFillPaint)
                iconFillPaint.color = Color.WHITE

                if (state.isCharging && b.showChargingIndicator) {
                    // simple bolt: two triangles via path, kept tiny to stay legible at 16-24dp
                    val boltPaint = Paint(iconFillPaint).apply { color = Color.YELLOW }
                    val cx = x + bodyW / 2f
                    val cy = y + iconH / 2f
                    canvas.drawCircle(cx, cy, dp(2.5f), boltPaint)
                }
            }
            BatteryStyle.MINIMAL_DOT -> {
                canvas.drawCircle(x + iconW / 2f, y + iconH / 2f, dp(3f), iconFillPaint)
            }
            BatteryStyle.TEXT_ONLY -> { /* percent text carries the info, no glyph drawn */ }
        }

        registerHit(ElementId.BATTERY, RectF(x, y, x + iconW, y + iconH))

        var newRight = x
        if (b.showPercent) {
            textPaint.textSize = dp(b.percentFontSize) * b.element.scale
            textPaint.alpha = 255
            val pctStr = "${state.batteryPercent}%"
            val pctBounds = Rect()
            textPaint.getTextBounds(pctStr, 0, pctStr.length, pctBounds)

            if (b.percentInsideIcon && b.style != BatteryStyle.TEXT_ONLY) {
                val px = x + iconW / 2f - pctBounds.width() / 2f + b.percentX
                val py = y + iconH / 2f - pctBounds.exactCenterY() + b.percentY
                canvas.drawText(pctStr, px, py, textPaint)
            } else {
                val px = newRight - pctBounds.width() + b.percentX
                val py = dp(c.barHeight) / 2f - pctBounds.exactCenterY() + b.percentY
                canvas.drawText(pctStr, px, py, textPaint)
                newRight = px - dp(4f)
                registerHit(ElementId.BATTERY_PERCENT, RectF(px, py + pctBounds.top, px + pctBounds.width(), py + pctBounds.bottom))
            }
        }
        return newRight.coerceAtMost(x)
    }

    private fun drawWifi(canvas: Canvas, c: StatusBarConfig, rightEdge: Float): Float {
        val wifiCfg = c.connectivity.wifi
        val size = dp(wifiCfg.iconSize) * wifiCfg.scale
        val x = rightEdge - size + wifiCfg.x
        val yBase = dp(c.barHeight) / 2f + size / 2f + wifiCfg.y
        val cx = x + size / 2f

        iconFillPaint.alpha = if (state.wifiConnected) 255 else 90
        val bars = 3
        for (i in 0 until bars) {
            val level = i + 1
            val radius = size * (0.35f + 0.22f * i)
            val active = state.wifiLevel >= level + 1
            iconFillPaint.alpha = if (active) 255 else 70
            val arcRect = RectF(cx - radius, yBase - radius, cx + radius, yBase + radius)
            canvas.drawArc(arcRect, 210f, 120f, false, Paint(iconPaint).apply {
                strokeWidth = dp(1.6f); alpha = if (active) 255 else 70
            })
        }
        canvas.drawCircle(cx, yBase, dp(1.4f), iconFillPaint)
        iconFillPaint.alpha = 255

        registerHit(ElementId.WIFI, RectF(x, yBase - size, x + size, yBase + dp(2f)))
        return x
    }

    private fun drawSignal(canvas: Canvas, c: StatusBarConfig, rightEdge: Float): Float {
        val sig = c.connectivity.signal
        val barCount = 4
        val barW = dp(sig.iconSize) * sig.scale / 6f
        val gap = dp(1.5f)
        val totalW = barCount * barW + (barCount - 1) * gap
        val baseX = rightEdge - totalW + sig.x
        val baseY = dp(c.barHeight) / 2f + dp(sig.iconSize) / 2.5f + sig.y

        for (i in 0 until barCount) {
            val h = dp(sig.iconSize) * (0.35f + 0.22f * i)
            val active = state.cellularConnected && state.cellularLevel >= i + 1
            iconFillPaint.alpha = if (active) 255 else 70
            val left = baseX + i * (barW + gap)
            canvas.drawRoundRect(RectF(left, baseY - h, left + barW, baseY), dp(1f), dp(1f), iconFillPaint)
        }
        iconFillPaint.alpha = 255
        registerHit(ElementId.SIGNAL, RectF(baseX, baseY - dp(sig.iconSize), baseX + totalW, baseY))
        return baseX
    }

    private fun drawNetworkTypeLabel(canvas: Canvas, c: StatusBarConfig, rightEdge: Float) {
        val nt = c.connectivity.networkType
        textPaint.textSize = dp(nt.fontSize) * nt.scale
        textPaint.alpha = (nt.opacity * 255).roundToInt()
        val label = state.networkTypeLabel
        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val x = rightEdge - bounds.width() + nt.x
        val y = dp(c.barHeight) / 2f - bounds.exactCenterY() + nt.y
        canvas.drawText(label, x, y, textPaint)
        registerHit(ElementId.NETWORK_TYPE, RectF(x, y + bounds.top, x + bounds.width(), y + bounds.bottom))
    }

    private fun registerHit(id: ElementId, rect: RectF) {
        if (editMode) hitRects[id] = rect
    }

    // --- Editor drag support (never active on the real overlay window) ---
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = hitRects.entries.firstOrNull { it.value.contains(event.x, event.y) }?.key
                lastTouchX = event.x
                lastTouchY = event.y
                dragTarget?.let { onElementSelected?.invoke(it) }
                return dragTarget != null
            }
            MotionEvent.ACTION_MOVE -> {
                val target = dragTarget ?: return false
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                onElementDragged?.invoke(target, dx / density, dy / density)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragTarget = null
                return false
            }
        }
        return false
    }
}
