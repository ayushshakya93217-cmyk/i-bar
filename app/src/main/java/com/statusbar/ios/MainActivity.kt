package com.statusbar.ios

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnabled: Switch
    private lateinit var permissionCard: LinearLayout
    private lateinit var currentPresetLabel: TextView
    private lateinit var previewView: StatusBarOverlayView

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeExportTo(it) }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readImportFrom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchEnabled = findViewById(R.id.switchEnabled)
        permissionCard = findViewById(R.id.permissionCard)
        currentPresetLabel = findViewById(R.id.currentPresetLabel)
        previewView = findViewById(R.id.previewView)

        findViewById<Button>(R.id.btnOpenAccessibilitySettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnEdit).setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        findViewById<Button>(R.id.btnPresets).setOnClickListener {
            startActivity(Intent(this, PresetsActivity::class.java))
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            exportLauncher.launch("ios-status-bar-settings.json")
        }
        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAccessibilityServiceEnabled()) {
                switchEnabled.isChecked = false
                Toast.makeText(this, "Turn on the Accessibility service first", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            PrefsManager.setEnabled(this, isChecked)
            StatusBarAccessibilityService.instance?.setEnabled(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val enabledService = isAccessibilityServiceEnabled()
        permissionCard.visibility = if (enabledService) android.view.View.GONE else android.view.View.VISIBLE

        val config = PrefsManager.getActiveConfig(this)
        currentPresetLabel.text = "Current preset: ${config.name}"
        previewView.config = config
        previewView.state = SystemState(batteryPercent = 87, wifiConnected = true, wifiLevel = 3, cellularLevel = 4)

        switchEnabled.setOnCheckedChangeListener(null)
        switchEnabled.isChecked = PrefsManager.isEnabled(this) && enabledService
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAccessibilityServiceEnabled()) {
                switchEnabled.isChecked = false
                Toast.makeText(this, "Turn on the Accessibility service first", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            PrefsManager.setEnabled(this, isChecked)
            StatusBarAccessibilityService.instance?.setEnabled(isChecked)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${StatusBarAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    private fun writeExportTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(PrefsManager.exportAllToJson(this).toByteArray())
            }
            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun readImportFrom(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            if (PrefsManager.importFromJson(this, text)) {
                StatusBarAccessibilityService.instance?.refreshConfig()
                refreshUi()
                Toast.makeText(this, "Imported", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "That file isn't a valid settings export", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
