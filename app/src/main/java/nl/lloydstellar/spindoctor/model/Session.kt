package nl.lloydstellar.spindoctor.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.lloydstellar.spindoctor.audio.Peaks
import kotlin.math.max
import kotlin.math.min

enum class Mode { SINGLE, RELEASE }

enum class Step(val number: String, val label: String) {
    COVER("01", "COVER"), AUDIO("02", "AUDIO"), SEGMENT("03", "SEGMENT"), STYLE("04", "STYLE"), EXPORT("05", "EXPORT")
}

/*
 * The website caps these to spare the web server (8 tracks, 8 minutes, 30 s
 * clips, artwork up to 2048 px). The app works on the phone itself, so the
 * limits are only there to keep things sensible: a big album, a DJ mix, a
 * one minute Story. Artwork of any size is scaled down when it is read.
 */
const val MAX_TRACKS = 30
const val MAX_TRACK_SECONDS = 60 * 60
const val MIN_CLIP = 5.0
const val MAX_CLIP = 60.0
const val DEFAULT_CLIP = 30.0

/** One audio file and the part of it that goes into the video. */
class Track(val uri: Uri, val fileName: String, val duration: Double) {
    var peaks by mutableStateOf<Peaks?>(null)
    var readError by mutableStateOf<String?>(null)

    /** the selected segment, in seconds */
    var start by mutableDoubleStateOf(0.0)
        private set
    var length by mutableDoubleStateOf(min(DEFAULT_CLIP, duration))
        private set
    val end: Double get() = start + length

    /** set once the user confirmed the segment of this track */
    var segmentChosen by mutableStateOf(false)

    /** The title on screen. Starts from the file name, the user can change it. */
    var title by mutableStateOf(splitName(fileName).second)

    /** "Artist - Title.wav" names the artist too; used when the artist field is still empty. */
    val artistInName: String? = splitName(fileName).first

    val baseName: String get() = fileName.substringBeforeLast('.', fileName)

    /** Moves and resizes the segment within the clip limits and the track (enforceRegionLimits). */
    fun setSegment(newStart: Double, newLength: Double) {
        val len = newLength.coerceIn(min(MIN_CLIP, duration), min(MAX_CLIP, duration))
        length = len
        start = newStart.coerceIn(0.0, max(0.0, duration - len))
    }

    companion object {
        /**
         * The website stops at the title below; file names like
         * "03 Lloyd Stellar - Dystopia.wav" also carry the artist, so that is
         * split off: ("Lloyd Stellar", "Dystopia").
         */
        fun splitName(fileName: String): Pair<String?, String> {
            val title = defaultTitle(fileName)
            val at = title.indexOf(" - ")
            if (at <= 0 || at + 3 >= title.length) return null to title
            /* "03 Lloyd Stellar": the website leaves a track number followed by a space */
            val artist = title.substring(0, at).replace(Regex("^\\d+\\s+"), "").trim()
            return artist.ifEmpty { null } to title.substring(at + 3).trim()
        }

        /** defaultTitleFromFilename() in app.js */
        fun defaultTitle(fileName: String): String = fileName
            .replace(Regex("\\.[^.]+$"), "")
            .replace(Regex("^\\s*\\d+\\s*[-._)\\]]\\s*"), "")
            .replace(Regex("_+"), " ")
            .trim()
    }
}

/** Everything chosen in one run through the five steps. */
class Session {
    var mode by mutableStateOf(Mode.SINGLE)
    var step by mutableStateOf(Step.COVER)

    /** the picked artwork before cropping, and the label cut from it */
    var artwork by mutableStateOf<Bitmap?>(null)
    var cover by mutableStateOf<Bitmap?>(null)

    val tracks = mutableStateListOf<Track>()
    var segmentIndex by mutableIntStateOf(0)
    var previewIndex by mutableIntStateOf(0)

    var backgroundImage by mutableStateOf<Bitmap?>(null)

    /** how far the user got; the rail lets them jump back to any of these */
    fun reachable(s: Step): Boolean = when (s) {
        Step.COVER -> true
        Step.AUDIO -> cover != null
        Step.SEGMENT -> cover != null && tracks.isNotEmpty() && tracks.all { it.peaks != null }
        Step.STYLE, Step.EXPORT -> cover != null && tracks.isNotEmpty() && tracks.all { it.segmentChosen }
    }

    fun previewTrack(): Track? = tracks.getOrNull(previewIndex.coerceIn(0, max(0, tracks.size - 1)))
}
