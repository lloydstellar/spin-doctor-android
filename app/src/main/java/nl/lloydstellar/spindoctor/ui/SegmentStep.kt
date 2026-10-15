package nl.lloydstellar.spindoctor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.audio.Peaks
import nl.lloydstellar.spindoctor.model.MAX_CLIP
import nl.lloydstellar.spindoctor.model.MIN_CLIP
import nl.lloydstellar.spindoctor.model.Mode
import nl.lloydstellar.spindoctor.model.Track

private val WaveColor = Color(0xFF2A7898)      // wavesurfer waveColor on the website
private val WaveSelected = Color(0xFF4DE0FF)   // progressColor
private val RegionFill = Color(0x38FFB000)     // rgba(255, 176, 0, 0.22)

fun clockTenths(seconds: Double): String {
    val t = (seconds * 10).roundToInt().coerceAtLeast(0)
    return "%d:%02d.%d".format(Locale.ROOT, t / 600, (t / 10) % 60, t % 10)
}

/** Step 3: choose the 5 to 60 seconds that go into the video, per track. */
@Composable
fun SegmentStep(next: () -> Unit) {
    val s = App.session
    val index = s.segmentIndex.coerceIn(0, max(0, s.tracks.size - 1))
    val track = s.tracks.getOrNull(index) ?: return
    val peaks = track.peaks ?: return
    val player = App.player
    val release = s.mode == Mode.RELEASE
    val last = index == s.tracks.size - 1

    var playhead by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(player.playing) {
        while (player.playing) withFrameNanos { playhead = player.positionOrNull() }
        playhead = null
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    fun play() = player.play(track.uri, track.start, track.end)
    fun replay() { if (player.playing) play() }

    StepLayout(bottom = {
        if (release) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("PREVIOUS", Modifier.weight(1f), color = C.Cyan, enabled = index > 0, arrow = false) {
                    player.stop(); s.segmentIndex = index - 1
                }
                CommandButton(if (last) "DONE" else "NEXT TRACK", Modifier.weight(1.4f), primary = true) {
                    player.stop()
                    track.segmentChosen = true
                    if (last) next() else s.segmentIndex = index + 1
                }
            }
        } else {
            CommandButton("USE THIS PART", Modifier.fillMaxWidth(), primary = true) {
                player.stop(); track.segmentChosen = true; next()
            }
        }
    }) {
        if (release) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.fileName, style = mono(14.sp, C.CyanBright, 0.sp), maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                HudChip("TRACK ${index + 1} OF ${s.tracks.size}")
            }
            Spacer(Modifier.height(10.dp))
        }

        SubHeading("Whole track")
        Overview(track, peaks, playhead, onMove = { track.setSegment(it, track.length) }, onRelease = ::replay)
        Row {
            Text("0:00", style = mono(11.sp, C.CyanDim, 1.sp), modifier = Modifier.weight(1f))
            Text(clock(track.duration), style = mono(11.sp, C.CyanDim, 1.sp))
        }

        SubHeading("Selection, close up")
        Detail(track, peaks, playhead, onRelease = ::replay)
        Hint("Drag the waveform to move the clip. Drag the amber edges to make it shorter or longer.", Modifier.padding(top = 6.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("IN", clockTenths(track.start))
            Readout("OUT", clockTenths(track.end))
            Readout("LENGTH", "%.1f S".format(Locale.ROOT, track.length))
        }

        SubHeading("Length")
        val lengths = listOf(5, 10, 15, 30, 60).filter { it <= track.duration + 0.01 }
        val exact = lengths.firstOrNull { abs(it - track.length) < 0.05 } ?: -1
        Segmented(lengths, exact, { "${it}S" }, columns = lengths.size) {
            track.setSegment(track.start, it.toDouble()); replay()
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CommandButton("−1 S", Modifier.weight(1f), color = C.Cyan, height = 48.dp) { track.setSegment(track.start - 1, track.length); replay() }
            CommandButton(
                if (player.playing) "■ STOP" else "▸ PLAY", Modifier.weight(1.6f), height = 48.dp, arrow = false,
            ) { if (player.playing) player.stop() else play() }
            CommandButton("+1 S", Modifier.weight(1f), color = C.Cyan, height = 48.dp) { track.setSegment(track.start + 1, track.length); replay() }
        }
    }
}

