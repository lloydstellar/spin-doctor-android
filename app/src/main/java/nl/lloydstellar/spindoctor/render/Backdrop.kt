package nl.lloydstellar.spindoctor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The background image of a frame: drawn to cover it, then blurred the way
 * ctx.filter = 'blur(Npx)' blurs in the browser (a Gaussian with sigma N, the
 * edges fading towards transparent, which the JPEG export turns into black).
 *
 * The website applies the blur in the pixels of the canvas it draws on. The
 * export is 1080 high there, so here sigma is the slider value at 1080 high
 * and scales with the frame: the preview and the export look alike.
 */
object Backdrop {

    fun sigmaFor(blur: Int, frameHeight: Int) = blur * frameHeight / 1080f

    fun render(image: Bitmap, width: Int, height: Int, blur: Int): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val imgRatio = image.width.toFloat() / image.height
        val canvasRatio = width.toFloat() / height
        val dst = if (imgRatio > canvasRatio) {
            val w = height * imgRatio
            RectF((width - w) / 2, 0f, (width + w) / 2, height.toFloat())
        } else {
            val h = width / imgRatio
            RectF(0f, (height - h) / 2, width.toFloat(), (height + h) / 2)
        }
        c.drawBitmap(image, null, dst, bitmapPaint)

        val sigma = sigmaFor(blur, height)
        if (sigma >= 0.3f) gaussian(out, sigma)

        /* what is left transparent at the edges ends up black in the video */
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.BLACK)
            drawBitmap(out, 0f, 0f, null)
        }
        out.recycle()
        flat.prepareToDraw()
        return flat
    }

    /*
     * Three box blurs approximate a Gaussian (the same trick Skia uses for
     * large sigmas). Pixels outside the image count as transparent black,
     * on premultiplied channels, like the browser's filter.
     */
    private fun gaussian(bmp: Bitmap, sigma: Float) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val ch = Array(4) { FloatArray(w * h) }
        for (i in px.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            ch[0][i] = a
            ch[1][i] = ((p shr 16) and 255) * a
            ch[2][i] = ((p shr 8) and 255) * a
            ch[3][i] = (p and 255) * a
        }
        val boxes = boxSizes(sigma, 3)
        val tmp = FloatArray(w * h)
        for (c in ch) {
            for (b in boxes) {
                val rad = (b - 1) / 2
                boxH(c, tmp, w, h, rad)
                boxV(tmp, c, w, h, rad)
            }
        }
        for (i in px.indices) {
            val a = ch[0][i]
            if (a <= 0.0005f) { px[i] = 0; continue }
            val ai = (a * 255).roundToInt().coerceIn(0, 255)
            val r = (ch[1][i] / a).roundToInt().coerceIn(0, 255)
            val g = (ch[2][i] / a).roundToInt().coerceIn(0, 255)
            val bl = (ch[3][i] / a).roundToInt().coerceIn(0, 255)
            px[i] = (ai shl 24) or (r shl 16) or (g shl 8) or bl
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Box widths whose three passes give a Gaussian of [sigma] (Kovesi). */
    private fun boxSizes(sigma: Float, n: Int): IntArray {
        val ideal = sqrt(12.0 * sigma * sigma / n + 1)
        var wl = ideal.toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12.0 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4 * wl - 4)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) max(1, wl) else wu }
    }

    private fun boxH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        if (r <= 0) { src.copyInto(dst); return }
        val norm = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (x in 0..r) if (x < w) acc += src[row + x]
            for (x in 0 until w) {
                dst[row + x] = acc * norm
                val add = x + r + 1
                val sub = x - r
                if (add < w) acc += src[row + add]
                if (sub >= 0) acc -= src[row + sub]
            }
        }
    }

    private fun boxV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
        if (r <= 0) { src.copyInto(dst); return }
        val norm = 1f / (2 * r + 1)
        for (x in 0 until w) {
            var acc = 0f
            for (y in 0..r) if (y < h) acc += src[y * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = acc * norm
                val add = y + r + 1
                val sub = y - r
                if (add < h) acc += src[add * w + x]
                if (sub >= 0) acc -= src[sub * w + x]
            }
        }
    }
}
