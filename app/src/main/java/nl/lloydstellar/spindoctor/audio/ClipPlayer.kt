package nl.lloydstellar.spindoctor.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Plays one part of a track, from [play]'s start to its end, then stops by
 * itself. Used by the clip selector and by the preview. Takes audio focus
 * like any music player and gives way to calls and other apps.
 */
class ClipPlayer(context: Context) {
    private val ctx = context.applicationContext
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var end = 0.0
    private var hasFocus = false
    private var onFinished: (() -> Unit)? = null

    /** True from the moment sound actually starts until it stops. */
    var playing by mutableStateOf(false)
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }
        .build()

    private val watch = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!playing) return
            if (positionOrNull()?.let { it >= end } != false || !p.isPlaying) {
                val done = onFinished
                stop()
                done?.invoke()
            } else {
                handler.postDelayed(this, 15)
            }
        }
    }

    /**
     * Starts [uri] at [start] seconds and stops at [end]. [onStarted] runs when
     * the first sound is out, which is where the preview starts its clock.
     */
    fun play(uri: Uri, start: Double, end: Double, onStarted: () -> Unit = {}, onFinished: () -> Unit = {}) {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        hasFocus = true
        this.end = end
        this.onFinished = onFinished
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(attributes)
            p.setDataSource(ctx, uri)
            p.setOnPreparedListener {
                if (player !== p) return@setOnPreparedListener
                p.setOnSeekCompleteListener {
                    if (player !== p) return@setOnSeekCompleteListener
                    p.start()
                    playing = true
                    onStarted()
                    handler.post(watch)
                }
                p.seekTo((start * 1000).toLong(), MediaPlayer.SEEK_CLOSEST)
            }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepareAsync()
        } catch (e: Exception) {
            stop()
        }
    }

    /** Seconds into the track, while playing. */
    fun positionOrNull(): Double? {
        val p = player ?: return null
        if (!playing) return null
        return try {
            p.currentPosition / 1000.0
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun stop() {
        handler.removeCallbacks(watch)
        onFinished = null
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: IllegalStateException) { }
            it.release()
        }
        player = null
        if (hasFocus) audio.abandonAudioFocusRequest(focus)
        hasFocus = false
        playing = false
    }
}
