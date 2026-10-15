package nl.lloydstellar.spindoctor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The building blocks of every screen, taken from the LLOYDOS look of
 * lloydstellar.nl (styles.css and the Spin Doctor theme layer): angular
 * command buttons with cut corners, console panels with corner brackets, mono
 * text, "> LABEL" form labels, amber for the one main action and for values.
 * One accent, no rounded glass, no gradients as decoration.
 */

/** Corners cut top-left and bottom-right, like clip-path on the site's buttons and panels. */
class CutTLBR(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }
        val p = Path().apply {
            moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
        }
        return Outline.Generic(p)
    }
}

/** The amber stroke under every h1 on lloydstellar.nl. */
@Composable
fun AmberStroke(width: Dp = 110.dp) {
    Canvas(Modifier.width(width).height(2.dp)) {
        drawRect(Brush.horizontalGradient(listOf(C.Amber, Color.Transparent)))
    }
}

/** A value that blinks on and off in hard steps, like the site's cursor. */
@Composable
fun rememberBlink(periodMs: Long = 540): Boolean {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); on = !on } }
    return on
}

/**
 * The console panel: dark fill, thin cyan edge, corners cut top-left and
 * bottom-right, brackets on the other two corners and faint scanlines.
 * One per group, never nested.
 */
@Composable
fun ConsolePanel(
    modifier: Modifier = Modifier,
    edge: Color = C.Cyan,
    fill: Color = Color(0xD901030C),
    content: @Composable () -> Unit,
) {
    val shape = CutTLBR(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, edge.copy(alpha = 0.35f), shape)
            .drawBehind {
                val sl = Color.Black.copy(alpha = 0.18f)
                var y = 2f
                while (y < size.height) { drawRect(sl, Offset(0f, y), Size(size.width, 1f)); y += 3f }
                val l = 26.dp.toPx(); val w = 2.dp.toPx()
                drawRect(edge, Offset(size.width - l, 0f), Size(l, w))
                drawRect(edge, Offset(size.width - w, 0f), Size(w, l))
                drawRect(edge, Offset(0f, size.height - w), Size(l, w))
                drawRect(edge, Offset(0f, size.height - l), Size(w, l))
            }
    ) { content() }
}

/** A dashed rule between rows. */
@Composable
fun DashedRule(modifier: Modifier = Modifier, color: Color = C.CyanDeep) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

/* ---------------- buttons ---------------- */

/**
 * The site's command button: "▸ LABEL", mono, uppercase, angular. Outlined in
 * its colour; filled with dark text when [primary] or while pressed (:hover).
 */
@Composable
fun CommandButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = C.Amber,
    primary: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    arrow: Boolean = true,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val compact = height < 50.dp
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val filled = enabled && (primary || pressed)
    val shape = CutTLBR(if (compact) 7.dp else 9.dp)
    val edge = if (enabled) color else C.CyanDeep
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (filled) color else if (enabled) color.copy(alpha = 0.08f) else Color(0x800A2040))
            .border(1.dp, edge, shape)
            .clickable(src, null, enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = if (compact) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val ink = if (!enabled) C.CyanDeep else if (filled) C.Void else color
        val style = when {
            height >= 56.dp -> mono(17.sp, ink, 2.sp)
            compact -> mono(13.sp, ink, 1.sp)
            else -> mono(15.sp, ink, 2.sp)
        }
        Text(if (arrow && !compact) "▸ $label" else label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, style = style.copy(color = ink.copy(alpha = 0.75f)), maxLines = 1)
        }
    }
}

