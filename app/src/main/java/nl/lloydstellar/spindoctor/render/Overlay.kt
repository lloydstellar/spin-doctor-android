package nl.lloydstellar.spindoctor.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import nl.lloydstellar.spindoctor.model.BadgeType
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.TextPlace
import nl.lloydstellar.spindoctor.model.TextStyle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Artist, title and badge, ported from the text overlay section of app.js:
 * the same presets, sizes, manual letter spacing, dark outline and shadow, and
 * the same rule that the record moves out of the way instead of overlapping.
 */

class TextPreset(
    val titleFont: Typeface,
    val artistFont: Typeface,
    val badgeFont: Typeface,
    val titleScale: Double,
    val artistScale: Double,
    val titleUpper: Boolean,
    val artistTracking: Float,
    val badgeTracking: Float,
)

private fun presetFor(style: TextStyle) = when (style) {
    TextStyle.CLEAN -> TextPreset(Fonts.sans700, Fonts.sans500, Fonts.sans700, 0.056, 0.027, false, 0.18f, 0.1f)
    TextStyle.TECHNO -> TextPreset(Fonts.orbitron700, Fonts.mono, Fonts.orbitron700, 0.05, 0.025, true, 0.22f, 0.14f)
    TextStyle.MONO -> TextPreset(Fonts.mono, Fonts.mono, Fonts.mono, 0.052, 0.026, true, 0.2f, 0.14f)
}

private val MONTHS = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

fun badgeLabel(look: Look): String = when (look.badgeType) {
    BadgeType.NEW_SINGLE -> "NEW SINGLE"
    BadgeType.NEW_EP -> "NEW EP"
    BadgeType.CUSTOM -> look.badgeText.trim().uppercase()
    BadgeType.DATE -> if (look.badgeDay in 1..31 && look.badgeMonth in 1..12) "OUT ${look.badgeDay} ${MONTHS[look.badgeMonth - 1]}" else ""
    BadgeType.OUT_NOW -> "OUT NOW"
}

internal enum class Block { ARTIST, TITLE, BADGE }

class OverlayLayout internal constructor(
    val preset: TextPreset,
    val artist: String,
    val title: String,
    val badge: String,
    val titleSize: Float,
    val artistSize: Float,
    val badgeSize: Float,
    val badgePadX: Float,
    val badgeBoxHeight: Float,
    internal val blocks: List<Pair<Block, Float>>,
    val total: Float,
    val gap: Float,
    val place: TextPlace,
)

private fun codePoints(text: String): List<String> {
    val out = ArrayList<String>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val n = Character.charCount(cp)
        out.add(text.substring(i, i + n))
        i += n
    }
    return out
}

private fun trackedWidth(p: Paint, text: String, spacing: Float): Float {
    val chars = codePoints(text)
    if (chars.isEmpty()) return 0f
    var w = 0f
    for (c in chars) w += p.measureText(c) + spacing
    return w - spacing
}

/** Manual letter spacing, like drawTracked() in app.js; [baseline] is the text baseline. */
private fun drawTracked(c: Canvas, p: Paint, text: String, centerX: Float, baseline: Float, spacing: Float) {
    val chars = codePoints(text)
    if (chars.isEmpty()) return
    val align = p.textAlign
    p.textAlign = Paint.Align.LEFT
    var x = centerX - trackedWidth(p, text, spacing) / 2
    for (ch in chars) {
        c.drawText(ch, x, baseline, p)
        x += p.measureText(ch) + spacing
    }
    p.textAlign = align
}

/** Shrinks a line until it fits, in one step (fitFontSize in app.js). */
private fun fitFontSize(p: Paint, text: String, font: Typeface, startSize: Float, maxWidth: Float, minSize: Float, tracking: Float): Float {
    p.typeface = font
    p.textSize = startSize
    val measured = if (tracking != 0f) trackedWidth(p, text, startSize * tracking) else p.measureText(text)
    if (measured <= maxWidth || measured == 0f) return startSize
    return max(minSize, floor(startSize * (maxWidth / measured)))
}

/*
 * Canvas textBaseline 'top' and 'middle' refer to the em box. Its top is the
 * ascent scaled so that ascent + descent is exactly one em.
 */
private fun emTop(p: Paint): Float {
    val fm = p.fontMetrics
    val asc = -fm.ascent; val desc = fm.descent
    return p.textSize * asc / (asc + desc)
}

private fun baselineForTop(p: Paint, top: Float) = top + emTop(p)
private fun baselineForMiddle(p: Paint, middle: Float) = middle + emTop(p) - p.textSize / 2

