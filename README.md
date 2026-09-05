# iOS Status Bar Overlay (Android)

Full Android Studio project source. **I couldn't compile or test this here** —
this sandbox has no Android SDK and no network access to fetch Gradle
dependencies, so I wrote and manually reviewed the code but it hasn't been
run on a device or emulator. Budget an hour to open it in Android Studio,
fix any small API mismatches, and test on your Moto G34.

## How to build
1. Open the `iOSStatusBarOverlay` folder in Android Studio (Koala+).
2. Let Gradle sync (it will download AndroidX/Material/Gson — needs internet).
3. Run on a device with Android 15, or the Moto G34 itself over USB debugging.
4. On first launch: tap **Open Accessibility Settings**, turn on "iOS Status
   Bar Overlay" under Accessibility → Installed apps. Then flip the enable
   switch on the main screen.

## How the tricky parts work
- **No black strip, no SYSTEM_ALERT_WINDOW permission**: the overlay is a
  `WindowManager` window of type `TYPE_ACCESSIBILITY_OVERLAY`, which only an
  `AccessibilityService` is allowed to add. It's `FLAG_NOT_TOUCHABLE` (taps
  pass through) and fully transparent — see `StatusBarAccessibilityService.kt`.
- **Notification shade hide/show**: the accessibility service watches
  `com.android.systemui` window-state events and matches known AOSP class
  names for the shade vs. the normal status bar (`SHADE_CLASS_HINTS` /
  `STATUSBAR_CLASS_HINTS` in `StatusBarAccessibilityService.kt`). This is a
  **heuristic, not a guaranteed public API** — Motorola's MyUX skin can
  rename these classes. If shade detection doesn't trigger on your G34, run
  `adb shell dumpsys accessibility` while pulling the shade down, find the
  real class name, and add it to those lists.
- **Real data, not placeholders**: battery % comes from the sticky
  `ACTION_BATTERY_CHANGED` broadcast, Wi-Fi/cellular bars from
  `ConnectivityManager` + `TelephonyManager.registerTelephonyCallback`
  (needs `READ_PHONE_STATE`, requested at runtime the first time it's
  needed — add a permission-request flow in `MainActivity` if you want a
  friendlier prompt than the system default denial).
- **Everything customizable** lives in `StatusBarConfig.kt` as plain data
  classes, serialized with Gson for export/import and SharedPreferences
  persistence (`PrefsManager.kt`) — so it survives reboot.
- **Editor**: `EditorActivity.kt` builds one slider/switch per field
  generically and also supports dragging elements directly on the live
  preview (`StatusBarOverlayView.onElementDragged`); dragging and the
  sliders stay in sync.
- **Presets**: `Presets.kt` has iPhone 15 / 16 / 16 Pro / Minimal iOS /
  Moto G34 5G. Users can duplicate any preset into an editable custom copy
  from the Presets screen.

## What's stubbed / left for you to finish
- The battery "charging bolt" glyph is a placeholder dot, not an actual
  bolt path — swap in a real vector path if you want it pixel-accurate.
- `EditorActivity` wires the main customization fields from the spec, not
  literally every combinatorial one (e.g. per-element `width`/`height`
  aren't hooked to sliders yet, only `x`/`y`/`scale`/`iconSize`/`fontSize`/
  `opacity` are) — the pattern (`floatSeek(...)`) is copy-pasteable for any
  field you want to add a slider for.
- No settings screen for permission troubleshooting beyond the one banner
  on the main screen.
- App icon is a simple placeholder vector, not a polished asset.
