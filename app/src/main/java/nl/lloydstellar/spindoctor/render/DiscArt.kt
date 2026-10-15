package nl.lloydstellar.spindoctor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.SweepGradient
import nl.lloydstellar.spindoctor.model.Finish
import nl.lloydstellar.spindoctor.model.Look
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The record itself: a 12-inch LP drawn to the RIAA proportions, baked once
 * per size into two bitmaps, exactly like getVinylDiscCanvas() and
 * getVinylSheenCanvas() in app.js:
 *
 *   disc   everything that turns: body, grooves, track gaps, dead wax, wear, rim
 *   sheen  the static light on top: side light, anisotropic fans, glints, shading
 *
 * The beat pulse only scales the stamps, so a pulsing record never rebakes.
 */

const val VINYL_LABEL = 0.33f          // label radius / disc radius
const val VINYL_LOCKED = 0.352f        // locked groove
const val VINYL_DEADWAX = 0.40f        // last music groove
const val VINYL_LEADIN = 0.97f         // first music groove
const val VINYL_RIM = 0.98f            // raised rim
const val VINYL_ECCENTRICITY = 0.005f  // hole offset as a fraction of the radius
val VINYL_LIGHT_ANGLE = atan2(-0.2, -0.6) // light from the left, slightly above

/* two or three tracks per side: separators, the width of the smooth gap, and the loudness of each band */
private val SEPARATORS = floatArrayOf(0.775f, 0.575f)
private const val GAP_WIDTH = 0.0035f
private val LOUDNESS = doubleArrayOf(0.9, 1.0, 0.85)

/* luminance ramp of a coloured disc from the middle outwards */
private val BODY_RAMP = arrayOf(0f to 1.0, 0.3f to 0.86, 0.7f to 0.62, 0.9f to 0.5, 1f to 0.42)

private const val TWO_PI = PI * 2

private fun bandAt(f: Float): Int {
    for (k in SEPARATORS.indices) if (f > SEPARATORS[k]) return k
    return SEPARATORS.size
}

private fun inGap(f: Float) = SEPARATORS.any { abs(f - it) < GAP_WIDTH }

/** What the disc bake depends on; two Looks with the same key share bitmaps. */
data class DiscKey(val finish: Finish, val colour: Int, val accent: Int, val baseRadius: Int) {
    companion object {
        fun of(look: Look, baseRadius: Float) = DiscKey(
            look.finish,
            if (look.finish.coloured) look.vinylColor else 0,
            if (look.finish.twoColours) look.vinylColor2 else 0,
            baseRadius.roundToInt(),
        )
    }
}

class DiscArt(val key: DiscKey, val disc: Bitmap, val sheen: Bitmap) {

