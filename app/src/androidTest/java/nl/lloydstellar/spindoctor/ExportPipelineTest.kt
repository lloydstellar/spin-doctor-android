package nl.lloydstellar.spindoctor

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.lloydstellar.spindoctor.export.ClipJob
import nl.lloydstellar.spindoctor.export.Exporter
import nl.lloydstellar.spindoctor.model.BadgeType
import nl.lloydstellar.spindoctor.model.BeatPulse
import nl.lloydstellar.spindoctor.model.Format
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real export on the phone: a 30 second clip of a 120 BPM kick track
 * (kicks at 0.25 s + n * 0.5 s) in the heaviest format, saved to the gallery.
 * The video is copied to Android/data/<app>/files/export-test.mp4 for checks
 * with ffprobe (format, length, audio and pulse in sync).
 */
@RunWith(AndroidJUnit4::class)
class ExportPipelineTest {

    @Test
    fun exportsVerticalClipWithAudio() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        /* -e audio kick120.wav picks a file pushed to Android/data/<app>/files instead of the bundled MP3 */
        val pushed = InstrumentationRegistry.getArguments().getString("audio")
        val audio = if (pushed != null) File(ctx.getExternalFilesDir(null), pushed) else File(ctx.cacheDir, "kick120.mp3").also { f ->
            inst.context.assets.open("kick120.mp3").use { input -> f.outputStream().use { input.copyTo(it) } }
        }
        val cover = inst.context.assets.open("cover.png").use { BitmapFactory.decodeStream(it) }
        val format = InstrumentationRegistry.getArguments().getString("format")?.let { Format.valueOf(it) } ?: Format.VERTICAL

        /* -e spinup 2.0 turns on a spin up of that many seconds */
        val spinUp = InstrumentationRegistry.getArguments().getString("spinup")?.toFloat()
        val look = Look(
            format = format, beatPulse = BeatPulse.STRONG, textEnabled = true, artist = "Lloyd Stellar",
            textStyle = TextStyle.TECHNO, badgeEnabled = true, badgeType = BadgeType.NEW_EP,
            spinUp = spinUp != null, spinUpSeconds = spinUp ?: 1f,
        )
        val started = System.nanoTime()
        inst.runOnMainSync {
            Exporter.reset()
            Exporter.start(ctx, look, cover, null, listOf(ClipJob(Uri.fromFile(audio), "kick120", "Kick Test", 10.0, 40.0)))
        }
        while (Exporter.state.value.running) Thread.sleep(200)
        val seconds = (System.nanoTime() - started) / 1e9
        val result = Exporter.state.value.results.single()
        Log.i("ExportTest", "${format.name}: 30 s clip exported in %.1f s, ${result.error ?: "ok"}".format(seconds))
        assertNotNull(result.error, result.uri)

        val out = File(ctx.getExternalFilesDir(null), "export-test-${format.name.lowercase()}-${audio.extension}.mp4")
        ctx.contentResolver.openInputStream(result.uri!!)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        assertEquals(1, ctx.contentResolver.delete(result.uri!!, null, null))
    }
}
