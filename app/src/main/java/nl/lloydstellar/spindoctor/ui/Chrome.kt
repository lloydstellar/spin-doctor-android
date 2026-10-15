package nl.lloydstellar.spindoctor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.lloydstellar.spindoctor.model.Step
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The page behind everything on lloydstellar.nl: the void, three faint nebula
 * glows (violet top right, cyan left, magenta below), a still starfield, the
 * CRT scanlines and the vignette.
 */
@Composable
fun SiteBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val r = Random(7)
        FloatArray(110 * 3) { r.nextFloat() }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(C.Void)
        val m = size.maxDimension
        drawRect(Brush.radialGradient(listOf(C.Violet.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.85f, -size.height * 0.1f), m * 0.62f))
        drawRect(Brush.radialGradient(listOf(C.Cyan.copy(alpha = 0.09f), Color.Transparent), Offset(-size.width * 0.12f, size.height * 0.18f), m * 0.55f))
        drawRect(Brush.radialGradient(listOf(C.Magenta.copy(alpha = 0.06f), Color.Transparent), Offset(size.width * 0.5f, size.height * 1.15f), m * 0.7f))
        var i = 0
        while (i < stars.size) {
            val a = 0.12f + 0.5f * stars[i + 2] * stars[i + 2]
            drawCircle(C.CyanBright.copy(alpha = a), radius = 0.7f + stars[i + 2] * 1.5f, center = Offset(stars[i] * size.width, stars[i + 1] * size.height))
            i += 3
        }
        val lines = Path()
        var y = 2f
        while (y < size.height) { lines.addRect(Rect(0f, y, size.width, y + 1f)); y += 3f }
        drawPath(lines, Color.Black.copy(alpha = 0.30f * 0.45f))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, C.Void.copy(alpha = 0.5f)), center, m * 0.75f))
    }
}

/** The status line of the site: system name on the left, a live clock on the right. */
@Composable
fun StatusLine(name: String) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.ENGLISH) }
    Column {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LLOYDOS v2.0 // $name", style = mono(12.sp, C.CyanDim, 1.sp), modifier = Modifier.weight(1f))
            Text(fmt.format(now), style = mono(12.sp, C.CyanMid, 1.sp))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(C.Cyan.copy(alpha = 0.3f)) }
    }
}

/**
 * The progress rail of the website (.sd-rail): five steps on a line, the
 * current one in amber, done ones with a check. Done steps can be tapped.
 */
@Composable
fun StepRail(current: Step, reachable: (Step) -> Boolean, onSelect: (Step) -> Unit) {
    val blink = rememberBlink(1000)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Step.entries.forEach { s ->
            val active = s == current
            val done = s.ordinal < current.ordinal
            val open = reachable(s) && !active
            /* the site's rail uses cyan-deep for steps ahead; on a phone that is too faint to read */
            val color = when {
                active -> C.Amber
                done -> C.CyanMid
                else -> C.CyanDim
            }
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = open, role = Role.Tab) { onSelect(s) }
                    .semantics { contentDescription = "Step ${s.number}, ${s.label}" + if (active) ", current" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                    drawRect(if (active && !blink) color.copy(alpha = 0.45f) else color, size = Size(size.width, size.height))
                }
                Spacer(Modifier.height(5.dp))
                Text(if (done) "${s.number} ✓" else s.number, style = mono(10.sp, if (done) C.Green.copy(alpha = 0.9f) else color.copy(alpha = 0.8f), 1.5.sp))
                Text(s.label, style = mono(10.sp, color, 1.sp), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
