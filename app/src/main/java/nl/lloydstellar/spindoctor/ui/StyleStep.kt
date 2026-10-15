package nl.lloydstellar.spindoctor.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.R
import nl.lloydstellar.spindoctor.audio.AudioDecoder
import nl.lloydstellar.spindoctor.audio.BeatEnvelope
import nl.lloydstellar.spindoctor.audio.ClipAudio
import nl.lloydstellar.spindoctor.audio.Pcm
import nl.lloydstellar.spindoctor.model.BackgroundType
import nl.lloydstellar.spindoctor.model.BadgeType
import nl.lloydstellar.spindoctor.model.BeatPulse
import nl.lloydstellar.spindoctor.model.Finish
import nl.lloydstellar.spindoctor.model.Format
import nl.lloydstellar.spindoctor.model.Images
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.Mode
import nl.lloydstellar.spindoctor.model.TextPlace
import nl.lloydstellar.spindoctor.model.TextStyle
import nl.lloydstellar.spindoctor.model.Track
import nl.lloydstellar.spindoctor.render.VinylRenderer
import nl.lloydstellar.spindoctor.render.badgeLabel
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val BACKGROUND_PRESETS = listOf(0xFF000000, 0xFF01030C, 0xFF0A2850, 0xFF2B1B3F, 0xFFA07070, 0xFFF2EDE4, 0xFFFFFFFF, 0xFFFFB000).map { it.toInt() }
private val VINYL_PRESETS = listOf(0xFFC81E3C, 0xFFFFB000, 0xFF4DE0FF, 0xFF8B5CF6, 0xFFFF4DDB, 0xFF2E8B57, 0xFFF2EDE4, 0xFF111111).map { it.toInt() }
private val TEXT_PRESETS = listOf(0xFFFFFFFF, 0xFFCDF4FF, 0xFF4DE0FF, 0xFFFFB000, 0xFFFF4DDB, 0xFFF2EDE4, 0xFF888888, 0xFF000000).map { it.toInt() }

private enum class Tab(val label: String) { FORMAT("FORMAT"), VINYL("VINYL"), BACKGROUND("BACKGROUND"), TEXT("TEXT"), MOTION("MOTION") }

