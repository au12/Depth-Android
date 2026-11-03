package com.depth.breathtrainer.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.VisibleForTesting
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TimerUiState
import com.depth.breathtrainer.model.TrainerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Singleton timer engine. One instance is shared by the phone UI, the media session that
 * Android Auto talks to, and the playback notification, so every surface always shows the
 * same running timer — the direct equivalent of the mac app's single-page JS `state` object.
 *
 * All state changes happen on the main thread (the tick loop runs on Dispatchers.Main), so
 * a skip from the car can never race a tick.
 */
object BreathTimerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var toneGen: ToneGenerator? = null
    private var appContext: Context? = null
    private var store: SettingsStore? = null

    /** When paused mid-second, how far into that second we were — resumed on start. */
    private var carryMs = 0L

    private val _settings = MutableStateFlow(TrainerSettings())
    val settings: StateFlow<TrainerSettings> = _settings

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState

    /**
     * A plain incrementing counter — every increment means "flash the screen once, right
     * now." The UI layer observes this and reacts with a brief flash animation each time
     * it changes; the engine doesn't know anything about how that's drawn.
     */
    private val _flashTrigger = MutableStateFlow(0)
    val flashTrigger: StateFlow<Int> = _flashTrigger

    /** Called once from DepthApp. Loads persisted settings and builds the first table. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        store = SettingsStore(context.applicationContext).also { _settings.value = it.load() }
        loadTable()
    }

    /** Persist new settings and rebuild the table (resets the timer, as on the Mac app). */
    fun updateSettings(newSettings: TrainerSettings) {
        _settings.value = newSettings
        store?.save(newSettings)
        loadTable()
    }

    fun loadTable() {
        tickJob?.cancel()
        carryMs = 0L
        val s = _settings.value
        val phases = TableBuilder.flatten(TableBuilder.buildRounds(s), s.mode, s.prepOn)
        _uiState.value = TimerUiState(
            phases = phases,
            phaseIndex = 0,
            remainingSeconds = phases.firstOrNull()?.seconds ?: 0,
            tickStamp = SystemClock.elapsedRealtime()
        )
    }

    fun start() {
        if (_uiState.value.complete || _uiState.value.phases.isEmpty()) loadTable()
        if (_uiState.value.phases.isEmpty() || _uiState.value.running) return
        val now = SystemClock.elapsedRealtime()
        // Re-anchor the stamp so position = elapsed + (now - stamp) stays continuous.
        _uiState.value = _uiState.value.copy(running = true, started = true, tickStamp = now - carryMs)
        tickJob?.cancel()
        tickJob = scope.launch {
            // Deadline-based so the countdown never drifts, however long the session.
            var nextTick = now + (1000 - carryMs)
            carryMs = 0L
            while (true) {
                delay((nextTick - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                tick(nextTick)
                nextTick += 1000
            }
        }
    }

    fun pause() {
        if (!_uiState.value.running) return
        tickJob?.cancel()
        carryMs = (SystemClock.elapsedRealtime() - _uiState.value.tickStamp).coerceIn(0, 999)
        _uiState.value = _uiState.value.copy(running = false)
    }

    fun togglePlayPause() = if (_uiState.value.running) pause() else start()

    fun reset() = loadTable()

    fun skipPhase() {
        if (_uiState.value.phases.isEmpty() || _uiState.value.complete) return
        carryMs = 0L
        val wasRunning = _uiState.value.running
        advancePhase(SystemClock.elapsedRealtime())
        if (wasRunning && _uiState.value.running) {
            // Restart the second boundary at the moment of the skip.
            tickJob?.cancel()
            _uiState.value = _uiState.value.copy(running = false)
            start()
        }
    }

    @VisibleForTesting
    internal fun tickForTest() = tick(SystemClock.elapsedRealtime())

    /** Tests get a fresh Application per test; rebind to it and reset to defaults. */
    @VisibleForTesting
    internal fun resetForTest(context: Context, settings: TrainerSettings = TrainerSettings()) {
        tickJob?.cancel()
        appContext = context.applicationContext
        store = SettingsStore(context.applicationContext)
        updateSettings(settings)
    }

    private fun tick(stamp: Long) {
        val s = _uiState.value
        val remaining = s.remainingSeconds - 1
        if (remaining in 1..10) {
            when (remaining) {
                // The first second of the lead-in, and the halfway point — three quick
                // pulses instead of one, so they stand out from the plain single pulses
                // used for the rest of the countdown.
                10, 5 -> triplePulse()
                else -> singlePulse()
            }
        }
        if (remaining <= 0) {
            advancePhase(stamp)
        } else {
            _uiState.value = s.copy(remainingSeconds = remaining, tickStamp = stamp)
        }
    }

    /** One short beep/vibrate — used for every lead-in second except the 10s and 5s marks. */
    private fun singlePulse() {
        beep(80)
        vibrate(80)
    }

    /**
     * Three quick beep/vibrate/flash pulses spread across the second, followed by one more
     * flash alone at the same 330ms rhythm — a trailing pulse that closes out the burst
     * visually without adding a fourth beep.
     */
    private fun triplePulse() {
        scope.launch {
            repeat(3) { i ->
                beep(80)
                vibrate(80)
                _flashTrigger.value += 1
                if (i < 2) delay(330)
            }
            delay(330)
            _flashTrigger.value += 1
        }
    }

    private fun advancePhase(stamp: Long) {
        val s = _uiState.value
        val nextIdx = s.phaseIndex + 1
        val cur = s.phases.getOrNull(nextIdx)
        if (cur == null) {
            tickJob?.cancel()
            phaseChangeBeep(null)
            _uiState.value = s.copy(running = false, complete = true, phaseIndex = nextIdx, remainingSeconds = 0, tickStamp = stamp)
            return
        }
        phaseChangeBeep(cur.kind)
        vibrate(1000) // long buzz — a new section just started
        _uiState.value = s.copy(phaseIndex = nextIdx, remainingSeconds = cur.seconds, tickStamp = stamp)
    }

    // ---------- Sound cues ----------

    private fun tone(): ToneGenerator? {
        if (toneGen == null) {
            // STREAM_NOTIFICATION (not STREAM_MUSIC), and Depth never requests audio focus:
            // these are short UI cues layered over whatever music is playing, so the music
            // app is never asked to pause or duck.
            toneGen = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
        }
        return toneGen
    }

    private fun beep(durationMs: Int) {
        if (!_settings.value.soundOn) return
        runCatching { tone()?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs) }
    }

    private fun phaseChangeBeep(kind: PhaseKind?) {
        if (!_settings.value.soundOn) return
        val toneType = when (kind) {
            PhaseKind.HOLD -> ToneGenerator.TONE_PROP_ACK
            PhaseKind.REST, PhaseKind.BREATHE -> ToneGenerator.TONE_PROP_BEEP2
            null -> ToneGenerator.TONE_PROP_NACK // table complete
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        runCatching { tone()?.startTone(toneType, 200) }
    }

    // ---------- Vibration ----------

    private fun vibrator(): Vibrator? {
        val ctx = appContext ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun vibrate(durationMs: Long) {
        if (!_settings.value.vibrateOn) return
        val v = vibrator() ?: return
        if (!v.hasVibrator()) return
        // Max amplitude for a strong pulse (ignored on devices without amplitude control).
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, 255)) }
    }
}
