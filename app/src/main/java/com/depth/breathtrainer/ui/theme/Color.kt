package com.depth.breathtrainer.ui.theme

import androidx.compose.ui.graphics.Color
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TimerUiState

// Base surfaces — a deep-water navy, darkest at the bottom of the screen.
val Abyss = Color(0xFF050D14)
val Deep = Color(0xFF0A1824)
val Surface = Color(0xFF0F2130)
val SurfaceHigh = Color(0xFF163044)
val Ink = Color(0xFFEEF4F7)
val InkMuted = Color(0xFF93A8B6)
val InkFaint = Color(0xFF5A7383)
val Hairline = Color(0x1FFFFFFF)
val Glass = Color(0x14FFFFFF)

val Warn = Color(0xFFFF5D52)

/**
 * Each phase has its own mood: a vertical background gradient plus an accent used for the
 * ring, the phase label, and the primary button. The colors keep the original meanings —
 * red for a hold, green for breathing, amber when a hold is about to start.
 */
data class PhasePalette(val top: Color, val bottom: Color, val accent: Color, val onAccent: Color)

object Palettes {
    val Idle = PhasePalette(Color(0xFF0D2236), Abyss, Color(0xFF6FE3B0), Color(0xFF03140D))
    val Prep = PhasePalette(Color(0xFF0D2840), Abyss, Color(0xFF7CC8FF), Color(0xFF021220))
    val Hold = PhasePalette(Color(0xFF4C121A), Color(0xFF14060A), Color(0xFFFF8A7A), Color(0xFF2A0508))
    val Breathe = PhasePalette(Color(0xFF0E3B2D), Color(0xFF04120E), Color(0xFF6FE3B0), Color(0xFF03140D))
    val Approach = PhasePalette(Color(0xFF4A3608), Color(0xFF150F03), Color(0xFFFFCB5C), Color(0xFF241700))
    val Complete = PhasePalette(Color(0xFF0D2E3A), Abyss, Color(0xFF6FE3B0), Color(0xFF03140D))

    fun forKind(kind: PhaseKind?): PhasePalette = when (kind) {
        PhaseKind.HOLD -> Hold
        PhaseKind.REST, PhaseKind.BREATHE -> Breathe
        PhaseKind.PREP -> Prep
        null -> Idle
    }

    fun forState(state: TimerUiState): PhasePalette {
        if (state.complete) return Complete
        val cur = state.current ?: return Idle
        if (!state.started) return Idle
        if (cur.kind != PhaseKind.HOLD && state.holdApproaching) return Approach
        return forKind(cur.kind)
    }
}
