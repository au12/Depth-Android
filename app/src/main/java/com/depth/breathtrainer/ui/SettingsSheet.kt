package com.depth.breathtrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.depth.breathtrainer.engine.TableBuilder
import com.depth.breathtrainer.model.TrainerMode
import com.depth.breathtrainer.model.TrainerSettings
import com.depth.breathtrainer.model.clock
import com.depth.breathtrainer.model.modeLabel
import com.depth.breathtrainer.ui.components.SectionLabel
import com.depth.breathtrainer.ui.components.Stepper
import com.depth.breathtrainer.ui.components.VSpace
import com.depth.breathtrainer.ui.theme.Abyss
import com.depth.breathtrainer.ui.theme.Hairline
import com.depth.breathtrainer.ui.theme.Ink
import com.depth.breathtrainer.ui.theme.InkFaint
import com.depth.breathtrainer.ui.theme.InkMuted
import com.depth.breathtrainer.ui.theme.Palettes
import com.depth.breathtrainer.ui.theme.SurfaceHigh

// Three rows of steppers (label + 54dp control + 14dp gap). Every mode's section is pinned to
// this height so switching tabs never moves the toggles or the Save button below it.
private val MODE_SECTION_HEIGHT = 246.dp

@Composable
fun SettingsSheet(settings: TrainerSettings, onApply: (TrainerSettings) -> Unit) {
    var draft by remember { mutableStateOf(settings) }
    val phases = remember(draft) { TableBuilder.flatten(TableBuilder.buildRounds(draft), draft.mode, draft.prepOn) }
    val rounds = phases.firstOrNull()?.totalRounds ?: 0
    val unit = if (draft.mode == TrainerMode.SQUARE) "cycles" else "rounds"

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp)
            .navigationBarsPadding()
    ) {
        Text("Table setup", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(
            "$rounds $unit · ${clock(phases.sumOf { it.seconds })} total",
            style = MaterialTheme.typography.bodyMedium, color = InkMuted
        )
        VSpace(20.dp)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = TrainerMode.values()
            modes.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = draft.mode == m,
                    onClick = { draft = draft.copy(mode = m) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Palettes.Breathe.accent,
                        activeContentColor = Palettes.Breathe.onAccent,
                        inactiveContainerColor = SurfaceHigh,
                        inactiveContentColor = Ink,
                        activeBorderColor = Color.Transparent,
                        inactiveBorderColor = Hairline
                    ),
                    icon = {}
                ) { Text(modeLabel(m), fontWeight = FontWeight.Bold) }
            }
        }
        VSpace(18.dp)

        Box(Modifier.fillMaxWidth().height(MODE_SECTION_HEIGHT)) {
            when (draft.mode) {
                TrainerMode.CO2 -> StepperGrid {
                    row {
                        Stepper("Max static", draft.maxHoldSeconds, { draft = draft.copy(maxHoldSeconds = it) }, 30..600, 5, ::clock, Modifier.weight(1f))
                        Stepper("Hold (% of max)", draft.co2HoldPct, { draft = draft.copy(co2HoldPct = it) }, 10..100, 5, ::pct, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Rest start", draft.co2RestStart, { draft = draft.copy(co2RestStart = it) }, 5..600, 5, ::clock, Modifier.weight(1f))
                        Stepper("Rest end", draft.co2RestEnd, { draft = draft.copy(co2RestEnd = it) }, 5..600, 5, ::clock, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Rounds", draft.rounds, { draft = draft.copy(rounds = it) }, 2..16, 1, Int::toString, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
                TrainerMode.O2 -> StepperGrid {
                    row {
                        Stepper("Max static", draft.maxHoldSeconds, { draft = draft.copy(maxHoldSeconds = it) }, 30..600, 5, ::clock, Modifier.weight(1f))
                        Stepper("Hold start (% max)", draft.o2HoldStartPct, { draft = draft.copy(o2HoldStartPct = it) }, 10..100, 5, ::pct, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Hold end (% max)", draft.o2HoldEndPct, { draft = draft.copy(o2HoldEndPct = it) }, 10..100, 5, ::pct, Modifier.weight(1f))
                        Stepper("Rest", draft.o2Rest, { draft = draft.copy(o2Rest = it) }, 5..600, 5, ::clock, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Rounds", draft.rounds, { draft = draft.copy(rounds = it) }, 2..16, 1, Int::toString, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
                TrainerMode.SQUARE -> StepperGrid {
                    row {
                        Stepper("Inhale", draft.sqIn, { draft = draft.copy(sqIn = it) }, 1..60, 1, ::secs, Modifier.weight(1f))
                        Stepper("Hold", draft.sqHold1, { draft = draft.copy(sqHold1 = it) }, 0..60, 1, ::secs, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Exhale", draft.sqOut, { draft = draft.copy(sqOut = it) }, 1..60, 1, ::secs, Modifier.weight(1f))
                        Stepper("Hold", draft.sqHold2, { draft = draft.copy(sqHold2 = it) }, 0..60, 1, ::secs, Modifier.weight(1f))
                    }
                    row {
                        Stepper("Cycles", draft.sqCycles, { draft = draft.copy(sqCycles = it) }, 1..60, 1, Int::toString, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
                TrainerMode.CUSTOM -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "One round per line as hold,rest in seconds — e.g. 60,120",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = draft.customRounds,
                        onValueChange = { draft = draft.copy(customRounds = it) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceHigh, unfocusedContainerColor = SurfaceHigh,
                            focusedBorderColor = Palettes.Breathe.accent, unfocusedBorderColor = Hairline,
                            cursorColor = Palettes.Breathe.accent
                        )
                    )
                }
            }
        }

        VSpace(22.dp)
        SectionLabel("Cues")
        Card {
            ToggleRow("Sound cues", "Beeps over your music — never pauses it", draft.soundOn) { draft = draft.copy(soundOn = it) }
            HorizontalDivider(color = Hairline)
            ToggleRow("Vibrate", "Pulses in the 10s lead-in, long buzz on change", draft.vibrateOn) { draft = draft.copy(vibrateOn = it) }
            HorizontalDivider(color = Hairline)
            ToggleRow("Prep countdown", "10 seconds to settle before round one", draft.prepOn) { draft = draft.copy(prepOn = it) }
        }

        VSpace(22.dp)
        SectionLabel("Android Auto")
        Card {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palettes.Prep.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.DirectionsCar, null, tint = Palettes.Prep.accent, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Shows up like a music app", style = MaterialTheme.typography.titleSmall, color = Ink)
                    VSpace(4.dp)
                    Text(
                        "Open Depth from the car's app grid and pick a table. Play/pause runs the timer, " +
                            "next skips a phase, previous resets. Your music keeps playing.",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted
                    )
                }
            }
        }

        VSpace(24.dp)
        Button(
            onClick = { onApply(draft) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palettes.Breathe.accent, contentColor = Palettes.Breathe.onAccent)
        ) { Text("Save & reset timer", style = MaterialTheme.typography.labelLarge) }
        VSpace(8.dp)
        Text(
            "Saving restarts the current table.",
            style = MaterialTheme.typography.bodySmall, color = InkFaint,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

private fun pct(v: Int) = "$v%"
private fun secs(v: Int) = "${v}s"

private class GridScope { val rows = mutableListOf<@Composable RowScope.() -> Unit>(); fun row(c: @Composable RowScope.() -> Unit) { rows += c } }

@Composable
private fun StepperGrid(build: GridScope.() -> Unit) {
    val scope = GridScope().apply(build)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        scope.rows.forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { r() } }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Abyss.copy(alpha = 0.55f))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
    ) { content() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palettes.Breathe.accent, checkedThumbColor = Palettes.Breathe.onAccent,
                uncheckedTrackColor = SurfaceHigh, uncheckedBorderColor = Hairline, uncheckedThumbColor = InkMuted
            )
        )
    }
}
