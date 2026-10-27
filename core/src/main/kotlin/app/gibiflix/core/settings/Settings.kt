package app.gibiflix.core.settings

import app.gibiflix.core.device.Profile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Nitidez: 0 desligada, 1 leve, 2 média, 3 forte. */
@Serializable
data class ImageSettings(
    val contrast: Int = 0,      // -50..+50
    val brightness: Int = 0,    // -50..+50
    val saturation: Int = 0,    // -50..+100 (ignorada no mono)
    val midtones: Int = 0,      // 0..60: escurecer meios-tons
    val sharpness: Int = 0,     // 0..3
    val grayscale: Boolean = false,
    val dither: Boolean = false,
) {
    val isIdentity: Boolean
        get() = contrast == 0 && brightness == 0 && saturation == 0 && midtones == 0 && sharpness == 0 && !grayscale && !dither
}

@Serializable
enum class PrepareOnImport { YES, ASK, NO }

@Serializable
data class ProfileSettings(
    val image: ImageSettings,
    /** Trocas entre refreshes completos (0 = nunca). */
    val fullRefreshEvery: Int,
    val flashClean: Boolean,
    val splitWidePanels: Boolean,
    val prepareOnImport: PrepareOnImport,
    val cacheMb: Int,
)

object ProfileDefaults {
    fun of(p: Profile): ProfileSettings = when (p) {
        Profile.TABLET -> ProfileSettings(
            image = ImageSettings(),
            fullRefreshEvery = 0,
            flashClean = false,
            splitWidePanels = false,
            prepareOnImport = PrepareOnImport.YES,
            cacheMb = 400,
        )
        Profile.EINK_COLOR -> ProfileSettings(
            image = ImageSettings(contrast = 20, saturation = 35, sharpness = 2),
            fullRefreshEvery = 6,
            flashClean = true,
            splitWidePanels = true,
            prepareOnImport = PrepareOnImport.ASK,
            cacheMb = 200,
        )
        Profile.EINK_MONO -> ProfileSettings(
            image = ImageSettings(contrast = 25, midtones = 30, sharpness = 2, grayscale = true),
            fullRefreshEvery = 10,
            flashClean = true,
            splitWidePanels = true,
            prepareOnImport = PrepareOnImport.ASK,
            cacheMb = 200,
        )
    }
}

@Serializable
enum class ReadMode { PANEL, PAGE }

@Serializable
data class ReaderSettings(
    val defaultMode: ReadMode = ReadMode.PANEL,
    val pageBeforePanels: Boolean = false,
    val pageAfterPanels: Boolean = false,
    /** Margem em volta do quadro, em % (0..5). */
    val panelMarginPct: Int = 2,
    /** Ampliação máxima do quadro (1..5). */
    val maxZoom: Float = 3f,
    val tapZones: Boolean = true,
    val keepScreenOn: Boolean = true,
    val useVolumeKeys: Boolean = true,
    val swapVolumeKeys: Boolean = false,
    val holdToTurnPage: Boolean = true,
    val defaultDirection: String = "ltr",
    /** Quantas vezes a dica dos botões já apareceu. */
    val hintsShown: Int = 0,
)

@Serializable
data class PrepareSettings(
    /** null = padrão do perfil (ligado no tablet). */
    val ocr: Boolean? = null,
    val pdfLongSide: Int = 2560,
    val tolerance: Int = 40,
)

@Serializable
enum class LibrarySort { RECENT_READ, RECENT_ADDED, TITLE, SERIES }

@Serializable
data class LibrarySettings(
    val sort: LibrarySort = LibrarySort.RECENT_READ,
    /** 0 = automático. */
    val columns: Int = 0,
)

@Serializable
data class UserSettings(
    val profiles: Map<String, ProfileSettings> = emptyMap(),
    val reader: ReaderSettings = ReaderSettings(),
    val prepare: PrepareSettings = PrepareSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val deviceName: String? = null,
    val backupReminder: Boolean = false,
    val lastBackupAt: Long = 0,
) {
    fun forProfile(p: Profile): ProfileSettings = profiles[p.name] ?: ProfileDefaults.of(p)

    fun withProfile(p: Profile, s: ProfileSettings) = copy(profiles = profiles + (p.name to s))

    fun ocrEnabled(p: Profile): Boolean = prepare.ocr ?: (p == Profile.TABLET)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(s: String?): UserSettings = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: UserSettings()
        fun encode(s: UserSettings): String = json.encodeToString(serializer(), s)
    }
}