    companion object {
        /** Bakes both bitmaps. Slow for marble (a second or so): never call it on the main thread. */
        fun bake(key: DiscKey): DiscArt = DiscArt(key, bakeDisc(key), bakeSheen(key))

        private val cacheLock = Any()
        private var marbleCache: Triple<DiscKey, Int, Bitmap>? = null

        /** The marble is the same field at any size, so it is cached by colour and resolution only. */
        private fun marble(key: DiscKey, res: Int): Bitmap = synchronized(cacheLock) {
            marbleCache?.let { (k, r, bmp) ->
                if (k.colour == key.colour && k.accent == key.accent && r == res) return bmp
            }
            val bmp = marbleBitmap(res, Rgb.of(key.colour), Rgb.of(key.accent), Mulberry32(0x5EED1234))
            marbleCache = Triple(key, res, bmp)
            bmp
        }

        private fun bodyShader(key: DiscKey, r: Float) = if (key.finish == Finish.BLACK) {
            radial(
                0f, 0f, 0f, 0f, 0f, r,
                0f to 0xFF1A1A1A.toInt(), 0.3f to 0xFF0D0D0D.toInt(), 0.7f to 0xFF030303.toInt(),
                0.9f to 0xFF010101.toInt(), 1f to 0xFF000000.toInt(),
            )
        } else {
            val rgb = Rgb.of(key.colour)
            radial(0f, 0f, 0f, 0f, 0f, r, *BODY_RAMP.map { (stop, f) -> stop to rgb.scale(f).argb() }.toTypedArray())
        }

        private fun bakeDisc(key: DiscKey): Bitmap {
            val baseRadius = key.baseRadius.toFloat()
            val ss = if (baseRadius < 700) 2 else 1
            val rInt = (baseRadius * ss).roundToInt()
            val r = rInt.toFloat()
            val size = rInt * 2 + 4
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val g = Canvas(bmp)
            g.translate(size / 2f, size / 2f)
            val black = key.finish == Finish.BLACK
            val rand = Mulberry32(0xA11D15C)
            val fill = fillPaint()
            val line = strokePaint(0, 1f)

            /* body */
            fill.gradient(bodyShader(key, r))
            g.drawCircle(0f, 0f, r, fill)
            fill.shader = null

            if (key.finish == Finish.SPLATTER || key.finish == Finish.MARBLE) {
                /* getVinylPatternCanvas(R): a canvas of 2R, drawn over the body */
                val pr = max(64, (r * 2).roundToInt()) / 2f
                val pattern = Bitmap.createBitmap((pr * 2).toInt(), (pr * 2).toInt(), Bitmap.Config.ARGB_8888)
                val pg = Canvas(pattern)
                pg.translate(pr, pr)
                if (key.finish == Finish.SPLATTER) {
                    drawSplatterPattern(pg, pr, Rgb.of(key.colour), Rgb.of(key.accent), Mulberry32(0x5EED1234))
                } else {
                    drawMarblePattern(pg, pr, marble(key, marbleResolution(pr)))
                }
                g.save()
                g.clipPath(circlePath(r))
                g.drawBitmap(pattern, null, RectF(-r, -r, r, r), bitmapPaint)
                g.restore()
                pattern.recycle()
            }

            /* the music area: three bands of satin groove texture, lifted by their loudness */
            for (b in 0 until 3) {
                val outer = if (b == 0) VINYL_LEADIN else SEPARATORS[b - 1] - GAP_WIDTH
                val inner = if (b == 2) VINYL_DEADWAX else SEPARATORS[b] + GAP_WIDTH
                val lift = (if (black) 0.018 else 0.03) * LOUDNESS[b]
                fill.color = white(fixed3(lift))
                g.drawPath(annulus(r, outer, inner), fill)
            }

            /* dense hairline grooves, alternately a shade darker and lighter, the alpha wandering like dynamics */
            val step = max(0.8f, r * 0.0017f)
            line.strokeWidth = max(0.5f, step * 0.5f)
            var rr = r * VINYL_LEADIN
            var n = 0
            while (rr > r * VINYL_DEADWAX) {
                val f = rr / r
                if (!inGap(f)) {
                    val loud = LOUDNESS[bandAt(f)]
                    val dyn = 0.5 + 0.5 * sin(n * 0.09) * sin(n * 0.023 + 0.7) * cos(n * 0.0071)
                    val noise = (rand.next() - 0.5) * 0.10
                    line.color = if (n % 2 == 0) {
                        val a = (if (black) 0.08 else 0.05) + dyn * (if (black) 0.12 else 0.07) * loud + noise
                        black(fixed3(max(0.02, a)))
                    } else {
                        val a = (if (black) 0.016 else 0.02) + dyn * 0.03 * loud + noise * 0.4
                        white(fixed3(max(0.005, a)))
                    }
                    g.drawCircle(0f, 0f, rr, line)
                }
                rr -= step
                n++
            }

            /* slow loudness swells inside the bands: a few wider soft rings */
            for (k in 0 until 10) {
                val f = (VINYL_DEADWAX + rand.next() * (VINYL_LEADIN - VINYL_DEADWAX)).toFloat()
                if (inGap(f)) continue
                val w = r * (0.012 + rand.next() * 0.03)
                val lighter = rand.next() < 0.5
                line.color = if (lighter) white(fixed3(0.02 + rand.next() * 0.03)) else black(fixed3(0.05 + rand.next() * 0.06))
                line.strokeWidth = w.toFloat()
                g.drawCircle(0f, 0f, r * f, line)
            }

            /* track separators: smooth glossy rings, dark, with an engraved edge */
            for (sp in SEPARATORS) {
                line.strokeWidth = r * GAP_WIDTH * 2
                line.color = if (black) black(0.4) else black(0.3)
                g.drawCircle(0f, 0f, r * sp, line)
                line.strokeWidth = max(0.6f, r * 0.0015f)
                line.color = black(0.3)
                g.drawCircle(0f, 0f, r * (sp + GAP_WIDTH), line)
                g.drawCircle(0f, 0f, r * (sp - GAP_WIDTH), line)
                line.color = white(0.05)
                g.drawCircle(0f, 0f, r * sp, line)
            }

            /* lead-in: the wide spiral from the rim to the first groove */
            line.strokeWidth = max(0.7f, r * 0.0016f)
            for (k in 0 until 3) {
                val f = VINYL_RIM - 0.002f - k * 0.0035f
                line.color = black(0.35 - k * 0.06)
                g.drawCircle(0f, 0f, r * f, line)
            }

            /* dead wax: glossy and smooth, lead-out spiral and locked groove */
            fill.color = if (black) black(0.08) else black(0.06)
            g.drawPath(annulus(r, VINYL_DEADWAX, VINYL_LABEL), fill)
            line.strokeWidth = max(0.7f, r * 0.0016f)
            val leadOut = 5
            for (k in 0 until leadOut) {
                val f = VINYL_DEADWAX - 0.003f - k * ((VINYL_DEADWAX - VINYL_LOCKED - 0.006f) / (leadOut - 1))
                line.color = black(0.5 - k * 0.05)
                g.drawCircle(0f, 0f, r * f, line)
                line.color = white(0.06)
                g.drawCircle(0f, 0f, r * f - line.strokeWidth, line)
            }
            line.color = black(0.6)
            line.strokeWidth = max(0.9f, r * 0.0022f)
            g.drawCircle(0f, 0f, r * VINYL_LOCKED, line)

            /* matrix number: a short hand-scratched arc of marks */
            line.strokeCap = Paint.Cap.ROUND
            val mx = r * 0.343
            for (k in 0 until 14) {
                val a0 = 0.42 + k * 0.022
                val h = r * (0.004 + rand.next() * 0.006)
                val x = cos(a0) * mx; val y = sin(a0) * mx
                line.color = white(0.18 + rand.next() * 0.14)
                line.strokeWidth = max(0.5f, r * 0.0011f)
                val x0 = x - cos(a0) * h; val y0 = y - sin(a0) * h
                val x1 = x + cos(a0) * h + (rand.next() - 0.5) * h
                val y1 = y + sin(a0) * h + (rand.next() - 0.5) * h
                g.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), line)
            }
            line.strokeCap = Paint.Cap.BUTT

