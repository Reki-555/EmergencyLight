package com.emergencylight.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencylight.app.R
import com.emergencylight.app.ScreenColor
import com.emergencylight.app.ui.theme.AppStyle
import com.emergencylight.app.ui.theme.FixedColors
import com.emergencylight.app.ui.theme.LocalAppStyle
import com.emergencylight.app.ui.theme.StyleId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val DISABLED_ALPHA = 0.4f
private const val TAP_DEBOUNCE_MS = 500L

/** Formats tenths of a second as "0.5" (always 1 decimal, never a comma). */
fun formatTenths(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

// ------------------------------------------------------------------
// Text
// ------------------------------------------------------------------

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 18.sp,
    color: Color = LocalAppStyle.current.palette.text,
    bold: Boolean = false,
    font: FontFamily? = null,
    align: TextAlign? = null,
    italic: Boolean = false,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    singleLine: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = font ?: LocalAppStyle.current.bodyFont,
        textAlign = align,
        letterSpacing = letterSpacing,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        softWrap = !singleLine,
    )
}

// ------------------------------------------------------------------
// Background (grid for Instrument Panel, gradient for RPG Parchment light)
// ------------------------------------------------------------------

fun Modifier.styledBackground(style: AppStyle): Modifier {
    val p = style.palette
    val base = if (p.bg != p.bgBottom) {
        this.background(Brush.verticalGradient(listOf(p.bg, p.bgBottom)))
    } else {
        this.background(p.bg)
    }
    val grid = p.grid ?: return base
    return base.drawBehind {
        val step = 24.dp.toPx()
        val w = 1.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), w)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), w)
            y += step
        }
    }
}

// ------------------------------------------------------------------
// Cards
// ------------------------------------------------------------------

@Composable
fun StyledCard(
    index: Int,
    title: String,
    subtitle: String?,
    emoji: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val st = LocalAppStyle.current
    val p = st.palette
    val shape = RoundedCornerShape(st.cardCorner)
    when (st.id) {
        StyleId.STICKER -> {
            // Tilted card with a solid shadow. Rotation is applied as a layer, so taps
            // land exactly where the controls are drawn.
            val angle = if (index % 2 == 0) -1.5f else 1.5f
            Box(modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp).rotate(angle)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(4.dp, 4.dp)
                        .background(Color.Black.copy(alpha = 0.25f), shape)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(p.surface, shape)
                        .border(3.dp, p.outline, shape)
                        .padding(16.dp)
                ) {
                    CardHeader(title, subtitle, emoji)
                    content()
                }
            }
        }
        StyleId.PARCHMENT -> {
            // Double frame: outer 2dp line + inner 1dp line, 4dp apart.
            val inner = RoundedCornerShape(2.dp)
            Column(
                modifier
                    .fillMaxWidth()
                    .background(p.surface, shape)
                    .border(2.dp, p.outline, shape)
                    .padding(6.dp)
                    .border(1.dp, p.outline, inner)
                    .padding(12.dp)
            ) {
                CardHeader(title, subtitle, emoji)
                content()
            }
        }
        else -> {
            Column(
                modifier
                    .fillMaxWidth()
                    .background(p.surface, shape)
                    .border(1.dp, p.outline, shape)
                    .padding(16.dp)
            ) {
                CardHeader(title, subtitle, emoji)
                content()
            }
        }
    }
}

@Composable
private fun CardHeader(title: String, subtitle: String?, emoji: String?) {
    val st = LocalAppStyle.current
    val p = st.palette
    val withEmoji = if (st.id == StyleId.STICKER && emoji != null) "$emoji $title" else title
    val shown = if (st.uppercaseHeaders) withEmoji.uppercase() else withEmoji
    AppText(
        text = shown,
        modifier = Modifier.semantics { heading() },
        size = 20.sp,
        bold = true,
        color = if (st.id == StyleId.PARCHMENT) p.accent else p.text,
        font = st.headerFont,
        letterSpacing = st.headerLetterSpacing,
    )
    if (st.id == StyleId.PARCHMENT && subtitle != null) {
        AppText(subtitle, size = 16.sp, italic = true, color = p.text2)
    }
    Spacer(Modifier.height(8.dp))
}

