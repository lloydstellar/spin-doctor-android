package nl.lloydstellar.spindoctor.model

/*
 * Every option of the Style & Preview step on lloydstellar.nl/vinyl, with the
 * same ranges and defaults as appState in app.js. One Look is shared by all
 * tracks of a release; only the title differs per track (see Track).
 */

/**
 * Output formats. The website encodes 1080 px high (608x1080 for 9:16); the
 * app encodes 1080 px wide, which is what Reels, TikTok and the feed ask for.
 * Everything in a frame is laid out relative to its size, so the picture is
 * the same, only sharper.
 */
enum class Format(val label: String, val ratio: String, val platforms: String, val width: Int, val height: Int) {
    SQUARE("SQUARE", "1:1", "Instagram feed, Facebook", 1080, 1080),
    VERTICAL("VERTICAL", "9:16", "Stories, Reels, TikTok", 1080, 1920),
    PORTRAIT("PORTRAIT", "4:5", "Instagram feed", 1080, 1350);

    val aspect: Float get() = width.toFloat() / height
}

enum class Finish(val label: String) {
    BLACK("BLACK"), SOLID("COLOURED"), SPLATTER("SPLATTER"), MARBLE("MARBLE");

    val coloured: Boolean get() = this != BLACK
    val twoColours: Boolean get() = this == SPLATTER || this == MARBLE
}

enum class TextStyle(val label: String) { CLEAN("CLEAN"), TECHNO("TECHNO"), MONO("MONO") }

enum class TextPlace(val label: String) { BELOW("BELOW RECORD"), ABOVE("ABOVE RECORD") }

enum class BadgeType(val label: String) {
    OUT_NOW("OUT NOW"), NEW_SINGLE("NEW SINGLE"), NEW_EP("NEW EP"), CUSTOM("OWN TEXT"), DATE("RELEASE DATE")
}

/** Extra scale of the record at the loudest kick (BEAT_AMOUNTS in app.js). */
enum class BeatPulse(val label: String, val amount: Double) {
    OFF("OFF", 0.0), SUBTLE("SUBTLE", 0.012), MEDIUM("MEDIUM", 0.028), STRONG("STRONG", 0.05)
}

enum class BackgroundType { COLOR, IMAGE }

data class Look(
    val format: Format = Format.SQUARE,

    val backgroundType: BackgroundType = BackgroundType.COLOR,
    val backgroundColor: Int = 0xFF000000.toInt(),
    /** CSS pixels of blur on a frame 1080 high, 0..20 */
    val blur: Int = 5,

    /** percent, 70..150; 70 is the default size */
    val zoom: Int = 70,
    /** 15..80 */
    val rpm: Int = 33,
    val centerHole: Boolean = true,
    val finish: Finish = Finish.BLACK,
    val vinylColor: Int = 0xFFC81E3C.toInt(),
    val vinylColor2: Int = 0xFFF2EDE4.toInt(),

    val textEnabled: Boolean = false,
    val artist: String = "",
    val textStyle: TextStyle = TextStyle.CLEAN,
    val textPlace: TextPlace = TextPlace.BELOW,
    val textColor: Int = 0xFFFFFFFF.toInt(),

    val badgeEnabled: Boolean = false,
    val badgeType: BadgeType = BadgeType.OUT_NOW,
    val badgeText: String = "",
    /** day and month of the release date badge; the year is never shown */
    val badgeDay: Int = 0,
    val badgeMonth: Int = 0,
    val badgeColor: Int = 0xFFFFB000.toInt(),

    val spinUp: Boolean = false,
    /** how long the record takes to come up to speed, SPIN_UP_MIN..SPIN_UP_MAX */
    val spinUpSeconds: Float = 1.0f,
    val beatPulse: BeatPulse = BeatPulse.OFF,
) {
    /** The spin up in seconds, 0 when it is off. */
    val spinUpTime: Double get() = if (spinUp) spinUpSeconds.toDouble() else 0.0

    companion object {
        const val ZOOM_MIN = 70
        const val ZOOM_MAX = 150
        const val RPM_MIN = 15
        const val RPM_MAX = 80
        const val BLUR_MAX = 20
        const val SPIN_UP_MIN = 0.3f
        const val SPIN_UP_MAX = 6.0f
    }
}