            /* wear: hairline scratches and dust */
            val oval = RectF()
            for (k in 0 until 12) {
                val a0 = rand.next() * TWO_PI
                val len = 0.04 + rand.next() * 0.25
                val r0 = (r * (0.42 + rand.next() * 0.52)).toFloat()
                line.color = white(0.02 + rand.next() * 0.04)
                line.strokeWidth = max(0.5f, r * 0.0011f)
                oval.set(-r0, -r0, r0, r0)
                g.drawArc(oval, Math.toDegrees(a0).toFloat(), Math.toDegrees(len).toFloat(), false, line)
            }
            for (k in 0 until 6) {
                val a0 = rand.next() * TWO_PI
                val r0 = r * (0.42 + rand.next() * 0.5)
                val len = r * (0.02 + rand.next() * 0.07)
                val dir = rand.next() * TWO_PI
                line.color = white(0.05 + rand.next() * 0.06)
                line.strokeWidth = max(0.5f, r * 0.001f)
                val sx = cos(a0) * r0; val sy = sin(a0) * r0
                g.drawLine(sx.toFloat(), sy.toFloat(), (sx + cos(dir) * len).toFloat(), (sy + sin(dir) * len).toFloat(), line)
            }
            for (k in 0 until 70) {
                val a0 = rand.next() * TWO_PI
                val r0 = r * (0.36 + rand.next() * 0.62)
                fill.color = white(0.05 + rand.next() * 0.10)
                val dot = max(0.4, r * (0.0005 + rand.next() * 0.0011))
                g.drawCircle((cos(a0) * r0).toFloat(), (sin(a0) * r0).toFloat(), dot.toFloat(), fill)
            }

            /* raised rim: a glossy bevel with a crisp dark edge */
            fill.gradient(radial(
                0f, 0f, r * VINYL_RIM, 0f, 0f, r,
                0f to black(0.3), 0.3f to white(0.05), 0.75f to black(0.25), 1f to black(0.7),
            ))
            g.drawPath(annulus(r, 1f, VINYL_RIM), fill)
            fill.shader = null
            line.color = black(0.9)
            line.strokeWidth = max(1f, r * 0.003f)
            g.drawCircle(0f, 0f, r - line.strokeWidth / 2, line)

