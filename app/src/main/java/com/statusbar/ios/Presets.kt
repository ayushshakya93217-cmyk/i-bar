package com.statusbar.ios

object Presets {

    fun iphone15() = StatusBarConfig(name = "iPhone 15").apply {
        barHeight = 47f
        notch.enabled = true
        notch.width = 126f
        notch.height = 30f
        notch.cornerRadius = 15f
        time.use24Hour = false
        time.element.fontSize = 15f
    }

    fun iphone16() = StatusBarConfig(name = "iPhone 16").apply {
        barHeight = 47f
        notch.enabled = true
        notch.width = 128f
        notch.height = 32f
        notch.cornerRadius = 16f
        time.element.fontSize = 15f
    }

    fun iphone16Pro() = StatusBarConfig(name = "iPhone 16 Pro").apply {
        barHeight = 49f
        notch.enabled = true
        notch.width = 120f
        notch.height = 32f
        notch.cornerRadius = 16f
        time.element.fontSize = 16f
        battery.style = BatteryStyle.IOS_PILL
    }

    fun minimalIos() = StatusBarConfig(name = "Minimal iOS").apply {
        barHeight = 40f
        notch.enabled = false
        battery.style = BatteryStyle.MINIMAL_DOT
        battery.showPercent = false
        connectivity.showNetworkTypeLabel = false
        backgroundOpacity = 0f
    }

    /**
     * Moto G34 5G (XT2363-5): 720x1612 px @ ~269dpi punch-hole camera,
     * top-center. Values below are tuned so nothing overlaps the punch-hole
     * and the bar height matches the device's real status bar height.
     */
    fun motoG34() = StatusBarConfig(name = "Moto G34 5G").apply {
        barHeight = 32f
        paddingLeft = 16f
        paddingRight = 16f
        notch.enabled = false // punch-hole, not a notch — handled via safe-area padding
        manualScreenWidth = 720f
        manualScreenHeight = 1612f
        manualSafeAreaTop = 32f
        time.element.fontSize = 13f
        battery.element.iconSize = 20f
        connectivity.signal.iconSize = 16f
        connectivity.wifi.iconSize = 16f
    }

    fun all(): List<StatusBarConfig> = listOf(
        iphone15(), iphone16(), iphone16Pro(), minimalIos(), motoG34()
    )
}
