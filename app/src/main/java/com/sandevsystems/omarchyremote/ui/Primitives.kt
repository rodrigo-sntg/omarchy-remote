package com.sandevsystems.omarchyremote.ui

import androidx.compose.foundation.layout.widthIn
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Primitives of design v2 (design/DESIGN.md §2–3). No M3 ripple or elevation: the relief is a 1 dp
// highlight on top plus a small shadow ("elevated"), or an inner shadow ("sunken").

/** Haptics are what let the phone be used without looking (§5). */
object Haptic {
    fun tap(view: View) = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    fun on(view: View) = view.performHapticFeedback(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )
    fun off(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/** Elevated keys, macros, dock: highlight line inside the top edge and a soft shadow below. */
fun Modifier.elevated(radius: Dp): Modifier = drawBehind {
    val r = radius.toPx()
    drawRoundRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 1.dp.toPx()), size = size, cornerRadius = CornerRadius(r))
}.drawWithContent {
    drawContent()
    val inset = radius.toPx() * 0.6f
    drawLine(KeypadColors.KeyHighlight, Offset(inset, 1.dp.toPx()), Offset(size.width - inset, 1.dp.toPx()), 1.dp.toPx())
}

/** Sunken trackpad, fields and map: inner shadow in the first 10 dp. */
fun Modifier.sunken(): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endY = 10.dp.toPx()))
}

/** Dot texture of the trackpad: radius 1.1 dp, 26 dp apart. */
fun Modifier.dotGrid(): Modifier = drawBehind {
    val step = KeypadDimens.DotGridStep.toPx()
    val r = KeypadDimens.DotGridRadius.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(KeypadColors.DotGrid, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/** Dashed border (empty states, Bluetooth Ver PC, "tela extra"). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = size.copy(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
}

/**
 * A pressable surface with the design's feedback: surface2 + scale 0.97 while pressed, haptic tap,
 * no ripple. [onLongClick] optional.
 */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = KeypadShapes.Key,
    background: Color = KeypadColors.Surface3,
    border: Color? = KeypadColors.Line,
    enabled: Boolean = true,
    raised: Boolean = true,
    haptic: Boolean = true,
    description: String? = null,
    state: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(80), label = "press")
    val fill = if (pressed && background == KeypadColors.Surface3) KeypadColors.Surface2 else background
    val radius = 14.dp
    Box(
        modifier
            .scale(scale)
            .then(if (raised) Modifier.elevated(radius) else Modifier)
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button,
                onLongClick = onLongClick,
                onClick = { if (haptic) Haptic.tap(view); onClick() },
            )
            .semantics {
                description?.let { contentDescription = it }
                state?.let { stateDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A key: 54 dp by default, accent fill when [on] (modifier latched). */
@Composable
fun Key(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = KeypadDimens.KeyHeight,
    style: TextStyle = KeypadType.Key,
    icon: ImageVector? = null,
    on: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
    caption: String? = null,
    /** Room on each side of the label: for a key sized by its text (e.g. an action on a card). */
    inset: Dp = 0.dp,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier.height(height),
        background = if (on) KeypadColors.Accent else KeypadColors.Surface3,
        border = if (on) KeypadColors.Accent else KeypadColors.Line,
        enabled = enabled,
        description = description ?: label,
        state = if (on) tr("ativado", "on") else null,
    ) {
        val color = when {
            !enabled -> KeypadColors.TextMute
            on -> KeypadColors.OnAccent
            else -> KeypadColors.Text
        }
        Column(Modifier.padding(horizontal = inset), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = color)
            else Text(label, style = style, color = color, maxLines = 1)
            if (caption != null) Text(caption, style = KeypadType.Mono.copy(fontSize = KeypadType.MacroCaption.fontSize), color = KeypadColors.TextMute, maxLines = 1)
        }
    }
}

/** A key sized by its label, next to text on a card or row: never squeezed against its own label. */
@Composable
fun ActionKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, on: Boolean = false, enabled: Boolean = true, description: String? = null) =
    Key(label, onClick, modifier.widthIn(min = 76.dp), height = 38.dp, style = KeypadType.KeySmall, on = on, enabled = enabled,
        description = description, inset = 16.dp)

