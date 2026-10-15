package nl.lloydstellar.spindoctor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The coloured vinyl patterns of app.js. The order of every rand() call is kept
 * exactly as in the original: the PRNG is seeded, so a different order would
 * put the splatter somewhere else than on the website.
 */

private const val TWO_PI = PI * 2
private fun deg(rad: Double) = Math.toDegrees(rad).toFloat()

/** Number.prototype.toFixed: the original rounds alpha values to a few decimals. */
private fun fixed(v: Double, digits: Int): Double {
    val m = 10.0.pow(digits)
    return round(v * m) / m
}

/* =============================================================
   Splatter vinyl: shards and crumbs of a second colour, smeared
   along the radius by the press. Drawn into [g], centred on the
   origin, inside a disc of radius [r].
   ============================================================= */
fun drawSplatterPattern(g: Canvas, r: Float, base: Rgb, accent: Rgb, rand: Mulberry32) {
    val mid = Rgb(
        ((base.r + accent.r) / 2.0).roundToInt(),
        ((base.g + accent.g) / 2.0).roundToInt(),
        ((base.b + accent.b) / 2.0).roundToInt(),
    )
    val shear = 0.35 + rand.next() * 0.3
    val fill = fillPaint()

    fun piece(dist: Double, angle: Double, size: Double, alpha: Double, tail: Double) {
        g.save()
        g.translate((cos(angle) * dist).toFloat(), (sin(angle) * dist).toFloat())
        g.rotate(deg(angle + (rand.next() - 0.5) * 0.5 + shear * 0.6))
        g.scale((1 + tail).toFloat(), 1f)
        val pts = 9 + floor(rand.next() * 6).toInt()
        val path = Path()
        for (k in 0..pts) {
            val t = (k % pts).toDouble() / pts * TWO_PI
            val wob = 0.62 + rand.next() * 0.55
            /* the head (towards the centre) is fuller, the tail thins out */
            val taper = 1 - 0.45 * max(0.0, cos(t))
            val px = (cos(t) * size * wob * taper).toFloat()
            val py = (sin(t) * size * wob * taper * 0.85).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.shader = null
        fill.color = accent.argb(fixed(alpha, 3))
        g.drawPath(path, fill)
        g.restore()
    }

    fun halo(dist: Double, angle: Double, size: Double, alpha: Double, tail: Double) {
        g.save()
        g.translate((cos(angle) * dist).toFloat(), (sin(angle) * dist).toFloat())
        g.rotate(deg(angle + shear * 0.6))
        g.scale((1 + tail * 1.3).toFloat(), 1f)
        val rr = (size * 1.9).toFloat()
        fill.gradient(radial(
            0f, 0f, 0f, 0f, 0f, rr,
            0f to mid.argb(fixed(alpha * 0.55, 3)),
            0.55f to mid.argb(fixed(alpha * 0.18, 3)),
            1f to mid.argb(0.0),
        ))
        g.drawCircle(0f, 0f, rr, fill)
        fill.shader = null
        g.restore()
    }

    g.save()
    g.clipPath(circlePath(r))

    /* a faint tint of melted accent across the whole disc, uneven */
    for (i in 0 until 9) {
        val a0 = rand.next() * TWO_PI
        val d0 = sqrt(rand.next()) * r * 0.9
        val sz = r * (0.18 + rand.next() * 0.22)
        val cx = (cos(a0) * d0).toFloat(); val cy = (sin(a0) * d0).toFloat()
        fill.gradient(radial(
            cx, cy, 0f, cx, cy, sz.toFloat(),
            0f to mid.argb(fixed(0.05 + rand.next() * 0.07, 3)),
            1f to mid.argb(0.0),
        ))
        g.drawRect(-r, -r, r, r, fill)
        fill.shader = null
    }
    /* a few big shards with halos */
    for (i in 0 until 14) {
        val a0 = rand.next() * TWO_PI
        val d0 = (0.2 + sqrt(rand.next()) * 0.78) * r
        val sz = r * (0.035 + rand.next() * 0.05)
        val tail = 0.6 + rand.next() * 1.4
        halo(d0, a0, sz, 0.5 + rand.next() * 0.3, tail)
        piece(d0, a0, sz, 0.78 + rand.next() * 0.2, tail)
    }
    /* many medium pieces */
    for (i in 0 until 110) {
        val a0 = rand.next() * TWO_PI
        val d0 = (0.12 + sqrt(rand.next()) * 0.87) * r
        val sz = r * (0.01 + rand.next() * 0.024)
        val tail = 0.3 + rand.next() * 1.6
        if (rand.next() < 0.5) halo(d0, a0, sz, 0.3 + rand.next() * 0.3, tail)
        piece(d0, a0, sz, 0.6 + rand.next() * 0.35, tail)
    }
    /* crumbs and dust, streaked along the radius */
    val oval = RectF()
    for (i in 0 until 900) {
        val a0 = rand.next() * TWO_PI
        val d0 = sqrt(rand.next()) * r * 0.99
        val sz = r * (0.0012 + rand.next() * 0.005)
        val tail = rand.next() * 3
        g.save()
        g.translate((cos(a0) * d0).toFloat(), (sin(a0) * d0).toFloat())
        g.rotate(deg(a0 + shear * 0.6))
        g.scale((1 + tail).toFloat(), 1f)
        val alpha = 0.3 + rand.next() * 0.6
        val colour = if (rand.next() < 0.75) accent else mid
        fill.color = colour.argb(alpha)
        val ry = sz * (0.6 + rand.next() * 0.5)
        oval.set((-sz).toFloat(), (-ry).toFloat(), sz.toFloat(), ry.toFloat())
        g.drawOval(oval, fill)
        g.restore()
    }
    g.restore()
}

/* =============================================================
   Marbled vinyl: colours squeezed outwards from the centre into
   veins and smoke-like wisps, rendered per pixel from domain-
   warped value noise on a cylinder (no seam at the angle wrap).
   ============================================================= */

/** The per-pixel part of drawMarblePattern(), as a [res] x [res] bitmap. */
fun marbleBitmap(res: Int, base: Rgb, accent: Rgb, rand: Mulberry32): Bitmap {
    val noise = ValueNoise3(rand)
    val half = res / 2.0
    val twist = 0.55 + rand.next() * 0.6
    val ringFreq = 2.3 + rand.next() * 0.9
    val radFreq = 2.1
    val phase = rand.next() * 10
    val accentAmount = 0.45 + rand.next() * 0.15
    val midR = (base.r + accent.r) / 2.0; val midG = (base.g + accent.g) / 2.0; val midB = (base.b + accent.b) / 2.0
    val darkR = base.r * 0.55; val darkG = base.g * 0.55; val darkB = base.b * 0.55
    val pixels = IntArray(res * res)

    /* ImageData is a Uint8ClampedArray: clamp, then round half to even */
    fun u8(v: Double) = Math.rint(v.coerceIn(0.0, 255.0)).toInt()

    IntStream.range(0, res).parallel().forEach { py ->
        for (px in 0 until res) {
            val dx = (px + 0.5 - half) / half
            val dy = (py + 0.5 - half) / half
            val rr = sqrt(dx * dx + dy * dy)
            val idx = py * res + px
            if (rr > 1.01) { pixels[idx] = 0; continue }
            val th = atan2(dy, dx) + rr * twist
            val cx = cos(th) * ringFreq; val cy = sin(th) * ringFreq; val cz = rr * radFreq * 2.6 + phase
            val wx = noise.fbm(cx + 3.1, cy + 1.7, cz, 3) - 0.5
            val wy = noise.fbm(cx - 2.4, cy + 4.2, cz + 5.5, 3) - 0.5
            val v = noise.fbm(cx + wx * 2.6, cy + wy * 2.6, cz + (wx - wy) * 1.1, 5)
            val region = noise.fbm(cx * 0.45 + 11.3, cy * 0.45 - 6.1, cz * 0.5 + 3.7, 2) - 0.5
            val vein = exp(-((v - 0.5) * 26).pow(2))
            var m = (v - (0.5 - accentAmount * 0.22 + region * 0.28)) / 0.2
            m = if (m < 0) 0.0 else if (m > 1) 1.0 else m
            m = m * m * (3 - 2 * m)
            val smoke = noise.fbm(cx * 2.3 + 7.7, cy * 2.3 - 3.3, cz * 1.7 + 2.2, 3)
            val haze = max(0.0, smoke - 0.55) * 1.6
            var cr: Double; var cg: Double; var cb: Double
            if (m < 0.5) {
                val t = m * 2
                cr = base.r + (midR - base.r) * t; cg = base.g + (midG - base.g) * t; cb = base.b + (midB - base.b) * t
            } else {
                val t = (m - 0.5) * 2
                cr = midR + (accent.r - midR) * t; cg = midG + (accent.g - midG) * t; cb = midB + (accent.b - midB) * t
            }
            val pocket = max(0.0, 0.42 - v) * 1.4 * (1 - m)
            cr += (darkR - cr) * pocket; cg += (darkG - cg) * pocket; cb += (darkB - cb) * pocket
            val k = vein * 0.55 + haze * 0.35
            cr += (accent.r - cr) * k; cg += (accent.g - cg) * k; cb += (accent.b - cb) * k
            /* the disc's own body gradient shows through a little */
            pixels[idx] = (235 shl 24) or (u8(cr) shl 16) or (u8(cg) shl 8) or u8(cb)
        }
    }
    return Bitmap.createBitmap(pixels, res, res, Bitmap.Config.ARGB_8888)
}

/** Resolution the original computes the marble at for a disc of radius [r]. */
fun marbleResolution(r: Float) = min(800, max(256, (r * 2).roundToInt()))

fun drawMarblePattern(g: Canvas, r: Float, marble: Bitmap) {
    g.save()
    g.clipPath(circlePath(r))
    g.drawBitmap(marble, null, RectF(-r, -r, r, r), Paint(bitmapPaint))
    g.restore()
}
