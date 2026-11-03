package com.depth.breathtrainer.model

/** Table mode, same four options as the Mac/web app's mode segmented control. */
enum class TrainerMode { CO2, O2, SQUARE, CUSTOM }

/** Kind of phase in the flattened sequence — drives color/sound cues, same as the JS `kind`. */
enum class PhaseKind { PREP, HOLD, REST, BREATHE }

/** A single round before flattening, e.g. {hold: 90, rest: 60} or a square-breathing cycle. */
data class RawRound(
    val hold: Int = 0,
    val rest: Int = 0,
    val square: List<Int>? = null // [inhale, hold1, exhale, hold2] seconds, square mode only
)

/** One step in the flattened, linear phase sequence the engine ticks through. */
data class Phase(
    val round: Int,
    val totalRounds: Int,
    val label: String,
    val kind: PhaseKind,
    val seconds: Int
)

/** Persisted table configuration — mirrors `collectSettings()` in the Mac app's JS. */
data class TrainerSettings(
    val mode: TrainerMode = TrainerMode.CO2,
    val maxHoldSeconds: Int = 180, // "3:00"
    val rounds: Int = 8,
    val co2HoldPct: Int = 50,
    val co2RestStart: Int = 120,
    val co2RestEnd: Int = 30,
    val o2HoldStartPct: Int = 50,
    val o2HoldEndPct: Int = 95,
    val o2Rest: Int = 120,
    val sqIn: Int = 4,
    val sqHold1: Int = 4,
    val sqOut: Int = 4,
    val sqHold2: Int = 4,
    val sqCycles: Int = 10,
    val customRounds: String = "60,120\n75,90\n90,60\n105,45",
    val soundOn: Boolean = true,
    val vibrateOn: Boolean = true,
    val prepOn: Boolean = true
)

/**
 * Full snapshot of the running timer, observed by the phone UI, the media session that
 * feeds Android Auto, and the playback notification.
 *
 * [tickStamp] is the SystemClock.elapsedRealtime() at which [remainingSeconds] last became
 * true. Media controllers (Android Auto's progress bar) extrapolate smoothly from it.
 */
data class TimerUiState(
    val phases: List<Phase> = emptyList(),
    val phaseIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val running: Boolean = false,
    val complete: Boolean = false,
    val started: Boolean = false,
    val tickStamp: Long = 0L
) {
    val current: Phase? get() = phases.getOrNull(phaseIndex)
    val next: Phase? get() = phases.getOrNull(phaseIndex + 1)
    val progressFraction: Float
        get() {
            val c = current ?: return 1f
            if (c.seconds <= 0) return 0f
            return (c.seconds - remainingSeconds).toFloat() / c.seconds
        }
    val elapsedInPhaseSeconds: Int
        get() = current?.let { (it.seconds - remainingSeconds).coerceAtLeast(0) } ?: 0
    val isWarning: Boolean
        get() {
            val c = current ?: return false
            return c.kind != PhaseKind.PREP && remainingSeconds in 1..3
        }

    /**
     * Inside the 10-second lead-in to the next section — the window the audio, vibration
     * and flash cues cover. Null outside it.
     */
    val leadInSeconds: Int?
        get() = if (next != null && remainingSeconds in 1..10) remainingSeconds else null

    /** Whether the upcoming section is a breath hold and it's 10s or less away. */
    val holdApproaching: Boolean
        get() = leadInSeconds != null && next?.kind == PhaseKind.HOLD

    /** Round the timer is on, for progress pips: 0 during prep. */
    val currentRound: Int get() = current?.round ?: (phases.lastOrNull()?.totalRounds ?: 0)
    val totalRounds: Int get() = phases.firstOrNull()?.totalRounds ?: 0
    val totalSeconds: Int get() = phases.sumOf { it.seconds }
}

/** Short display names, used on the phone and in Android Auto. */
fun modeLabel(mode: TrainerMode): String = when (mode) {
    TrainerMode.CO2 -> "CO2"
    TrainerMode.O2 -> "O2"
    TrainerMode.SQUARE -> "Square"
    TrainerMode.CUSTOM -> "Custom"
}

fun modeTitle(mode: TrainerMode): String = when (mode) {
    TrainerMode.CO2 -> "CO2 Table"
    TrainerMode.O2 -> "O2 Table"
    TrainerMode.SQUARE -> "Square Breathing"
    TrainerMode.CUSTOM -> "Custom Table"
}

/** "BREATHE / REST" -> "Breathe / Rest" */
fun titleCase(s: String): String =
    s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Car-friendly phase word: "Hold", "Breathe", "Inhale", "Exhale", "Get ready". */
fun Phase.shortTitle(): String = when (kind) {
    PhaseKind.PREP -> "Get ready"
    PhaseKind.HOLD -> "Hold"
    PhaseKind.REST -> "Breathe"
    PhaseKind.BREATHE -> titleCase(label)
}

/** 95 -> "1:35", 7 -> "0:07". */
fun clock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
