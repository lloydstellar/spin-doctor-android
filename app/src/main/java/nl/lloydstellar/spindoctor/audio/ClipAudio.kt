package nl.lloydstellar.spindoctor.audio

import nl.lloydstellar.spindoctor.model.SpinUp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The sound of a clip as it is heard in the preview and the video.
 *
 * Without spin up that is the segment itself. With spin up the segment is
 * played the way a record starting up plays it: read at the speed of the
 * record, so it starts silent, rises from a low rumble to normal pitch, and
 * runs on unchanged from the moment the record is at full speed. The level
 * follows the speed too, as a cartridge's output follows the groove speed,
 * which also keeps the start free of clicks.
 */
object ClipAudio {

    fun forVideo(segment: Pcm, spinUpSeconds: Double): Pcm {
        if (spinUpSeconds <= 0 || segment.frames == 0) return segment
        val rate = segment.sampleRate
        val ch = segment.channels
        val src = segment.samples
        val frames = segment.frames

        /* the ramp ends on a whole sample, and the read position is shifted by less than
           a sample so that it is whole there too: after the ramp samples are copied as they are */
        val rampFrames = (spinUpSeconds * rate).roundToInt().coerceAtLeast(1)
        val ramp = rampFrames.toDouble() / rate
        val endPosition = SpinUp.played(ramp, ramp) * rate
        val firstWhole = ceil(endPosition).toInt()
        val shift = firstWhole - endPosition

        val out = ShortArray(frames * ch)
        for (i in 0 until frames) {
            if (i >= rampFrames) {
                val from = firstWhole + (i - rampFrames)
                if (from >= frames) break
                System.arraycopy(src, from * ch, out, i * ch, (frames - i).coerceAtMost(frames - from) * ch)
                break
            }
            val t = i.toDouble() / rate
            val position = SpinUp.played(t, ramp) * rate + shift
            val gain = SpinUp.speed(t, ramp)
            val base = floor(position).toInt()
            val f = position - base
            for (c in 0 until ch) {
                val v = catmullRom(sample(src, base - 1, c, ch, frames), sample(src, base, c, ch, frames),
                    sample(src, base + 1, c, ch, frames), sample(src, base + 2, c, ch, frames), f) * gain
                out[i * ch + c] = v.roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return Pcm(out, rate, ch)
    }

    private fun sample(src: ShortArray, frame: Int, c: Int, ch: Int, frames: Int): Double =
        src[frame.coerceIn(0, frames - 1) * ch + c].toDouble()

    /** Cubic interpolation between [b] and [c], with [a] and [d] as neighbours. */
    private fun catmullRom(a: Double, b: Double, c: Double, d: Double, t: Double): Double =
        b + 0.5 * t * (c - a + t * (2 * a - 5 * b + 4 * c - d + t * (3 * (b - c) + d - a)))
}
