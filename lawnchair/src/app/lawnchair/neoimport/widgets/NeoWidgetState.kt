/*
 * Copyright 2026, NeoLauncher (Lawnchair fork)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.neoimport.widgets

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings

/** Reads real on-device state for the toggle tiles. Fail-soft: unknown = off. */
object NeoWidgetState {

    const val PREFS_NAME = "neo_widgets"
    const val KEY_FLASHLIGHT_ON = "flashlight_on"

    fun toggleState(context: Context): NeoWidgetRenderer.ToggleState {
        val wifi = try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled
        } catch (e: Exception) {
            false
        }
        val bt = try {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
        } catch (e: Exception) {
            false
        }
        val flash = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLASHLIGHT_ON, false)
        val dnd = try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            false
        }
        return NeoWidgetRenderer.ToggleState(wifi, bt, flash, dnd)
    }

    fun hasCameraPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Flips the torch. Caller must have checked [hasCameraPermission] first.
     * Returns true when the torch state actually flipped.
     */
    fun toggleFlashlight(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getBoolean(KEY_FLASHLIGHT_ON, false)
        val next = !current
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return false
            cm.setTorchMode(cameraId, next)
            prefs.edit().putBoolean(KEY_FLASHLIGHT_ON, next).apply()
            true
        } catch (e: Exception) {
            // camera in use or no flash: leave state alone
            false
        }
    }

    fun wifiPanelIntent(): Intent = Intent(Settings.Panel.ACTION_WIFI).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Android has no Settings.Panel for Bluetooth — open the Bluetooth settings page.
    fun bluetoothPanelIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun dndSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun dialerIntent(): Intent = Intent(Intent.ACTION_DIAL).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun messagesIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MESSAGING)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun cameraIntent(): Intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun browserIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_BROWSER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun clockIntent(): Intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun airplaneModeOn(context: Context): Boolean {
        return try {
            android.provider.Settings.Global.getInt(
                context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    fun rotationOn(context: Context): Boolean {
        return try {
            android.provider.Settings.System.getInt(
                context.contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    // Android does not let apps flip airplane mode or rotation directly;
    // open the system pages where the user flips them.
    fun airplaneSettingsIntent(): Intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun displaySettingsIntent(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun timerIntent(): Intent = Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun musicIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MUSIC)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun mapsIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MAPS)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun systemSettingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Maps a spec/control-center action key to its real intent, or null for "flashlight". */
    fun intentForAction(action: String): Intent? = when (action) {
        "wifi" -> wifiPanelIntent()
        "bluetooth" -> bluetoothPanelIntent()
        "airplane" -> airplaneSettingsIntent()
        "dnd" -> dndSettingsIntent()
        "rotate" -> displaySettingsIntent()
        "camera" -> cameraIntent()
        "timer" -> timerIntent()
        "messages" -> messagesIntent()
        "music" -> musicIntent()
        "maps" -> mapsIntent()
        "settings" -> systemSettingsIntent()
        "phone" -> dialerIntent()
        "browser" -> browserIntent()
        "clock" -> clockIntent()
        "flashlight" -> null // handled by toggleFlashlight (needs CameraManager + permission)
        else -> null
    }

    /** Whether a spec action key is a live toggle (drawn ON when its state is on). */
    fun isOnForAction(context: Context, action: String, toggle: NeoWidgetRenderer.ToggleState): Boolean = when (action) {
        "wifi" -> toggle.wifi
        "bluetooth" -> toggle.bluetooth
        "airplane" -> airplaneModeOn(context)
        "dnd" -> toggle.dnd
        "flashlight" -> toggle.flashlight
        "rotate" -> rotationOn(context)
        else -> false
    }
}
