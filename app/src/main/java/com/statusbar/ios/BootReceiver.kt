package com.statusbar.ios

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Nothing to actively start: PrefsManager already persisted "enabled" +
        // the active config. If the user has the Accessibility service turned on
        // in system settings, Android reconnects StatusBarAccessibilityService on
        // its own and onServiceConnected() reads PrefsManager and shows the
        // overlay automatically. This receiver exists so future versions have a
        // hook if that ever needs to change.
    }
}
