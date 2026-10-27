package app.gibiflix.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.BuildConfig
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.device.Profile
import app.gibiflix.core.device.ProfileChoice
import app.gibiflix.ui.components.RadioRow
import app.gibiflix.ui.components.SectionTitle
import app.gibiflix.ui.components.SettingRow
import app.gibiflix.ui.components.TopBar
import kotlinx.coroutines.launch

@Composable
fun profileLabel(p: Profile): String = stringResource(
    when (p) {
        Profile.TABLET -> R.string.profile_tablet
        Profile.EINK_COLOR -> R.string.profile_eink_color
        Profile.EINK_MONO -> R.string.profile_eink_mono
    }
)

@Composable
fun SettingsScreen(onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val state by container.deviceProfile.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showProfile by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = null)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_section_device))
            val choiceText = when (state.choice) {
                ProfileChoice.AUTO -> stringResource(R.string.profile_auto_with, profileLabel(state.profile))
                else -> profileLabel(state.profile)
            }
            SettingRow(stringResource(R.string.settings_profile), choiceText, onClick = { showProfile = true })
            SettingRow(
                stringResource(R.string.settings_diagnostics),
                stringResource(R.string.settings_diagnostics_hint),
                onClick = onOpenDiagnostics,
            )

            SectionTitle(stringResource(R.string.settings_about))
            SettingRow(stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            SettingRow(stringResource(R.string.settings_licenses), onClick = { showLicenses = true })
        }
    }

    if (showProfile) {
        ProfileChoiceDialog(
            current = state.choice,
            detected = container.deviceProfile.detected,
            onDismiss = { showProfile = false },
            onChoose = { choice ->
                showProfile = false
                scope.launch { container.settings.setProfileChoice(choice) }
            },
        )
    }
    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun ProfileChoiceDialog(
    current: ProfileChoice,
    detected: Profile,
    onDismiss: () -> Unit,
    onChoose: (ProfileChoice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile)) },
        text = {
            Column {
                RadioRow(stringResource(R.string.profile_auto_with, profileLabel(detected)), current == ProfileChoice.AUTO) { onChoose(ProfileChoice.AUTO) }
                RadioRow(stringResource(R.string.profile_tablet), current == ProfileChoice.TABLET) { onChoose(ProfileChoice.TABLET) }
                RadioRow(stringResource(R.string.profile_eink_color), current == ProfileChoice.EINK_COLOR) { onChoose(ProfileChoice.EINK_COLOR) }
                RadioRow(stringResource(R.string.profile_eink_mono), current == ProfileChoice.EINK_MONO) { onChoose(ProfileChoice.EINK_MONO) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
                "== $name ==\n" + context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            }
        }.getOrDefault("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** Primeiro uso num Onyx: "A tela dele é colorida ou preto e branco?". */
@Composable
fun OnyxConfirmDialog(suggested: Profile) {
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(if (suggested == Profile.EINK_COLOR) Profile.EINK_COLOR else Profile.EINK_MONO) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.onyx_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.onyx_dialog_text), style = MaterialTheme.typography.bodyLarge)
                RadioRow(stringResource(R.string.onyx_dialog_color), choice == Profile.EINK_COLOR) { choice = Profile.EINK_COLOR }
                RadioRow(stringResource(R.string.onyx_dialog_mono), choice == Profile.EINK_MONO) { choice = Profile.EINK_MONO }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { container.settings.setConfirmedProfile(choice) } }) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