/** "✦ ✦ ✦" divider used between cards in RPG Parchment. */
@Composable
fun ParchmentDivider() {
    val p = LocalAppStyle.current.palette
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        AppText("✦ ✦ ✦", size = 18.sp, color = p.outline, modifier = Modifier.clearAndSetSemantics { })
    }
}

// ------------------------------------------------------------------
// Switch row (whole row is tappable, min height 56dp, 0.5 s tap protection)
// ------------------------------------------------------------------

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val st = LocalAppStyle.current
    val p = st.palette
    val lastTap = remember { mutableLongStateOf(0L) }
    val toggle by rememberUpdatedState(onToggle)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTap.longValue >= TAP_DEBOUNCE_MS) {
                        lastTap.longValue = now
                        toggle()
                    }
                },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (st.id == StyleId.DASHBOARD) {
            Box(Modifier.size(8.dp).background(if (checked) p.accent else p.outline, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        AppText(label, modifier = Modifier.weight(1f), size = 18.sp)
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.accent,
                checkedThumbColor = p.onAccent,
                checkedBorderColor = p.accent,
                uncheckedTrackColor = p.surface,
                uncheckedThumbColor = p.text2,
                uncheckedBorderColor = p.outline,
                disabledCheckedTrackColor = p.accent,
                disabledCheckedThumbColor = p.onAccent,
                disabledCheckedBorderColor = p.accent,
                disabledUncheckedTrackColor = p.surface,
                disabledUncheckedThumbColor = p.text2,
                disabledUncheckedBorderColor = p.outline,
            ),
        )
    }
}

// ------------------------------------------------------------------
// [–] value [+] controls
// ------------------------------------------------------------------

@Composable
fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    size: Dp,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    val p = LocalAppStyle.current.palette
    val shape = RoundedCornerShape(12.dp)
    val action by rememberUpdatedState(onPress)
    val isEnabled by rememberUpdatedState(enabled)
    val scope = rememberCoroutineScope()
    val base = Modifier
        .size(size)
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
        .clip(shape)
        .border(2.dp, p.outline, shape)
    val interaction = if (repeat) {
        // Hold to keep changing the value every 100 ms.
        base
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick { action(); true }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!isEnabled) return@awaitEachGesture
                    down.consume()
                    action()
                    val job = scope.launch {
                        delay(400)
                        while (isActive && isEnabled) {
                            action()
                            delay(100)
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        job.cancel()
                    }
                }
            }
    } else {
        base
            .clickable(enabled = enabled, role = Role.Button) { action() }
            .semantics { contentDescription = description }
    }
    Box(interaction, contentAlignment = Alignment.Center) {
        AppText(symbol, size = 28.sp, bold = true, color = p.text, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
fun StepperRow(
    valueText: String,
    unitLabel: String?,
    valueDescription: String,
    minusDescription: String,
    plusDescription: String,
    canMinus: Boolean,
    canPlus: Boolean,
    enabled: Boolean,
    repeat: Boolean = false,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val st = LocalAppStyle.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton("–", minusDescription, enabled && canMinus, 56.dp, repeat, onMinus)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clearAndSetSemantics { contentDescription = valueDescription },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (unitLabel != null) {
                AppText(unitLabel, size = 16.sp, color = st.palette.text2, font = st.numberFont, singleLine = true)
            }
            AppText(valueText, size = 28.sp, bold = true, font = st.numberFont, singleLine = true)
        }
        StepButton("+", plusDescription, enabled && canPlus, 56.dp, repeat, onPlus)
    }
}

@Composable
fun StepSlider(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val p = LocalAppStyle.current.palette
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Slider(
        value = value.toFloat(),
        onValueChange = { v ->
            val snapped = (((v - min) / step).roundToInt() * step + min).coerceIn(min, max)
            if (snapped != current) change(snapped)
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min) / step - 1,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        colors = SliderDefaults.colors(
            thumbColor = p.accent,
            activeTrackColor = p.accent,
            inactiveTrackColor = p.outline,
            activeTickColor = p.onAccent,
            inactiveTickColor = p.text2,
            disabledThumbColor = p.accent,
            disabledActiveTrackColor = p.accent,
            disabledInactiveTrackColor = p.outline,
            disabledActiveTickColor = p.onAccent,
            disabledInactiveTickColor = p.text2,
        ),
    )
}

