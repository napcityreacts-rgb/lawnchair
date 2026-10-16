# NEO Import UI — Wiring Map

Fork of Lawnchair (16-dev, base `85335d9`), branch `neo-import`.
Every file the feature touches, and exactly where. The branch itself is the
patch — this file is the index, not a second copy of the diff.

## New code

| File | What it is |
|---|---|
| `lawnchair/src/app/lawnchair/neoimport/UiSpec.kt` | `.neoui.zip` spec data classes + `org.json` parsing. |
| `lawnchair/src/app/lawnchair/neoimport/NeoUiFile.kt` | Reads a `.neoui.zip` from a content `Uri`. |
| `lawnchair/src/app/lawnchair/neoimport/UiImporter.kt` | Applies a spec; reports applied vs pending. |
| `lawnchair/src/app/lawnchair/neoimport/ImportUiActivity.kt` | Picker → preview → apply screen (Compose). |
| `lawnchair/src/app/lawnchair/neoimport/NeoControlCenterActivity.kt` | **Increment 2.** Control Center overlay (spec tiles or built-in defaults). |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoWidgetRenderer.kt` | **Increment 2.** Canvas bitmap renderer for all widget faces + icons. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoWidgetState.kt` | **Increment 2.** Toggle state + real intents. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoWidgetClickReceiver.kt` | **Increment 2.** Widget tile taps; torch toggle. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoWidgetUpdater.kt` | **Increment 2.** 1-minute tick redraw. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoWidgetTickReceiver.kt` | **Increment 2.** Tick receiver. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoClockWidget.kt` | **Increment 2.** NEO editorial clock widget. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoGlassWidget.kt` | **Increment 2.** NEO crimson glass clock widget. |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoControlCenterWidget.kt` | **Increment 2.** Control Center widget (header → overlay, 12 tiles). |
| `lawnchair/src/app/lawnchair/neoimport/widgets/NeoAppWheelWidget.kt` | **Increment 2.** App Wheel widget (2×2 quick launch). |
| `lawnchair/res/layout/neo_widget_single.xml` | **Increment 2.** Single-face widget layout. |
| `lawnchair/res/layout/neo_widget_cc.xml` | **Increment 2.** Control Center widget layout. |
| `lawnchair/res/layout/neo_widget_wheel.xml` | **Increment 2.** App Wheel widget layout. |
| `lawnchair/res/xml/neo_clock_info.xml` | **Increment 2.** Clock widget provider info. |
| `lawnchair/res/xml/neo_glass_info.xml` | **Increment 2.** Glass widget provider info. |
| `lawnchair/res/xml/neo_control_info.xml` | **Increment 2.** Control Center widget provider info. |
| `lawnchair/res/xml/neo_wheel_info.xml` | **Increment 2.** App Wheel widget provider info. |
| `README_NEO_IMPORT.md` | Spec format + wired-vs-TODO. |
| `neo-tactile-sample.neoui.zip` | Sample pack (NEO-TACTILE tokens + 12 tiles + wallpaper). |

## Modified files (anchor lines at time of writing)

| File | Change |
|---|---|
| `lawnchair/AndroidManifest.xml` | CAMERA permission (~line 42); `ImportUiActivity` (~line 132), `NeoControlCenterActivity` (~line 140), four NEO widget receivers (~lines 147–193), click + tick receivers (~lines 197–201). |
| `lawnchair/res/values/strings.xml` | `import_ui*` strings (~lines 1075–1089); `neo_*` strings (~lines 1092–1106). |
| `lawnchair/src/app/lawnchair/ui/preferences/destinations/BackupAndRestorePreference.kt` | Settings entries: Import UI (~line 51) + Open Control Center (~line 58). |
| `lawnchair/src/app/lawnchair/neoimport/UiImporter.kt` | Control center reported as applied (~line 137); wheel-only TODO (~line 197). |

## Upstream APIs the feature calls (verified against this tree)

| Call | Declared in |
|---|---|
| `PreferenceManager2.accentColor / hotseatBackgroundColor / appDrawerBackgroundColor / colorStyle` + opto `setBlocking` | `lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt` |
| `WallpaperManager.setBitmap` | Android framework — same call `LawnchairBackup.restore()` uses |
| `CameraManager.setTorchMode` | Android framework — needs CAMERA permission (manifest + runtime request in overlay) |
| `Settings.Panel.ACTION_WIFI`, `ACTION_BLUETOOTH_SETTINGS`, `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`, `ACTION_AIRPLANE_MODE_SETTINGS`, `ACTION_DISPLAY_SETTINGS`, `ACTION_SETTINGS`, `AlarmClock.ACTION_SHOW_TIMERS`, `CATEGORY_APP_MESSAGING/MUSIC/MAPS/BROWSER` | Android framework — panels, because Android does not let apps flip WiFi/BT/airplane/rotation directly |

## Still TODO (marked in code — nothing faked)

1. Home-screen / dock placement — `UiImporter.applyHomeScreenItems()` → Favorites rows via `ModelDbController` / `RestoreDbTask` (see `LawnchairBackup.restore()`).
2. App-wheel screen (drawer replacement) — `UiImporter.applyControlCenterAndWheel()`.
3. Swipe-down gesture hook → `NeoControlCenterActivity`.
4. Grid + darkMode from spec — `DeviceGridState` + Lawnchair theme setting.
5. On-phone verification — nothing here has run on a device; no CI run verified.
