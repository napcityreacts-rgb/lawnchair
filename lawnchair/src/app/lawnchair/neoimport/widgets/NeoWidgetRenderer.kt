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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws every NEO-TACTILE widget face as a bitmap. RemoteViews cannot do blur,
 * gradients, felt texture or custom type, so the whole widget face is rendered
 * here with Canvas and handed to the widget as one image per tile.
 *
 * Adapted from the proven Nappy Widgets renderer (same drawing approach, NEO
 * palette: crimson #FF2A44, ink #0C0A0D, beige glass #F5F0E8, plush #19171B).
 */
object NeoWidgetRenderer {

    private val rand = Random(1337)

    // ---------- sizing ----------

    data class Size(val w: Int, val h: Int)

    fun widgetSize(context: Context, appWidgetId: Int, fallbackWdp: Int, fallbackHdp: Int): Size {
        val mgr = AppWidgetManager.getInstance(context)
        val opts: Bundle? = mgr.getAppWidgetOptions(appWidgetId)
        val density = context.resources.displayMetrics.density
        val wdp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallbackWdp) ?: fallbackWdp
        val hdp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHdp) ?: fallbackHdp
        val w = (wdp * density).toInt().coerceAtLeast(100)
        val h = (hdp * density).toInt().coerceAtLeast(60)
        return Size(w, h)
    }

    // ---------- shared drawing helpers ----------

    private fun textPaint(size: Float, color: Int, bold: Boolean = false, condensed: Boolean = false): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color = color
            typeface = when {
                condensed -> Typeface.create("sans-serif-condensed", if (bold) Typeface.BOLD else Typeface.NORMAL)
                bold -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                else -> Typeface.SANS_SERIF
            }
        }
    }

    /** Soft outer glow behind a rounded rect: draw the same shape blurred. */
    private fun glowRect(canvas: Canvas, rect: RectF, radius: Float, color: Int, blur: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(rect, radius, radius, p)
    }

    /** Felt texture: near-black base + thousands of tiny light/dark speckles. */
    private fun feltBase(canvas: Canvas, w: Int, h: Int, baseColor: Int) {
        canvas.drawColor(baseColor)
        val speckle = Paint().apply { style = Paint.Style.FILL }
        repeat(w * h / 90) {
            val x = rand.nextFloat() * w
            val y = rand.nextFloat() * h
            val light = rand.nextBoolean()
            speckle.color = if (light) Color.argb(14, 255, 255, 255) else Color.argb(18, 0, 0, 0)
            val r = 0.6f + rand.nextFloat() * 1.4f
            canvas.drawCircle(x, y, r, speckle)
        }
    }

    /** Glass panel: translucent vertical gradient + bright top edge + thin border. */
    private fun glassPanel(canvas: Canvas, rect: RectF, radius: Float, tintTop: Int, tintBottom: Int, border: Int) {
        val grad = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(tintTop, tintBottom), null, Shader.TileMode.CLAMP
        )
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
        canvas.drawRoundRect(rect, radius, radius, fill)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = border
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        // glossy highlight across the top third
        val glossRect = RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.top + rect.height() * 0.42f)
        val glossGrad = LinearGradient(
            glossRect.left, glossRect.top, glossRect.left, glossRect.bottom,
            intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = glossGrad }
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRoundRect(glossRect, radius, radius, gloss)
        canvas.restore()
    }

    // ---------- line icons (stroke style) ----------

    private fun iconPaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun iconFillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    /** Draw a simple line icon centered at (cx, cy) with box size s. */
    fun drawIcon(canvas: Canvas, kind: String, cx: Float, cy: Float, s: Float, color: Int) {
        val stroke = iconPaint(color, s * 0.09f)
        val half = s / 2f
        when (kind) {
            "wifi" -> {
                // three arcs + dot
                val arc = Paint(stroke).apply { style = Paint.Style.STROKE }
                canvas.drawArc(RectF(cx - half * 0.9f, cy - half * 0.5f, cx + half * 0.9f, cy + half * 1.3f), 225f, 90f, false, arc)
                canvas.drawArc(RectF(cx - half * 0.6f, cy - half * 0.2f, cx + half * 0.6f, cy + half * 1.0f), 225f, 90f, false, arc)
                canvas.drawArc(RectF(cx - half * 0.3f, cy + half * 0.1f, cx + half * 0.3f, cy + half * 0.7f), 225f, 90f, false, arc)
                canvas.drawCircle(cx, cy + half * 0.55f, s * 0.06f, iconFillPaint(color))
            }
            "bluetooth" -> {
                val p = Path().apply {
                    moveTo(cx - half * 0.3f, cy - half * 0.8f)
                    lineTo(cx + half * 0.4f, cy - half * 0.1f)
                    lineTo(cx - half * 0.4f, cy + half * 0.2f)
                    lineTo(cx + half * 0.3f, cy + half * 0.8f)
                    moveTo(cx + half * 0.4f, cy - half * 0.1f)
                    lineTo(cx - half * 0.4f, cy - half * 0.5f)
                    moveTo(cx - half * 0.4f, cy + half * 0.2f)
                    lineTo(cx + half * 0.4f, cy + half * 0.6f)
                }
                canvas.drawPath(p, stroke)
            }
            "flashlight" -> {
                val r = RectF(cx - half * 0.35f, cy - half * 0.7f, cx + half * 0.35f, cy + half * 0.5f)
                canvas.drawRoundRect(r, s * 0.12f, s * 0.12f, stroke)
                canvas.drawLine(cx, cy + half * 0.5f, cx, cy + half * 0.85f, stroke)
                canvas.drawLine(cx - half * 0.45f, cy + half * 0.62f, cx + half * 0.45f, cy + half * 0.62f, stroke)
                canvas.drawLine(cx - half * 0.6f, cy - half * 0.45f, cx - half * 0.85f, cy - half * 0.7f, stroke)
                canvas.drawLine(cx + half * 0.6f, cy - half * 0.45f, cx + half * 0.85f, cy - half * 0.7f, stroke)
            }
            "moon" -> {
                // crescent: filled circle with an offset circle cleared out
                val layer = canvas.saveLayer(null, null)
                canvas.drawCircle(cx, cy, half * 0.8f, iconFillPaint(color))
                val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                }
                canvas.drawCircle(cx + half * 0.45f, cy - half * 0.3f, half * 0.62f, clear)
                canvas.restoreToCount(layer)
            }
            "phone" -> {
                val p = Path().apply {
                    moveTo(cx - half * 0.6f, cy - half * 0.5f)
                    cubicTo(cx - half * 0.9f, cy - half * 0.2f, cx - half * 0.7f, cy + half * 0.5f, cx - half * 0.3f, cy + half * 0.75f)
                    cubicTo(cx, cy + half * 0.95f, cx + half * 0.4f, cy + half * 0.7f, cx + half * 0.65f, cy + half * 0.35f)
                    lineTo(cx + half * 0.35f, cy + half * 0.1f)
                    cubicTo(cx + half * 0.1f, cy + half * 0.35f, cx - half * 0.15f, cy + half * 0.45f, cx - half * 0.35f, cy + half * 0.15f)
                    lineTo(cx - half * 0.6f, cy - half * 0.5f)
                    close()
                }
                canvas.drawPath(p, iconFillPaint(color))
            }
            "message" -> {
                val r = RectF(cx - half * 0.8f, cy - half * 0.6f, cx + half * 0.8f, cy + half * 0.4f)
                canvas.drawRoundRect(r, s * 0.15f, s * 0.15f, stroke)
                val tail = Path().apply {
                    moveTo(cx - half * 0.4f, cy + half * 0.4f)
                    lineTo(cx - half * 0.4f, cy + half * 0.85f)
                    lineTo(cx - half * 0.05f, cy + half * 0.4f)
                    close()
                }
                canvas.drawPath(tail, stroke)
                canvas.drawLine(cx - half * 0.5f, cy - half * 0.1f, cx + half * 0.5f, cy - half * 0.1f, stroke)
                canvas.drawLine(cx - half * 0.5f, cy + half * 0.15f, cx + half * 0.2f, cy + half * 0.15f, stroke)
            }
            "camera" -> {
                val r = RectF(cx - half * 0.85f, cy - half * 0.45f, cx + half * 0.85f, cy + half * 0.6f)
                canvas.drawRoundRect(r, s * 0.14f, s * 0.14f, stroke)
                canvas.drawLine(cx - half * 0.4f, cy - half * 0.45f, cx - half * 0.25f, cy - half * 0.75f, stroke)
                canvas.drawLine(cx - half * 0.25f, cy - half * 0.75f, cx + half * 0.25f, cy - half * 0.75f, stroke)
                canvas.drawLine(cx + half * 0.25f, cy - half * 0.75f, cx + half * 0.4f, cy - half * 0.45f, stroke)
                canvas.drawCircle(cx, cy + half * 0.08f, half * 0.38f, stroke)
                canvas.drawCircle(cx, cy + half * 0.08f, half * 0.12f, iconFillPaint(color))
            }
            "globe" -> {
                canvas.drawCircle(cx, cy, half * 0.8f, stroke)
                canvas.drawOval(RectF(cx - half * 0.35f, cy - half * 0.8f, cx + half * 0.35f, cy + half * 0.8f), stroke)
                canvas.drawLine(cx - half * 0.8f, cy, cx + half * 0.8f, cy, stroke)
            }
            "airplane" -> {
                // front-view plane: fuselage + wings + tail
                canvas.drawLine(cx, cy - half * 0.8f, cx, cy + half * 0.8f, stroke)
                canvas.drawLine(cx - half * 0.8f, cy - half * 0.1f, cx + half * 0.8f, cy - half * 0.1f, stroke)
                canvas.drawLine(cx - half * 0.4f, cy + half * 0.55f, cx + half * 0.4f, cy + half * 0.55f, stroke)
                canvas.drawCircle(cx, cy - half * 0.8f, s * 0.07f, iconFillPaint(color))
            }
            "rotate" -> {
                val arcRect = RectF(cx - half * 0.7f, cy - half * 0.7f, cx + half * 0.7f, cy + half * 0.7f)
                canvas.drawArc(arcRect, -60f, 270f, false, stroke)
                // arrowhead at arc end
                val ax = cx + half * 0.7f
                val ay = cy - half * 0.35f
                canvas.drawLine(ax, ay, ax - half * 0.35f, ay - half * 0.15f, stroke)
                canvas.drawLine(ax, ay, ax - half * 0.05f, ay + half * 0.35f, stroke)
            }
            "timer" -> {
                canvas.drawCircle(cx, cy + half * 0.1f, half * 0.65f, stroke)
                canvas.drawLine(cx, cy - half * 0.75f, cx, cy - half * 0.45f, stroke)
                canvas.drawLine(cx - half * 0.2f, cy - half * 0.75f, cx + half * 0.2f, cy - half * 0.75f, stroke)
                canvas.drawLine(cx, cy + half * 0.1f, cx, cy - half * 0.35f, stroke)
                canvas.drawLine(cx, cy + half * 0.1f, cx + half * 0.3f, cy + half * 0.25f, stroke)
            }
            "music" -> {
                canvas.drawCircle(cx - half * 0.45f, cy + half * 0.4f, half * 0.22f, iconFillPaint(color))
                canvas.drawCircle(cx + half * 0.45f, cy + half * 0.4f, half * 0.22f, iconFillPaint(color))
                canvas.drawLine(cx - half * 0.45f, cy + half * 0.4f, cx - half * 0.45f, cy - half * 0.7f, stroke)
                canvas.drawLine(cx + half * 0.45f, cy + half * 0.4f, cx + half * 0.45f, cy - half * 0.7f, stroke)
                canvas.drawLine(cx - half * 0.45f, cy - half * 0.7f, cx + half * 0.45f, cy - half * 0.7f, stroke)
            }
            "maps" -> {
                val r = RectF(cx - half * 0.8f, cy - half * 0.6f, cx + half * 0.8f, cy + half * 0.6f)
                canvas.drawRoundRect(r, s * 0.1f, s * 0.1f, stroke)
                canvas.drawLine(cx - half * 0.25f, cy - half * 0.6f, cx - half * 0.25f, cy + half * 0.6f, stroke)
                canvas.drawLine(cx + half * 0.25f, cy - half * 0.6f, cx + half * 0.25f, cy + half * 0.6f, stroke)
                canvas.drawCircle(cx, cy - half * 0.1f, half * 0.22f, stroke)
                canvas.drawCircle(cx, cy - half * 0.1f, half * 0.07f, iconFillPaint(color))
            }
            "settings" -> {
                canvas.drawCircle(cx, cy, half * 0.45f, stroke)
                for (i in 0 until 8) {
                    val a = (Math.PI / 4 * i).toFloat()
                    val x1 = cx + kotlin.math.cos(a) * half * 0.55f
                    val y1 = cy + kotlin.math.sin(a) * half * 0.55f
                    val x2 = cx + kotlin.math.cos(a) * half * 0.85f
                    val y2 = cy + kotlin.math.sin(a) * half * 0.85f
                    canvas.drawLine(x1, y1, x2, y2, stroke)
                }
            }
        }
    }

    // ---------- clock widgets ----------

    private fun timeStrings(): Pair<String, String> {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR)
        val displayHour = if (hour == 0) 12 else hour
        val minute = cal.get(Calendar.MINUTE)
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val time = String.format(Locale.US, "%d:%02d", displayHour, minute)
        val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US) ?: ""
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) ?: ""
        val date = "$day, $month ${cal.get(Calendar.DAY_OF_MONTH)}  ·  $amPm"
        return time to date
    }

    /** NEO editorial clock: huge condensed time, crimson accent, vertical label. */
    fun drawNeoClock(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0C0A0D"))
        val (time, date) = timeStrings()

        // thin crimson rule on the left
        val rule = Paint().apply { color = Color.parseColor("#FF2A44"); style = Paint.Style.FILL }
        c.drawRect(0f, 0f, w * 0.016f, h.toFloat(), rule)

        // big time
        val padX = w * 0.07f
        val timePaint = textPaint(h * 0.52f, Color.WHITE, bold = true, condensed = true)
        val timeY = h * 0.58f
        c.drawText(time, padX, timeY, timePaint)

        // date line under it
        val datePaint = textPaint(h * 0.15f, Color.parseColor("#B9B9BE"))
        c.drawText(date.uppercase(Locale.US), padX, h * 0.82f, datePaint)

        // small crimson dot + NEO tag top right
        val tagPaint = textPaint(h * 0.13f, Color.parseColor("#FF2A44"), bold = true)
        val tag = "NEO"
        val tagW = tagPaint.measureText(tag)
        c.drawText(tag, w - padX - tagW, h * 0.22f, tagPaint)
        c.drawCircle(w - padX - tagW - w * 0.035f, h * 0.17f, h * 0.045f, rule)

        // vertical "NEO-TACTILE" text on the right edge
        val vertPaint = textPaint(h * 0.11f, Color.parseColor("#55555C"), bold = true)
        c.save()
        c.rotate(90f, w.toFloat(), 0f)
        // after a 90° rotation about (w,0), +y maps to -x: positive y keeps the
        // baseline inside the right edge, +x runs downward on screen
        c.drawText("NEO-TACTILE", w + h * 0.12f, h * 0.05f, vertPaint)
        c.restore()

        return bmp
    }

    /** NEO glass clock: crimson-tinted frosted panel, glossy highlight, neon edge. */
    fun drawNeoGlassClock(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val radius = min(w, h) * 0.22f
        val rect = RectF(w * 0.02f, h * 0.04f, w * 0.98f, h * 0.96f)

        // crimson neon glow behind
        glowRect(c, rect, radius, Color.argb(90, 255, 42, 68), radius * 0.5f)

        // glass panel: deep crimson translucent
        glassPanel(
            c, rect, radius,
            Color.argb(150, 110, 20, 30),
            Color.argb(120, 30, 5, 10),
            Color.argb(140, 255, 180, 190)
        )

        val (time, date) = timeStrings()
        val cx = w / 2f
        val timePaint = textPaint(h * 0.46f, Color.WHITE, bold = true, condensed = true)
        timePaint.textAlign = Paint.Align.CENTER
        c.drawText(time, cx, h * 0.60f, timePaint)

        val datePaint = textPaint(h * 0.14f, Color.argb(210, 255, 200, 210))
        datePaint.textAlign = Paint.Align.CENTER
        c.drawText(date.uppercase(Locale.US), cx, h * 0.84f, datePaint)
        return bmp
    }

    // ---------- NEO-TACTILE control center ----------

    data class ToggleState(val wifi: Boolean, val bluetooth: Boolean, val flashlight: Boolean, val dnd: Boolean)

    /** Control-center header: ink background, NEO-TACTILE tag, big time + date. */
    fun drawNeoHeader(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0C0A0D"))
        val (time, date) = timeStrings()

        // crimson rule on the left
        val rule = Paint().apply { color = Color.parseColor("#FF2A44"); style = Paint.Style.FILL }
        c.drawRect(0f, 0f, w * 0.012f, h.toFloat(), rule)

        val padX = w * 0.05f
        val tagPaint = textPaint(h * 0.22f, Color.parseColor("#FF2A44"), bold = true)
        c.drawText("NEO-TACTILE", padX, h * 0.34f, tagPaint)

        val timePaint = textPaint(h * 0.52f, Color.WHITE, bold = true, condensed = true)
        c.drawText(time, padX, h * 0.88f, timePaint)

        val datePaint = textPaint(h * 0.20f, Color.parseColor("#B9B9BE"))
        val dateW = datePaint.measureText(date.uppercase(Locale.US))
        c.drawText(date.uppercase(Locale.US), w - padX - dateW, h * 0.34f, datePaint)
        return bmp
    }

    /** App-wheel header: ink background, APP WHEEL tag, big time. */
    fun drawNeoWheelHeader(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#0C0A0D"))
        val (time, _) = timeStrings()

        val rule = Paint().apply { color = Color.parseColor("#FF2A44"); style = Paint.Style.FILL }
        c.drawRect(0f, 0f, w * 0.012f, h.toFloat(), rule)

        val padX = w * 0.05f
        val tagPaint = textPaint(h * 0.22f, Color.parseColor("#FF2A44"), bold = true)
        c.drawText("APP WHEEL", padX, h * 0.34f, tagPaint)

        val timePaint = textPaint(h * 0.52f, Color.WHITE, bold = true, condensed = true)
        c.drawText(time, padX, h * 0.88f, timePaint)
        return bmp
    }

    /**
     * One NEO-TACTILE tile.
     * style "plush": ink-noir tile, crimson glow ring when on.
     * style "glass": beige-glass tile (cream), dark icon — quick-launch row.
     */
    fun drawNeoTile(size: Int, icon: String, label: String, on: Boolean, tileStyle: String): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = size * 0.05f
        val rect = RectF(pad, pad, size - pad, size - pad)
        val radius = size * 0.20f

        if (tileStyle == "glass") {
            // beige glass: warm cream gradient + soft gold border
            glassPanel(
                c, rect, radius,
                Color.argb(235, 245, 240, 232),
                Color.argb(220, 212, 196, 160),
                Color.argb(180, 255, 255, 255)
            )
            drawIcon(c, icon, size / 2f, size * 0.38f, size * 0.40f, Color.parseColor("#2B0307"))
            val labelPaint = textPaint(size * 0.115f, Color.parseColor("#2B0307"), bold = true)
            labelPaint.textAlign = Paint.Align.CENTER
            c.drawText(label, size / 2f, size * 0.86f, labelPaint)
        } else {
            // plush ink-noir tile
            feltBase(c, size, size, Color.TRANSPARENT)
            val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#19171B") }
            c.drawRoundRect(rect, radius, radius, base)
            if (on) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = size * 0.03f
                    color = Color.argb(140, 255, 42, 68)
                    maskFilter = BlurMaskFilter(size * 0.03f, BlurMaskFilter.Blur.NORMAL)
                }
                c.drawRoundRect(rect, radius, radius, glow)
            }
            val iconColor = if (on) Color.parseColor("#FF2A44") else Color.parseColor("#9A9AA0")
            drawIcon(c, icon, size / 2f, size * 0.38f, size * 0.40f, iconColor)
            val labelPaint = textPaint(size * 0.115f, if (on) Color.WHITE else Color.parseColor("#9A9AA0"), bold = true)
            labelPaint.textAlign = Paint.Align.CENTER
            c.drawText(label.uppercase(Locale.US), size / 2f, size * 0.86f, labelPaint)
        }
        return bmp
    }

    @Suppress("unused")
    fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }
}
