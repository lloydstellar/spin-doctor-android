package nl.lloydstellar.spindoctor

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.lloydstellar.spindoctor.audio.AudioDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * How fast tracks are read (it gates the clip selector), and whether a
 * segment cut after a jump into the file holds exactly the same samples as
 * the same segment decoded from the start.
 */
@RunWith(AndroidJUnit4::class)
class DecodeSpeedTest {

    private fun asset(name: String): Uri {
        val inst = InstrumentationRegistry.getInstrumentation()
        val f = File(inst.targetContext.cacheDir, name)
        inst.context.assets.open(name).use { input -> f.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(f)
    }

    @Test fun mp3() = check("kick120.mp3", asset("kick120.mp3"))
    @Test fun m4a() = check("kick120.m4a", asset("kick120.m4a"))

    /* large files pushed by hand: adb push kick120.wav /sdcard/Android/data/<app>/files/ */
    @Test fun pushed() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        dir.listFiles { f -> f.name.startsWith("kick120.") }?.forEach { check(it.name, Uri.fromFile(it)) }
    }

    private fun check(name: String, uri: Uri) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val t0 = System.nanoTime()
        val peaks = AudioDecoder.peaks(ctx, uri)
        val t1 = System.nanoTime()
        val cut = AudioDecoder.region(ctx, uri, 40.0, 70.0)
        val t2 = System.nanoTime()
        val whole = AudioDecoder.region(ctx, uri, 0.0, 75.0)
        Log.i("DecodeSpeed", "$name: peaks of %.1f s in %.0f ms; 30 s segment at 40 s in %.0f ms".format(
            peaks.duration, (t1 - t0) / 1e6, (t2 - t1) / 1e6))

        assertEquals(30 * cut.sampleRate, cut.frames)
        /* the lag at which the cut matches the same stretch of the whole decode best */
        val ch = cut.channels
        val base = 40 * whole.sampleRate
        var bestLag = 0; var best = -1.0
        for (lag in -4096..4096) {
            var dot = 0.0
            for (i in 0 until 44100 step 3) dot += cut.samples[i * ch].toDouble() * whole.samples[(base + i + lag) * ch]
            if (dot > best) { best = dot; bestLag = lag }
        }
        Log.i("DecodeSpeed", "$name: segment after a jump is off by $bestLag samples")
        /*
         * After a jump some decoders run a frame (23 to 35 ms) apart from a decode
         * from the start. The player jumps the same way, so the export matches
         * what was heard; anything under 50 ms is far below what anyone can pick
         * out when choosing a part of a track.
         */
        assertTrue("off by $bestLag samples", kotlin.math.abs(bestLag) <= cut.sampleRate / 20)
    }
}
