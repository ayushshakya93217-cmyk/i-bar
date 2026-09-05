package com.statusbar.ios

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Every control here mutates the in-memory `config` and immediately calls
 * `refreshPreview()`. Nothing is written to disk until Save / Save as New —
 * so Reset can cheaply discard changes by reloading from PrefsManager.
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var preview: StatusBarOverlayView
    private lateinit var controls: LinearLayout
    private lateinit var config: StatusBarConfig

    // Registered so a drag on the preview can update the matching slider live.
    private val xSeekBars = mutableMapOf<ElementId, SeekBar>()
    private val ySeekBars = mutableMapOf<ElementId, SeekBar>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        preview = findViewById(R.id.editorPreview)
        controls = findViewById(R.id.controlsContainer)
        config = PrefsManager.getActiveConfig(this).deepCopy()

        preview.editMode = true
        preview.config = config
        preview.state = SystemState(batteryPercent = 76, wifiConnected = true, wifiLevel = 3, cellularLevel = 4)

        preview.onElementDragged = { id, dx, dy -> handleDrag(id, dx, dy) }

        buildControls()

        findViewById<android.widget.Button>(R.id.btnSave).setOnClickListener {
            PrefsManager.saveActiveConfig(this, config)
            StatusBarAccessibilityService.instance?.refreshConfig()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.widget.Button>(R.id.btnSaveAsNew).setOnClickListener { promptSaveAsNew() }
        findViewById<android.widget.Button>(R.id.btnReset).setOnClickListener {
            config = PrefsManager.getActiveConfig(this).deepCopy()
            preview.config = config
            controls.removeAllViews()
            buildControls()
        }
    }

    private fun handleDrag(id: ElementId, dx: Float, dy: Float) {
        when (id) {
            ElementId.TIME -> { config.time.element.x += dx; config.time.element.y += dy }
            ElementId.BATTERY -> { config.battery.iconX += dx; config.battery.iconY += dy }
            ElementId.BATTERY_PERCENT -> { config.battery.percentX += dx; config.battery.percentY += dy }
            ElementId.WIFI -> { config.connectivity.wifi.x += dx; config.connectivity.wifi.y += dy }
            ElementId.SIGNAL -> { config.connectivity.signal.x += dx; config.connectivity.signal.y += dy }
            ElementId.NETWORK_TYPE -> { config.connectivity.networkType.x += dx; config.connectivity.networkType.y += dy }
            ElementId.NOTCH -> { config.notch.x += dx; config.notch.y += dy }
        }
        preview.invalidate()
        syncSlidersFor(id)
    }

    private fun syncSlidersFor(id: ElementId) {
        val (x, y) = when (id) {
            ElementId.TIME -> config.time.element.x to config.time.element.y
            ElementId.BATTERY -> config.battery.iconX to config.battery.iconY
            ElementId.BATTERY_PERCENT -> config.battery.percentX to config.battery.percentY
            ElementId.WIFI -> config.connectivity.wifi.x to config.connectivity.wifi.y
            ElementId.SIGNAL -> config.connectivity.signal.x to config.connectivity.signal.y
            ElementId.NETWORK_TYPE -> config.connectivity.networkType.x to config.connectivity.networkType.y
            ElementId.NOTCH -> config.notch.x to config.notch.y
        }
        xSeekBars[id]?.progress = (x + 100).toInt().coerceIn(0, 200)
        ySeekBars[id]?.progress = (y + 100).toInt().coerceIn(0, 200)
    }

    private fun promptSaveAsNew() {
        val input = EditText(this).apply { hint = "Preset name"; setText("${config.name} copy") }
        AlertDialog.Builder(this)
            .setTitle("Save as new preset")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().ifBlank { "Custom ${System.currentTimeMillis()}" }
                val copy = config.deepCopy().apply { this.name = name }
                PrefsManager.saveCustomPreset(this, copy)
                Toast.makeText(this, "Saved as \"$name\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshPreview() = preview.invalidate()

    // ---------------------------------------------------------------------
    // Generic row builders
    // ---------------------------------------------------------------------

    private fun sectionHeader(title: String) {
        controls.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 32, 0, 8)
        })
    }

    /** A slider over an arbitrary float range, showing the live value as a label. */
    private fun floatSeek(
        label: String, min: Float, max: Float, get: () -> Float, set: (Float) -> Unit
    ): SeekBar {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val steps = 200
        val labelView = TextView(this).apply { text = "$label: ${"%.1f".format(get())}" }
        val seek = SeekBar(this).apply {
            this.max = steps
            progress = (((get() - min) / (max - min)) * steps).toInt().coerceIn(0, steps)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = min + (max - min) * (progress / steps.toFloat())
                set(value)
                labelView.text = "$label: ${"%.1f".format(value)}"
                refreshPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(labelView)
        row.addView(seek)
        controls.addView(row)
        return seek
    }

    private fun switchRow(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        row.addView(TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(Switch(this).apply {
            isChecked = get()
            setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, checked ->
                set(checked); refreshPreview()
            })
        })
        controls.addView(row)
    }

    /** Cycles an enum through its values on tap — a lightweight stand-in for a spinner. */
    private fun <T> enumCycleRow(label: String, values: List<T>, get: () -> T, set: (T) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        val valueLabel = TextView(this).apply { text = get().toString() }
        row.addView(TextView(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(valueLabel)
        row.addView(android.widget.Button(this).apply {
            text = "Change"
            setOnClickListener {
                val current = values.indexOf(get())
                val next = values[(current + 1) % values.size]
                set(next)
                valueLabel.text = next.toString()
                refreshPreview()
            }
        })
        controls.addView(row)
    }

    private fun buildControls() {
        val c = config

        sectionHeader("Global")
        floatSeek("Overall scale", 0.5f, 2.0f, { c.overallScale }, { c.overallScale = it })
        floatSeek("Overall X", -100f, 100f, { c.overallX }, { c.overallX = it })
        floatSeek("Overall Y", -50f, 50f, { c.overallY }, { c.overallY = it })
        floatSeek("Top padding", 0f, 40f, { c.paddingTop }, { c.paddingTop = it })
        floatSeek("Left padding", 0f, 60f, { c.paddingLeft }, { c.paddingLeft = it })
        floatSeek("Right padding", 0f, 60f, { c.paddingRight }, { c.paddingRight = it })
        floatSeek("Element spacing", 0f, 30f, { c.elementSpacing }, { c.elementSpacing = it })
        floatSeek("Background opacity", 0f, 1f, { c.backgroundOpacity }, { c.backgroundOpacity = it })
        floatSeek("Bar height", 24f, 60f, { c.barHeight }, { c.barHeight = it })
        floatSeek("Corner size", 0f, 30f, { c.cornerSize }, { c.cornerSize = it })

        sectionHeader("Notch / Dynamic Island area")
        switchRow("Enabled", { c.notch.enabled }, { c.notch.enabled = it })
        floatSeek("Notch width", 40f, 200f, { c.notch.width }, { c.notch.width = it })
        floatSeek("Notch height", 12f, 40f, { c.notch.height }, { c.notch.height = it })
        floatSeek("Notch corner radius", 0f, 24f, { c.notch.cornerRadius }, { c.notch.cornerRadius = it })
        xSeekBars[ElementId.NOTCH] = floatSeek("Notch X", -100f, 100f, { c.notch.x }, { c.notch.x = it })
        ySeekBars[ElementId.NOTCH] = floatSeek("Notch Y", -20f, 20f, { c.notch.y }, { c.notch.y = it })

        sectionHeader("Time")
        switchRow("Visible", { c.time.element.visible }, { c.time.element.visible = it })
        switchRow("24-hour format", { c.time.use24Hour }, { c.time.use24Hour = it })
        switchRow("Show seconds", { c.time.showSeconds }, { c.time.showSeconds = it })
        enumCycleRow("Weight", TimeWeight.values().toList(), { c.time.weight }, { c.time.weight = it })
        floatSeek("Font size", 10f, 24f, { c.time.element.fontSize }, { c.time.element.fontSize = it })
        floatSeek("Scale", 0.5f, 2f, { c.time.element.scale }, { c.time.element.scale = it })
        floatSeek("Opacity", 0f, 1f, { c.time.element.opacity }, { c.time.element.opacity = it })
        floatSeek("Letter spacing", -5f, 20f, { c.time.letterSpacing }, { c.time.letterSpacing = it })
        xSeekBars[ElementId.TIME] = floatSeek("Time X", -100f, 100f, { c.time.element.x }, { c.time.element.x = it })
        ySeekBars[ElementId.TIME] = floatSeek("Time Y", -30f, 30f, { c.time.element.y }, { c.time.element.y = it })

        sectionHeader("Battery")
        switchRow("Visible", { c.battery.element.visible }, { c.battery.element.visible = it })
        enumCycleRow("Style", BatteryStyle.values().toList(), { c.battery.style }, { c.battery.style = it })
        switchRow("Show percentage", { c.battery.showPercent }, { c.battery.showPercent = it })
        switchRow("Percentage inside icon", { c.battery.percentInsideIcon }, { c.battery.percentInsideIcon = it })
        switchRow("Charging indicator", { c.battery.showChargingIndicator }, { c.battery.showChargingIndicator = it })
        switchRow("Low-battery warning", { c.battery.showLowBatteryWarning }, { c.battery.showLowBatteryWarning = it })
        floatSeek("Low-battery threshold %", 5f, 40f, { c.battery.lowBatteryThreshold.toFloat() }, { c.battery.lowBatteryThreshold = it.toInt() })
        floatSeek("Icon size", 12f, 36f, { c.battery.element.iconSize }, { c.battery.element.iconSize = it })
        floatSeek("Icon scale", 0.5f, 2f, { c.battery.element.scale }, { c.battery.element.scale = it })
        floatSeek("Icon opacity", 0f, 1f, { c.battery.element.opacity }, { c.battery.element.opacity = it })
        floatSeek("Percent font size", 8f, 20f, { c.battery.percentFontSize }, { c.battery.percentFontSize = it })
        xSeekBars[ElementId.BATTERY] = floatSeek("Icon X", -60f, 60f, { c.battery.iconX }, { c.battery.iconX = it })
        ySeekBars[ElementId.BATTERY] = floatSeek("Icon Y", -20f, 20f, { c.battery.iconY }, { c.battery.iconY = it })
        xSeekBars[ElementId.BATTERY_PERCENT] = floatSeek("Percent X", -60f, 60f, { c.battery.percentX }, { c.battery.percentX = it })
        ySeekBars[ElementId.BATTERY_PERCENT] = floatSeek("Percent Y", -20f, 20f, { c.battery.percentY }, { c.battery.percentY = it })

        sectionHeader("Wi-Fi")
        switchRow("Visible", { c.connectivity.wifi.visible }, { c.connectivity.wifi.visible = it })
        floatSeek("Icon size", 10f, 30f, { c.connectivity.wifi.iconSize }, { c.connectivity.wifi.iconSize = it })
        floatSeek("Opacity", 0f, 1f, { c.connectivity.wifi.opacity }, { c.connectivity.wifi.opacity = it })
        xSeekBars[ElementId.WIFI] = floatSeek("Wi-Fi X", -60f, 60f, { c.connectivity.wifi.x }, { c.connectivity.wifi.x = it })
        ySeekBars[ElementId.WIFI] = floatSeek("Wi-Fi Y", -20f, 20f, { c.connectivity.wifi.y }, { c.connectivity.wifi.y = it })

        sectionHeader("Cellular signal")
        switchRow("Visible", { c.connectivity.signal.visible }, { c.connectivity.signal.visible = it })
        floatSeek("Icon size", 10f, 30f, { c.connectivity.signal.iconSize }, { c.connectivity.signal.iconSize = it })
        floatSeek("Opacity", 0f, 1f, { c.connectivity.signal.opacity }, { c.connectivity.signal.opacity = it })
        xSeekBars[ElementId.SIGNAL] = floatSeek("Signal X", -60f, 60f, { c.connectivity.signal.x }, { c.connectivity.signal.x = it })
        ySeekBars[ElementId.SIGNAL] = floatSeek("Signal Y", -20f, 20f, { c.connectivity.signal.y }, { c.connectivity.signal.y = it })

        sectionHeader("Network type label (4G/5G/LTE)")
        switchRow("Show label", { c.connectivity.showNetworkTypeLabel }, { c.connectivity.showNetworkTypeLabel = it })
        switchRow("Visible", { c.connectivity.networkType.visible }, { c.connectivity.networkType.visible = it })
        floatSeek("Font size", 8f, 16f, { c.connectivity.networkType.fontSize }, { c.connectivity.networkType.fontSize = it })
        xSeekBars[ElementId.NETWORK_TYPE] = floatSeek("Label X", -60f, 60f, { c.connectivity.networkType.x }, { c.connectivity.networkType.x = it })
        ySeekBars[ElementId.NETWORK_TYPE] = floatSeek("Label Y", -20f, 20f, { c.connectivity.networkType.y }, { c.connectivity.networkType.y = it })
    }
}
