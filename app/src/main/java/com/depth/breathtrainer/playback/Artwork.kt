package com.depth.breathtrainer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.depth.breathtrainer.R
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.modeLabel
import com.depth.breathtrainer.ui.theme.PhasePalette
import com.depth.breathtrainer.ui.theme.Palettes

/**
 * Album-art style tiles for Android Auto and the notification. Rendered once per phase or
 * mode and cached — art that changes every second can flicker on some head units, so the
 * live countdown lives in the title text instead.
 */
class Artwork(private val context: Context) {

    private val size = 320
    private val cache = HashMap<String, Bitmap>()
    private val heavy: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.manrope_extrabold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val medium: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.manrope_semibold) }.getOrNull() ?: Typeface.DEFAULT
    }

    fun forPhase(kind: PhaseKind?, word: String, mode: TrainerMode): Bitmap =
        cache.getOrPut("phase:$kind:$word:$mode") {
            render(Palettes.forKind(kind), word.uppercase(), modeLabel(mode).uppercase(), ring = true)
        }

    fun forMode(mode: TrainerMode): Bitmap = cache.getOrPut("mode:$mode") {
        val palette = when (mode) {
            TrainerMode.CO2 -> Palettes.Hold
            TrainerMode.O2 -> Palettes.Prep
            TrainerMode.SQUARE -> Palettes.Breathe
            TrainerMode.CUSTOM -> Palettes.Approach
        }
        render(palette, modeLabel(mode).uppercase(), "DEPTH", ring = false)
    }

    private fun render(p: PhasePalette, word: String, overline: String, ring: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()

        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, s, p.top.toArgb(), p.bottom.toArgb(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, s, s, bg)

        val accent = p.accent.toArgb()
        if (ring) {
            val stroke = s * 0.055f
            val inset = s * 0.14f
            val oval = RectF(inset, inset, s - inset, s - inset)
            val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke; color = 0x24FFFFFF
            }
            c.drawOval(oval, track)
            val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke; color = accent; strokeCap = Paint.Cap.ROUND
            }
            c.drawArc(oval, -90f, 270f, false, arc)
        } else {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            c.drawCircle(s / 2f, s * 0.78f, s * 0.025f, dot)
        }

        val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = heavy; color = 0xFFEEF4F7.toInt(); textAlign = Paint.Align.CENTER
            textSize = s * if (ring) 0.15f else 0.26f
            letterSpacing = 0.04f
        }
        // Shrink long words ("GET READY", "BREATHE") to fit inside the ring.
        val maxWidth = s * if (ring) 0.5f else 0.8f
        while (wordPaint.measureText(word) > maxWidth && wordPaint.textSize > 12f) wordPaint.textSize -= 2f
        val baseline = s / 2f - (wordPaint.descent() + wordPaint.ascent()) / 2f
        c.drawText(word, s / 2f, baseline, wordPaint)

        val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium; color = accent; textAlign = Paint.Align.CENTER
            textSize = s * 0.058f; letterSpacing = 0.25f
        }
        c.drawText(overline, s / 2f, if (ring) s * 0.36f else s * 0.3f, overPaint)
        return bmp
    }
}