// ------------------------------------------------------------------
// Summary line ("Color: White ▾") and color grid
// ------------------------------------------------------------------

@Composable
fun SummaryLine(text: String, onClick: () -> Unit) {
    val p = LocalAppStyle.current.palette
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AppText(text, size = 16.sp, color = p.text2)
    }
}

@Composable
fun ColorGrid(selected: ScreenColor, onSelect: (ScreenColor) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenColor.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { color ->
                    ColorCell(color, color == selected, Modifier.weight(1f)) { onSelect(color) }
                }
            }
        }
    }
}

@Composable
private fun ColorCell(color: ScreenColor, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val p = LocalAppStyle.current.palette
    val name = stringResource(color.label)
    val description = stringResource(
        if (selected) R.string.cd_color_selected else R.string.cd_color_not_selected, name
    )
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onSelect(); true }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Outer ring (3dp, main text color) only when selected; the color circle itself is 48dp.
        Box(
            Modifier
                .size(60.dp)
                .then(if (selected) Modifier.border(3.dp, p.text, CircleShape) else Modifier)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.color)
                    .border(1.dp, FixedColors.ColorCircleBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = FixedColors.Check, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        AppText(name, size = 16.sp, align = TextAlign.Center)
    }
}

// ------------------------------------------------------------------
// Status chips ("🔦 TORCH ON", "🚨 SOS ON")
// ------------------------------------------------------------------

@Composable
fun StatusChip(text: String) {
    val p = LocalAppStyle.current.palette
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .background(p.surface, shape)
            .border(1.dp, p.accent, shape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, size = 16.sp, bold = true, singleLine = true)
    }
}

// ------------------------------------------------------------------
// Night Dashboard half-circle gauge
// ------------------------------------------------------------------

@Composable
fun HalfGauge(fraction: Float) {
    val p = LocalAppStyle.current.palette
    Canvas(Modifier.size(width = 160.dp, height = 88.dp).clearAndSetSemantics { }) {
        val stroke = 8.dp.toPx()
        val d = size.width - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(d, d)
        drawArc(p.outline, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(
            p.accent, 180f, 180f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
    }
}

// ------------------------------------------------------------------
// Press-and-hold gesture with progress (used by Hold STOP, Lock and pocket SOS)
// ------------------------------------------------------------------

/**
 * Calls [onComplete] after the finger has been held down for [durationMs].
 * [progress] goes 0 -> 1 while holding and back to 0 on release.
 * [onReleasedEarly] is called when the finger is lifted too early.
 */
@Composable
fun Modifier.holdToActivate(
    durationMs: Int,
    progress: Animatable<Float, AnimationVector1D>,
    onComplete: () -> Unit,
    onReleasedEarly: () -> Unit = {},
): Modifier {
    val scope = rememberCoroutineScope()
    val complete by rememberUpdatedState(onComplete)
    val early by rememberUpdatedState(onReleasedEarly)
    return this.pointerInput(durationMs) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            var done = false
            val job = scope.launch {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
                done = true
                complete()
            }
            val up = try {
                waitForUpOrCancellation()
            } catch (e: CancellationException) {
                job.cancel()
                scope.launch { progress.snapTo(0f) }
                throw e
            }
            up?.consume()
            if (!done) {
                job.cancel()
                if (up != null) early()
            }
            scope.launch { progress.snapTo(0f) }
        }
    }
}

/** Ring that fills clockwise around a round hold-button. */
fun Modifier.progressRing(progress: () -> Float, color: Color): Modifier = this.drawBehind {
    val value = progress()
    if (value > 0f) {
        val stroke = 4.dp.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * value,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

// ------------------------------------------------------------------
// Snackbar (text 16sp)
// ------------------------------------------------------------------

@Composable
fun AppSnackbar(data: SnackbarData) {
    val p = LocalAppStyle.current.palette
    Snackbar(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = p.text,
        contentColor = p.surface,
    ) {
        Text(data.visuals.message, fontSize = 16.sp, color = p.surface)
    }
}

/** Plain outlined button used in the settings sheet. */
@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalAppStyle.current.palette
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .border(2.dp, p.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, size = 18.sp, bold = true)
    }
}
