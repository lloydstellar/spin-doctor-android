package nl.lloydstellar.spindoctor.render

import android.content.Context
import android.graphics.Typeface

/**
 * The typefaces of the website, loaded once. Orbitron is a variable font; the
 * site uses it at 700 for headings and the techno text style. Share Tech Mono
 * is the site's body font. The clean style uses "Helvetica Neue", Helvetica,
 * Arial on the website, which Android does not ship, so it falls back to the
 * system sans, as a browser on Android does.
 */
object Fonts {
    lateinit var orbitron400: Typeface; private set
    lateinit var orbitron500: Typeface; private set
    lateinit var orbitron700: Typeface; private set
    lateinit var orbitron800: Typeface; private set
    lateinit var mono: Typeface; private set
    val sans700: Typeface = Typeface.create(Typeface.SANS_SERIF, 700, false)
    val sans500: Typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)

    @Volatile private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val assets = ctx.applicationContext.assets
            fun orbitron(weight: Int) = Typeface.Builder(assets, "fonts/orbitron.ttf")
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build()
            orbitron400 = orbitron(400)
            orbitron500 = orbitron(500)
            orbitron700 = orbitron(700)
            orbitron800 = orbitron(800)
            mono = Typeface.createFromAsset(assets, "fonts/share_tech_mono.ttf")
            loaded = true
        }
    }
}
