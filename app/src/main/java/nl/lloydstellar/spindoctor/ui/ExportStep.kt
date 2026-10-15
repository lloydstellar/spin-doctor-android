package nl.lloydstellar.spindoctor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.ceil
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.export.ClipJob
import nl.lloydstellar.spindoctor.export.ExportResult
import nl.lloydstellar.spindoctor.export.ExportState
import nl.lloydstellar.spindoctor.export.Exporter
import nl.lloydstellar.spindoctor.export.Gallery
import nl.lloydstellar.spindoctor.export.Share
import nl.lloydstellar.spindoctor.model.BackgroundType
import nl.lloydstellar.spindoctor.render.VinylRenderer

/** Step 5: render, save to the gallery, share. */
@Composable
fun ExportStep(onRestyle: () -> Unit, onNew: () -> Unit) {
    val s = App.session
    val ctx = LocalContext.current
    val state by Exporter.state.collectAsStateWithLifecycle()
    val look = App.look
    val count = s.tracks.size

    /* the screen stays on while frames are being rendered */
    val activity = LocalActivity.current
    DisposableEffect(state.running) {
        if (state.running) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        App.player.stop()
        App.pcmPlayer.stop()
        /* once, so the progress shows in the notification shade during a long release */
        if (Build.VERSION.SDK_INT >= 33 && !App.prefs.askedForNotifications &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            App.prefs.askedForNotifications = true
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Exporter.start(
            ctx, look, s.cover,
            if (look.backgroundType == BackgroundType.IMAGE) s.backgroundImage else null,
            s.tracks.map { ClipJob(it.uri, it.baseName, it.title, it.start, it.end) },
        )
    }

    val done = !state.running && state.results.isNotEmpty()
    StepLayout(bottom = {
        when {
            state.running -> CommandButton("CANCEL", Modifier.fillMaxWidth(), color = C.Red) { Exporter.cancel() }
            done -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("CHANGE STYLE", Modifier.weight(1f), color = C.Cyan, height = 50.dp) { Exporter.reset(); onRestyle() }
                CommandButton("NEW VIDEO", Modifier.weight(1f), height = 50.dp) { Exporter.reset(); onNew() }
            }
            else -> CommandButton(
                if (count > 1) "MAKE $count VIDEOS" else "MAKE VIDEO", Modifier.fillMaxWidth(), primary = true, height = 58.dp, onClick = ::start,
            )
        }
    }) {
        when {
            state.running -> Progress(state)
            done -> Results(state.results, state.cancelled)
            else -> Summary(count)
        }
    }
}

@Composable
private fun Summary(count: Int) {
    val s = App.session
    val look = App.look
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Leader("FORMAT", "${look.format.label} ${look.format.ratio}")
            Leader("SIZE", "${look.format.width} × ${look.format.height}")
            Leader("VIDEOS", "$count")
            s.tracks.forEach { t -> Leader(t.title.ifBlank { t.baseName }.uppercase(), "%.1f S".format(Locale.ROOT, t.length)) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Hint("Videos are made on this phone and saved to Movies/${Gallery.FOLDER}.")
    if (count > 1) Hint("You can switch to another app while they render; the progress stays in the notification.")
}

@Composable
private fun Leader(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, style = mono(13.sp, C.CyanMid, 1.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 210.dp))
        Spacer(Modifier.width(8.dp))
        DashedRule(Modifier.weight(1f).padding(bottom = 4.dp), C.CyanDeep)
        Spacer(Modifier.width(8.dp))
        Text(value, style = mono(13.sp, C.Amber, 1.sp), maxLines = 1)
    }
}

@Composable
private fun Progress(state: ExportState) {
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.phase.uppercase(), style = mono(13.sp, C.Cyan, 2.sp), modifier = Modifier.weight(1f))
                if (state.clipCount > 1) HudChip("VIDEO ${state.clipIndex + 1} OF ${state.clipCount}")
            }
            Spacer(Modifier.height(12.dp))
            LiveFrame(state)
            Spacer(Modifier.height(12.dp))
            ProgressBeam(if (state.frames > 0) state.frame.toFloat() / state.frames else 0f)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.frames > 0) "FRAME ${state.frame} / ${state.frames}" else "…",
                style = mono(12.sp, C.CyanMid, 1.sp),
            )
        }
    }
}

/** The record at the moment that is being rendered, drawn by the preview renderer. */
@Composable
private fun LiveFrame(state: ExportState) {
    val s = App.session
    val look = App.look
    val track = s.tracks.getOrNull(state.clipIndex) ?: return
    val t = state.frame.toDouble() / 30
    val format = look.format
    Box(Modifier.fillMaxWidth().heightIn(max = 240.dp), contentAlignment = Alignment.Center) {
        VinylView(
            look, s.cover, if (look.backgroundType == BackgroundType.IMAGE) s.backgroundImage else null, track.title,
            rotation = VinylRenderer.rotationAt(t, look), animated = true, pulse = 0.0,
            modifier = Modifier.heightIn(max = 240.dp).aspectRatio(format.aspect, matchHeightConstraintsFirst = true).border(1.dp, C.CyanDeep),
        )
    }
}

@Composable
private fun Results(results: List<ExportResult>, cancelled: Boolean) {
    val ctx = LocalContext.current
    val saved = results.filter { it.uri != null }
    val uris = saved.mapNotNull { it.uri }
    val targets = remember(uris.size) { Share.installedTargets(ctx, uris.size > 1) }

    if (saved.isNotEmpty()) {
        Text(
            if (saved.size > 1) "${saved.size} VIDEOS SAVED" else "VIDEO SAVED",
            style = display(20.sp, C.CyanBright, 1.sp),
        )
        Hint("In your gallery under Movies/${Gallery.FOLDER}.", Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        CommandButton(if (uris.size > 1) "SHARE ALL" else "SHARE", Modifier.fillMaxWidth(), primary = true) { Share.send(ctx, uris) }
        if (targets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            targets.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> CommandButton(t.label, Modifier.weight(1f), color = C.Cyan, height = 44.dp) { Share.send(ctx, uris, t) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (cancelled) Notice("Stopped. Videos that were finished are saved.")
    Spacer(Modifier.height(10.dp))
    ConsolePanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            results.forEachIndexed { i, r ->
                if (i > 0) DashedRule()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (results.size > 1) "%02d  %s".format(Locale.ROOT, i + 1, r.title.ifBlank { r.name }) else r.title.ifBlank { r.name },
                            style = mono(13.sp, C.CyanBright, 0.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (r.uri != null) "SAVED  ·  ${ceil(r.seconds).toInt()} S" else "ERROR  ·  ${r.error}",
                            style = mono(11.sp, if (r.uri != null) C.Green else C.Red, 1.sp),
                            maxLines = 2,
                        )
                    }
                    r.uri?.let { uri ->
                        Spacer(Modifier.width(8.dp))
                        CommandButton("PLAY", color = C.Cyan, height = 36.dp) { Share.open(ctx, uri) }
                        if (results.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            CommandButton("SHARE", color = C.Amber, height = 36.dp) { Share.send(ctx, listOf(uri)) }
                        }
                    }
                }
            }
        }
    }
}