/** A small square command button with an icon (back, play). */
@Composable
fun IconCommand(
    icon: ImageVector, description: String, modifier: Modifier = Modifier,
    size: Dp = 44.dp, color: Color = C.Cyan, enabled: Boolean = true, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val shape = CutTLBR(7.dp)
    val tint = if (!enabled) C.CyanDeep else if (pressed) C.Void else color
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(if (pressed && enabled) color else C.Void.copy(alpha = 0.7f))
            .border(1.dp, (if (enabled) color else C.CyanDeep).copy(alpha = 0.7f), shape)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClickLabel = description) {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Screen title: back button, h1 with its amber stroke. */
@Composable
fun ScreenTitle(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCommand(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text(title, style = h1(24.sp), modifier = Modifier.weight(1f), maxLines = 1)
            trailing?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        AmberStroke()
    }
}

/** The HUD chip of the site (.sd-badge): "TRACK 2 OF 5". */
@Composable
fun HudChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = mono(12.sp, C.Amber, 1.5.sp),
        modifier = modifier
            .border(1.dp, C.Amber)
            .background(C.Amber.copy(alpha = 0.1f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

/* ---------------- form controls ---------------- */

/** "> ZOOM", the site's form label: amber prompt, cyan uppercase mono. */
@Composable
fun SdLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("> ", style = mono(13.sp, C.Amber, 0.sp))
        Text(text.uppercase(), style = mono(13.sp, C.Cyan, 1.6.sp), maxLines = 1)
    }
}

/** The amber read-out next to a slider (.sd-value). */
@Composable
fun ValueBox(text: String) {
    Text(
        text,
        style = mono(13.sp, C.Amber, 1.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(76.dp)
            .border(1.dp, C.CyanDeep)
            .background(C.Void)
            .padding(vertical = 4.dp),
        maxLines = 1,
    )
}

/**
 * The site's range input: a ticked track and a square amber thumb. Tap or
 * drag anywhere on the row; TalkBack can set it too.
 */
@Composable
fun TermSlider(
    label: String,
    value: Int,
    range: IntRange,
    display: (Int) -> String,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SdLabel(label, Modifier.weight(1f))
            ValueBox(display(value))
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = display(value)
                    setProgress { target ->
                        val v = target.roundToInt().coerceIn(range)
                        change(v); true
                    }
                }
                .pointerInput(range) {
                    val thumb = 16.dp.toPx()
                    fun valueAt(x: Float): Int {
                        val f = ((x - thumb / 2) / (size.width - thumb)).coerceIn(0f, 1f)
                        return (range.first + f * (range.last - range.first)).roundToInt()
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun apply(v: Int) {
                            if (v != current) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                change(v)
                            }
                        }
                        apply(valueAt(down.position.x))
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            apply(valueAt(c.position.x))
                            c.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val th = 8.dp.toPx()
                val top = (size.height - th) / 2
                drawRect(Brush.verticalGradient(listOf(Color(0xE60F4868), Color(0xE6020714)), startY = top, endY = top + th), Offset(0f, top), Size(size.width, th))
                var x = 0f
                val tick = C.Cyan.copy(alpha = 0.22f)
                while (x < size.width) { drawRect(tick, Offset(x, top), Size(1f, th)); x += 8.dp.toPx() }
                drawRect(C.CyanDeep, Offset(0f, top), Size(size.width, th), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                val thumbW = 16.dp.toPx(); val thumbH = 24.dp.toPx()
                val f = (value - range.first).toFloat() / (range.last - range.first)
                val tx = f * (size.width - thumbW)
                drawRect(
                    Brush.verticalGradient(listOf(C.Amber, Color(0xFFB87C00)), startY = (size.height - thumbH) / 2, endY = (size.height + thumbH) / 2),
                    Offset(tx, (size.height - thumbH) / 2), Size(thumbW, thumbH),
                )
                drawRect(Color.White.copy(alpha = 0.35f), Offset(tx, (size.height - thumbH) / 2), Size(thumbW, thumbH), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
        }
    }
}

/**
 * A choice between a few options, all visible at once (the website uses
 * dropdowns; on a phone a row of options is one tap instead of two).
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    columns: Int = minOf(options.size, 4),
    onSelect: (T) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val on = option == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, if (on) C.Amber else C.CyanDeep)
                            .background(if (on) C.Amber.copy(alpha = 0.08f) else Color(0xB801030C))
                            .clickable(role = Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option)
                            }
                            .semantics { stateDescription = if (on) "Selected" else "Not selected" }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(option), style = mono(12.sp, if (on) C.Amber else C.CyanMid, 1.sp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                    if (row.size < columns && option == row.last()) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A setting as a terminal line: "SPIN UP ...... [ ON ]", the whole row toggles. */
@Composable
fun TermToggle(label: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) {
                haptic.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onChange(!checked)
            }
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = mono(15.sp, C.CyanBright, 1.sp), modifier = Modifier.weight(1f))
            Text(if (checked) "[ ON ]" else "[OFF ]", style = mono(15.sp, if (checked) C.Amber else C.CyanDim, 1.sp))
        }
        if (sub != null) Text(sub, style = mono(12.sp, C.CyanDim, 0.sp))
    }
}

/** A one-line text input in the site's style: void field, cyan edge, mono text. */
@Composable
fun TermTextField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SdLabel(label)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = mono(16.sp, C.CyanBright, 0.5.sp),
            cursorBrush = SolidColor(C.Amber),
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, C.CyanDim)
                .background(C.Void)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = mono(16.sp, C.CyanDeep, 0.5.sp))
                    inner()
                }
            },
        )
    }
}

/** A section heading inside a tab, mono, in cyan with tracking. */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = mono(12.sp, C.CyanDim, 2.sp), modifier = modifier.padding(top = 14.dp, bottom = 4.dp))
}

/** Hint text under a control (.sd-hint). */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, size: TextUnit = 12.sp) {
    Text(text, style = mono(size, C.CyanMid, 0.sp), modifier = modifier.padding(bottom = 6.dp))
}

/** The progress bar of the export panel. */
@Composable
fun ProgressBeam(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        drawRect(C.Void)
        drawRect(Brush.horizontalGradient(listOf(C.CyanDim, C.Cyan)), size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
        drawRect(C.CyanDeep, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}
