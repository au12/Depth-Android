package com.depth.breathtrainer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.depth.breathtrainer.ui.theme.Glass
import com.depth.breathtrainer.ui.theme.Hairline
import com.depth.breathtrainer.ui.theme.Ink
import com.depth.breathtrainer.ui.theme.InkMuted
import com.depth.breathtrainer.ui.theme.SurfaceHigh
import com.depth.breathtrainer.ui.theme.TABULAR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * The countdown ring: a faint full track, a soft glow, the progress arc with round caps,
 * and a bright head dot marking "now". [content] is drawn centered inside.
 */
@Composable
fun TimerRing(
    progress: Float,
    accent: Color,
    diameter: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f + sw * 0.4f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val p = progress.coerceIn(0f, 1f)

            // Inner bloom — gives the ring a sense of depth without a hard edge.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    center = center, radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f
            )
            drawArc(
                color = Color.White.copy(alpha = 0.07f), startAngle = 0f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(sw)
            )
            if (p > 0f) {
                // A single soft glow just wider than the arc.
                drawArc(
                    color = accent.copy(alpha = 0.10f), startAngle = -90f, sweepAngle = 360f * p,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(sw * 1.6f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent, startAngle = -90f, sweepAngle = 360f * p,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
                val r = arcSize.width / 2f
                val angle = Math.toRadians((-90.0 + 360.0 * p))
                val head = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
                drawCircle(color = Color.White, radius = sw * 0.28f, center = head)
            }
        }
        content()
    }
}

/** Circular icon button. Primary uses the phase accent; secondary is frosted glass. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    accent: Color = Ink,
    onAccent: Color = Color.Black
) {
    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .clip(shape)
            .then(
                if (primary) Modifier.background(accent)
                else Modifier.background(Glass).border(1.dp, Hairline, shape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (primary) onAccent else Ink),
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (primary) onAccent else Ink,
            modifier = Modifier.size(size * if (primary) 0.42f else 0.44f)
        )
    }
}

/**
 * One pip per round: done rounds filled, the current one wide and bright, upcoming ones
 * faint. Falls back to a thin bar when there are too many rounds for pips to read.
 */
@Composable
fun RoundPips(current: Int, total: Int, accent: Color, modifier: Modifier = Modifier, maxWidth: Dp = 260.dp) {
    if (total <= 0) return
    if (total > 20) {
        Box(modifier.width(maxWidth).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth((current.toFloat() / total).coerceIn(0f, 1f))
                    .clip(CircleShape).background(accent)
            )
        }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val isCurrent = i == current
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (isCurrent) 22.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            i < current -> accent.copy(alpha = 0.85f)
                            isCurrent -> accent
                            else -> Color.White.copy(alpha = 0.16f)
                        }
                    )
            )
        }
    }
}

/**
 * Labeled −/+ stepper. Hold a button to repeat. [format] renders the value
 * (e.g. seconds as m:ss).
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    modifier: Modifier = Modifier
) {
    val current = rememberUpdatedState(value)
    val change = rememberUpdatedState(onValueChange)
    fun bump(dir: Int) {
        val nv = (current.value + dir * step).coerceIn(range.first, range.last)
        if (nv != current.value) change.value(nv)
    }
    Column(modifier) {
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = InkMuted,
            maxLines = 1, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceHigh)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RepeatButton(Icons.Rounded.Remove, "Decrease $label", enabled = value > range.first) { bump(-1) }
            Text(
                format(value),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR, fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            RepeatButton(Icons.Rounded.Add, "Increase $label", enabled = value < range.last) { bump(1) }
        }
    }
}

@Composable
private fun RepeatButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step = rememberUpdatedState(onStep)
    Box(
        Modifier
            .size(54.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        step.value()
                        delay(420)
                        while (true) { step.value(); delay(70) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) Ink else InkMuted.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
