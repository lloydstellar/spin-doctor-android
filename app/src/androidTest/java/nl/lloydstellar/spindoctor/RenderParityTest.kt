package nl.lloydstellar.spindoctor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.lloydstellar.spindoctor.model.BadgeType
import nl.lloydstellar.spindoctor.model.Finish
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.TextPlace
import nl.lloydstellar.spindoctor.model.TextStyle
import nl.lloydstellar.spindoctor.render.DiscArt
import nl.lloydstellar.spindoctor.render.DiscKey
import nl.lloydstellar.spindoctor.render.Fonts
import nl.lloydstellar.spindoctor.render.Scene
import nl.lloydstellar.spindoctor.render.VinylRenderer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders tools/parity/scenes.json with the app's renderer, for side by side
 * comparison with the same scenes rendered by the website's app.js
 * (tools/parity/render-web.sh). Output: Android/data/<app>/files/parity.
 */
@RunWith(AndroidJUnit4::class)
class RenderParityTest {

    @Test
    fun renderScenes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        Fonts.init(ctx)
        val assets = inst.context.assets
        val cover = assets.open("cover.png").use { BitmapFactory.decodeStream(it) }
        val scenes = JSONArray(assets.open("scenes.json").bufferedReader().readText())
        val dir = File(ctx.getExternalFilesDir(null), "parity").apply { mkdirs() }
        for (i in 0 until scenes.length()) {
            val sc = scenes.getJSONObject(i)
            val w = sc.getInt("w"); val h = sc.getInt("h")
            val state = sc.getJSONObject("state")
            val look = lookOf(state)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val art = DiscArt.bake(DiscKey.of(look, VinylRenderer.radiusFor(w, h, look, 0.0)))
            val scene = Scene(look, cover, null, state.optString("trackTitle", ""))
            val started = System.nanoTime()
            VinylRenderer.render(Canvas(bmp), w, h, scene, art, sc.getDouble("rotation"), sc.getBoolean("animated"), sc.getDouble("pulse"))
            val ms = (System.nanoTime() - started) / 1e6
            File(dir, "${sc.getString("name")}-app.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            android.util.Log.i("Parity", "${sc.getString("name")}: one frame in %.1f ms".format(ms))
        }
    }

    private fun lookOf(s: JSONObject): Look {
        val d = Look()
        fun color(key: String, fallback: Int) = if (s.has(key)) Color.parseColor(s.getString(key)) else fallback
        return d.copy(
            finish = when (s.optString("vinylFinish")) {
                "solid" -> Finish.SOLID; "splatter" -> Finish.SPLATTER; "marble" -> Finish.MARBLE; else -> Finish.BLACK
            },
            vinylColor = color("vinylColor", d.vinylColor),
            vinylColor2 = color("vinylColor2", d.vinylColor2),
            zoom = s.optInt("vinylZoom", d.zoom),
            backgroundColor = color("backgroundColor", d.backgroundColor),
            centerHole = s.optBoolean("showCenterHole", d.centerHole),
            textEnabled = s.optBoolean("textEnabled", d.textEnabled),
            artist = s.optString("artistName", d.artist),
            textStyle = when (s.optString("textStyle")) { "techno" -> TextStyle.TECHNO; "mono" -> TextStyle.MONO; else -> TextStyle.CLEAN },
            textPlace = if (s.optString("textPosition") == "above") TextPlace.ABOVE else TextPlace.BELOW,
            textColor = color("textColor", d.textColor),
            badgeEnabled = s.optBoolean("badgeEnabled", d.badgeEnabled),
            badgeType = when (s.optString("badgeType")) {
                "newsingle" -> BadgeType.NEW_SINGLE; "newep" -> BadgeType.NEW_EP; "custom" -> BadgeType.CUSTOM; "date" -> BadgeType.DATE
                else -> BadgeType.OUT_NOW
            },
            badgeText = s.optString("badgeText", d.badgeText),
            badgeColor = color("badgeColor", d.badgeColor),
        )
    }
}