fun measureOverlay(width: Int, height: Int, look: Look, rawTitleIn: String): OverlayLayout? {
    val preset = presetFor(look.textStyle)
    val artist = if (look.textEnabled) look.artist.trim() else ""
    val rawTitle = if (look.textEnabled) rawTitleIn.trim() else ""
    val badge = if (look.badgeEnabled) badgeLabel(look) else ""
    if (artist.isEmpty() && rawTitle.isEmpty() && badge.isEmpty()) return null

    val title = if (preset.titleUpper) rawTitle.uppercase() else rawTitle
    val maxWidth = width * 0.88f
    val minSize = max(9f, height * 0.018f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    val titleSize = if (rawTitle.isNotEmpty())
        fitFontSize(p, title, preset.titleFont, (height * preset.titleScale).roundToInt().toFloat(), maxWidth, minSize, 0f) else 0f
    val artistSize = if (artist.isNotEmpty())
        fitFontSize(p, artist.uppercase(), preset.artistFont, (height * preset.artistScale).roundToInt().toFloat(), maxWidth, minSize, preset.artistTracking) else 0f

    val badgeSize = if (badge.isNotEmpty()) max(9f, (height * 0.024f).roundToInt().toFloat()) else 0f
    val badgePadY = badgeSize * 0.62f
    val badgeBoxHeight = badgeSize + badgePadY * 2

    val gap = height * 0.016f
    val blocks = ArrayList<Pair<Block, Float>>()
    if (artist.isNotEmpty()) blocks.add(Block.ARTIST to artistSize * 1.15f)
    if (rawTitle.isNotEmpty()) blocks.add(Block.TITLE to titleSize * 1.2f)
    if (badge.isNotEmpty()) blocks.add(Block.BADGE to badgeBoxHeight)

    var total = 0f
    blocks.forEachIndexed { i, b -> total += b.second + if (i > 0) gap else 0f }

    return OverlayLayout(
        preset, artist, title, badge, titleSize, artistSize, badgeSize,
        badgeSize * 1.05f, badgeBoxHeight, blocks, total, gap, look.textPlace,
    )
}

/* With text and the default zoom the record fills most of a square frame, so it
   slides just far enough out of the way. Without text the offset is zero. */
private const val OVERLAY_DISC_GAP = 0.03f

fun discOffsetForOverlay(layout: OverlayLayout?, width: Int, height: Int, look: Look): Float {
    if (layout == null) return 0f
    val radius = VinylRenderer.radiusFor(width, height, look, 0.0)
    val margin = height * 0.045f
    val needed = radius + height * OVERLAY_DISC_GAP + layout.total + margin
    val available = height / 2f
    val room = available - radius - margin
    return if (layout.place == TextPlace.ABOVE) {
        min(max(0f, needed - available), max(0f, room))
    } else {
        max(min(0f, available - needed), -max(0f, room))
    }
}

private fun contrastInk(color: Int): Int {
    val luma = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
    return if (luma > 150) 0xFF0B0B0B.toInt() else Color.WHITE
}

fun drawOverlay(c: Canvas, width: Int, height: Int, look: Look, layout: OverlayLayout?, discOffsetY: Float) {
    if (layout == null) return
    val preset = layout.preset

    /* measured against the unpulsed radius so the text stays put while the record breathes */
    val radius = VinylRenderer.radiusFor(width, height, look, 0.0)
    val margin = height * 0.045f
    val discCenter = height / 2f + discOffsetY
    val top = if (layout.place == TextPlace.ABOVE) {
        max(margin, discCenter - radius - height * OVERLAY_DISC_GAP - layout.total)
    } else {
        min(discCenter + radius + height * OVERLAY_DISC_GAP, height - margin - layout.total)
    }

    val shadowRadius = shadowRadiusFor(height * 0.014f)
    val shadowY = height * 0.0025f
    val shadowColour = black(0.55)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = look.textColor
        setShadowLayer(shadowRadius, 0f, shadowY, shadowColour)
    }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 2f
        color = black(0.55)
        setShadowLayer(shadowRadius, 0f, shadowY, shadowColour)
    }

    val centerX = width / 2f
    var y = top
    layout.blocks.forEachIndexed { i, (kind, blockHeight) ->
        if (i > 0) y += layout.gap
        when (kind) {
            Block.ARTIST -> {
                val size = layout.artistSize
                val text = layout.artist.uppercase()
                val spacing = size * preset.artistTracking
                for (p in arrayOf(outline, fill)) { p.typeface = preset.artistFont; p.textSize = size }
                outline.strokeWidth = max(1f, size * 0.14f)
                val base = baselineForTop(fill, y)
                drawTracked(c, outline, text, centerX, base, spacing)
                drawTracked(c, fill, text, centerX, base, spacing)
            }
            Block.TITLE -> {
                val size = layout.titleSize
                for (p in arrayOf(outline, fill)) {
                    p.typeface = preset.titleFont; p.textSize = size; p.textAlign = Paint.Align.CENTER
                }
                outline.strokeWidth = max(1f, size * 0.14f)
                val base = baselineForTop(fill, y)
                c.drawText(layout.title, centerX, base, outline)
                c.drawText(layout.title, centerX, base, fill)
                for (p in arrayOf(outline, fill)) p.textAlign = Paint.Align.LEFT
            }
            Block.BADGE -> {
                val size = layout.badgeSize
                val tracking = size * preset.badgeTracking
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = preset.badgeFont; textSize = size }
                val boxWidth = trackedWidth(ink, layout.badge, tracking) + layout.badgePadX * 2
                val box = RectF(centerX - boxWidth / 2, y, centerX + boxWidth / 2, y + layout.badgeBoxHeight)
                val pillRadius = min(layout.badgeBoxHeight / 2, min(boxWidth / 2, layout.badgeBoxHeight / 2))
                val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = look.badgeColor
                    setShadowLayer(shadowRadius, 0f, shadowY, shadowColour)
                }
                c.drawRoundRect(box, pillRadius, pillRadius, pill)
                ink.color = contrastInk(look.badgeColor)
                drawTracked(c, ink, layout.badge, centerX, baselineForMiddle(ink, y + layout.badgeBoxHeight / 2), tracking)
            }
        }
        y += blockHeight
    }
}