/** Step 4: the style, with the live preview pinned on top. */
@Composable
fun StyleStep(next: () -> Unit) {
    val s = App.session
    val look = App.look
    val track = s.previewTrack() ?: return
    val player = App.pcmPlayer
    val release = s.mode == Mode.RELEASE && s.tracks.size > 1
    var tab by rememberSaveable { mutableStateOf(Tab.FORMAT) }
    var safeArea by rememberSaveable { mutableStateOf(false) }

    /*
     * The preview plays the clip's sound exactly as it goes into the video, spin up
     * included, and takes its clock from the frames the speaker has played.
     */
    val sound = rememberClipSound(track, look.spinUpTime)
    val envelope = produceState<BeatEnvelope?>(null, sound, look.beatPulse != BeatPulse.OFF) {
        value = if (sound != null && look.beatPulse != BeatPulse.OFF) withContext(Dispatchers.Default) { BeatEnvelope.build(sound) } else null
    }.value
    var wantPlay by remember { mutableStateOf(false) }
    val playing = player.playing
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(playing) {
        while (playing) withFrameNanos { elapsed = player.positionSeconds() ?: elapsed }
        elapsed = 0.0
    }
    /* a tap before the sound is ready starts it as soon as it is */
    LaunchedEffect(wantPlay, sound) {
        if (wantPlay && sound != null) { wantPlay = false; player.play(sound) }
    }
    /* a change to the spin up while playing restarts the clip with the new sound */
    LaunchedEffect(sound) { if (player.playing) { player.stop(); wantPlay = true } }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    fun stop() { player.stop(); wantPlay = false }
    fun play() { wantPlay = true }
    val pulse = if (playing) (envelope?.at(elapsed) ?: 0.0) * look.beatPulse.amount else 0.0

    StepLayout(scroll = false, bottom = {
        CommandButton("NEXT: EXPORT", Modifier.fillMaxWidth(), primary = true) { stop(); next() }
    }) {
        /* preview, sized to leave most of the screen for the options */
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        /* the preview plays when tapped, so no button row takes room from the options */
        BoxWithConstraints(Modifier.fillMaxWidth().height(screenH * 0.36f), contentAlignment = Alignment.Center) {
            val aspect = look.format.aspect
            val w = min(maxWidth, maxHeight * aspect)
            ConsolePanel(Modifier.width(w).height(w / aspect)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClickLabel = if (playing) "Stop" else "Play") {
                            if (playing || wantPlay) stop() else play()
                        }
                ) {
                    VinylView(
                        look, s.cover, s.backgroundImage, track.title,
                        rotation = if (playing) VinylRenderer.rotationAt(elapsed, look) else 0.0,
                        animated = playing, pulse = pulse,
                        modifier = Modifier.fillMaxSize(), safeArea = safeArea,
                    )
                    Icon(
                        if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = C.Amber,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp)
                            .background(C.Void.copy(alpha = 0.6f)).padding(3.dp),
                    )
                }
            }
        }
        if (release) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconCommand(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous track", enabled = s.previewIndex > 0) {
                    stop(); s.previewIndex -= 1
                }
                Column(Modifier.weight(1f)) {
                    Text("TRACK ${s.previewIndex + 1} OF ${s.tracks.size}", style = mono(12.sp, C.Amber, 1.5.sp))
                    Text(track.fileName, style = mono(12.sp, C.CyanMid, 0.sp), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
                IconCommand(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next track", enabled = s.previewIndex < s.tracks.size - 1) {
                    stop(); s.previewIndex += 1
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        /* all five tabs in view, so none is hidden off the edge */
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(Modifier.clickable(role = Role.Tab) { tab = t }.padding(vertical = 8.dp)) {
                    Text(t.label, style = mono(13.sp, if (on) C.Amber else C.CyanMid, 1.sp), maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    if (on) AmberStroke(40.dp) else Spacer(Modifier.height(2.dp))
                }
            }
        }
        DashedRule()
        /* every tab opens at its top */
        val scroll = remember(tab) { ScrollState(0) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Spacer(Modifier.height(4.dp))
            when (tab) {
                Tab.FORMAT -> FormatTab(look)
                Tab.VINYL -> VinylTab(look)
                Tab.BACKGROUND -> BackgroundTab(look)
                Tab.TEXT -> TextTab(look, track, release)
                Tab.MOTION -> MotionTab(look, safeArea) { safeArea = it }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * The sound of the shown clip as it goes into the video, worked out in the
 * background; null until ready. The segment is decoded once, a change of the
 * spin up only re-renders it.
 */
@Composable
private fun rememberClipSound(track: Track, spinUpSeconds: Double): Pcm? {
    val ctx = LocalContext.current
    val segment = produceState<Pcm?>(null, track, track.start, track.end) {
        value = null
        value = withContext(Dispatchers.Default) {
            runCatching { AudioDecoder.region(ctx, track.uri, track.start, track.end) }.getOrNull()
        }
    }.value
    return produceState<Pcm?>(null, segment, spinUpSeconds) {
        value = segment?.let { withContext(Dispatchers.Default) { ClipAudio.forVideo(it, spinUpSeconds) } }
    }.value
}

private fun update(change: (Look) -> Look) = App.updateLook(change)

@Composable
private fun FormatTab(look: Look) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Format.entries.forEach { f ->
            val on = f == look.format
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (on) C.Amber else C.CyanDeep)
                    .background(if (on) C.Amber.copy(alpha = 0.07f) else C.Void.copy(alpha = 0.7f))
                    .clickable(role = Role.RadioButton) { update { it.copy(format = f) } }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(if (f.aspect >= 1f) 34.dp else 34.dp * f.aspect, if (f.aspect >= 1f) 34.dp / f.aspect else 34.dp)) {
                        drawRect(if (on) C.Amber else C.CyanDim, style = Stroke(1.5.dp.toPx()))
                        drawRect((if (on) C.Amber else C.CyanDim).copy(alpha = 0.15f), Offset.Zero, Size(size.width, size.height))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${f.label} ${f.ratio}", style = mono(15.sp, if (on) C.Amber else C.CyanBright, 1.5.sp))
                    Text(f.platforms, style = mono(12.sp, C.CyanMid, 0.sp))
                }
                Text("${f.width}×${f.height}", style = mono(11.sp, C.CyanDim, 0.5.sp))
            }
        }
        Hint("Square for feeds, vertical for Stories, Reels and TikTok, portrait for the Instagram feed.")
    }
}

@Composable
private fun VinylTab(look: Look) {
    TermSlider("Zoom", look.zoom, Look.ZOOM_MIN..Look.ZOOM_MAX, { "$it%" }) { v -> update { it.copy(zoom = v) } }
    TermSlider("Speed", look.rpm, Look.RPM_MIN..Look.RPM_MAX, { "$it RPM" }) { v -> update { it.copy(rpm = v) } }
    SubHeading("Finish")
    Segmented(Finish.entries, look.finish, { it.label }) { v -> update { it.copy(finish = v) } }
    if (look.finish.coloured) ColorRow("Colour", look.vinylColor, VINYL_PRESETS) { v -> update { it.copy(vinylColor = v) } }
    if (look.finish.twoColours) ColorRow("Accent", look.vinylColor2, VINYL_PRESETS) { v -> update { it.copy(vinylColor2 = v) } }
    if (look.finish == Finish.MARBLE) Hint("Marble is mixed per pixel; a new colour takes a moment to press.")
    TermToggle("Center hole", "The spindle hole in the middle of the label", look.centerHole) { v -> update { it.copy(centerHole = v) } }
}

