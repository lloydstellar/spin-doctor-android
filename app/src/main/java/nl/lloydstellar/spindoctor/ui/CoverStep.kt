package nl.lloydstellar.spindoctor.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.model.Images
import kotlin.math.min
import kotlin.math.roundToInt

/** Step 1: pick the artwork and cut the circle that becomes the label. */
@Composable
fun CoverStep(next: () -> Unit) {
    val s = App.session
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropping by remember { mutableStateOf(s.artwork != null && s.cover == null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try {
                s.artwork = Images.load(ctx, uri)
                error = null
                cropping = true
            } catch (e: Exception) {
                error = "This image can't be opened. Try a JPG or PNG."
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val artwork = s.artwork
    if (cropping && artwork != null) {
        Cropper(
            artwork,
            onCancel = { cropping = false; if (s.cover == null) s.artwork = null },
            onApply = { s.cover = it; cropping = false },
        )
        return
    }

    StepLayout(bottom = {
        CommandButton("NEXT: AUDIO", Modifier.fillMaxWidth(), primary = s.cover != null, enabled = s.cover != null, onClick = next)
    }) {
        val cover = s.cover
        if (cover == null) {
            Hint("Square artwork works best. The circle you choose becomes the label in the middle of the record.")
            Spacer(Modifier.height(8.dp))
            Dropzone("Pick the artwork of your release", "CHOOSE IMAGE", "Large images are scaled down to 2048 px", pick)
        } else {
            Spacer(Modifier.height(10.dp))
            Image(
                cover.asImageBitmap(), "Your label",
                Modifier.align(Alignment.CenterHorizontally).size(220.dp).clip(CircleShape),
                filterQuality = FilterQuality.High,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommandButton("RECROP", Modifier.weight(1f), color = C.Cyan, height = 48.dp) { cropping = true }
                CommandButton("OTHER IMAGE", Modifier.weight(1f), color = C.Cyan, height = 48.dp, onClick = pick)
            }
        }
        error?.let { Spacer(Modifier.height(10.dp)); Notice(it) }
    }
}

/**
 * The crop circle of the website: the image darkened outside a cyan circle.
 * Drag to move it, pinch or use the slider to size it (50 to 100 percent of
 * the short side, as on the site).
 */
@Composable
private fun Cropper(image: Bitmap, onCancel: () -> Unit, onApply: (Bitmap) -> Unit) {
    val maxRadius = min(image.width, image.height) / 2f
    var cx by remember(image) { mutableFloatStateOf(image.width / 2f) }
    var cy by remember(image) { mutableFloatStateOf(image.height / 2f) }
    var radius by remember(image) { mutableFloatStateOf(maxRadius) }
    val bitmap = remember(image) { image.asImageBitmap() }

    fun clampCentre() {
        cx = cx.coerceIn(radius, image.width - radius)
        cy = cy.coerceIn(radius, image.height - radius)
    }

    StepLayout(scroll = false, bottom = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandButton("CANCEL", Modifier.weight(1f), color = C.Cyan, onClick = onCancel)
            CommandButton("APPLY", Modifier.weight(1f), primary = true) { onApply(Images.cropCircle(image, cx, cy, radius)) }
        }
    }) {
        Hint("Drag the circle over the part of the artwork you want on the label. Pinch to resize.")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val aspect = image.width.toFloat() / image.height
            val fitWidth = min(maxWidth.value, maxHeight.value * aspect)
            Canvas(
                Modifier
                    .width(fitWidth.dp)
                    .aspectRatio(aspect)
                    .semantics { contentDescription = "Artwork with crop circle" }
                    .pointerInput(image) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val scale = image.width / size.width.toFloat()
                            radius = (radius * zoom).coerceIn(maxRadius * 0.5f, maxRadius)
                            cx += pan.x * scale
                            cy += pan.y * scale
                            clampCentre()
                        }
                    }
            ) {
                val scale = size.width / image.width
                drawImage(
                    bitmap, IntOffset.Zero, IntSize(image.width, image.height),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                val centre = Offset(cx * scale, cy * scale)
                val r = radius * scale
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(centre, r))
                }
                drawPath(shade, Color.Black.copy(alpha = 0.6f))
                drawCircle(Color(0xFF00E5FF), r, centre, style = Stroke(2.dp.toPx()))
            }
        }
        Spacer(Modifier.height(10.dp))
        TermSlider(
            "Circle size", (radius / maxRadius * 100).roundToInt(), 50..100, { "$it%" },
        ) { pct ->
            radius = maxRadius * pct / 100f
            clampCentre()
        }
        Text("${image.width} × ${image.height} PX", style = mono(11.sp, C.CyanDim, 1.sp), modifier = Modifier.padding(top = 2.dp))
    }
}
