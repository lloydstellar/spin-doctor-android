package nl.lloydstellar.spindoctor.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

/**
 * Plays a clip's sound exactly as it goes into the video (spin up included),
 * for the style preview. The preview's clock is [positionSeconds]: the frames
 * the speaker has actually played, so the record and the sound stay together.
 */
class PcmPlayer(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var writer: Thread? = null
    @Volatile private var generation = 0
    private var hasFocus = false

    var playing by mutableStateOf(false)
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { stop() }
        }
        .build()

    fun play(pcm: Pcm, onFinished: () -> Unit = {}) {
        stop()
        if (pcm.frames == 0) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        hasFocus = true
        val mask = if (pcm.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(pcm.sampleRate)
            .setChannelMask(mask)
            .build()
        val min = AudioTrack.getMinBufferSize(pcm.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min * 4)
            .build()
        track = t
        val gen = ++generation
        t.play()
        playing = true
        writer = thread(name = "preview-audio") {
            val chunk = pcm.sampleRate / 20 * pcm.channels
            var at = 0
            while (at < pcm.samples.size && generation == gen) {
                val n = minOf(chunk, pcm.samples.size - at)
                val written = t.write(pcm.samples, at, n)
                if (written <= 0) break
                at += written
            }
            /* wait until the last frame has been heard */
            while (generation == gen) {
                val head = try { t.playbackHeadPosition } catch (_: IllegalStateException) { break }
                if (head >= pcm.frames) break
                Thread.sleep(10)
            }
            main.post {
                if (generation == gen) {
                    stop()
                    onFinished()
                }
            }
        }
    }

    /** Seconds of the clip that have been played, while playing. */
    fun positionSeconds(): Double? {
        val t = track ?: return null
        if (!playing) return null
        return try {
            t.playbackHeadPosition.toDouble() / t.sampleRate
        } catch (_: IllegalStateException) {
            null
        }
    }

    fun stop() {
        generation++
        track?.let {
            try { it.pause(); it.flush(); it.stop() } catch (_: IllegalStateException) { }
            it.release()
        }
        track = null
        writer = null
        if (hasFocus) audio.abandonAudioFocusRequest(focus)
        hasFocus = false
        playing = false
    }
}
