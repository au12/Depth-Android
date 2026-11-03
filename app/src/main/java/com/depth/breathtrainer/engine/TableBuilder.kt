package com.depth.breathtrainer.engine

import com.depth.breathtrainer.model.Phase
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.RawRound
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import kotlin.math.roundToInt

object TableBuilder {

    /** Same math as `buildRoundsFromConfig()` in the mac app: builds the raw hold/rest table. */
    fun buildRounds(s: TrainerSettings): List<RawRound> {
        return when (s.mode) {
            TrainerMode.CO2 -> {
                val n = s.rounds.coerceIn(2, 16)
                val holdPct = s.co2HoldPct.coerceIn(10, 100) / 100.0
                val hold = (s.maxHoldSeconds * holdPct).roundToInt()
                (0 until n).map { i ->
                    val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
                    val rest = (s.co2RestStart + (s.co2RestEnd - s.co2RestStart) * t).roundToInt()
                    RawRound(hold = hold, rest = rest)
                }
            }
            TrainerMode.O2 -> {
                val n = s.rounds.coerceIn(2, 16)
                (0 until n).map { i ->
                    val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
                    val pct = (s.o2HoldStartPct + (s.o2HoldEndPct - s.o2HoldStartPct) * t) / 100.0
                    RawRound(hold = (s.maxHoldSeconds * pct).roundToInt(), rest = s.o2Rest)
                }
            }
            TrainerMode.CUSTOM -> {
                s.customRounds.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { line ->
                        val parts = line.split(",").map { it.trim().toIntOrNull() ?: 0 }
                        RawRound(hold = parts.getOrElse(0) { 0 }, rest = parts.getOrElse(1) { 0 })
                    }
            }
            TrainerMode.SQUARE -> {
                val n = s.sqCycles.coerceIn(1, 60)
                val square = listOf(s.sqIn, s.sqHold1, s.sqOut, s.sqHold2)
                (0 until n).map { RawRound(square = square) }
            }
        }
    }

    /** Same as `flattenPhases()`: turns raw rounds into the linear sequence the engine steps through. */
    fun flatten(rounds: List<RawRound>, mode: TrainerMode, prepOn: Boolean): List<Phase> {
        val seq = mutableListOf<Phase>()
        if (prepOn) {
            seq.add(Phase(round = 0, totalRounds = rounds.size, label = "GET READY", kind = PhaseKind.PREP, seconds = 10))
        }
        if (mode == TrainerMode.SQUARE) {
            val labels = listOf("INHALE", "HOLD", "EXHALE", "HOLD")
            val kinds = listOf(PhaseKind.BREATHE, PhaseKind.HOLD, PhaseKind.BREATHE, PhaseKind.HOLD)
            rounds.forEachIndexed { i, r ->
                r.square?.forEachIndexed { j, secs ->
                    if (secs > 0) {
                        seq.add(Phase(round = i + 1, totalRounds = rounds.size, label = labels[j], kind = kinds[j], seconds = secs))
                    }
                }
            }
        } else {
            rounds.forEachIndexed { i, r ->
                if (r.hold > 0) seq.add(Phase(round = i + 1, totalRounds = rounds.size, label = "HOLD", kind = PhaseKind.HOLD, seconds = r.hold))
                if (r.rest > 0) seq.add(Phase(round = i + 1, totalRounds = rounds.size, label = "BREATHE / REST", kind = PhaseKind.REST, seconds = r.rest))
            }
        }
        return seq
    }

    fun formatTime(totalSeconds: Int): String {
        val sec = totalSeconds.coerceAtLeast(0)
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
