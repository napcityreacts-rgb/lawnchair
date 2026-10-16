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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.lawnchair.neoimport.NeoControlCenterActivity
import app.lawnchair.neoimport.UiImporter
import com.android.launcher3.R

/**
 * NEO-TACTILE Control Center widget: header (clock) + 3 rows of 4 tiles.
 * Tiles come from the last imported .neoui.zip spec when one exists, otherwise
 * the built-in NEO-TACTILE defaults (12 tiles: WiFi, Bluetooth, Airplane, DND,
 * Torch, Rotate, Camera, Timer, Messages, Music, Maps, Settings).
 *
 * Header tap opens the [NeoControlCenterActivity] overlay.
 * TODO: swipe-down gesture hook to open the overlay from anywhere — not faked.
 */
class NeoControlCenterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val toggle = NeoWidgetState.toggleState(context)
        val tiles = tilesFor(context)

        val tileIdRows = arrayOf(
            intArrayOf(R.id.neo_cc_0_0, R.id.neo_cc_0_1, R.id.neo_cc_0_2, R.id.neo_cc_0_3),
            intArrayOf(R.id.neo_cc_1_0, R.id.neo_cc_1_1, R.id.neo_cc_1_2, R.id.neo_cc_1_3),
            intArrayOf(R.id.neo_cc_2_0, R.id.neo_cc_2_1, R.id.neo_cc_2_2, R.id.neo_cc_2_3)
        )

        for (id in ids) {
            val size = NeoWidgetRenderer.widgetSize(context, id, 300, 300)
            val headerH = (size.h * 0.25f).toInt().coerceAtLeast(40)
            val rowH = ((size.h - headerH) / 3).coerceAtLeast(40)
            val tileSize = minOf(size.w / 4, rowH).coerceAtLeast(40)

            val views = RemoteViews(context.packageName, R.layout.neo_widget_cc)

            // header → opens the overlay activity
            val headerBmp = NeoWidgetRenderer.drawNeoHeader(size.w, headerH)
            views.setImageViewBitmap(R.id.neo_cc_header, headerBmp)
            val headerClick = PendingIntent.getActivity(
                context, id + 90000,
                Intent(context, NeoControlCenterActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.neo_cc_header, headerClick)

            // tiles
            for (r in tiles.indices) {
                for (c in tiles[r].indices) {
                    val tile = tiles[r][c]
                    val on = NeoWidgetState.isOnForAction(context, tile.action, toggle)
                    val bmp = NeoWidgetRenderer.drawNeoTile(tileSize, tile.icon, tile.label, on, tile.style)
                    views.setImageViewBitmap(tileIdRows[r][c], bmp)
                    val broadcastAction = NeoWidgetClickReceiver.actionForKey(tile.action) ?: continue
                    val click = PendingIntent.getBroadcast(
                        context, id * 100 + r * 10 + c + 70000,
                        Intent(context, NeoWidgetClickReceiver::class.java).apply { action = broadcastAction },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(tileIdRows[r][c], click)
                }
            }
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onEnabled(context: Context) {
        NeoWidgetUpdater.startTick(context)
    }

    override fun onDisabled(context: Context) {
        NeoWidgetUpdater.stopTick(context)
    }

    companion object {
        /** 12 tiles in 3 rows of 4: imported spec wins, built-in defaults otherwise. */
        fun tilesFor(context: Context): List<List<Tile>> {
            val imported = UiImporter.readImportedSpec(context)?.controlCenter?.tiles
                ?.takeIf { it.size == 12 }
                ?.map { Tile(it.icon, it.label, it.action, it.style) }
                ?.chunked(4)
            if (imported != null) return imported
            return listOf(
                listOf(
                    Tile("wifi", "WiFi", "wifi", "plush"),
                    Tile("bluetooth", "BT", "bluetooth", "plush"),
                    Tile("airplane", "Airplane", "airplane", "plush"),
                    Tile("moon", "DND", "dnd", "plush")
                ),
                listOf(
                    Tile("flashlight", "Torch", "flashlight", "plush"),
                    Tile("rotate", "Rotate", "rotate", "plush"),
                    Tile("camera", "Camera", "camera", "plush"),
                    Tile("timer", "Timer", "timer", "plush")
                ),
                listOf(
                    Tile("message", "Messages", "messages", "glass"),
                    Tile("music", "Music", "music", "glass"),
                    Tile("maps", "Maps", "maps", "glass"),
                    Tile("settings", "Settings", "settings", "glass")
                )
            )
        }

        data class Tile(val icon: String, val label: String, val action: String, val style: String)
    }
}
