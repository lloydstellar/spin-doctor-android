package nl.lloydstellar.spindoctor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nl.lloydstellar.spindoctor.model.BackgroundType
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.render.Backdrop
import nl.lloydstellar.spindoctor.render.DiscArt
import nl.lloydstellar.spindoctor.render.DiscKey
import nl.lloydstellar.spindoctor.render.Scene
import nl.lloydstellar.spindoctor.render.VinylRenderer

/**
 * A live frame at the size it is shown, drawn by the same renderer as the
 * video. The record and the blurred background are baked off the main thread;
 * until a new bake is ready the previous one is shown, so dragging the zoom
 * slider or switching to marble never stalls the screen.
 */
@Composable
fun VinylView(
    look: Look,
    cover: Bitmap?,
    background: Bitmap?,
    title: String,
    rotation: Double,
    animated: Boolean,
    pulse: Double,
    modifier: Modifier = Modifier,
    safeArea: Boolean = false,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var art by remember { mutableStateOf<DiscArt?>(null) }
    var backdrop by remember { mutableStateOf<Bitmap?>(null) }
    val key = if (size.width > 0) DiscKey.of(look, VinylRenderer.radiusFor(size.width, size.height, look, 0.0)) else null

    LaunchedEffect(key) {
        if (key == null || art?.key == key) return@LaunchedEffect
        if (art != null) delay(70) // let a slider drag settle
        art = withContext(Dispatchers.Default) { DiscArt.bake(key) }
    }
    val wantsBackdrop = look.backgroundType == BackgroundType.IMAGE && background != null
    LaunchedEffect(wantsBackdrop, background, size, look.blur) {
        if (!wantsBackdrop || size.width == 0) { backdrop = null; return@LaunchedEffect }
        delay(70)
        backdrop = withContext(Dispatchers.Default) { Backdrop.render(background, size.width, size.height, look.blur) }
    }

    val measurer = rememberTextMeasurer()
    Box(modifier.onSizeChanged { size = it }.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            if (size.width == 0) return@Canvas
            val scene = Scene(look, cover, if (wantsBackdrop) backdrop else null, title)
            drawIntoCanvas {
                VinylRenderer.render(it.nativeCanvas, size.width, size.height, scene, art, rotation, animated, pulse)
            }
            if (safeArea) drawSafeArea(measurer)
        }
    }
}

/**
 * Where the Reels and TikTok buttons and captions cover a vertical video, and
 * an edge margin for the feed formats. Preview only, never in the video.
 */
private fun DrawScope.drawSafeArea(measurer: TextMeasurer) {
    val w = size.width; val h = size.height
    val vertical = h / w > 1.5f
    val top = h * if (vertical) 0.06f else 0.05f
    val bottom = h * if (vertical) 0.22f else 0.05f
    val left = w * if (vertical) 0.04f else 0.05f
    val right = w * if (vertical) 0.11f else 0.05f
    val iw = w - left - right; val ih = h - top - bottom
    if (iw <= 0 || ih <= 0) return
    val shade = Color.Black.copy(alpha = 0.34f)
    drawRect(shade, Offset.Zero, Size(w, top))
    drawRect(shade, Offset(0f, h - bottom), Size(w, bottom))
    drawRect(shade, Offset(0f, top), Size(left, ih))
    drawRect(shade, Offset(w - right, top), Size(right, ih))
    drawRect(
        C.Cyan.copy(alpha = 0.9f), Offset(left, top), Size(iw, ih),
        style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
    )
    drawText(
        measurer, "SAFE AREA",
        topLeft = Offset(left + 10f, top + 8f), style = mono(10.sp, C.Cyan.copy(alpha = 0.95f), 1.sp),
    )
}
