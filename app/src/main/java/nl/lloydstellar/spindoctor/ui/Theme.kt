package nl.lloydstellar.spindoctor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import nl.lloydstellar.spindoctor.render.Fonts

/** The LLOYDOS palette of lloydstellar.nl (styles.css, :root). */
object C {
    val Void = Color(0xFF01030C)
    val BgDeep = Color(0xFF020714)
    val Bg = Color(0xFF04102A)
    val Panel = Color(0xFF082040)
    val Cyan = Color(0xFF4DE0FF)
    val CyanBright = Color(0xFFCDF4FF)
    val CyanMid = Color(0xFF56B8D8)
    val CyanDim = Color(0xFF2A7898)
    val CyanDeep = Color(0xFF0F4868)
    val Violet = Color(0xFF8B5CF6)
    val Magenta = Color(0xFFFF4DDB)
    val Amber = Color(0xFFFFB000)
    val Red = Color(0xFFFF4D6D)
    val Green = Color(0xFF4DFF9A)
}

object Type {
    val orbitron by lazy { FontFamily(Typeface(Fonts.orbitron700)) }
    val orbitronHeavy by lazy { FontFamily(Typeface(Fonts.orbitron800)) }
    val mono by lazy { FontFamily(Typeface(Fonts.mono)) }
}

fun mono(size: TextUnit, color: Color = C.CyanMid, spacing: TextUnit = 1.sp) =
    TextStyle(fontFamily = Type.mono, fontSize = size, color = color, letterSpacing = spacing)

fun display(size: TextUnit, color: Color = C.CyanBright, spacing: TextUnit = 1.sp) =
    TextStyle(fontFamily = Type.orbitron, fontSize = size, color = color, letterSpacing = spacing)

/** The site's h1: white to cyan from top to bottom, a soft phosphor glow. */
fun h1(size: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = Type.orbitronHeavy, fontSize = size, letterSpacing = 1.sp, lineHeight = lineHeight,
    brush = Brush.verticalGradient(listOf(Color.White, C.CyanBright, C.Cyan)),
    shadow = Shadow(C.Cyan.copy(alpha = 0.35f), blurRadius = 14f),
)

@Composable
fun SpinDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Amber, onPrimary = C.Void, secondary = C.Cyan, tertiary = C.Violet,
            background = C.Void, surface = C.Bg, onSurface = C.CyanBright, onBackground = C.CyanBright,
            error = C.Red,
        ),
        content = content,
    )
}
