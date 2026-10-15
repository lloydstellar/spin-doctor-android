package nl.lloydstellar.spindoctor.render

import kotlin.math.floor

/**
 * mulberry32 from app.js, bit for bit. Int arithmetic wraps exactly like
 * Math.imul and the ToInt32 conversions in JavaScript, so a seed gives the same
 * sequence as in the browser and the splatter lands in the same places.
 */
class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
    }
}

/** makeValueNoise3 from app.js: seeded 3D value noise with smoothstep fade. */
class ValueNoise3(rand: Mulberry32) {
    private val perm = IntArray(512)
    private val grad = FloatArray(256)

    init {
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = floor(rand.next() * (i + 1)).toInt()
            val t = p[i]; p[i] = p[j]; p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
        /* a Float32Array in the original: keep the float rounding */
        for (i in 0 until 256) grad[i] = rand.next().toFloat()
    }

    private fun lat(x: Int, y: Int, z: Int): Double =
        grad[perm[(perm[(perm[x and 255] + y) and 255] + z) and 255]].toDouble()

    private fun fade(t: Double) = t * t * (3 - 2 * t)

    fun noise(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val zi = floor(z).toInt()
        val xf = x - xi; val yf = y - yi; val zf = z - zi
        val u = fade(xf); val v = fade(yf); val w = fade(zf)
        val x00 = lat(xi, yi, zi) + (lat(xi + 1, yi, zi) - lat(xi, yi, zi)) * u
        val x10 = lat(xi, yi + 1, zi) + (lat(xi + 1, yi + 1, zi) - lat(xi, yi + 1, zi)) * u
        val x01 = lat(xi, yi, zi + 1) + (lat(xi + 1, yi, zi + 1) - lat(xi, yi, zi + 1)) * u
        val x11 = lat(xi, yi + 1, zi + 1) + (lat(xi + 1, yi + 1, zi + 1) - lat(xi, yi + 1, zi + 1)) * u
        val y0 = x00 + (x10 - x00) * v
        val y1 = x01 + (x11 - x01) * v
        return y0 + (y1 - y0) * w
    }

    fun fbm(x: Double, y: Double, z: Double, octaves: Int): Double {
        var sum = 0.0; var amp = 0.5; var f = 1.0; var norm = 0.0
        for (o in 0 until octaves) {
            sum += noise(x * f, y * f, z * f) * amp
            norm += amp; amp *= 0.5; f *= 2.1
        }
        return sum / norm
    }
}
