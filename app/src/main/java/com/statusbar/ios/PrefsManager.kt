package com.statusbar.ios

import android.content.Context

/**
 * Single source of truth for saved state. Everything survives reboot because
 * it's backed by SharedPreferences (apply() is durable + async).
 */
object PrefsManager {
    private const val FILE = "ios_status_bar_prefs"
    private const val KEY_ACTIVE_CONFIG = "active_config_json"
    private const val KEY_ENABLED = "overlay_enabled"
    private const val KEY_CUSTOM_PRESETS = "custom_presets_json_list"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getActiveConfig(ctx: Context): StatusBarConfig {
        val json = prefs(ctx).getString(KEY_ACTIVE_CONFIG, null)
        return if (json != null) {
            try { StatusBarConfig.fromJson(json) } catch (e: Exception) { Presets.iphone16() }
        } else Presets.iphone16()
    }

    fun saveActiveConfig(ctx: Context, config: StatusBarConfig) {
        prefs(ctx).edit().putString(KEY_ACTIVE_CONFIG, config.toJson()).apply()
    }

    fun getCustomPresets(ctx: Context): MutableList<StatusBarConfig> {
        val json = prefs(ctx).getString(KEY_CUSTOM_PRESETS, null) ?: return mutableListOf()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<StatusBarConfig>>() {}.type
            GsonHolder.gson.fromJson<MutableList<StatusBarConfig>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveCustomPreset(ctx: Context, config: StatusBarConfig) {
        val list = getCustomPresets(ctx)
        val existingIndex = list.indexOfFirst { it.name == config.name }
        if (existingIndex >= 0) list[existingIndex] = config else list.add(config)
        persistCustomPresets(ctx, list)
    }

    fun deleteCustomPreset(ctx: Context, name: String) {
        val list = getCustomPresets(ctx)
        list.removeAll { it.name == name }
        persistCustomPresets(ctx, list)
    }

    private fun persistCustomPresets(ctx: Context, list: List<StatusBarConfig>) {
        prefs(ctx).edit().putString(KEY_CUSTOM_PRESETS, GsonHolder.gson.toJson(list)).apply()
    }

    fun exportAllToJson(ctx: Context): String {
        val bundle = ExportBundle(getActiveConfig(ctx), getCustomPresets(ctx))
        return GsonHolder.gson.toJson(bundle)
    }

    fun importFromJson(ctx: Context, json: String): Boolean {
        return try {
            val bundle = GsonHolder.gson.fromJson(json, ExportBundle::class.java)
            saveActiveConfig(ctx, bundle.active)
            persistCustomPresets(ctx, bundle.customPresets)
            true
        } catch (e: Exception) {
            false
        }
    }

    private data class ExportBundle(
        val active: StatusBarConfig,
        val customPresets: List<StatusBarConfig>
    )
}
