package com.depth.breathtrainer.ui

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.depth.breathtrainer.engine.BreathTimerEngine
import com.depth.breathtrainer.engine.TableBuilder
import com.depth.breathtrainer.model.Phase
import com.depth.breathtrainer.model.PhaseKind
import com.depth.breathtrainer.model.TimerUiState
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.clock
import com.depth.breathtrainer.model.modeLabel
import com.depth.breathtrainer.model.titleCase
import com.depth.breathtrainer.playback.DepthMediaService
import com.depth.breathtrainer.ui.components.CircleButton
import com.depth.breathtrainer.ui.components.RoundPips
import com.depth.breathtrainer.ui.components.TimerRing
import com.depth.breathtrainer.ui.components.VSpace
import com.depth.breathtrainer.ui.theme.Ink
import com.depth.breathtrainer.ui.theme.InkMuted
import com.depth.breathtrainer.ui.theme.Manrope
import com.depth.breathtrainer.ui.theme.Palettes
import com.depth.breathtrainer.ui.theme.Surface as SheetSurface
import com.depth.breathtrainer.ui.theme.TABULAR
import com.depth.breathtrainer.ui.theme.Warn

// Ring target size — same in both orientations. The ring is centered on the full screen,
// and everything else is positioned around it.
private val RING_BASELINE = 300.dp
private val RING_MIN = 90.dp
private const val COLOR_MS = 700

/** Everything the layout needs to know about orientation and window size. */
private data class Frame(
    val landscape: Boolean,
    val reversePortrait: Boolean,
    val compact: Boolean,
    val ultraCompact: Boolean
)

/**
 * Colors for the current moment, already animated. [accent] turns to the warning color in
 * the final three seconds of a hold or breathing phase.
 */
private data class Look(val top: Color, val bottom: Color, val accent: Color, val onAccent: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerScreen() {
    val state by BreathTimerEngine.uiState.collectAsStateWithLifecycle()
    val settings by BreathTimerEngine.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Keep the screen on for the duration of an active session.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    val palette = Palettes.forState(state)
    val top by animateColorAsState(palette.top, tween(COLOR_MS), label = "top")
    val bottom by animateColorAsState(palette.bottom, tween(COLOR_MS), label = "bottom")
    val accent by animateColorAsState(if (state.isWarning) Warn else palette.accent, tween(300), label = "accent")
    val onAccent by animateColorAsState(palette.onAccent, tween(COLOR_MS), label = "onAccent")
    val look = Look(top, bottom, accent, onAccent)

    // Screen flash, synced to the engine's triple pulses at the 10s and 5s lead-in marks.
    // Each increment restarts the fade, so rapid triggers stay distinct flashes.
    val flashTrigger by BreathTimerEngine.flashTrigger.collectAsStateWithLifecycle()
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flashTrigger) {
        if (flashTrigger == 0) return@LaunchedEffect
        flashAlpha.snapTo(0.6f)
        flashAlpha.animateTo(0f, tween(220))
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val surfaceRotation by rememberSurfaceRotation()

    val controls = Controls(
        running = state.running,
        onPlayPause = {
            if (state.running) BreathTimerEngine.pause()
            else {
                DepthMediaService.ensureStarted(context)
                BreathTimerEngine.start()
            }
        },
        onReset = BreathTimerEngine::reset,
        onSkip = BreathTimerEngine::skipPhase,
        onSettings = { showSettings = true }
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(look.top, look.bottom)))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val frame = Frame(
                landscape = isLandscape,
                reversePortrait = !isLandscape && surfaceRotation == Surface.ROTATION_180,
                compact = maxHeight < 380.dp || maxWidth < 260.dp,
                ultraCompact = maxHeight < 220.dp || maxWidth < 180.dp
            )
            val margin = if (frame.compact) 8.dp else 16.dp
            val available = (minOf(maxWidth, maxHeight) - margin * 2).coerceAtLeast(0.dp)
            val ringSize = available.coerceAtMost(RING_BASELINE)
            val scale = (available / RING_BASELINE).coerceIn(0.32f, 1f)

            // The ring, dead-centered on the full screen in every orientation.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TimerCore(state, look, available, ringSize, scale)
            }

            if (frame.landscape) {
                LandscapeLayout(state, settings.mode, look, frame, ringSize, scale, controls, maxWidth)
            } else {
                PortraitLayout(state, settings.mode, look, frame, ringSize, scale, available, controls, maxHeight)
            }
        }

        if (flashAlpha.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha.value)))
        }
    }

    if (showSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = SheetSurface,
            dragHandle = {
                Box(
                    Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.22f))
                )
            }
        ) {
            SettingsSheet(
                settings = settings,
                onApply = { newSettings ->
                    BreathTimerEngine.updateSettings(newSettings)
                    showSettings = false
                }
            )
        }
    }
}

