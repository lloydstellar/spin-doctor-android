package nl.lloydstellar.spindoctor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.lloydstellar.spindoctor.BuildConfig
import nl.lloydstellar.spindoctor.export.Gallery

@Composable
fun AboutScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTitle("ABOUT", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Text("Lloyd's Spin Doctor ${BuildConfig.VERSION_NAME}", style = mono(16.sp, C.CyanBright, 1.sp))
            Spacer(Modifier.height(8.dp))
            Body(
                "Spin Doctor started as a free tool on my website. This is the same tool for your phone: the same record, " +
                    "the same options and the same video. The website sends the frames to my server to be encoded; " +
                    "the app does all of it on the phone."
            )
            Body(
                "I'm Lloyd Stellar, an electro producer from Hilversum. On lloydstellar.nl you'll find more free " +
                    "music tools, games and my music."
            )
            Spacer(Modifier.height(10.dp))
            CommandButton("LLOYDSTELLAR.NL", Modifier.fillMaxWidth(), primary = true) { onOpen("https://lloydstellar.nl/") }
            Spacer(Modifier.height(10.dp))
            CommandButton("LISTEN ON BANDCAMP", Modifier.fillMaxWidth(), color = C.Cyan) { onOpen("https://lloydstellar.bandcamp.com/") }

            Spacer(Modifier.height(18.dp))
            DashedRule()
            Heading("YOUR VIDEOS")
            Body("Every video is saved to Movies/${Gallery.FOLDER} on this phone, so it shows up in your gallery and in the picker of Instagram and TikTok.")
            Heading("PRIVACY")
            Body("No ads, no account, no tracking. The app has no internet access at all. Your artwork and music never leave this phone.")
            Heading("CREDITS")
            Body(
                "Made by Lloyd Stellar. Fonts: Orbitron by Matt McInerney and Share Tech Mono by Carrois Type Design, " +
                    "both under the SIL Open Font License."
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = mono(13.sp, C.Cyan, 2.sp), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, style = mono(14.sp, C.CyanMid, 0.sp), modifier = Modifier.padding(bottom = 10.dp))
}
