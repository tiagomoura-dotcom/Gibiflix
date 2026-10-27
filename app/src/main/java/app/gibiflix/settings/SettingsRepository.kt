package app.gibiflix.settings

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.gibiflix.core.device.Profile
import app.gibiflix.core.device.ProfileChoice
import app.gibiflix.core.settings.ProfileSettings
import app.gibiflix.core.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val profileChoice: ProfileChoice = ProfileChoice.AUTO,
    /** Resposta do diálogo do primeiro uso num Onyx (null = ainda não respondeu). */
    val confirmedProfile: Profile? = null,
    val user: UserSettings = UserSettings(),
    val deviceId: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val PROFILE_CHOICE = stringPreferencesKey("profile_choice")
        val CONFIRMED_PROFILE = stringPreferencesKey("confirmed_profile")
        val USER = stringPreferencesKey("user_settings")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            profileChoice = p[Keys.PROFILE_CHOICE]?.let { runCatching { ProfileChoice.valueOf(it) }.getOrNull() }
                ?: ProfileChoice.AUTO,
            confirmedProfile = p[Keys.CONFIRMED_PROFILE]?.let { runCatching { Profile.valueOf(it) }.getOrNull() },
            user = UserSettings.decode(p[Keys.USER]),
            deviceId = p[Keys.DEVICE_ID].orEmpty(),
        )
    }

    val user: Flow<UserSettings> = settings.map { it.user }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setProfileChoice(choice: ProfileChoice) {
        context.dataStore.edit { it[Keys.PROFILE_CHOICE] = choice.name }
    }

    suspend fun setConfirmedProfile(profile: Profile) {
        context.dataStore.edit { it[Keys.CONFIRMED_PROFILE] = profile.name }
    }

    suspend fun updateUser(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { p ->
            p[Keys.USER] = UserSettings.encode(transform(UserSettings.decode(p[Keys.USER])))
        }
    }

    suspend fun updateProfile(profile: Profile, transform: (ProfileSettings) -> ProfileSettings) =
        updateUser { u -> u.withProfile(profile, transform(u.forProfile(profile))) }

    suspend fun resetProfile(profile: Profile) =
        updateUser { u -> u.copy(profiles = u.profiles - profile.name) }

    /** Identificador estável deste aparelho (gerado na primeira vez). */
    suspend fun deviceId(): String {
        val existing = current().deviceId
        if (existing.isNotEmpty()) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { if (it[Keys.DEVICE_ID].isNullOrEmpty()) it[Keys.DEVICE_ID] = id }
        return current().deviceId
    }

    suspend fun deviceName(): String = current().user.deviceName?.takeIf { it.isNotBlank() } ?: defaultDeviceName()

    companion object {
        fun defaultDeviceName(): String {
            val model = Build.MODEL.orEmpty()
            val man = Build.MANUFACTURER.orEmpty()
            return if (model.startsWith(man, ignoreCase = true)) model else "$man $model".trim()
        }
    }
}
