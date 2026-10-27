package app.gibiflix.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import app.gibiflix.core.settings.PrepareOnImport
import app.gibiflix.core.settings.ProfileSettings
import app.gibiflix.core.settings.ReadMode
import app.gibiflix.core.settings.ReaderSettings
import app.gibiflix.core.settings.UserSettings
import app.gibiflix.settings.SettingsRepository
import app.gibiflix.ui.components.ChoiceRow
import app.gibiflix.ui.components.RadioRow
import app.gibiflix.ui.components.SectionTitle
import app.gibiflix.ui.components.SettingRow
import app.gibiflix.ui.components.StepperRow
import app.gibiflix.ui.components.SwitchRow
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

class SettingsRoutes(
    val diagnostics: () -> Unit,
    val image: () -> Unit,
    val servers: () -> Unit,
    val stats: () -> Unit,
    val backup: () -> Unit,
    val bookmarks: () -> Unit,
)

@Composable
fun SettingsScreen(routes: SettingsRoutes) {
    val context = LocalContext.current
    val container = context.container
    val state by container.deviceProfile.state.collectAsStateWithLifecycle()
    val user by container.settings.user.collectAsStateWithLifecycle(UserSettings())
    val scope = rememberCoroutineScope()
    var showProfile by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    val profile = state.profile
    val ps = user.forProfile(profile)
    fun updateUser(f: (UserSettings) -> UserSettings) = scope.launch { container.settings.updateUser(f) }
    fun updateProfile(f: (ProfileSettings) -> ProfileSettings) = scope.launch { container.settings.updateProfile(profile, f) }
    fun reader(f: (ReaderSettings) -> ReaderSettings) = updateUser { it.copy(reader = f(it.reader)) }

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
                stringResource(R.string.settings_device_name),
                user.deviceName?.takeIf { it.isNotBlank() } ?: SettingsRepository.defaultDeviceName(),
                onClick = { editName = true },
            )
            SettingRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_hint), onClick = routes.diagnostics)

            SectionTitle(stringResource(R.string.settings_section_image))
            SettingRow(stringResource(R.string.settings_image), stringResource(R.string.settings_image_hint, profileLabel(profile)), onClick = routes.image)

            if (profile.isEink) {
                SectionTitle(stringResource(R.string.settings_section_eink))
                StepperRow(
                    stringResource(R.string.settings_full_refresh), ps.fullRefreshEvery, 0..30,
                    format = { if (it == 0) context.getString(R.string.never) else context.getString(R.string.every_n, it) },
                ) { v -> updateProfile { it.copy(fullRefreshEvery = v) } }
                SwitchRow(stringResource(R.string.settings_flash), ps.flashClean, stringResource(R.string.settings_flash_hint)) { v ->
                    updateProfile { it.copy(flashClean = v) }
                }
            }

            SectionTitle(stringResource(R.string.settings_section_reading))
            ChoiceRow(
                stringResource(R.string.settings_default_mode),
                listOf(ReadMode.PANEL to stringResource(R.string.mode_panel), ReadMode.PAGE to stringResource(R.string.mode_page)),
                user.reader.defaultMode,
            ) { v -> reader { it.copy(defaultMode = v) } }
            SwitchRow(stringResource(R.string.settings_page_before), user.reader.pageBeforePanels) { v -> reader { it.copy(pageBeforePanels = v) } }
            SwitchRow(stringResource(R.string.settings_page_after), user.reader.pageAfterPanels) { v -> reader { it.copy(pageAfterPanels = v) } }
            SwitchRow(stringResource(R.string.settings_split_wide), ps.splitWidePanels, stringResource(R.string.settings_split_wide_hint)) { v ->
                updateProfile { it.copy(splitWidePanels = v) }
            }
            StepperRow(stringResource(R.string.settings_margin), user.reader.panelMarginPct, 0..5, format = { "$it%" }) { v ->
                reader { it.copy(panelMarginPct = v) }
            }
            StepperRow(stringResource(R.string.settings_max_zoom), user.reader.maxZoom.toInt(), 1..5, format = { "${it}×" }) { v ->
                reader { it.copy(maxZoom = v.toFloat()) }
            }
            SwitchRow(stringResource(R.string.settings_tap_zones), user.reader.tapZones, stringResource(R.string.settings_tap_zones_hint)) { v ->
                reader { it.copy(tapZones = v) }
            }
            ChoiceRow(
                stringResource(R.string.settings_default_direction),
                listOf("ltr" to stringResource(R.string.direction_ltr), "rtl" to stringResource(R.string.direction_rtl)),
                user.reader.defaultDirection,
            ) { v -> reader { it.copy(defaultDirection = v) } }
            SwitchRow(stringResource(R.string.settings_keep_screen), user.reader.keepScreenOn) { v -> reader { it.copy(keepScreenOn = v) } }

            SectionTitle(stringResource(R.string.settings_section_buttons))
            SwitchRow(stringResource(R.string.settings_use_volume), user.reader.useVolumeKeys) { v -> reader { it.copy(useVolumeKeys = v) } }
            SwitchRow(stringResource(R.string.settings_swap_volume), user.reader.swapVolumeKeys) { v -> reader { it.copy(swapVolumeKeys = v) } }
            SwitchRow(stringResource(R.string.settings_hold), user.reader.holdToTurnPage, stringResource(R.string.settings_hold_hint)) { v ->
                reader { it.copy(holdToTurnPage = v) }
            }

            SectionTitle(stringResource(R.string.settings_section_prepare))
            ChoiceRow(
                stringResource(R.string.settings_prepare_on_import),
                listOf(
                    PrepareOnImport.YES to stringResource(R.string.yes),
                    PrepareOnImport.ASK to stringResource(R.string.ask),
                    PrepareOnImport.NO to stringResource(R.string.no),
                ),
                ps.prepareOnImport,
            ) { v -> updateProfile { it.copy(prepareOnImport = v) } }
            SwitchRow(stringResource(R.string.settings_ocr), user.ocrEnabled(profile), stringResource(R.string.settings_ocr_hint)) { v ->
                updateUser { it.copy(prepare = it.prepare.copy(ocr = v)) }
            }
            ChoiceRow(
                stringResource(R.string.settings_translate),
                listOf("" to stringResource(R.string.translate_off)) + TRANSLATE_LANGS.map { it to java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.forLanguageTag("pt-BR")) },
                user.prepare.translateTo.orEmpty(),
            ) { v -> updateUser { it.copy(prepare = it.prepare.copy(translateTo = v.ifEmpty { null })) } }
            StepperRow(stringResource(R.string.settings_pdf_size), user.prepare.pdfLongSide, 1200..4000, step = 200, format = { "$it px" }) { v ->
                updateUser { it.copy(prepare = it.prepare.copy(pdfLongSide = v)) }
            }
            StepperRow(stringResource(R.string.settings_tolerance), user.prepare.tolerance, 10..90, step = 5) { v ->
                updateUser { it.copy(prepare = it.prepare.copy(tolerance = v)) }
            }

            SectionTitle(stringResource(R.string.settings_section_library))
            StepperRow(
                stringResource(R.string.settings_columns), user.library.columns, 0..8,
                format = { if (it == 0) context.getString(R.string.auto) else it.toString() },
            ) { v -> updateUser { it.copy(library = it.library.copy(columns = v)) } }
            SettingRow(stringResource(R.string.bookmarks_title), onClick = routes.bookmarks)

            SectionTitle(stringResource(R.string.settings_section_more))
            SettingRow(stringResource(R.string.servers_title), stringResource(R.string.servers_hint), onClick = routes.servers)
            SettingRow(stringResource(R.string.stats_title), onClick = routes.stats)
            SettingRow(stringResource(R.string.backup_title), onClick = routes.backup)

            SectionTitle(stringResource(R.string.settings_about))
            SettingRow(stringResource(R.string.app_name), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            SettingRow(stringResource(R.string.settings_licenses), onClick = { showLicenses = true })
            Spacer(Modifier.height(24.dp))
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
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
    if (editName) {
        app.gibiflix.ui.library.TextInputDialog(
            title = stringResource(R.string.settings_device_name),
            label = stringResource(R.string.settings_device_name),
            initial = user.deviceName ?: SettingsRepository.defaultDeviceName(),
            confirm = stringResource(R.string.save),
            onDismiss = { editName = false },
            onConfirm = { v -> editName = false; updateUser { it.copy(deviceName = v.trim().ifEmpty { null }) } },
        )
    }
}

/** Idiomas oferecidos para tradução dos balões. */
val TRANSLATE_LANGS = listOf("pt", "en", "es", "fr", "it", "de", "ja")

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
