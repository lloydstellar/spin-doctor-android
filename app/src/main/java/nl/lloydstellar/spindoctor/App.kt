package nl.lloydstellar.spindoctor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.lloydstellar.spindoctor.audio.ClipPlayer
import nl.lloydstellar.spindoctor.audio.PcmPlayer
import nl.lloydstellar.spindoctor.model.Look
import nl.lloydstellar.spindoctor.model.Prefs
import nl.lloydstellar.spindoctor.model.Session
import nl.lloydstellar.spindoctor.render.Fonts

/* Process-wide state: one style, one session, one player. */
object App {
    lateinit var prefs: Prefs
        private set
    lateinit var player: ClipPlayer
        private set
    /** the style preview: the clip's sound as it goes into the video */
    lateinit var pcmPlayer: PcmPlayer
        private set

    var session by mutableStateOf(Session())

    /** The style, remembered between runs. */
    var look by mutableStateOf(Look())
        private set

    fun updateLook(change: (Look) -> Look) {
        look = change(look)
        prefs.saveLook(look)
    }

    fun newSession() {
        player.stop()
        pcmPlayer.stop()
        session = Session()
    }

    internal fun init(app: Application) {
        Fonts.init(app)
        prefs = Prefs(app)
        player = ClipPlayer(app)
        pcmPlayer = PcmPlayer(app)
        look = prefs.loadLook()
    }
}

class SpinDoctorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.init(this)
    }
}
