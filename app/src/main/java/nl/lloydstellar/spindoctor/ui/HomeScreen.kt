package nl.lloydstellar.spindoctor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.MAX_TRACKS
import nl.lloydstellar.spindoctor.model.Mode
import nl.lloydstellar.spindoctor.render.Fonts
import nl.lloydstellar.spindoctor.render.VinylRenderer

/**
 * The intro screen in the LLOYDOS style of the website and Stellar
 * Singularity: status line, the h1 with its amber stroke, and a real record
 * spinning in a console panel, drawn by the same renderer as the videos.
 */
@Composable
fun HomeScreen(onStart: (Mode) -> Unit, onContinue: () -> Unit, onAbout: () -> Unit, onWebsite: () -> Unit) {
    val session = App.session
    val resumable = session.cover != null || session.tracks.isNotEmpty()

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 22.dp)
    ) {
        StatusLine("SPIN DOCTOR")
        Spacer(Modifier.height(22.dp))
        Text("LLOYD'S", style = mono(14.sp, C.CyanMid, 4.sp))
        Text("SPIN\nDOCTOR", style = h1(44.sp, lineHeight = 48.sp))
        Spacer(Modifier.height(10.dp))
        AmberStroke(120.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Turn a release into a spinning vinyl clip for Instagram, TikTok and Reels. " +
                "Pick your artwork and audio, choose the part people should hear, and the video goes straight into your gallery.",
            style = mono(14.sp, C.CyanMid, 0.sp),
        )

        /* the record takes what is left, so small phones still show every button */
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            DemoRecord(Modifier.sizeIn(maxWidth = 300.dp, maxHeight = 300.dp).aspectRatio(1f, matchHeightConstraintsFirst = true))
        }

        if (resumable) {
            CommandButton("CONTINUE", Modifier.fillMaxWidth(), primary = true, height = 58.dp, trailing = session.step.label, onClick = onContinue)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("NEW SINGLE", Modifier.weight(1f), height = 48.dp) { onStart(Mode.SINGLE) }
                CommandButton("NEW RELEASE", Modifier.weight(1f), height = 48.dp) { onStart(Mode.RELEASE) }
            }
        } else {
            CommandButton("SINGLE TRACK", Modifier.fillMaxWidth(), primary = true, height = 58.dp, trailing = "1 VIDEO") { onStart(Mode.SINGLE) }
            Spacer(Modifier.height(10.dp))
            CommandButton("FULL RELEASE", Modifier.fillMaxWidth(), height = 54.dp, trailing = "UP TO $MAX_TRACKS") { onStart(Mode.RELEASE) }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Prompt(onWebsite, Modifier.weight(1f))
            CommandButton("ABOUT", color = C.Cyan, height = 40.dp, onClick = onAbout)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A record with the Spin Doctor label, turning at 33 RPM. */
@Composable
private fun DemoRecord(modifier: Modifier = Modifier) {
    val label = remember { demoLabel(512) }
    val look = remember { Look(backgroundColor = 0xFF01030C.toInt(), zoom = 92) }
    var time by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        val t0 = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - t0) / 1e9 }
    }
    ConsolePanel(modifier) {
        Box(Modifier.fillMaxSize().padding(1.dp)) {
            VinylView(
                look, label, null, "",
                rotation = VinylRenderer.rotationAt(time, look), animated = true, pulse = 0.0,
                modifier = Modifier.fillMaxSize(),
            )
            Text("33 RPM", style = mono(11.sp, C.CyanDim, 1.5.sp), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }
}

/** The demo label: flat amber paper with the name, in the site's typefaces. */
private fun demoLabel(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFFFFB000.toInt()
    c.drawCircle(r, r, r, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = size * 0.012f
    p.color = 0x9901030C.toInt()
    c.drawCircle(r, r, r * 0.9f, p)
    p.style = Paint.Style.FILL
    p.color = 0xFF01030C.toInt()
    p.textAlign = Paint.Align.CENTER

    /* each line shrinks until it fits inside the ring at its height, with some air */
    fun line(text: String, x: Float, baseline: Float, height: Float, maxWidth: Float = Float.MAX_VALUE) {
        p.textSize = height
        val top = baseline + p.fontMetrics.ascent
        val bottom = baseline + p.fontMetrics.descent
        val far = maxOf(kotlin.math.abs(top - r), kotlin.math.abs(bottom - r))
        val inner = r * 0.82f
        val chord = if (far < inner) 2 * kotlin.math.sqrt(inner * inner - far * far) else 0f
        val room = minOf(chord, maxWidth)
        val width = p.measureText(text)
        if (width > room && room > 0) p.textSize = height * room / width
        c.drawText(text, x, baseline, p)
    }
    p.typeface = Fonts.orbitron800
    line("SPIN", r, r - size * 0.1f, size * 0.15f)
    line("DOCTOR", r, r + size * 0.21f, size * 0.15f)
    p.typeface = Fonts.mono
    p.letterSpacing = 0.2f
    line("LLOYD STELLAR", r, r - size * 0.255f, size * 0.065f)
    line("SIDE A", r - size * 0.27f, r + size * 0.02f, size * 0.065f, size * 0.2f)
    line("33⅓", r + size * 0.27f, r + size * 0.02f, size * 0.065f, size * 0.2f)
    return bmp
}

/** The terminal prompt of lloydstellar.nl, with the blinking block cursor. */
@Composable
private fun Prompt(onWebsite: () -> Unit, modifier: Modifier = Modifier) {
    val on = rememberBlink()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = C.CyanDim)) { append("guest@lloydstellar:~$ ") }
            withStyle(SpanStyle(color = C.Cyan, textDecoration = TextDecoration.Underline)) { append("lloydstellar.nl") }
            withStyle(SpanStyle(color = if (on) C.Cyan else Color.Transparent)) { append(" ▎") }
        },
        style = mono(13.sp, C.Cyan, 0.5.sp),
        modifier = modifier.clickable(onClick = onWebsite).padding(vertical = 10.dp),
        maxLines = 1,
    )
}
