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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Central refresh: redraws every NEO widget instance, and keeps a 1-minute tick alive. */
object NeoWidgetUpdater {

    const val ACTION_TICK = "app.lawnchair.neoimport.widgets.ACTION_TICK"

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val providers = listOf(
            NeoClockWidget::class.java,
            NeoGlassWidget::class.java,
            NeoControlCenterWidget::class.java,
            NeoAppWheelWidget::class.java
        )
        for (provider in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    fun startTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = tickPendingIntent(context)
        am.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 60_000L,
            60_000L,
            pi
        )
    }

    fun stopTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPendingIntent(context))
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NeoWidgetTickReceiver::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
