package nl.lloydstellar.spindoctor.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import nl.lloydstellar.spindoctor.model.BackgroundType
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.SpinUp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything a frame shows apart from time. */
class Scene(
    val look: Look,
    /** the label: the circle cropped from the artwork */
    val cover: Bitmap?,
    /** the background image already fitted and blurred for this frame size, or null */
    val backdrop: Bitmap?,
    val title: String,
)

/**
 * Single source of truth for a frame, like renderVinylFrame() in app.js: the
 * preview and the video encoder both call [render], so the preview is by
 * definition what ends up in the video.
 *
 *   rotation  angle of the disc in radians
 *   animated  the off-centre wobble of a spinning record
 *   pulse     extra scale from the beat envelope, 0 for none
 */
object VinylRenderer {

    /** Radius of the disc, zoom and beat pulse included (vinylRadiusFor). */
    fun radiusFor(width: Int, height: Int, look: Look, pulse: Double): Float {
        val zoom = look.zoom / 70.0
        return (min(width, height) * 0.7 * zoom * (1 + pulse) / 2).toFloat()
    }

    /**
     * Rotation at [time] seconds into the clip (rotationAt). With spin up the
     * record follows [SpinUp], the same curve the music is played at.
     */
    fun rotationAt(time: Double, look: Look): Double =
        SpinUp.played(time, look.spinUpTime) * look.rpm / 60.0 * Math.PI * 2

    fun render(c: Canvas, width: Int, height: Int, scene: Scene, art: DiscArt?, rotation: Double, animated: Boolean, pulse: Double) {
        drawBackground(c, width, height, scene)
        /* the text block is measured first: the record moves up if it does not fit below */
        val layout = measureOverlay(width, height, scene.look, scene.title)
        val offset = discOffsetForOverlay(layout, width, height, scene.look)
        drawDisc(c, width, height, scene, art, rotation, animated, pulse, offset)
        drawOverlay(c, width, height, scene.look, layout, offset)
    }

    private fun drawBackground(c: Canvas, width: Int, height: Int, scene: Scene) {
        val look = scene.look
        /* drawRect, not drawColor: a preview canvas is not clipped to its frame */
        val frame = RectF(0f, 0f, width.toFloat(), height.toFloat())
        if (look.backgroundType == BackgroundType.IMAGE && scene.backdrop != null) {
            c.drawBitmap(scene.backdrop, null, frame, bitmapPaint)
        } else if (look.backgroundType == BackgroundType.COLOR) {
            c.drawRect(frame, fillPaint(look.backgroundColor or 0xFF000000.toInt()))
        } else {
            /* image chosen but not picked yet: transparent on the website, black in its export */
            c.drawRect(frame, fillPaint(Color.BLACK))
        }
    }

    private fun drawDisc(
        c: Canvas, width: Int, height: Int, scene: Scene, art: DiscArt?,
        rotation: Double, animated: Boolean, pulse: Double, offsetY: Float,
    ) {
        val look = scene.look
        val cx = width / 2f
        val cy = height / 2f + offsetY
        val vr = radiusFor(width, height, look, pulse)
        val lr = vr * VINYL_LABEL
        val rotDeg = Math.toDegrees(rotation).toFloat()

        c.save()
        c.translate(cx, cy)
        drawShadow(c, vr)

        /* the hole is never exactly centred: the disc turns around the spindle */
        val ecc = if (animated) vr * VINYL_ECCENTRICITY else 0f
        c.translate((cos(rotation) * ecc).toFloat(), (sin(rotation) * ecc).toFloat())

        val stamp = RectF(-vr, -vr, vr, vr)
        if (art != null) {
            c.save()
            if (rotation != 0.0) c.rotate(rotDeg)
            c.drawBitmap(art.disc, null, stamp, bitmapPaint)
            c.restore()
            /* the light is fixed in angle but sits on the disc, so it moves with it */
            c.drawBitmap(art.sheen, null, stamp, bitmapPaint)
        } else {
            c.drawCircle(0f, 0f, vr, fillPaint(0xFF0D0D0D.toInt()))
        }

        if (animated) c.rotate(rotDeg)

        if (scene.cover != null) {
            c.clipPath(circlePath(lr))
            c.drawBitmap(scene.cover, null, RectF(-lr, -lr, lr, lr), bitmapPaint)
            drawLabelDetail(c, vr, lr)
        }

        if (look.centerHole) {
            /* the label clip is active, so start again from the frame */
            c.restore()
            c.save()
            c.translate(cx, cy)
            if (animated) c.rotate(rotDeg)
            drawSpindleHole(c, vr)
        }
        c.restore()
    }

    /** A soft shadow under the record so it sits on the background instead of floating. */
    private fun drawShadow(c: Canvas, vr: Float) {
        val ox = (-cos(VINYL_LIGHT_ANGLE) * vr * 0.045).toFloat()
        val oy = (-sin(VINYL_LIGHT_ANGLE) * vr * 0.045 + vr * 0.03).toFloat()
        val p = fillPaint()
        p.gradient(radial(ox, oy, vr * 0.92f, ox, oy, vr * 1.12f, 0f to black(0.55), 0.45f to black(0.28), 1f to black(0.0)))
        c.drawCircle(ox, oy, vr * 1.12f, p)
    }

    /** The label edge, drawn after the cover, rotating with it. */
    private fun drawLabelDetail(c: Canvas, vr: Float, lr: Float) {
        val p = fillPaint()
        p.gradient(radial(0f, 0f, lr * 0.9f, 0f, 0f, lr, 0f to black(0.0), 0.75f to black(0.12), 1f to black(0.5)))
        c.drawCircle(0f, 0f, lr, p)
        val lx = (cos(VINYL_LIGHT_ANGLE) * lr).toFloat(); val ly = (sin(VINYL_LIGHT_ANGLE) * lr).toFloat()
        p.gradient(LinearGradient(lx, ly, -lx, -ly, intArrayOf(white(0.10), white(0.0), black(0.10)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP))
        c.drawCircle(0f, 0f, lr, p)
        val line = strokePaint(black(0.7), max(0.8f, vr * 0.003f))
        c.drawCircle(0f, 0f, lr, line)
        line.color = white(0.10)
        line.strokeWidth = max(0.6f, vr * 0.002f)
        c.drawCircle(0f, 0f, lr + line.strokeWidth * 1.5f, line)
    }

    /** The spindle hole: 7.24 mm on a 150.8 mm radius. */
    private fun drawSpindleHole(c: Canvas, vr: Float) {
        val r = vr * 0.024f
        val p = fillPaint()
        p.gradient(radial(0f, 0f, r * 0.8f, 0f, 0f, r * 1.5f, 0f to black(0.45), 0.6f to white(0.08), 1f to white(0.0)))
        c.drawCircle(0f, 0f, r * 1.5f, p)
        p.shader = null
        p.color = Color.BLACK
        c.drawCircle(0f, 0f, r, p)
        p.color = rgba(40, 40, 44, 0.6)
        c.drawCircle((-cos(VINYL_LIGHT_ANGLE) * r * 0.25).toFloat(), (-sin(VINYL_LIGHT_ANGLE) * r * 0.25).toFloat(), r * 0.8f, p)
    }
}
