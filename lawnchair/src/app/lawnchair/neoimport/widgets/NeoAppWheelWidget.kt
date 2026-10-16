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
import com.android.launcher3.R

/**
 * NEO App Wheel widget: header + 2×2 beige-glass quick-launch tiles
 * (Phone, Messages, Camera, Browser).
 *
 * TODO: the full radial app-wheel SCREEN (drawer replacement reading
 * spec.appWheel.apps) is still to build — this widget is the quick-launch
 * slice, not the wheel screen. Nothing here pretends to be the wheel.
 */
class NeoAppWheelWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val tiles = listOf(
            listOf(
                Tile("phone", "Phone", NeoWidgetClickReceiver.ACTION_OPEN_DIALER),
                Tile("message", "Messages", NeoWidgetClickReceiver.ACTION_OPEN_MESSAGES)
            ),
            listOf(
                Tile("camera", "Camera", NeoWidgetClickReceiver.ACTION_OPEN_CAMERA),
                Tile("globe", "Browser", NeoWidgetClickReceiver.ACTION_OPEN_BROWSER)
            )
        )
        val tileIdRows = arrayOf(
            intArrayOf(R.id.neo_wheel_0_0, R.id.neo_wheel_0_1),
            intArrayOf(R.id.neo_wheel_1_0, R.id.neo_wheel_1_1)
        )

        for (id in ids) {
            val size = NeoWidgetRenderer.widgetSize(context, id, 250, 250)
            val headerH = (size.h * 0.25f).toInt().coerceAtLeast(40)
            val rowH = ((size.h - headerH) / 2).coerceAtLeast(40)
            val tileSize = minOf(size.w / 2, rowH).coerceAtLeast(40)

            val views = RemoteViews(context.packageName, R.layout.neo_widget_wheel)
            views.setImageViewBitmap(R.id.neo_wheel_header, NeoWidgetRenderer.drawNeoWheelHeader(size.w, headerH))
            val headerClick = PendingIntent.getBroadcast(
                context, id + 90000, Intent(context, NeoWidgetClickReceiver::class.java).apply {
                    action = NeoWidgetClickReceiver.ACTION_OPEN_CLOCK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.neo_wheel_header, headerClick)

            for (r in tiles.indices) {
                for (c in tiles[r].indices) {
                    val tile = tiles[r][c]
                    val bmp = NeoWidgetRenderer.drawNeoTile(tileSize, tile.icon, tile.label, false, "glass")
                    views.setImageViewBitmap(tileIdRows[r][c], bmp)
                    val click = PendingIntent.getBroadcast(
                        context, id * 100 + r * 10 + c + 80000,
                        Intent(context, NeoWidgetClickReceiver::class.java).apply { action = tile.action },
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

    private data class Tile(val icon: String, val label: String, val action: String)
}