private data class Controls(
    val running: Boolean,
    val onPlayPause: () -> Unit,
    val onReset: () -> Unit,
    val onSkip: () -> Unit,
    val onSettings: () -> Unit
)

// ---------------------------------------------------------------------------------------
// Ring + time
// ---------------------------------------------------------------------------------------

/**
 * Renders the ring + countdown, scaled to the space available. Below [RING_MIN] the ring is
 * dropped and only the time is shown — legibility wins once space is tight.
 */
@Composable
private fun TimerCore(state: TimerUiState, look: Look, available: Dp, ringSize: Dp, scale: Float) {
    val timeStyle = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = (66 * scale).coerceAtLeast(24f).sp, letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR, color = if (state.isWarning) Warn else Ink
    )
    val caption = when {
        state.complete -> TableBuilder.formatTime(state.totalSeconds) + " TOTAL"
        state.running -> "REMAINING"
        state.started -> "PAUSED"
        else -> "READY"
    }
    val center: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (state.complete) "Done" else TableBuilder.formatTime(state.remainingSeconds),
                style = timeStyle, maxLines = 1
            )
            if (ringSize >= 170.dp) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (12 * scale).coerceAtLeast(9f).sp),
                    color = if (state.started && !state.running && !state.complete) look.accent else InkMuted,
                    letterSpacing = 2.sp
                )
            }
        }
    }
    if (available >= RING_MIN) {
        TimerRing(
            progress = if (state.complete) 1f else state.progressFraction,
            accent = look.accent,
            diameter = ringSize,
            strokeWidth = (26 * scale).dp.coerceAtLeast(6.dp)
        ) { center() }
    } else {
        center()
    }
}

