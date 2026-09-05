package com.statusbar.ios

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PresetsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presets)
        container = findViewById(R.id.presetContainer)
        render()
    }

    private fun render() {
        container.removeAllViews()

        addSectionHeader("Built-in")
        Presets.all().forEach { preset -> addRow(preset, isCustom = false) }

        addSectionHeader("Custom")
        val custom = PrefsManager.getCustomPresets(this)
        if (custom.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No custom presets yet. Duplicate a built-in preset to start one."
                setPadding(0, 8, 0, 8)
                alpha = 0.6f
            }
            container.addView(empty)
        } else {
            custom.forEach { preset -> addRow(preset, isCustom = true) }
        }
    }

    private fun addSectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            textSize = 14f
            setPadding(0, 24, 0, 4)
            alpha = 0.6f
        }
        container.addView(header)
    }

    private fun addRow(preset: StatusBarConfig, isCustom: Boolean) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_preset_row, container, false)
        row.findViewById<TextView>(R.id.presetName).text = preset.name
        row.findViewById<Button>(R.id.btnUse).setOnClickListener {
            PrefsManager.saveActiveConfig(this, preset.deepCopy())
            StatusBarAccessibilityService.instance?.refreshConfig()
            Toast.makeText(this, "${preset.name} applied", Toast.LENGTH_SHORT).show()
            finish()
        }
        row.findViewById<Button>(R.id.btnDuplicate).setOnClickListener {
            val copy = preset.deepCopy()
            var newName = "${preset.name} copy"
            var suffix = 2
            val existingNames = (Presets.all() + PrefsManager.getCustomPresets(this)).map { it.name }
            while (existingNames.contains(newName)) { newName = "${preset.name} copy $suffix"; suffix++ }
            copy.name = newName
            PrefsManager.saveCustomPreset(this, copy)
            Toast.makeText(this, "Saved as \"$newName\"", Toast.LENGTH_SHORT).show()
            render()
        }
        val deleteBtn = row.findViewById<Button>(R.id.btnDelete)
        if (isCustom) {
            deleteBtn.setOnClickListener {
                PrefsManager.deleteCustomPreset(this, preset.name)
                render()
            }
        } else {
            deleteBtn.visibility = View.GONE
        }
        container.addView(row)
    }
}
