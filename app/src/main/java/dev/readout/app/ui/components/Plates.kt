package dev.readout.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.MachineState
import dev.readout.app.ui.theme.LocalPalette
import dev.readout.app.ui.theme.Spacing

/**
 * The running header of a service manual: wordmark, the machine it belongs to, its lamp.
 * Always present, never a hero.
 */
@Composable
fun Masthead(
    printerName: String,
    connectionLabel: String,
    live: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Spacing.l, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "READOUT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(Spacing.m))
        Canvas(Modifier.size(6.dp)) {
            drawCircle(if (live) palette.ready else palette.work)
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            text = printerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = connectionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        actions()
    }
}

/**
 * A machine plate riveted to the frame: the largest state statement in the app.
 * Colour is the state, but the state is also always written out.
 */
@Composable
fun StatusPlate(
    state: MachineState,
    stateLabel: String,
    jobName: String?,
    fallbackDetail: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val scheme = MaterialTheme.colorScheme
    val working = state in setOf(
        MachineState.Printing, MachineState.Paused, MachineState.Pausing,
        MachineState.Resuming, MachineState.Cancelling, MachineState.Finishing
    )
    val failed = state == MachineState.Error || state == MachineState.Disconnected

    val background = when {
        failed -> scheme.errorContainer
        working -> palette.plate
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        failed -> scheme.onErrorContainer
        working -> palette.onPlate
        else -> scheme.onSurface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Spacing.l, vertical = Spacing.l)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stateLabel.uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.sp),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (working) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(content, RoundedCornerShape(1.dp))
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = jobName ?: fallbackDetail,
            style = MaterialTheme.typography.bodyMedium,
            color = content.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Progress as a machined ruler: 100 minor divisions, milled majors, inked travel, a carriage.
 * It shows position and rate of travel at a glance, which a ring does not.
 */
@Composable
fun RulerProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp
) {
    val palette = LocalPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.outlineVariant
    val value = progress ?: 0f
    val animated by animateFloatAsState(targetValue = value, animationSpec = tween(600), label = "ruler")

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val width = size.width
        val baseY = size.height * 0.70f
        val minor = 1.dp.toPx()
        val major = 2.dp.toPx()

        for (index in 0..100) {
            val x = width * index / 100f
            val isMajor = index % 10 == 0
            val isMid = index % 5 == 0
            val length = when {
                isMajor -> 16.dp.toPx()
                isMid -> 10.dp.toPx()
                else -> 6.dp.toPx()
            }
            val inked = index / 100f <= animated
            drawLine(
                color = if (inked) ink else track,
                start = Offset(x, baseY),
                end = Offset(x, baseY - length),
                strokeWidth = if (isMajor) major else minor
            )
        }

        drawLine(track, Offset(0f, baseY), Offset(width, baseY), strokeWidth = major)

        val carriageX = width * animated
        val carriageTop = baseY - 26.dp.toPx()
        drawLine(ink, Offset(carriageX, carriageTop), Offset(carriageX, baseY + 8.dp.toPx()), strokeWidth = 3.dp.toPx())
        drawCircle(ink, radius = 3.5.dp.toPx(), center = Offset(carriageX, carriageTop))
    }
}

/** A numbered callout used by the schematic table. */
@Composable
fun CalloutNumber(number: Int, active: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = 1.dp,
                color = if (active) scheme.onSurface else scheme.outline,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (active) scheme.onSurface else scheme.outline
        )
    }
}

/** A parts-list row: callout, name, and the measured value. */
@Composable
fun SpecRow(
    number: Int?,
    label: String,
    value: String,
    secondary: String? = null,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (muted) scheme.onSurfaceVariant else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            CalloutNumber(number, active = !muted)
            Spacer(Modifier.width(Spacing.m))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = content)
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = content
        )
    }
}

/** Section heading in the manual's voice: number, rule, label. */
@Composable
fun SectionHeading(number: String, title: String, trailing: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacing.s))
        Box(
            Modifier
                .width(18.dp)
                .height(1.dp)
                .background(scheme.outline)
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A panel that reads as card stock: hairline rule, small radius, no shadow. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Box(Modifier.padding(Spacing.l)) { content() }
    }
}

