package nl.lloydstellar.spindoctor

import nl.lloydstellar.spindoctor.audio.BeatEnvelope
import nl.lloydstellar.spindoctor.audio.Pcm
import nl.lloydstellar.spindoctor.model.BadgeType
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.Track
import nl.lloydstellar.spindoctor.render.Mulberry32
import nl.lloydstellar.spindoctor.render.badgeLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pieces that must give exactly what app.js on lloydstellar.nl/vinyl gives.
 * The expected values come from running the original JavaScript.
 */
class WebsiteParityTest {

    @Test
    fun mulberry32MatchesJavaScript() {
        /* node: mulberry32(seed)() for draws 1..4 and 1000 */
        fun draws(seed: Int): List<Double> {
            val r = Mulberry32(seed)
            val all = List(1000) { r.next() }
            return listOf(all[0], all[1], all[2], all[3], all[999])
        }
        assertEquals(listOf(0.41157995723187923, 0.841819531749934, 0.21625563758425415, 0.9828756144270301, 0.6995102490764111), draws(0x5EED1234))
        assertEquals(listOf(0.5007152126636356, 0.2764570277649909, 0.2845942622516304, 0.9313021977432072, 0.05324937985278666), draws(0xA11D15C))
    }

    @Test
    fun titleFromFileNameMatchesJavaScript() {
        val names = listOf("03 - Night Drive.mp3", "01_Orbit.mp3", "Track_name_final.wav", "12) Last One.flac", "2024 Remix.mp3")
        assertEquals(listOf("Night Drive", "Orbit", "Track name final", "Last One", "2024 Remix"), names.map { Track.defaultTitle(it) })
    }

    @Test
    fun artistIsSplitFromTheTitle() {
        assertEquals("Lloyd Stellar" to "Dystopia", Track.splitName("03 Lloyd Stellar - Dystopia.wav"))
        assertEquals("Lloyd Stellar" to "Computer Generated (intro)", Track.splitName("01 Lloyd Stellar - Computer Generated (intro).wav"))
        assertEquals(null to "Night Drive", Track.splitName("03 - Night Drive.mp3"))
        assertEquals(null to "Orbit", Track.splitName("01_Orbit.mp3"))
    }

    @Test
    fun badgeTexts() {
        assertEquals("OUT NOW", badgeLabel(Look(badgeType = BadgeType.OUT_NOW)))
        assertEquals("NEW EP", badgeLabel(Look(badgeType = BadgeType.NEW_EP)))
        assertEquals("OUT FRIDAY", badgeLabel(Look(badgeType = BadgeType.CUSTOM, badgeText = "  out friday ")))
        assertEquals("OUT 17 OCT", badgeLabel(Look(badgeType = BadgeType.DATE, badgeDay = 17, badgeMonth = 10)))
        assertEquals("", badgeLabel(Look(badgeType = BadgeType.DATE)))
    }

    /** The signal of tools/parity/beat-envelope.mjs: a 120 BPM kick and a high tone. */
    private fun signal(rate: Int, seconds: Int) = DoubleArray(rate * seconds) { i ->
        val t = i.toDouble() / rate
        val p = (t - 0.25) - floor((t - 0.25) / 0.5) * 0.5
        val kick = if (t >= 0.25) 0.7 * sin(2 * PI * 60 * p) * exp(-25 * p) else 0.0
        kick + 0.2 * sin(2 * PI * 3000 * t)
    }

    @Test
    fun beatEnvelopeMatchesJavaScript() {
        val expected = javaClass.getResource("/beat-envelope.json")!!.readText()
            .trim('[', ']', '\n').split(',').map { it.toDouble() }
        val rate = 44100
        val whole = signal(rate, 12)
        val from = (2.0 * rate).toInt(); val to = (10.5 * rate).toInt()
        val samples = ShortArray(to - from) { (whole[from + it] * 32767).roundToInt().toShort() }
        val envelope = BeatEnvelope.build(Pcm(samples, rate, 1))!!
        assertEquals(expected.size, 255)
        var worst = 0.0
        expected.forEachIndexed { i, v -> worst = maxOf(worst, abs(envelope.at(i / 30.0) - v)) }
        /* the app works on 16-bit samples, the browser on 32-bit floats */
        assert(worst < 0.002) { "envelope differs by up to $worst" }
    }
}
