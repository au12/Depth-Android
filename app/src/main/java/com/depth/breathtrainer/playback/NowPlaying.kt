package com.depth.breathtrainer.playback

import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TimerUiState
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.model.clock
import com.depth.breathtrainer.model.modeLabel
import com.depth.breathtrainer.model.modeTitle
import com.depth.breathtrainer.model.shortTitle

/**
 * What a media surface (Android Auto, the notification, the lock screen) should show for a
 * given timer state. Pure data — no Android types — so it can be tested directly.
 *
 * Android Auto's now-playing screen shows the title largest, the subtitle under it, and a
 * progress bar driven by [durationMs] / [positionMs]. The remaining time is in the title so
 * it is the biggest thing on the car screen.
 */
data class NowPlaying(
    val title: String,
    val subtitle: String,
    val description: String,
    val durationMs: Long,
    val positionMs: Long,
    val kind: PhaseKind?,
    val playing: Boolean
)

object NowPlayingMapper {

    fun map(state: TimerUiState, settings: TrainerSettings): NowPlaying {
        val mode = modeLabel(settings.mode)
        val cur = state.current

        if (state.complete || cur == null) {
            return NowPlaying(
                title = "Session complete",
                subtitle = "${modeTitle(settings.mode)} · ${clock(state.totalSeconds)}",
                description = "Press play to run it again",
                durationMs = 0, positionMs = 0, kind = null, playing = false
            )
        }

        if (!state.started) {
            return NowPlaying(
                title = "Ready · ${modeTitle(settings.mode)}",
                subtitle = "${roundsWord(settings.mode, state.totalRounds)} · ${clock(state.totalSeconds)} total",
                description = "Press play to begin",
                durationMs = cur.seconds * 1000L, positionMs = 0, kind = cur.kind, playing = false
            )
        }

        val title = "${cur.shortTitle()} · ${clock(state.remainingSeconds)}"
        val roundPart = if (cur.kind == PhaseKind.PREP) roundsWord(settings.mode, cur.totalRounds)
        else "Round ${cur.round} of ${cur.totalRounds}"
        val next = state.next
        val lead = state.leadInSeconds
        val subtitle = when {
            !state.running -> "Paused · $mode · $roundPart"
            // The car-screen equivalent of a pop-up: during the 10s lead-in the subtitle
            // announces what's coming, alongside the existing beeps and vibration.
            lead != null && next != null ->
                "${if (next.kind == PhaseKind.HOLD) "Breath hold" else next.shortTitle()} in ${lead}s · $mode"
            else -> "$mode · $roundPart"
        }
        val description = if (next != null) "Next: ${next.shortTitle()} · ${clock(next.seconds)}" else "Final phase"

        return NowPlaying(
            title = title,
            subtitle = subtitle,
            description = description,
            durationMs = cur.seconds * 1000L,
            positionMs = state.elapsedInPhaseSeconds * 1000L,
            kind = cur.kind,
            playing = state.running
        )
    }

    private fun roundsWord(mode: TrainerMode, n: Int): String =
        if (mode == TrainerMode.SQUARE) "$n cycles" else "$n rounds"
}
