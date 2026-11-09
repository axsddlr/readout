package dev.readout.app.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.readout.app.data.ComponentTemperature
import dev.readout.app.data.MachineState
import dev.readout.app.ui.theme.LocalPalette
import dev.readout.app.ui.theme.ReadoutStyle
import dev.readout.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Top bar: the printer's name as a chip that opens the switcher, a connection chip, and
 * trailing actions. The machine is the title; the app does not name itself.
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
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = Spacing.s, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(start = Spacing.s, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = printerName,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (onClick != null) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Switch printer",
                    tint = scheme.onSurfaceVariant
                )
            }
        }
        }
        Spacer(Modifier.width(Spacing.s))
        ConnectionChip(label = connectionLabel, live = live)
        actions()
    }
}

/** A small tonal pill: coloured dot plus the word. */
@Composable
fun ConnectionChip(label: String, live: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dot = if (live) LocalPalette.current.ready else scheme.outline
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs + Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(dot)
        Spacer(Modifier.width(Spacing.s))
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/**
 * The hero: a large tonal card whose colour is the machine state and whose first line says it.
 * [content] holds whatever the state needs underneath (job progress, an idle note).
 */
@Composable
fun StatusCard(
    state: MachineState?,
    stateLabel: String,
    detail: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val tone = LocalPalette.current.forState(state)
    val container by animateColorAsState(tone.container, label = "status-container")
    val onContainer by animateColorAsState(tone.content, label = "status-content")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = onContainer
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = onContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content()
        }
    }
}

/** True when the user (or a test harness) has turned animations off. */
@Composable
fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Job progress: a thick rounded bar. Travel animates with a spring; with animations off it
 * cuts straight to the value. A missing percentage leaves the bar empty rather than guessing.
 */
@Composable
fun ReadoutProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.24f),
    height: Dp = 12.dp
) {
    val target = (progress ?: 0f).coerceIn(0f, 1f)
    // Read the animated value inside the progress lambda, so travel redraws without recomposing.
    val animated = animateFloatAsState(
        targetValue = target,
        animationSpec = if (animationsDisabled()) snap() else spring(stiffness = 50f),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { animated.value },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Progress ${(target * 100).toInt()} percent" },
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
        gapSize = 4.dp,
        drawStopIndicator = {}
    )
}

/**
 * One heater as a tile: name, the measured value large, the target and a bar toward it.
 * Tapping opens the temperature sheet.
 */
@Composable
fun TemperatureTile(
    component: ComponentTemperature,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val heating = component.isHeating
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Text(
                component.label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "${component.actual.toInt()}°",
                style = ReadoutStyle.copy(fontSize = ReadoutStyle.fontSize * 1.3f),
                color = scheme.onSurface
            )
            Text(
                text = when {
                    component.target <= 0f -> "Off"
                    heating -> "Heating to ${component.target.toInt()}°"
                    component.isAtTarget -> "At ${component.target.toInt()}°"
                    else -> "Cooling to ${component.target.toInt()}°"
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(Spacing.m))
            val fraction = if (component.target > 0f) component.actual / component.target else 0f
            MeterBar(
                fraction = fraction,
                color = if (heating) scheme.tertiary else scheme.primary
            )
        }
    }
}

/**
 * A list row: optional leading icon, label, optional secondary line, value at the end.
 * 56 dp minimum, optional click.
 */
@Composable
fun SpecRow(
    icon: ImageVector?,
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
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.l))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = content)
            if (secondary != null) {
                Text(secondary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

/** A section title in sentence case, with an optional trailing action. */
@Composable
fun SectionHeading(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(top = Spacing.l),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** A tonal card: no border, no shadow; separation comes from the surface tone. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) { content() }
    }
}

/** A thin rounded meter, e.g. heater travel toward its target. */
@Composable
fun MeterBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.2f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
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
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Divider between rows inside a card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/**
 * Swipe a row from end to start to remove it. The row slides over an error-toned background
 * that says what will happen. [onRemove] returns false to cancel (for example when the caller
 * asks for confirmation first), which springs the row back.
 */
@Composable
fun SwipeToRemove(
    label: String,
    onRemove: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (swiping) scheme.errorContainer else Color.Transparent)
                    .padding(horizontal = Spacing.l),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (swiping) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onErrorContainer)
                    Spacer(Modifier.width(Spacing.s))
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = scheme.onErrorContainer)
                }
            }
        },
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !onRemove()) {
                scope.launch { state.reset() }
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }
}

/** Lays [items] out in rows of [columns], with even gaps. */
@Composable
fun <T> TileGrid(
    items: List<T>,
    columns: Int = 2,
    modifier: Modifier = Modifier,
    tile: @Composable (T, Modifier) -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { item -> tile(item, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
