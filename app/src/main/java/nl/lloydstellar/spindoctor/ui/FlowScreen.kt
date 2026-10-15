package nl.lloydstellar.spindoctor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.spindoctor.App
import nl.lloydstellar.spindoctor.model.Mode
import nl.lloydstellar.spindoctor.model.Step

/** The five steps of the website, one screen each, with the progress rail on top. */
@Composable
fun FlowScreen(onExit: () -> Unit, onNew: () -> Unit) {
    val s = App.session
    fun go(step: Step) {
        App.player.stop()
        App.pcmPlayer.stop()
        s.step = step
    }
    BackHandler {
        if (s.step == Step.COVER) onExit() else go(Step.entries[s.step.ordinal - 1])
    }

    val title = when (s.step) {
        Step.COVER -> "ALBUM COVER"
        Step.AUDIO -> if (s.mode == Mode.RELEASE) "RELEASE" else "AUDIO"
        Step.SEGMENT -> "SEGMENT"
        Step.STYLE -> "STYLE"
        Step.EXPORT -> "EXPORT"
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTitle(title, onBack = { if (s.step == Step.COVER) onExit() else go(Step.entries[s.step.ordinal - 1]) })
        StepRail(s.step, s::reachable) { go(it) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                s.step,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "step",
            ) { step ->
                when (step) {
                    Step.COVER -> CoverStep(next = { go(Step.AUDIO) })
                    Step.AUDIO -> AudioStep(next = { go(Step.SEGMENT) })
                    Step.SEGMENT -> SegmentStep(next = { go(Step.STYLE) })
                    Step.STYLE -> StyleStep(next = { go(Step.EXPORT) })
                    Step.EXPORT -> ExportStep(onRestyle = { go(Step.STYLE) }, onNew = onNew)
                }
            }
        }
    }
}

/** A step: scrolling content and the action pinned at the bottom. */
@Composable
fun StepLayout(
    scroll: Boolean = true,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp)) { bottom() }
    }
}

/**
 * The website's dropzone: dashed cyan edge, faint diagonal stripes, corner
 * brackets, a line of text and the button that opens the picker.
 */
@Composable
fun Dropzone(text: String, button: String, note: String?, onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                drawRect(Color(0xB801030C))
                val stripe = C.Cyan.copy(alpha = 0.03f)
                val step = 20.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), 10.dp.toPx())
                    x += step
                }
                drawRect(C.CyanDim, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                val l = 14.dp.toPx(); val w = 2.dp.toPx()
                drawRect(C.CyanDim, Offset.Zero, Size(l, w)); drawRect(C.CyanDim, Offset.Zero, Size(w, l))
                drawRect(C.CyanDim, Offset(size.width - l, size.height - w), Size(l, w))
                drawRect(C.CyanDim, Offset(size.width - w, size.height - l), Size(w, l))
            }
            .padding(horizontal = 18.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = mono(14.sp, C.CyanMid, 0.5.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        CommandButton(button, onClick = onPick)
        if (note != null) {
            Spacer(Modifier.height(14.dp))
            Text(note, style = mono(12.sp, C.CyanMid, 1.sp), textAlign = TextAlign.Center)
        }
    }
}

/** A problem with a file, in the site's red. */
@Composable
fun Notice(text: String) {
    Text("! $text", style = mono(13.sp, C.Red, 0.sp), modifier = Modifier.padding(vertical = 4.dp))
}
