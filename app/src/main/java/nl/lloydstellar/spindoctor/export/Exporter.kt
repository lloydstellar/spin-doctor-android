package nl.lloydstellar.spindoctor.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.lloydstellar.spindoctor.audio.AudioDecoder
import nl.lloydstellar.spindoctor.audio.BeatEnvelope
import nl.lloydstellar.spindoctor.audio.ClipAudio
import nl.lloydstellar.spindoctor.model.BackgroundType
import nl.lloydstellar.spindoctor.model.BeatPulse
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.render.Backdrop
import nl.lloydstellar.spindoctor.render.DiscArt
import nl.lloydstellar.spindoctor.render.DiscKey
import nl.lloydstellar.spindoctor.render.Scene
import nl.lloydstellar.spindoctor.render.VinylRenderer
import java.io.File
import kotlin.math.ceil

/** One video to make: a track and its segment. */
data class ClipJob(
    val uri: Uri,
    val baseName: String,
    val title: String,
    val start: Double,
    val end: Double,
)

data class ExportResult(
    val name: String,
    val title: String,
    val seconds: Double,
    val uri: Uri? = null,
    val error: String? = null,
)

data class ExportState(
    val running: Boolean = false,
    val clipIndex: Int = 0,
    val clipCount: Int = 0,
    val phase: String = "",
    val frame: Int = 0,
    val frames: Int = 0,
    val results: List<ExportResult> = emptyList(),
    val cancelled: Boolean = false,
) {
    /** 0..1 over the whole batch */
    val progress: Double get() =
        if (clipCount == 0) 0.0 else (clipIndex + if (frames == 0) 0.0 else frame.toDouble() / frames) / clipCount
}

/**
 * Renders and encodes every clip of a release one after the other, like
 * exportReleaseMP4() on the website, and saves each video to the gallery as
 * soon as it is done. Lives at process level so it keeps going while the
 * user looks at another app (ExportService keeps the process alive).
 */
object Exporter {
    private const val FPS = 30
    private const val TAG = "Exporter"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var cancelRequested = false

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state

    fun start(ctx: Context, look: Look, cover: Bitmap?, background: Bitmap?, clips: List<ClipJob>) {
        if (_state.value.running) return
        val app = ctx.applicationContext
        cancelRequested = false
        _state.value = ExportState(running = true, clipCount = clips.size, phase = "Preparing")
        ExportService.start(app)
        job = scope.launch {
            val results = ArrayList<ExportResult>()
            try {
                clips.forEachIndexed { i, clip ->
                    if (cancelRequested) return@forEachIndexed
                    _state.update { it.copy(clipIndex = i, frame = 0, frames = 0, phase = "Reading audio") }
                    val result = try {
                        val uri = exportClip(app, look, cover, background, clip)
                        ExportResult(Gallery.fileName(clip.baseName), clip.title, clip.end - clip.start, uri = uri)
                    } catch (e: EncodeCancelled) {
                        null
                    } catch (e: Exception) {
                        Log.e(TAG, "Export of ${clip.baseName} failed", e)
                        ExportResult(Gallery.fileName(clip.baseName), clip.title, clip.end - clip.start, error = e.message ?: "Export failed")
                    }
                    if (result != null) {
                        results.add(result)
                        _state.update { it.copy(results = results.toList()) }
                    }
                }
            } finally {
                _state.update {
                    it.copy(running = false, clipIndex = clips.size, frame = 0, frames = 0, phase = "", cancelled = cancelRequested)
                }
                ExportService.stop(app)
            }
        }
    }

    fun cancel() {
        cancelRequested = true
    }

    /** Forget the last batch, for a new video. */
    fun reset() {
        if (!_state.value.running) _state.value = ExportState()
    }

    private fun exportClip(ctx: Context, look: Look, cover: Bitmap?, background: Bitmap?, clip: ClipJob): Uri {
        val w = look.format.width
        val h = look.format.height
        val isCancelled = { cancelRequested }

        /* the sound of the clip, spin up included: into the video, and the source of the beat pulse */
        val segment = AudioDecoder.region(ctx, clip.uri, clip.start, clip.end, isCancelled)
        if (cancelRequested) throw EncodeCancelled()
        val pcm = ClipAudio.forVideo(segment, look.spinUpTime)
        val envelope = if (look.beatPulse != BeatPulse.OFF) BeatEnvelope.build(pcm) else null

        _state.update { it.copy(phase = "Pressing the record") }
        val art = DiscArt.bake(DiscKey.of(look, VinylRenderer.radiusFor(w, h, look, 0.0)))
        val backdrop = if (look.backgroundType == BackgroundType.IMAGE && background != null)
            Backdrop.render(background, w, h, look.blur) else null
        val scene = Scene(look, cover, backdrop, clip.title)

        val duration = clip.end - clip.start
        val frames = ceil(duration * FPS).toInt()
        val tmp = File(ctx.cacheDir, "export").apply { mkdirs() }.let { File(it, "clip-${System.nanoTime()}.mp4") }
        try {
            _state.update { it.copy(phase = "Rendering", frames = frames) }
            Mp4Encoder(w, h, FPS).encode(
                output = tmp,
                frameCount = frames,
                audio = pcm,
                render = { i, canvas ->
                    val t = i.toDouble() / FPS
                    val pulse = (envelope?.at(t) ?: 0.0) * look.beatPulse.amount
                    VinylRenderer.render(canvas, w, h, scene, art, VinylRenderer.rotationAt(t, look), true, pulse)
                },
                progress = { p -> _state.update { it.copy(frame = (p * frames).toInt()) } },
                isCancelled = isCancelled,
            )
            _state.update { it.copy(phase = "Saving") }
            return Gallery.save(ctx, tmp, Gallery.fileName(clip.baseName))
        } finally {
            tmp.delete()
            art.recycle()
            backdrop?.recycle()
        }
    }
}