// ---------------------------------------------------------------------------------------
// Portrait
// ---------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PortraitLayout(
    state: TimerUiState,
    mode: TrainerMode,
    look: Look,
    frame: Frame,
    ringSize: Dp,
    scale: Float,
    available: Dp,
    controls: Controls,
    height: Dp
) {
    val topBarH = when {
        frame.ultraCompact -> 0.dp
        frame.compact -> 44.dp
        else -> 64.dp
    }
    val primary = if (frame.compact) 56.dp else 80.dp
    val controlsH = primary + if (frame.compact) 24.dp else 32.dp
    // Normal portrait: title top, controls bottom. Upside-down: controls on top (with a
    // moderate gap from the edge), title at the bottom.
    val topChrome = if (frame.reversePortrait) controlsH else topBarH
    val bottomChrome = if (frame.reversePortrait) topBarH else controlsH

    val ringTop = ((height - ringSize) / 2).coerceAtLeast(0.dp)
    val ringBottom = height - ringTop
    val blockSpacing = 20.dp

    val cur = state.current
    if (cur != null || state.complete) {
        // Mode, phase, round: one block, bottom-anchored 20dp above the ring, with the same
        // 20dp between its lines. Type scales down if a short screen can't fit it.
        val gap = (ringTop - blockSpacing - topChrome).coerceAtLeast(0.dp)
        val fontScale = LocalDensity.current.fontScale
        // Manrope's line box is ~1.4em; the 40dp is the two inter-line gaps.
        val natural = ((52 + 52 + 34) * 1.4f * fontScale * scale + 40).dp
        val fit = (gap / natural).coerceIn(0.45f, 1f)
        val big = (52 * scale * fit).coerceAtLeast(18f)

        Box(
            Modifier.align(Alignment.TopCenter).padding(top = topChrome).height(gap).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Unbounded so a tight fit overflows upward rather than clipping the last line.
            Column(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!frame.compact) {
                    Text(
                        modeLabel(mode), color = InkMuted, maxLines = 1,
                        style = labelStyle(big, FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(blockSpacing * fit))
                }
                Text(
                    if (state.complete) "Complete" else cur!!.label,
                    color = look.accent, maxLines = 1,
                    style = labelStyle(big, FontWeight.ExtraBold)
                )
                if (!frame.compact && available > 170.dp) {
                    Spacer(Modifier.height(blockSpacing * fit))
                    Text(
                        roundText(state, cur),
                        color = Ink, maxLines = 1,
                        style = labelStyle((34 * scale * fit).coerceAtLeast(16f), FontWeight.SemiBold, 0f)
                    )
                }
            }
        }

        // Below the ring: what's next, then round progress.
        val belowGap = height - ringBottom - bottomChrome
        if (!frame.compact && available > 170.dp && belowGap > 36.dp) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = ringBottom + 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    nextText(state), color = InkMuted, maxLines = 1,
                    style = labelStyle((17 * scale).coerceAtLeast(12f), FontWeight.SemiBold, 0.2f)
                )
                if (belowGap > 72.dp && state.totalRounds > 1) {
                    VSpace(14.dp)
                    RoundPips(
                        current = if (state.complete) state.totalRounds + 1 else state.currentRound,
                        total = state.totalRounds, accent = look.accent
                    )
                }
            }
        }
    }

    // Chrome.
    if (!frame.ultraCompact) {
        TopBar(
            compact = frame.compact,
            onSettings = controls.onSettings,
            modifier = Modifier.align(if (frame.reversePortrait) Alignment.BottomCenter else Alignment.TopCenter)
        )
    }
    ControlRow(
        controls = controls, look = look, compact = frame.compact,
        modifier = Modifier
            .align(if (frame.reversePortrait) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(
                top = if (frame.reversePortrait) (if (frame.compact) 24.dp else 32.dp) else 0.dp,
                bottom = if (frame.reversePortrait) 0.dp else (if (frame.compact) 12.dp else 28.dp)
            )
    )
    if (frame.ultraCompact) {
        CircleButton(
            Icons.Rounded.Tune, "Settings", 32.dp, controls.onSettings,
            modifier = Modifier.align(if (frame.reversePortrait) Alignment.BottomEnd else Alignment.TopEnd).padding(4.dp)
        )
    }
}

@Composable
private fun TopBar(compact: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 20.dp, vertical = if (compact) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!compact) {
            Text(
                "DEPTH", color = Ink,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 5.sp)
            )
        }
        Spacer(Modifier.weight(1f))
        CircleButton(Icons.Rounded.Tune, "Settings", if (compact) 36.dp else 44.dp, onSettings)
    }
}

@Composable
private fun ControlRow(controls: Controls, look: Look, compact: Boolean, modifier: Modifier = Modifier) {
    val primary = if (compact) 56.dp else 80.dp
    val secondary = if (compact) 42.dp else 58.dp
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 44.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(Icons.Rounded.RestartAlt, "Reset", secondary, controls.onReset)
        CircleButton(
            if (controls.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (controls.running) "Pause" else "Start",
            primary, controls.onPlayPause, primary = true, accent = look.accent, onAccent = look.onAccent
        )
        CircleButton(Icons.Rounded.SkipNext, "Skip phase", secondary, controls.onSkip)
    }
}

