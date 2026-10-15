package nl.lloydstellar.spindoctor.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The beat pulse of app.js: computed once per segment from the decoded audio
 * and then sampled by time, so the preview and every exported frame get the
 * same value for the same moment.
 */
class BeatEnvelope(private val values: FloatArray, private val fps: Int) {

    /** Envelope value (0..1) at [time] seconds into the clip, linearly interpolated. */
    fun at(time: Double): Double {
        if (values.isEmpty()) return 0.0
        val position = time * fps
        val index = floor(position).toInt()
        if (index < 0) return values[0].toDouble()
        if (index >= values.size - 1) return values[values.size - 1].toDouble()
        val fraction = position - index
        return values[index] + (values[index + 1] - values[index]) * fraction
    }

    companion object {
        const val FPS = 30

        /** buildBeatEnvelope(): [pcm] is the selected segment itself. */
        fun build(pcm: Pcm, fps: Int = FPS): BeatEnvelope? {
            val sampleRate = pcm.sampleRate
            val length = pcm.frames
            if (length < sampleRate * 0.1) return null

            val frameCount = max(1, ceil(length.toDouble() / sampleRate * fps).toInt())
            val samplesPerFrame = length.toDouble() / frameCount

            /* one-pole low pass around 150 Hz: keeps kick and bass, drops the rest */
            val alpha = 1 - exp(-2 * PI * 150 / sampleRate)

            val raw = FloatArray(frameCount)
            var lowPass = 0.0
            var sum = 0.0
            var count = 0
            var index = 0
            var boundary = samplesPerFrame
            val ch = pcm.channels
            for (i in 0 until length) {
                /* channel 0, as getChannelData(0) */
                val x = pcm.samples[i * ch] / 32768.0
                lowPass += alpha * (x - lowPass)
                sum += lowPass * lowPass
                count++
                if (i + 1 >= boundary && index < frameCount) {
                    raw[index++] = sqrt(sum / count).toFloat()
                    sum = 0.0
                    count = 0
                    boundary += samplesPerFrame
                }
            }
            while (index < frameCount) raw[index++] = 0f

            /* normalise against a high percentile so one loud hit cannot flatten the rest */
            val sorted = raw.copyOf().also { it.sort() }
            val reference = sorted[min(sorted.size - 1, floor(sorted.size * 0.95).toInt())]
            if (reference == 0f) return null

            /* gate the quiet half away, then fast attack and slow release so the record pumps on the beat */
            val out = FloatArray(frameCount)
            var smoothed = 0.0
            for (i in 0 until frameCount) {
                var value = min(1.0, raw[i].toDouble() / reference.toDouble())
                value = max(0.0, (value - 0.35) / 0.65)
                value = value.pow(1.3)
                smoothed += (value - smoothed) * (if (value > smoothed) 0.6 else 0.12)
                out[i] = smoothed.toFloat()
            }
            return BeatEnvelope(out, fps)
        }
    }
}