@Composable
private fun Readout(label: String, value: String) {
    Column {
        Text(label, style = mono(11.sp, C.CyanDim, 1.5.sp))
        Text(value, style = mono(16.sp, C.Amber, 1.sp))
    }
}

/**
 * Draws the waveform for [t0]..[t1] seconds across the full width, [height]
 * high, the way a DAW does: the peak dim behind, the RMS bright in front.
 */
private fun DrawScope.bars(peaks: Peaks, t0: Double, t1: Double, selected: ClosedFloatingPointRange<Double>, height: Float = size.height) {
    val bar = 2.dp.toPx()
    val gap = 1.dp.toPx()
    val count = (size.width / (bar + gap)).toInt().coerceAtLeast(1)
    val mid = height / 2
    for (i in 0 until count) {
        val a = t0 + (t1 - t0) * i / count
        val b = t0 + (t1 - t0) * (i + 1) / count
        val from = floor(a * peaks.perSecond).toInt().coerceAtLeast(0)
        val to = ceil(b * peaks.perSecond).toInt().coerceAtMost(peaks.values.size)
        var peak = 0f
        var power = 0f
        for (k in from until to) {
            if (peaks.values[k] > peak) peak = peaks.values[k]
            power += peaks.rms[k] * peaks.rms[k]
        }
        if (to <= from) continue
        val loud = sqrt(power / (to - from))
        val colour = if ((a + b) / 2 in selected) WaveSelected else WaveColor
        val x = i * (bar + gap)
        val hp = max(1f, peak / peaks.loudest * (height - 4))
        val hr = max(1f, loud / peaks.loudestRms * (height - 4) * 0.92f)
        drawRect(colour.copy(alpha = 0.35f), Offset(x, mid - hp / 2), Size(bar, hp))
        drawRect(colour, Offset(x, mid - hr / 2), Size(bar, hr))
    }
}