@Composable
fun MeterBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = LocalPalette.current.work
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        drawRect(track)
        drawRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = Spacing.l)
    )
}

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DashedRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        val dash = 3.dp.toPx()
        val gap = 3.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color,
                Offset(x, 0f),
                Offset((x + dash).coerceAtMost(size.width), 0f),
                strokeWidth = size.height
            )
            x += dash + gap
        }
    }
}

/**
 * The machine, front elevation, drawn to the same hairline as the tables.
 * It is a diagram, not an illustration: every line is a part the user can name.
 */
@Composable
fun PrinterSchematic(
    hasChamber: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val stroke = 1.4.dp.toPx()
        val w = size.width
        val h = size.height

        val left = w * 0.16f
        val right = w * 0.84f
        val top = h * 0.12f
        val bottom = h * 0.90f

        drawLine(ink, Offset(left, top), Offset(right, top), strokeWidth = stroke)
        drawLine(ink, Offset(left, top), Offset(left, bottom), strokeWidth = stroke)
        drawLine(ink, Offset(right, top), Offset(right, bottom), strokeWidth = stroke)
        drawLine(ink, Offset(left, bottom), Offset(right, bottom), strokeWidth = stroke)

        val gantryY = h * 0.34f
        drawLine(ink, Offset(left, gantryY), Offset(right, gantryY), strokeWidth = stroke)

        val carriageW = w * 0.12f
        drawRect(
            color = ink,
            topLeft = Offset(w * 0.5f - carriageW / 2f, gantryY),
            size = Size(carriageW, h * 0.13f),
            style = Stroke(stroke)
        )
        drawLine(
            ink,
            Offset(w * 0.5f, gantryY + h * 0.13f),
            Offset(w * 0.5f, gantryY + h * 0.20f),
            strokeWidth = stroke
        )
        drawCircle(ink, radius = 2.5.dp.toPx(), center = Offset(w * 0.5f, gantryY + h * 0.20f))

        val bedY = h * 0.72f
        drawLine(ink, Offset(w * 0.24f, bedY), Offset(w * 0.76f, bedY), strokeWidth = stroke)
        drawLine(ink, Offset(w * 0.24f, bedY), Offset(w * 0.30f, bedY + h * 0.10f), strokeWidth = stroke)
        drawLine(ink, Offset(w * 0.76f, bedY), Offset(w * 0.70f, bedY + h * 0.10f), strokeWidth = stroke)
        drawLine(ink, Offset(w * 0.30f, bedY + h * 0.10f), Offset(w * 0.70f, bedY + h * 0.10f), strokeWidth = stroke)

        if (hasChamber) {
            drawRect(
                color = scheme.outline,
                topLeft = Offset(left + 6.dp.toPx(), top + 6.dp.toPx()),
                size = Size(right - left - 12.dp.toPx(), bottom - top - 12.dp.toPx()),
                style = Stroke(1.dp.toPx())
            )
        }
    }
}

/**
 * The diagram with its callout badges placed on the parts they name, so the table under it
 * reads as a legend rather than a separate list.
 */
@Composable
fun AnnotatedSchematic(
    components: List<ComponentTemperature>,
    modifier: Modifier = Modifier
) {
    val height = 180.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        PrinterSchematic(
            hasChamber = components.any { it.key == "chamber" },
            modifier = Modifier.height(height)
        )
        components.forEachIndexed { index, component ->
            val isSecondTool = component.key.startsWith("tool") && component.index > 0
            if (isSecondTool) return@forEachIndexed
            val xFraction = when {
                component.key == "chamber" -> 0.20f
                component.key == "bed" -> 0.66f
                else -> 0.58f
            }
            val yFraction = when {
                component.key == "chamber" -> 0.18f
                component.key == "bed" -> 0.80f
                else -> 0.56f
            }
            Box(
                modifier = Modifier.offset(
                    x = maxWidth * xFraction - 10.dp,
                    y = height * yFraction - 10.dp
                )
            ) {
                CalloutNumber(index + 1)
            }
        }
    }
}
