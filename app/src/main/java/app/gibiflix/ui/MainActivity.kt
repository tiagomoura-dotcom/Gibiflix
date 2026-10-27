package app.gibiflix.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.container
import app.gibiflix.ui.theme.GibiflixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceProfile = container.deviceProfile
        setContent {
            val state by deviceProfile.state.collectAsStateWithLifecycle()
            GibiflixTheme(state.profile) {
                AppRoot()
            }
        }
    }
}