/** The whole track with the selected window. Tap or drag to move the window. */
@Composable
private fun Overview(track: Track, peaks: Peaks, playhead: Double?, onMove: (Double) -> Unit, onRelease: () -> Unit) {
    val move by rememberUpdatedState(onMove)
    val release by rememberUpdatedState(onRelease)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics { contentDescription = "Whole track, selection from ${clock(track.start)} to ${clock(track.end)}" }
            .pointerInput(track) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val perPx = track.duration / size.width
                    val at = down.position.x * perPx
                    val grab = if (at in track.start..track.end) at - track.start else track.length / 2
                    move(at - grab)
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        move(c.position.x * perPx - grab)
                        c.consume()
                    }
                    release()
                }
            }
    ) {
        drawRect(C.Void)
        bars(peaks, 0.0, track.duration, track.start..track.end)
        val x0 = (track.start / track.duration * size.width).toFloat()
        val x1 = (track.end / track.duration * size.width).toFloat()
        drawRect(RegionFill, Offset(x0, 0f), Size(x1 - x0, size.height))
        drawRect(C.Amber, Offset(x0, 0f), Size(2.dp.toPx(), size.height))
        drawRect(C.Amber, Offset(x1 - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        playhead?.let {
            val x = (it / track.duration * size.width).toFloat()
            drawRect(Color.White, Offset(x, 0f), Size(1.5.dp.toPx(), size.height))
        }
        drawRect(C.CyanDeep, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}

private enum class Grip { MOVE, IN, OUT }

/**
 * The selection close up. The window sits in the middle and the waveform
 * slides under it like tape; the edges trim. Ticks every second, labels
 * every five.
 */
@Composable
private fun Detail(track: Track, peaks: Peaks, playhead: Double?, onRelease: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val release by rememberUpdatedState(onRelease)
    /* while an edge is dragged the view holds still, otherwise it follows the window */
    var frozen by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val span = track.length * 1.7
    val viewStart = frozen?.first ?: (track.start - (span - track.length) / 2)
    val viewSpan = frozen?.second ?: span

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "Selection close up" }
            .pointerInput(track) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val vs = track.start - (track.length * 1.7 - track.length) / 2
                    val vSpan = track.length * 1.7
                    val perPx = vSpan / size.width
                    val xIn = ((track.start - vs) / perPx).toFloat()
                    val xOut = ((track.end - vs) / perPx).toFloat()
                    val reach = 28.dp.toPx()
                    val grip = when {
                        abs(down.position.x - xIn) < reach -> Grip.IN
                        abs(down.position.x - xOut) < reach -> Grip.OUT
                        else -> Grip.MOVE
                    }
                    if (grip != Grip.MOVE) frozen = vs to vSpan
                    val start0 = track.start
                    val end0 = track.end
                    var lastSecond = floor(track.start).toInt()
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        val dt = (c.position.x - down.position.x) * perPx
                        when (grip) {
                            Grip.MOVE -> track.setSegment(start0 - dt, track.length)
                            Grip.IN -> {
                                val s = (start0 + dt).coerceIn(max(0.0, end0 - MAX_CLIP), end0 - MIN_CLIP)
                                track.setSegment(s, end0 - s)
                            }
                            Grip.OUT -> {
                                val e = (end0 + dt).coerceIn(start0 + MIN_CLIP, min(track.duration, start0 + MAX_CLIP))
                                track.setSegment(start0, e - start0)
                            }
                        }
                        val second = floor(if (grip == Grip.OUT) track.end else track.start).toInt()
                        if (second != lastSecond) {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            lastSecond = second
                        }
                        c.consume()
                    }
                    frozen = null
                    release()
                }
            }
    ) {
        drawRect(C.Void)
        val perPx = viewSpan / size.width
        /* the waveform in a band above the time scale */
        val waveHeight = size.height - 22.dp.toPx()
        bars(peaks, viewStart, viewStart + viewSpan, track.start..track.end, waveHeight)

        val x0 = ((track.start - viewStart) / perPx).toFloat()
        val x1 = ((track.end - viewStart) / perPx).toFloat()
        drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, Size(x0.coerceAtLeast(0f), waveHeight))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(x1, 0f), Size((size.width - x1).coerceAtLeast(0f), waveHeight))
        drawRect(RegionFill, Offset(x0, 0f), Size(x1 - x0, waveHeight))
        val gripW = 4.dp.toPx()
        for (x in floatArrayOf(x0, x1 - gripW)) {
            drawRect(C.Amber, Offset(x, 0f), Size(gripW, waveHeight))
            /* the grip: three short lines in the middle of each edge */
            for (k in -1..1) drawRect(C.Void, Offset(x + 1f, waveHeight / 2 + k * 6.dp.toPx() - 1f), Size(gripW - 2f, 2f))
        }
        playhead?.let {
            val x = ((it - viewStart) / perPx).toFloat()
            drawRect(Color.White, Offset(x, 0f), Size(1.5.dp.toPx(), waveHeight))
        }

        /* time scale */
        val first = ceil(viewStart).toInt()
        val lastS = floor(viewStart + viewSpan).toInt()
        val labelEvery = if (viewSpan > 30) 10 else 5
        for (sec in first..lastS) {
            if (sec < 0 || sec > track.duration) continue
            val x = ((sec - viewStart) / perPx).toFloat()
            val major = sec % labelEvery == 0
            drawRect(if (major) C.CyanMid else C.CyanDeep, Offset(x, waveHeight), Size(1f, if (major) 8.dp.toPx() else 4.dp.toPx()))
            if (major) drawText(measurer, clock(sec.toDouble()), Offset(x + 3.dp.toPx(), waveHeight + 4.dp.toPx()), mono(10.sp, C.CyanDim, 0.5.sp))
        }
        drawRect(C.CyanDeep, Offset.Zero, Size(size.width, waveHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}
