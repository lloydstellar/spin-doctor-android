package nl.lloydstellar.spindoctor.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.lloydstellar.spindoctor.audio.AudioDecoder
import nl.lloydstellar.spindoctor.render.bitmapPaint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Images {
    /**
     * Decodes a picked image in sRGB, rotated as the camera meant it. The
     * website refuses anything over 2048 px; the app scales it down instead.
     */
    suspend fun load(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val w = info.size.width; val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
        }
    }

    /** The label: the circle at ([cx], [cy]) with radius [r] in image pixels, as a square bitmap. */
    fun cropCircle(src: Bitmap, cx: Float, cy: Float, r: Float): Bitmap {
        val size = (r * 2).roundToInt().coerceIn(64, 1024)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        c.drawBitmap(
            src,
            Rect((cx - r).roundToInt(), (cy - r).roundToInt(), (cx + r).roundToInt(), (cy + r).roundToInt()),
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            bitmapPaint,
        )
        return out
    }
}

object Files {
    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { return it }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "track"
    }
}

/**
 * Reads the waveform of added tracks in the background, one at a time, so a
 * release of eight long tracks does not decode eight files at once.
 */
object TrackLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Mutex()

    fun load(ctx: Context, track: Track) {
        val app = ctx.applicationContext
        scope.launch {
            queue.withLock {
                try {
                    val peaks = AudioDecoder.peaks(app, track.uri)
                    withContext(Dispatchers.Main) { track.peaks = peaks }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { track.readError = e.message ?: "Can't read this file" }
                }
            }
        }
    }
}