            bmp.prepareToDraw()
            return bmp
        }

        /* The static light: side light, the anisotropic sheen of the bands, the glint on the glossy
           rings, a soft gloss on the dead wax, and the far side shading with a rim light. */
        private fun bakeSheen(key: DiscKey): Bitmap {
            val r = key.baseRadius.toFloat()
            val size = key.baseRadius * 2 + 4
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val g = Canvas(bmp)
            g.translate(size / 2f, size / 2f)
            val k = if (key.finish == Finish.BLACK) 1.0 else 0.65
            val l = VINYL_LIGHT_ANGLE
            val lx = (cos(l) * r).toFloat(); val ly = (sin(l) * r).toFloat()
            val fill = fillPaint()

            fun fan(sigmaBroad: Double, aBroad: Double, sigmaCore: Double, aCore: Double): SweepGradient {
                val stops = 240
                val colours = IntArray(stops + 1)
                val positions = FloatArray(stops + 1)
                for (i in 0..stops) {
                    val ang = i.toDouble() / stops * TWO_PI
                    var d = abs(((ang - l) % PI + PI) % PI)
                    d = min(d, PI - d)
                    val a = aBroad * exp(-(d * d) / (2 * sigmaBroad * sigmaBroad)) + aCore * exp(-(d * d) / (2 * sigmaCore * sigmaCore))
                    colours[i] = white(fixed(min(1.0, a), 4))
                    positions[i] = i.toFloat() / stops
                }
                return SweepGradient(0f, 0f, colours, positions)
            }

            fun fillAll(shader: android.graphics.Shader?) {
                fill.gradient(shader)
                g.drawRect(-r, -r, r, r, fill)
                fill.shader = null
            }

            /* 1. side light */
            fill.gradient(radial(
                -r * 0.6f, -r * 0.2f, 0f, 0f, 0f, r * 1.4f,
                0f to white(0.15 * k), 0.3f to white(0.085 * k), 0.6f to white(0.04 * k), 1f to white(0.0),
            ))
            g.drawCircle(0f, 0f, r, fill)
            fill.shader = null

            /* 2. the sheen over the whole music area, gaps included */
            g.save(); g.clipPath(annulus(r, VINYL_LEADIN, VINYL_DEADWAX))
            fillAll(fan(0.7, 0.13 * k, 0.26, 0.17 * k))
            g.restore()
            /* the smooth dead wax reflects a little less diffusely */
            g.save(); g.clipPath(annulus(r, VINYL_DEADWAX, VINYL_LABEL))
            fillAll(fan(0.7, 0.09 * k, 0.26, 0.11 * k))
            g.restore()
            /* slightly weaker right next to the label and at the very rim */
            g.save(); g.clipPath(annulus(r, VINYL_LEADIN, VINYL_DEADWAX))
            fill.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            fillAll(
                radial(
                    0f, 0f, r * VINYL_DEADWAX, 0f, 0f, r * VINYL_LEADIN,
                    0f to black(0.2), 0.1f to black(0.0), 0.92f to black(0.0), 1f to black(0.12),
                )
            )
            fill.xfermode = null
            g.restore()
            /* the smooth track gaps mirror the dark room */
            g.save()
            val gap = strokePaint(black(0.42), r * GAP_WIDTH * 2).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            for (sp in SEPARATORS) g.drawCircle(0f, 0f, r * sp, gap)
            g.restore()

            /* 3. glint on the glossy rings */
            val glossy = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addCircle(0f, 0f, r, Path.Direction.CW)
                addCircle(0f, 0f, r * VINYL_RIM, Path.Direction.CCW)
                for (sp in SEPARATORS) {
                    addCircle(0f, 0f, r * (sp + GAP_WIDTH), Path.Direction.CW)
                    addCircle(0f, 0f, r * (sp - GAP_WIDTH), Path.Direction.CCW)
                }
                addCircle(0f, 0f, r * VINYL_DEADWAX, Path.Direction.CW)
                addCircle(0f, 0f, r * VINYL_LABEL, Path.Direction.CCW)
            }
            g.save(); g.clipPath(glossy)
            fillAll(fan(0.1, 0.16 * k, 0.03, 0.38 * k))
            g.restore()

            /* 4. gloss on the dead wax */
            val gx = (cos(l) * r * 0.37).toFloat(); val gy = (sin(l) * r * 0.37).toFloat()
            g.save(); g.clipPath(annulus(r, VINYL_DEADWAX, VINYL_LABEL))
            fillAll(radial(gx, gy, 0f, gx, gy, r * 0.16f, 0f to white(0.18 * k), 0.5f to white(0.06 * k), 1f to white(0.0)))
            g.restore()

            /* 5. far side shading and rim light */
            g.save(); g.clipPath(circlePath(r))
            fillAll(
                android.graphics.LinearGradient(
                    lx, ly, -lx, -ly, intArrayOf(black(0.0), black(0.0), black(0.34)), floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            )
            g.restore()
            val rim = strokePaint(0, max(1.2f, r * 0.005f))
            rim.gradient(android.graphics.LinearGradient(
                lx, ly, -lx, -ly,
                intArrayOf(white(0.5 * k), white(0.18 * k), white(0.02), white(0.08 * k)),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            ))
            g.drawCircle(0f, 0f, r - rim.strokeWidth * 0.9f, rim)

            bmp.prepareToDraw()
            return bmp
        }

        private fun fixed3(v: Double) = fixed(v, 3)
        private fun fixed(v: Double, digits: Int): Double {
            val m = Math.pow(10.0, digits.toDouble())
            return Math.round(v * m) / m
        }
    }

    fun recycle() {
        disc.recycle()
        sheen.recycle()
    }
}