/** Macro key of the shortcut row: glyph on top, what it does below. */
@Composable
fun MacroKey(glyph: String, caption: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, onLongClick: (() -> Unit)? = null) {
    Pressable(
        onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.height(KeypadDimens.MacroHeight), shape = KeypadShapes.Macro, enabled = enabled,
        description = "$glyph, $caption",
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(glyph, style = KeypadType.MacroGlyph, color = KeypadColors.Text, maxLines = 1)
            Text(caption, style = KeypadType.MacroCaption, color = KeypadColors.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Uppercase section label. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = KeypadColors.TextMute) =
    Text(text.uppercase(), modifier, style = KeypadType.Overline, color = color, maxLines = 1)

/** Segmented choice: track surface1, active = accent border, 12 % tint and accent text. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val view = LocalView.current
    Row(
        modifier.clip(KeypadShapes.SegmentTrack).background(KeypadColors.Surface1)
            .border(1.dp, KeypadColors.Line, KeypadShapes.SegmentTrack).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            Box(
                Modifier.weight(1f).height(height).clip(KeypadShapes.Segment)
                    // A solid pill, like iOS: reads as "chosen" on any theme, colorful or monochrome.
                    .background(if (active) KeypadColors.Text.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(role = Role.Tab) { if (!active) { Haptic.tap(view); onSelect(value) } }
                    .semantics { stateDescription = if (active) tr("selecionado", "selected") else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = KeypadType.Key.copy(fontSize = KeypadType.Body.fontSize, fontWeight = KeypadType.Overline.fontWeight),
                    color = if (active) KeypadColors.Text else KeypadColors.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Toggle row: label (+ caption), track 48×28, on = accent with an onAccent knob. */
@Composable
fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, caption: String? = null) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = KeypadDimens.MinTouch)
            .clickable(role = Role.Switch) { if (checked) Haptic.off(view) else Haptic.on(view); onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 14.dp)) {
            Text(label, style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize), color = KeypadColors.Text)
            if (caption != null) Text(caption, style = KeypadType.Caption, color = KeypadColors.TextMute)
        }
        Switch(
            checked, { if (it) Haptic.on(view) else Haptic.off(view); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = KeypadColors.Accent, checkedThumbColor = KeypadColors.OnAccent, checkedBorderColor = KeypadColors.Accent,
                uncheckedTrackColor = KeypadColors.Surface3, uncheckedThumbColor = KeypadColors.TextDim, uncheckedBorderColor = KeypadColors.Line2,
            ),
        )
    }
}

/** Slider row: label left, value in mono accent right. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingSlider(label: String, value: Float, valueText: String, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = KeypadType.Body.copy(fontSize = KeypadType.Key.fontSize), color = KeypadColors.Text)
            Text(valueText, style = KeypadType.MonoValue, color = KeypadColors.Accent)
        }
        val colors = SliderDefaults.colors(
            thumbColor = KeypadColors.Accent, activeTrackColor = KeypadColors.Accent, inactiveTrackColor = KeypadColors.Surface3,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        )
        // DESIGN: 6 dp track with no gap, round 26 dp thumb ringed in the sheet colour.
        Slider(
            value, onChange, valueRange = range, steps = steps, colors = colors,
            thumb = {
                Box(Modifier.size(26.dp).clip(CircleShape).background(KeypadColors.Surface2).padding(3.dp).clip(CircleShape).background(KeypadColors.Accent))
            },
            track = { state ->
                SliderDefaults.Track(state, Modifier.height(6.dp), colors = colors, drawStopIndicator = null, drawTick = { _, _ -> },
                    thumbTrackGapSize = 0.dp)
            },
        )
    }
}

/** Primary (accent) or secondary (outlined) big button of sheets and empty states. */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    val fg = when {
        !enabled -> KeypadColors.TextMute
        primary -> KeypadColors.OnAccent
        danger -> KeypadColors.Danger
        else -> KeypadColors.Text
    }
    Pressable(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = KeypadShapes.Dock,
        background = if (primary && enabled) KeypadColors.Accent else Color.Transparent,
        border = when {
            primary && enabled -> KeypadColors.Accent
            danger -> KeypadColors.Danger
            else -> KeypadColors.Line2
        },
        enabled = enabled,
        raised = false,
        description = label,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = fg)
            Text(label, style = KeypadType.Key.copy(fontWeight = KeypadType.Overline.fontWeight), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Grabber of layers and sheets: 38×4 bar in a 72×22 touch area. */
@Composable
fun Grabber(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.width(72.dp).height(22.dp).clickable(onClickLabel = tr("Fechar", "Close"), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(38.dp).height(4.dp).clip(KeypadShapes.Segment).background(KeypadColors.Line2)) }
}

/** The small phone · · · monitor drawing of empty states. */
@Composable
fun ConnectIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.width(140.dp).height(60.dp)) {
        val stroke = Stroke(1.5.dp.toPx())
        val c = KeypadColors.Line2
        drawRoundRect(c, Offset(0f, 2.dp.toPx()), androidx.compose.ui.geometry.Size(34.dp.toPx(), 56.dp.toPx()), CornerRadius(9.dp.toPx()), stroke)
        for (i in 0..2) drawCircle(c, 2.5.dp.toPx(), Offset((47 + i * 9).dp.toPx(), 30.dp.toPx()))
        drawRoundRect(c, Offset(80.dp.toPx(), 3.dp.toPx()), androidx.compose.ui.geometry.Size(60.dp.toPx(), 54.dp.toPx()), CornerRadius(7.dp.toPx()), stroke)
    }
}

/** A row of equal-weight children (keys), gap 7. */
@Composable
fun KeyRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KeypadDimens.KeyGap), content = content)
