package app.gibiflix.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.gibiflix.core.device.Profile
import app.gibiflix.core.device.ProfileChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val profileChoice: ProfileChoice = ProfileChoice.AUTO,
    /** Resposta do diálogo do primeiro uso num Onyx (null = ainda não respondeu). */
    val confirmedProfile: Profile? = null,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val PROFILE_CHOICE = stringPreferencesKey("profile_choice")
        val CONFIRMED_PROFILE = stringPreferencesKey("confirmed_profile")
    }

    val dataStore: DataStore<Preferences> get() = context.dataStore

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            profileChoice = p[Keys.PROFILE_CHOICE]?.let { runCatching { ProfileChoice.valueOf(it) }.getOrNull() }
                ?: ProfileChoice.AUTO,
            confirmedProfile = p[Keys.CONFIRMED_PROFILE]?.let { runCatching { Profile.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun setProfileChoice(choice: ProfileChoice) {
        context.dataStore.edit { it[Keys.PROFILE_CHOICE] = choice.name }
    }

    suspend fun setConfirmedProfile(profile: Profile) {
        context.dataStore.edit { it[Keys.CONFIRMED_PROFILE] = profile.name }
    }
}
