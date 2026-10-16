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

/** NEO glass panel: crimson-tinted frosted clock. Tap opens Clock. */
class NeoGlassWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val size = NeoWidgetRenderer.widgetSize(context, id, 250, 110)
            val bmp = NeoWidgetRenderer.drawNeoGlassClock(size.w, size.h)
            val views = RemoteViews(context.packageName, R.layout.neo_widget_single)
            views.setImageViewBitmap(R.id.neo_widget_image, bmp)
            val click = PendingIntent.getBroadcast(
                context, id, Intent(context, NeoWidgetClickReceiver::class.java).apply {
                    action = NeoWidgetClickReceiver.ACTION_OPEN_CLOCK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.neo_widget_image, click)
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onEnabled(context: Context) {
        NeoWidgetUpdater.startTick(context)
    }

    override fun onDisabled(context: Context) {
        NeoWidgetUpdater.stopTick(context)
    }
}
