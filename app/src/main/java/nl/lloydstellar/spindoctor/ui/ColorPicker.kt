package nl.lloydstellar.spindoctor.ui

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

fun hex(color: Int) = "#%06X".format(Locale.ROOT, color and 0xFFFFFF)

/** The colour row of a panel: label, hex value and the swatch (.option-color). */
@Composable
fun ColorRow(label: String, color: Int, presets: List<Int>, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = "$label, ${hex(color)}" }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SdLabel(label, Modifier.weight(1f))
        Text(hex(color), style = mono(13.sp, C.CyanMid, 1.sp))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(52.dp, 36.dp).border(1.dp, C.CyanDim).background(C.Void).padding(3.dp)
                .background(Color(color or 0xFF000000.toInt()))
        )
    }
    if (open) ColorDialog(label, color, presets, onDismiss = { open = false }) { onChange(it); open = false }
}

/** Hue bar, saturation/brightness field, presets and a hex field. */
@Composable
fun ColorDialog(title: String, initial: Int, presets: List<Int>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { AColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val picked = AColor.HSVToColor(floatArrayOf(hue, sat, value))
    var hexText by remember { mutableStateOf(hex(initial)) }

    fun set(color: Int) {
        val f = FloatArray(3); AColor.colorToHSV(color, f)
        /* keep the hue when picking a grey, so the field does not jump */
        if (f[1] > 0.001f) hue = f[0]
        sat = f[1]; value = f[2]
        hexText = hex(color)
    }

    Dialog(onDismissRequest = onDismiss) {
        ConsolePanel(Modifier.fillMaxWidth(), fill = C.Void) {
            Column(Modifier.padding(18.dp)) {
                Text(title.uppercase(), style = mono(14.sp, C.Cyan, 2.sp))
                Spacer(Modifier.height(14.dp))

                /* saturation left to right, brightness top to bottom */
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1.6f)
                        .semantics { contentDescription = "Saturation and brightness" }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun at(o: Offset) {
                                    sat = (o.x / size.width).coerceIn(0f, 1f)
                                    value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                    hexText = hex(AColor.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                                at(down.position)
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull() ?: break
                                    if (!c.pressed) break
                                    at(c.position); c.consume()
                                }
                            }
                        }
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color(AColor.HSVToColor(floatArrayOf(hue, 1f, 1f))))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawRect(C.CyanDim, style = Stroke(1.dp.toPx()))
                    val p = Offset(sat * size.width, (1 - value) * size.height)
                    drawCircle(Color.Black, 9.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
                }
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(30.dp)
                        .semantics { contentDescription = "Hue" }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun at(x: Float) {
                                    hue = (x / size.width).coerceIn(0f, 1f) * 360f
                                    hexText = hex(AColor.HSVToColor(floatArrayOf(hue, sat, value)))
                                }
                                at(down.position.x)
                                while (true) {
                                    val c = awaitPointerEvent().changes.firstOrNull() ?: break
                                    if (!c.pressed) break
                                    at(c.position.x); c.consume()
                                }
                            }
                        }
                ) {
                    drawRect(Brush.horizontalGradient((0..6).map { Color(AColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
                    drawRect(C.CyanDim, style = Stroke(1.dp.toPx()))
                    val x = hue / 360f * size.width
                    drawRect(Color.White, Offset(x - 2.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { c ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).border(1.dp, if (c == picked) C.Amber else C.CyanDeep).padding(2.dp)
                                .background(Color(c))
                                .clickable(role = Role.Button) { set(c) }
                                .semantics { contentDescription = hex(c) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp, 40.dp).border(1.dp, C.CyanDim).padding(3.dp).background(Color(picked)))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = hexText,
                        onValueChange = { t ->
                            hexText = t.take(7)
                            parseHex(t)?.let { set(it); hexText = t.take(7) }
                        },
                        singleLine = true,
                        textStyle = mono(16.sp, C.CyanBright, 1.sp),
                        cursorBrush = SolidColor(C.Amber),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f).border(1.dp, C.CyanDim).background(C.Void).padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandButton("CANCEL", Modifier.weight(1f), color = C.Cyan, height = 48.dp, onClick = onDismiss)
                    CommandButton("USE", Modifier.weight(1f), primary = true, height = 48.dp) { onPick(picked or 0xFF000000.toInt()) }
                }
            }
        }
    }
}

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || t.any { it !in "0123456789abcdefABCDEF" }) return null
    return 0xFF000000.toInt() or t.toInt(16)
}
