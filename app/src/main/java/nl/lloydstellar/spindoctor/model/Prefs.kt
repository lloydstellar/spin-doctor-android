package nl.lloydstellar.spindoctor.model

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The last used style, kept on the phone. The website only remembers the
 * artist name; an app gets used for release after release with the same look,
 * so here the whole style is remembered. Images are not stored.
 */
class Prefs(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("spindoctor", Context.MODE_PRIVATE)

    fun loadLook(): Look {
        val raw = prefs.getString("look", null) ?: return Look()
        return try {
            val j = JSONObject(raw)
            val d = Look()
            Look(
                format = enumOr(j.optString("format"), d.format),
                backgroundType = enumOr(j.optString("backgroundType"), d.backgroundType),
                backgroundColor = j.optInt("backgroundColor", d.backgroundColor),
                blur = j.optInt("blur", d.blur).coerceIn(0, Look.BLUR_MAX),
                zoom = j.optInt("zoom", d.zoom).coerceIn(Look.ZOOM_MIN, Look.ZOOM_MAX),
                rpm = j.optInt("rpm", d.rpm).coerceIn(Look.RPM_MIN, Look.RPM_MAX),
                centerHole = j.optBoolean("centerHole", d.centerHole),
                finish = enumOr(j.optString("finish"), d.finish),
                vinylColor = j.optInt("vinylColor", d.vinylColor),
                vinylColor2 = j.optInt("vinylColor2", d.vinylColor2),
                textEnabled = j.optBoolean("textEnabled", d.textEnabled),
                artist = j.optString("artist", d.artist),
                textStyle = enumOr(j.optString("textStyle"), d.textStyle),
                textPlace = enumOr(j.optString("textPlace"), d.textPlace),
                textColor = j.optInt("textColor", d.textColor),
                badgeEnabled = j.optBoolean("badgeEnabled", d.badgeEnabled),
                badgeType = enumOr(j.optString("badgeType"), d.badgeType),
                badgeText = j.optString("badgeText", d.badgeText),
                badgeColor = j.optInt("badgeColor", d.badgeColor),
                spinUp = j.optBoolean("spinUp", d.spinUp),
                spinUpSeconds = j.optDouble("spinUpSeconds", d.spinUpSeconds.toDouble()).toFloat()
                    .coerceIn(Look.SPIN_UP_MIN, Look.SPIN_UP_MAX),
                beatPulse = enumOr(j.optString("beatPulse"), d.beatPulse),
            ).withNextFridayIfDate()
        } catch (e: Exception) {
            Look()
        }
    }

    /** The release date is left out on purpose: next time it is a different release. */
    fun saveLook(look: Look) {
        val j = JSONObject()
            .put("format", look.format.name)
            .put("backgroundType", look.backgroundType.name)
            .put("backgroundColor", look.backgroundColor)
            .put("blur", look.blur)
            .put("zoom", look.zoom)
            .put("rpm", look.rpm)
            .put("centerHole", look.centerHole)
            .put("finish", look.finish.name)
            .put("vinylColor", look.vinylColor)
            .put("vinylColor2", look.vinylColor2)
            .put("textEnabled", look.textEnabled)
            .put("artist", look.artist)
            .put("textStyle", look.textStyle.name)
            .put("textPlace", look.textPlace.name)
            .put("textColor", look.textColor)
            .put("badgeEnabled", look.badgeEnabled)
            .put("badgeType", look.badgeType.name)
            .put("badgeText", look.badgeText)
            .put("badgeColor", look.badgeColor)
            .put("spinUp", look.spinUp)
            .put("spinUpSeconds", look.spinUpSeconds.toDouble())
            .put("beatPulse", look.beatPulse.name)
        prefs.edit().putString("look", j.toString()).apply()
    }

    /** The date itself is not remembered: a date badge starts at the coming Friday again. */
    private fun Look.withNextFridayIfDate(): Look {
        if (badgeType != BadgeType.DATE) return this
        val friday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        return copy(badgeDay = friday.dayOfMonth, badgeMonth = friday.monthValue)
    }

    var askedForNotifications: Boolean
        get() = prefs.getBoolean("askedNotifications", false)
        set(v) = prefs.edit().putBoolean("askedNotifications", v).apply()

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
