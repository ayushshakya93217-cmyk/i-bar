package com.statusbar.ios

/**
 * Every value that can be tweaked for a single element (clock, battery icon,
 * battery percent text, wifi icon, signal icon, network-type label, notch).
 * All units are dp unless noted otherwise; x/y are offsets from that
 * element's default anchor position so presets stay resolution-independent.
 */
data class ElementConfig(
    var visible: Boolean = true,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = -1f,      // -1 = intrinsic
    var height: Float = -1f,     // -1 = intrinsic
    var scale: Float = 1.0f,
    var iconSize: Float = 18f,
    var fontSize: Float = 14f,
    var opacity: Float = 1.0f,   // 0..1
    var spacing: Float = 6f
)

enum class BatteryStyle { IOS_PILL, IOS_OUTLINE, MINIMAL_DOT, TEXT_ONLY }
enum class TimeWeight { REGULAR, MEDIUM, SEMIBOLD, BOLD }

data class BatteryConfig(
    val element: ElementConfig = ElementConfig(iconSize = 24f, fontSize = 13f),
    var style: BatteryStyle = BatteryStyle.IOS_PILL,
    var showPercent: Boolean = true,
    var percentInsideIcon: Boolean = false,
    var percentFontSize: Float = 12f,
    var percentX: Float = 0f,
    var percentY: Float = 0f,
    var iconX: Float = 0f,
    var iconY: Float = 0f,
    var showChargingIndicator: Boolean = true,
    var showLowBatteryWarning: Boolean = true,
    var lowBatteryThreshold: Int = 20
)

data class TimeConfig(
    val element: ElementConfig = ElementConfig(fontSize = 15f),
    var use24Hour: Boolean = false,
    var showSeconds: Boolean = false,
    var weight: TimeWeight = TimeWeight.SEMIBOLD,
    var letterSpacing: Float = 0f
)

data class ConnectivityConfig(
    val signal: ElementConfig = ElementConfig(iconSize = 18f),
    val wifi: ElementConfig = ElementConfig(iconSize = 18f),
    val networkType: ElementConfig = ElementConfig(fontSize = 11f),
    var showNetworkTypeLabel: Boolean = true
)

data class NotchConfig(
    var enabled: Boolean = false,
    var width: Float = 90f,
    var height: Float = 24f,
    var x: Float = 0f,      // 0 = horizontally centered
    var y: Float = 0f,
    var cornerRadius: Float = 14f
)

data class StatusBarConfig(
    var name: String = "Custom",

    // Global
    var overallScale: Float = 1.0f,
    var overallX: Float = 0f,
    var overallY: Float = 0f,
    var paddingTop: Float = 8f,
    var paddingLeft: Float = 24f,
    var paddingRight: Float = 24f,
    var elementSpacing: Float = 8f,
    var backgroundOpacity: Float = 0f,     // 0 = fully transparent
    var barHeight: Float = 44f,
    var cornerSize: Float = 0f,

    var notch: NotchConfig = NotchConfig(),
    var time: TimeConfig = TimeConfig(),
    var battery: BatteryConfig = BatteryConfig(),
    var connectivity: ConnectivityConfig = ConnectivityConfig(),

    // Manual overrides for auto-detected safe area / display metrics.
    // Null means "use the value Android reports".
    var manualSafeAreaTop: Float? = null,
    var manualScreenWidth: Float? = null,
    var manualScreenHeight: Float? = null
) {
    fun deepCopy(): StatusBarConfig = fromJson(toJson())
    fun toJson(): String = GsonHolder.gson.toJson(this)

    companion object {
        fun fromJson(json: String): StatusBarConfig =
            GsonHolder.gson.fromJson(json, StatusBarConfig::class.java)
    }
}

object GsonHolder {
    val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
}
