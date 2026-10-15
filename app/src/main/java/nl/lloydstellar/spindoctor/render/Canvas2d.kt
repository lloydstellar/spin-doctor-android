package nl.lloydstellar.spindoctor.render

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/*
 * Small bridges from the browser's CanvasRenderingContext2D to android.graphics,
 * so the ported drawing code can stay close to app.js. Both sides draw with
 * Skia, which is why the same calls give the same picture.
 */

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun scale(f: Double) = Rgb(
        (r * f).roundToInt().coerceIn(0, 255),
        (g * f).roundToInt().coerceIn(0, 255),
        (b * f).roundToInt().coerceIn(0, 255),
    )

    fun argb(alpha: Double = 1.0) = rgba(r, g, b, alpha)

    companion object {
        fun of(color: Int) = Rgb(Color.red(color), Color.green(color), Color.blue(color))
    }
}

fun rgba(r: Int, g: Int, b: Int, a: Double): Int =
    Color.argb((a.coerceIn(0.0, 1.0) * 255).roundToInt(), r, g, b)

fun white(a: Double) = rgba(255, 255, 255, a)
fun black(a: Double) = rgba(0, 0, 0, a)

/** createRadialGradient(x0, y0, r0, x1, y1, r1) with addColorStop() pairs. */
fun radial(
    x0: Float, y0: Float, r0: Float, x1: Float, y1: Float, r1: Float,
    vararg stops: Pair<Float, Int>,
): Shader = RadialGradient(
    x0, y0, r0, x1, y1, r1.coerceAtLeast(0.001f),
    LongArray(stops.size) { Color.pack(stops[it].second) },
    FloatArray(stops.size) { stops[it].first },
    Shader.TileMode.CLAMP,
)

fun fillPaint(color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    this.color = color
}

fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    this.color = color
    strokeWidth = width
}

val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

/** annulusPath() from app.js: a ring between two radii as fractions of [r]. */
fun annulus(r: Float, outer: Float, inner: Float) = Path().apply {
    fillType = Path.FillType.EVEN_ODD
    addCircle(0f, 0f, r * outer, Path.Direction.CW)
    addCircle(0f, 0f, r * inner, Path.Direction.CCW)
}

fun circlePath(r: Float) = Path().apply { addCircle(0f, 0f, r, Path.Direction.CW) }

/**
 * Canvas shadowBlur is twice the Gaussian sigma; Paint.setShadowLayer takes
 * a Skia blur radius, which Skia turns into sigma = radius * 0.57735 + 0.5.
 */
fun shadowRadiusFor(canvasShadowBlur: Float): Float {
    val sigma = canvasShadowBlur / 2f
    return if (sigma <= 0.5f) 0.01f else (sigma - 0.5f) / 0.57735f
}

/**
 * Sets [shader] as the paint's fill. On Android the paint's own alpha also
 * scales a shader (a canvas fillStyle gradient has no such second alpha), so
 * the colour is reset to opaque first.
 */
fun Paint.gradient(shader: Shader?): Paint {
    color = Color.BLACK
    this.shader = shader
    return this
}
