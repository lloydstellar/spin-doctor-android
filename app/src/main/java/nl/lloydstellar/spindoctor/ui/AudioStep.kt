package nl.lloydstellar.spindoctor.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.audio.AudioDecoder
import nl.lloydstellar.spindoctor.model.Files
import nl.lloydstellar.spindoctor.model.MAX_TRACKS
import nl.lloydstellar.spindoctor.model.MAX_TRACK_SECONDS
import nl.lloydstellar.spindoctor.model.MIN_CLIP
import nl.lloydstellar.spindoctor.model.Mode
import nl.lloydstellar.spindoctor.model.Track
import nl.lloydstellar.spindoctor.model.TrackLoader

fun clock(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, s / 60, s % 60)
}

/** Step 2: one audio file, or up to eight for a release (sorted by file name, like the website). */
@Composable
fun AudioStep(next: () -> Unit) {
    val s = App.session
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notices = remember { mutableStateListOf<String>() }
    var adding by remember { mutableStateOf(false) }
    val release = s.mode == Mode.RELEASE

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        notices.clear()
        adding = true
        scope.launch {
            val added = addTracks(ctx, uris, replace = !release, notices)
            adding = false
            added.forEach { TrackLoader.load(ctx, it) }
        }
    }

    val one = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> add(listOf(u)) } }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { add(it) }
    val pick = { if (release) many.launch(arrayOf("audio/*")) else one.launch(arrayOf("audio/*")) }

    /* files that turned out unreadable while their waveform was read */
    val broken = s.tracks.filter { it.readError != null }
    LaunchedEffect(broken) {
        broken.forEach { t ->
            notices.add("${t.fileName}: ${t.readError}")
            s.tracks.remove(t)
        }
    }

    val ready = s.tracks.isNotEmpty() && s.tracks.all { it.peaks != null }
    StepLayout(bottom = {
        CommandButton(
            if (ready || s.tracks.isEmpty()) "NEXT: SEGMENT" else "READING AUDIO",
            Modifier.fillMaxWidth(), primary = ready, enabled = ready, onClick = next,
        )
    }) {
        if (s.tracks.isEmpty()) {
            Hint(if (release) "Add the tracks of your EP or album. Each one gets its own video in the same style." else "The track you want to promote.")
            Spacer(Modifier.height(8.dp))
            Dropzone(
                if (release) "Pick the audio files of the release" else "Pick an audio file",
                if (release) "CHOOSE FILES" else "CHOOSE FILE",
                "MP3, WAV, FLAC, M4A or OGG" + if (release) ", up to $MAX_TRACKS tracks" else "",
                pick,
            )
        } else {
            if (release) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubHeading("Tracks", Modifier.weight(1f))
                    Text("${s.tracks.size}/$MAX_TRACKS", style = mono(12.sp, C.CyanDim, 1.sp))
                }
            }
            ConsolePanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    s.tracks.forEachIndexed { i, t ->
                        if (i > 0) DashedRule()
                        TrackRow(i, t, release) { s.tracks.remove(t) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (release) {
                if (s.tracks.size < MAX_TRACKS) {
                    CommandButton("ADD TRACKS", Modifier.fillMaxWidth(), color = C.Cyan, height = 48.dp, onClick = pick)
                    Hint("You can add more tracks from other folders before you continue.", Modifier.padding(top = 8.dp))
                }
            } else {
                CommandButton("OTHER FILE", Modifier.fillMaxWidth(), color = C.Cyan, height = 48.dp, onClick = pick)
            }
        }
        if (adding) Text("Checking files…", style = mono(13.sp, C.CyanMid, 0.sp), modifier = Modifier.padding(top = 10.dp))
        notices.forEach { Notice(it) }
    }
}

@Composable
private fun TrackRow(index: Int, t: Track, removable: Boolean, onRemove: () -> Unit) {
    val blink = rememberBlink()
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(Locale.ROOT, index + 1), style = mono(13.sp, C.Amber, 1.sp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.fileName, style = mono(14.sp, C.CyanBright, 0.sp), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            val status = if (t.peaks == null) (if (blink) "READING ▎" else "READING  ") else "READY"
            Text("${clock(t.duration)}  ·  $status", style = mono(12.sp, if (t.peaks == null) C.CyanDim else C.CyanMid, 1.sp))
        }
        if (removable) {
            Spacer(Modifier.width(8.dp))
            CommandButton("REMOVE", color = C.Red, height = 36.dp, onClick = onRemove)
        }
    }
}

/**
 * Checks and adds picked files: audio only, at most eight minutes, at least
 * five seconds, no file twice, at most eight tracks, sorted by name.
 */
private suspend fun addTracks(ctx: Context, uris: List<Uri>, replace: Boolean, notices: MutableList<String>): List<Track> {
    val s = App.session
    val checked = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val name = Files.displayName(ctx, uri)
            name to runCatching { AudioDecoder.probe(ctx, uri) }
        }
    }
    if (replace) s.tracks.clear()
    val added = ArrayList<Track>()
    for ((i, entry) in checked.withIndex()) {
        val (name, duration) = entry
        val seconds = duration.getOrElse { e -> notices.add("$name: ${e.message}"); continue }
        when {
            s.tracks.any { it.fileName == name } -> notices.add("$name is already in the list")
            seconds > MAX_TRACK_SECONDS -> notices.add("$name is longer than an hour (${clock(seconds)})")
            seconds < MIN_CLIP -> notices.add("$name is shorter than 5 seconds")
            s.tracks.size >= MAX_TRACKS -> notices.add("$name skipped: a release can have $MAX_TRACKS tracks")
            else -> {
                val t = Track(uris[i], name, seconds)
                s.tracks.add(t)
                added.add(t)
            }
        }
    }
    /* the artist from the file names, unless the user already typed one */
    if (App.look.artist.isBlank()) added.firstNotNullOfOrNull { it.artistInName }?.let { name -> App.updateLook { it.copy(artist = name) } }
    val sorted = s.tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
    s.tracks.clear()
    s.tracks.addAll(sorted)
    s.segmentIndex = 0
    s.previewIndex = 0
    return added
}
