package nl.lloydstellar.spindoctor

import nl.lloydstellar.spindoctor.audio.ClipAudio
import nl.lloydstellar.spindoctor.audio.Pcm
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.SpinUp
import nl.lloydstellar.spindoctor.render.VinylRenderer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** The record and the music come up to speed together, and land exactly on normal speed. */
class SpinUpTest {

    @Test
    fun curveStartsStillAndLandsOnFullSpeed() {
        assertEquals(0.0, SpinUp.speed(0.0, 2.0), 0.0)
        assertEquals(1.0, SpinUp.speed(2.0, 2.0), 0.0)
        assertEquals(1.0, SpinUp.speed(5.0, 2.0), 0.0)
        /* played() is the integral of speed() */
        var sum = 0.0
        val dt = 1e-4
        var t = 0.0
        while (t < 3.0) { sum += SpinUp.speed(t + dt / 2, 2.0) * dt; t += dt }
        assertEquals(sum, SpinUp.played(3.0, 2.0), 1e-6)
        /* without spin up nothing changes */
        assertEquals(7.5, SpinUp.played(7.5, 0.0), 0.0)
    }

    @Test
    fun recordTurnsAtTheSpeedTheMusicPlays() {
        val look = Look(spinUp = true, spinUpSeconds = 2f, rpm = 33)
        val full = 33 / 60.0 * 2 * PI
        val dt = 1e-4
        for (t in listOf(0.2, 1.0, 1.9, 2.5)) {
            val turning = (VinylRenderer.rotationAt(t + dt, look) - VinylRenderer.rotationAt(t, look)) / dt / full
            assertEquals(SpinUp.speed(t + dt / 2, 2.0), turning, 1e-3)
        }
    }

    private fun tone(hz: Double, seconds: Double, rate: Int = 44100): Pcm {
        val n = (seconds * rate).toInt()
        return Pcm(ShortArray(n) { (sin(2 * PI * hz * it / rate) * 20000).roundToInt().toShort() }, rate, 1)
    }

    /** Frequency of a tone around [at] seconds, from its zero crossings over 50 ms. */
    private fun frequencyAt(pcm: Pcm, at: Double): Double {
        val rate = pcm.sampleRate
        val from = ((at - 0.025) * rate).toInt(); val to = ((at + 0.025) * rate).toInt()
        var crossings = 0
        for (i in from + 1 until to) if ((pcm.samples[i - 1] < 0) != (pcm.samples[i] < 0)) crossings++
        return crossings / 2.0 / ((to - from).toDouble() / rate)
    }

    @Test
    fun pitchRisesWithTheRecordAndIsUntouchedAfterwards() {
        val source = tone(1000.0, 6.0)
        val out = ClipAudio.forVideo(source, 2.0)
        assertEquals(source.frames, out.frames)
        /* silent at the start, quieter while slow */
        assertTrue(abs(out.samples[0].toInt()) < 10)
        /* the pitch follows the speed of the record */
        for (t in listOf(0.5, 1.0, 1.5)) {
            val expected = 1000 * SpinUp.speed(t, 2.0)
            assertEquals("at $t s", expected, frequencyAt(out, t), expected * 0.06)
        }
        /* from full speed on the samples are the track's own, only later in time */
        val rate = source.sampleRate
        val ramp = 2 * rate
        val offset = ramp - kotlin.math.ceil(SpinUp.played(2.0, 2.0) * rate).toInt()
        val tail = out.samples.copyOfRange(ramp, out.frames)
        val expectedTail = source.samples.copyOfRange(ramp - offset, out.frames - offset)
        assertArrayEquals(expectedTail, tail)
    }

    @Test
    fun withoutSpinUpTheSoundIsTheSegment() {
        val source = tone(440.0, 1.0)
        assertTrue(ClipAudio.forVideo(source, 0.0) === source)
    }
}