// ---------------------------------------------------------------------------------------
// Landscape
// ---------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.LandscapeLayout(
    state: TimerUiState,
    mode: TrainerMode,
    look: Look,
    frame: Frame,
    ringSize: Dp,
    scale: Float,
    controls: Controls,
    width: Dp
) {
    val cur = state.current
    if (cur != null || state.complete) {
        // Text column, left-aligned, centered as a block in the gap between the screen's
        // left edge and the ring's left edge.
        val leftGap = ((width - ringSize) / 2).coerceAtLeast(0.dp)
        Box(
            Modifier.align(Alignment.CenterStart).width(leftGap).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = if (frame.compact) 140.dp else 200.dp).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (!frame.compact) {
                    Text(modeLabel(mode).uppercase(), color = InkMuted, style = labelStyle((18 * scale).coerceAtLeast(12f), FontWeight.Bold, 2f))
                    VSpace(8.dp)
                }
                val phaseSize = (44 * scale).coerceAtLeast(20f)
                Text(
                    if (state.complete) "Complete" else cur!!.label,
                    color = look.accent,
                    // Explicit line height so wrapped labels ("Breathe / Rest") never overlap.
                    style = labelStyle(phaseSize, FontWeight.ExtraBold).copy(lineHeight = (phaseSize * 1.15f).sp)
                )
                if (!frame.compact) {
                    VSpace(16.dp)
                    Text(roundText(state, cur), color = Ink, style = labelStyle((19 * scale).coerceAtLeast(13f), FontWeight.SemiBold, 0f))
                    VSpace(28.dp)
                    Text(nextText(state, withTime = false), color = InkMuted, style = labelStyle((19 * scale).coerceAtLeast(13f), FontWeight.SemiBold, 0f))
                    if (state.totalRounds in 2..20) {
                        VSpace(16.dp)
                        RoundPips(
                            current = if (state.complete) state.totalRounds + 1 else state.currentRound,
                            total = state.totalRounds, accent = look.accent
                        )
                    }
                }
            }
        }
    }

    // Control column on the right: settings at the top, transport stacked in the middle.
    val primary = if (frame.compact) 52.dp else 72.dp
    val secondary = if (frame.compact) 38.dp else 52.dp
    Column(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(if (frame.compact) 72.dp else 104.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        CircleButton(Icons.Rounded.Tune, "Settings", if (frame.compact) 34.dp else 42.dp, controls.onSettings)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircleButton(Icons.Rounded.SkipNext, "Skip phase", secondary, controls.onSkip)
            CircleButton(
                if (controls.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (controls.running) "Pause" else "Start",
                primary, controls.onPlayPause, primary = true, accent = look.accent, onAccent = look.onAccent
            )
            CircleButton(Icons.Rounded.RestartAlt, "Reset", secondary, controls.onReset)
        }
        Spacer(Modifier.size(if (frame.compact) 34.dp else 42.dp))
    }
}

// ---------------------------------------------------------------------------------------
// Text helpers
// ---------------------------------------------------------------------------------------

private fun labelStyle(size: Float, weight: FontWeight, spacing: Float = 1f) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp, lineHeight = (size * 1.18f).sp)

private fun roundText(state: TimerUiState, cur: Phase?): String = when {
    state.complete -> "Nice work"
    cur == null -> ""
    cur.kind == PhaseKind.PREP -> "Starting shortly"
    else -> "Round ${cur.round} of ${cur.totalRounds}"
}

private fun nextText(state: TimerUiState, withTime: Boolean = true): String {
    if (state.complete) return "${state.totalRounds} rounds · ${clock(state.totalSeconds)}"
    val next = state.next ?: return "Final phase"
    return "Next: ${titleCase(next.label)}" + if (withTime) " · ${clock(next.seconds)}" else ""
}

// ---------------------------------------------------------------------------------------
// Rotation
// ---------------------------------------------------------------------------------------

/**
 * Configuration.orientation can't tell normal portrait from upside-down portrait; the
 * display rotation can. The system still handles the rotation itself — this only decides
 * which edge the controls belong on.
 */
@Composable
private fun rememberSurfaceRotation(): State<Int> {
    val view = LocalView.current
    val context = LocalContext.current
    val rotationState = remember { mutableIntStateOf(currentSurfaceRotation(view, context)) }
    DisposableEffect(view) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                rotationState.intValue = currentSurfaceRotation(view, context)
            }
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm.unregisterDisplayListener(listener) }
    }
    return rotationState
}

/** The window's own display (always valid once attached), falling back to the default one. */
private fun currentSurfaceRotation(view: android.view.View, context: Context): Int {
    val display = view.display
        ?: (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
    return display?.rotation ?: Surface.ROTATION_0
}
