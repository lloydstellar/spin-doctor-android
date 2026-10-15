package nl.lloydstellar.spindoctor

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import nl.lloydstellar.spindoctor.ui.AboutScreen
import nl.lloydstellar.spindoctor.ui.FlowScreen
import nl.lloydstellar.spindoctor.ui.HomeScreen
import nl.lloydstellar.spindoctor.ui.SiteBackground
import nl.lloydstellar.spindoctor.ui.SpinDoctorTheme

enum class Screen { HOME, FLOW, ABOUT }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        /* light icons on the dark site, also with three-button navigation */
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        setContent { SpinDoctorTheme { Root() } }
    }

    override fun onStop() {
        /* no music from an app you are not looking at */
        App.player.stop()
        App.pcmPlayer.stop()
        super.onStop()
    }

    private fun open(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) { }
    }

    @Composable
    private fun Root() {
        /* not saveable on purpose: after the process was gone the session is empty, so start at home */
        var screen by remember { mutableStateOf(Screen.HOME) }
        if (screen == Screen.ABOUT) BackHandler { screen = Screen.HOME }

        Box(Modifier.fillMaxSize()) {
            SiteBackground()
            AnimatedContent(
                screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> HomeScreen(
                        onStart = { mode -> App.newSession(); App.session.mode = mode; screen = Screen.FLOW },
                        onContinue = { screen = Screen.FLOW },
                        onAbout = { screen = Screen.ABOUT },
                        onWebsite = { open("https://lloydstellar.nl/") },
                    )
                    Screen.FLOW -> FlowScreen(onExit = { screen = Screen.HOME }, onNew = { App.newSession(); screen = Screen.HOME })
                    Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME }, onOpen = { open(it) })
                }
            }
        }
    }
}
