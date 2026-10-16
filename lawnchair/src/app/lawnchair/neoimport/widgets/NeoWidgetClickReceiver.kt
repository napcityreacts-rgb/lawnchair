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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.lawnchair.neoimport.NeoControlCenterActivity

/** Handles every tap on a NEO widget tile. */
class NeoWidgetClickReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_FLASHLIGHT = "app.lawnchair.neoimport.widgets.ACTION_TOGGLE_FLASHLIGHT"
        const val ACTION_OPEN_WIFI_PANEL = "app.lawnchair.neoimport.widgets.ACTION_OPEN_WIFI_PANEL"
        const val ACTION_OPEN_BT_PANEL = "app.lawnchair.neoimport.widgets.ACTION_OPEN_BT_PANEL"
        const val ACTION_OPEN_DND_SETTINGS = "app.lawnchair.neoimport.widgets.ACTION_OPEN_DND_SETTINGS"
        const val ACTION_OPEN_DIALER = "app.lawnchair.neoimport.widgets.ACTION_OPEN_DIALER"
        const val ACTION_OPEN_MESSAGES = "app.lawnchair.neoimport.widgets.ACTION_OPEN_MESSAGES"
        const val ACTION_OPEN_CAMERA = "app.lawnchair.neoimport.widgets.ACTION_OPEN_CAMERA"
        const val ACTION_OPEN_BROWSER = "app.lawnchair.neoimport.widgets.ACTION_OPEN_BROWSER"
        const val ACTION_OPEN_CLOCK = "app.lawnchair.neoimport.widgets.ACTION_OPEN_CLOCK"
        const val ACTION_OPEN_AIRPLANE_SETTINGS = "app.lawnchair.neoimport.widgets.ACTION_OPEN_AIRPLANE_SETTINGS"
        const val ACTION_OPEN_DISPLAY_SETTINGS = "app.lawnchair.neoimport.widgets.ACTION_OPEN_DISPLAY_SETTINGS"
        const val ACTION_OPEN_TIMER = "app.lawnchair.neoimport.widgets.ACTION_OPEN_TIMER"
        const val ACTION_OPEN_MUSIC = "app.lawnchair.neoimport.widgets.ACTION_OPEN_MUSIC"
        const val ACTION_OPEN_MAPS = "app.lawnchair.neoimport.widgets.ACTION_OPEN_MAPS"
        const val ACTION_OPEN_SYSTEM_SETTINGS = "app.lawnchair.neoimport.widgets.ACTION_OPEN_SYSTEM_SETTINGS"

        /** Maps a spec action key to this receiver's broadcast action. */
        fun actionForKey(key: String): String? = when (key) {
            "flashlight" -> ACTION_TOGGLE_FLASHLIGHT
            "wifi" -> ACTION_OPEN_WIFI_PANEL
            "bluetooth" -> ACTION_OPEN_BT_PANEL
            "dnd" -> ACTION_OPEN_DND_SETTINGS
            "phone" -> ACTION_OPEN_DIALER
            "messages" -> ACTION_OPEN_MESSAGES
            "camera" -> ACTION_OPEN_CAMERA
            "browser" -> ACTION_OPEN_BROWSER
            "clock" -> ACTION_OPEN_CLOCK
            "airplane" -> ACTION_OPEN_AIRPLANE_SETTINGS
            "rotate" -> ACTION_OPEN_DISPLAY_SETTINGS
            "timer" -> ACTION_OPEN_TIMER
            "music" -> ACTION_OPEN_MUSIC
            "maps" -> ACTION_OPEN_MAPS
            "settings" -> ACTION_OPEN_SYSTEM_SETTINGS
            else -> null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_FLASHLIGHT -> toggleFlashlight(context)
            ACTION_OPEN_WIFI_PANEL -> safeStart(context, NeoWidgetState.wifiPanelIntent())
            ACTION_OPEN_BT_PANEL -> safeStart(context, NeoWidgetState.bluetoothPanelIntent())
            ACTION_OPEN_DND_SETTINGS -> safeStart(context, NeoWidgetState.dndSettingsIntent())
            ACTION_OPEN_DIALER -> safeStart(context, NeoWidgetState.dialerIntent())
            ACTION_OPEN_MESSAGES -> safeStart(context, NeoWidgetState.messagesIntent())
            ACTION_OPEN_CAMERA -> safeStart(context, NeoWidgetState.cameraIntent())
            ACTION_OPEN_BROWSER -> safeStart(context, NeoWidgetState.browserIntent())
            ACTION_OPEN_CLOCK -> safeStart(context, NeoWidgetState.clockIntent())
            ACTION_OPEN_AIRPLANE_SETTINGS -> safeStart(context, NeoWidgetState.airplaneSettingsIntent())
            ACTION_OPEN_DISPLAY_SETTINGS -> safeStart(context, NeoWidgetState.displaySettingsIntent())
            ACTION_OPEN_TIMER -> safeStart(context, NeoWidgetState.timerIntent())
            ACTION_OPEN_MUSIC -> safeStart(context, NeoWidgetState.musicIntent())
            ACTION_OPEN_MAPS -> safeStart(context, NeoWidgetState.mapsIntent())
            ACTION_OPEN_SYSTEM_SETTINGS -> safeStart(context, NeoWidgetState.systemSettingsIntent())
        }
        // refresh all widgets so toggle states redraw
        NeoWidgetUpdater.updateAll(context)
    }

    private fun toggleFlashlight(context: Context) {
        if (!NeoWidgetState.hasCameraPermission(context)) {
            // No camera permission: open the Control Center overlay, which can
            // request the permission and then toggle the torch.
            safeStart(context, Intent(context, NeoControlCenterActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }
        NeoWidgetState.toggleFlashlight(context)
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // no app handles it: do nothing rather than crash
        }
    }
}