@Composable
private fun BackgroundTab(look: Look) {
    val s = App.session
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Images.load(ctx, uri) }.onSuccess {
                s.backgroundImage = it
                update { l -> l.copy(backgroundType = BackgroundType.IMAGE) }
            }
        }
    }
    SubHeading("Background")
    Segmented(listOf(BackgroundType.COLOR, BackgroundType.IMAGE), look.backgroundType, { if (it == BackgroundType.COLOR) "COLOUR" else "IMAGE" }) { v ->
        update { it.copy(backgroundType = v) }
        if (v == BackgroundType.IMAGE && s.backgroundImage == null) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Spacer(Modifier.height(6.dp))
    if (look.backgroundType == BackgroundType.COLOR) {
        ColorRow("Colour", look.backgroundColor, BACKGROUND_PRESETS) { v -> update { it.copy(backgroundColor = v) } }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            s.backgroundImage?.let {
                Image(
                    it.asImageBitmap(), "Background image", Modifier.size(64.dp).border(1.dp, C.CyanDim),
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium,
                )
                Spacer(Modifier.width(12.dp))
            }
            CommandButton(if (s.backgroundImage == null) "SELECT IMAGE" else "OTHER IMAGE", Modifier.weight(1f), color = C.Cyan, height = 48.dp) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (s.backgroundImage != null) {
            TermSlider("Blur", look.blur, 0..Look.BLUR_MAX, { "${it}px" }) { v -> update { it.copy(blur = v) } }
        }
    }
}

@Composable
private fun TextTab(look: Look, track: Track, release: Boolean) {
    val ctx = LocalContext.current
    TermToggle("Artist & title", "Your name and the track title on the frame", look.textEnabled) { v -> update { it.copy(textEnabled = v) } }
    if (look.textEnabled) {
        TermTextField("Artist", look.artist, "Artist name", { v -> update { it.copy(artist = v) } })
        TermTextField(if (release) "Title of this track" else "Title", track.title, "Track title", { track.title = it })
        if (release) Hint("Every track keeps its own title. Use the arrows above the tabs to go to the next one.")
        SubHeading("Style")
        Segmented(TextStyle.entries, look.textStyle, { it.label }) { v -> update { it.copy(textStyle = v) } }
        SubHeading("Place")
        Segmented(TextPlace.entries, look.textPlace, { it.label }) { v -> update { it.copy(textPlace = v) } }
        ColorRow("Colour", look.textColor, TEXT_PRESETS) { v -> update { it.copy(textColor = v) } }
    }
    DashedRule(Modifier.padding(vertical = 6.dp))
    TermToggle("Badge", "OUT NOW, NEW EP or your own text", look.badgeEnabled) { v -> update { it.copy(badgeEnabled = v) } }
    if (look.badgeEnabled) {
        Segmented(BadgeType.entries, look.badgeType, { it.label }, columns = 3) { v ->
            update {
                if (v == BadgeType.DATE && it.badgeDay == 0) {
                    /* records come out on Fridays */
                    val friday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                    it.copy(badgeType = v, badgeDay = friday.dayOfMonth, badgeMonth = friday.monthValue)
                } else it.copy(badgeType = v)
            }
        }
        Spacer(Modifier.height(4.dp))
        when (look.badgeType) {
            BadgeType.CUSTOM -> TermTextField("Text", look.badgeText, "OUT FRIDAY", { v -> update { it.copy(badgeText = v) } }, KeyboardCapitalization.Characters)
            BadgeType.DATE -> Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    val today = LocalDate.now()
                    val month = if (look.badgeMonth in 1..12) look.badgeMonth else today.monthValue
                    val day = if (look.badgeDay in 1..31) look.badgeDay else today.dayOfMonth
                    DatePickerDialog(ctx, R.style.Theme_SpinDoctor_Dialog, { _, _, m, d ->
                        update { it.copy(badgeDay = d, badgeMonth = m + 1) }
                    }, today.year, month - 1, day).show()
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SdLabel("Release date", Modifier.weight(1f))
                Text(badgeLabel(look), style = mono(14.sp, C.Amber, 1.sp), textAlign = TextAlign.End)
            }
            else -> {}
        }
        ColorRow("Colour", look.badgeColor, VINYL_PRESETS) { v -> update { it.copy(badgeColor = v) } }
    }
}

@Composable
private fun MotionTab(look: Look, safeArea: Boolean, onSafeArea: (Boolean) -> Unit) {
    TermToggle("Spin up", "The record starts still and the music starts with it, from a low rumble up to normal pitch", look.spinUp) { v -> update { it.copy(spinUp = v) } }
    if (look.spinUp) {
        val tenths = (Look.SPIN_UP_MIN * 10).roundToInt()..(Look.SPIN_UP_MAX * 10).roundToInt()
        TermSlider("Spin up time", (look.spinUpSeconds * 10).roundToInt(), tenths, { "%.1f S".format(Locale.ROOT, it / 10.0) }) { v ->
            update { it.copy(spinUpSeconds = v / 10f) }
        }
    }
    SubHeading("Beat pulse")
    Segmented(BeatPulse.entries, look.beatPulse, { it.label }) { v -> update { it.copy(beatPulse = v) } }
    Hint("The record pumps on the kick of your selected segment. Press play to see it.", Modifier.padding(top = 6.dp))
    DashedRule(Modifier.padding(vertical = 6.dp))
    TermToggle("Safe area guides", "Shows where the Reels and TikTok buttons cover your frame. Preview only, never in the video.", safeArea, onSafeArea)
}
