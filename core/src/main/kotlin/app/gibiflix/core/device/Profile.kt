package app.gibiflix.core.device

/** Perfil de tela. Toda decisão "é e-ink?" passa por aqui. */
enum class Profile {
    TABLET, EINK_COLOR, EINK_MONO;

    val isEink: Boolean get() = this != TABLET
    val isMono: Boolean get() = this == EINK_MONO
}

/** Escolha do dono em Ajustes. AUTO segue a detecção (e a confirmação do primeiro uso). */
enum class ProfileChoice { AUTO, TABLET, EINK_COLOR, EINK_MONO;

    fun toProfile(): Profile? = when (this) {
        AUTO -> null
        TABLET -> Profile.TABLET
        EINK_COLOR -> Profile.EINK_COLOR
        EINK_MONO -> Profile.EINK_MONO
    }
}

data class BuildInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
)

object ProfileDetector {
    /** Modelos Onyx com tela colorida (Kaleido) cujo nome não contém "color". Complete com o Diagnóstico. */
    val KNOWN_COLOR_MODELS: Set<String> = setOf(
        "Go7Color", "GoColor7", "Go Color 7", "NoteAir3C", "NoteAir4C", "Tab Mini C", "TabMiniC",
        "Note Air3 C", "Note Air4 C", "Palma2Pro", "Palma 2 Pro", "Tab Ultra C", "TabUltraC", "TabUltraCPro",
    )

    fun isOnyx(info: BuildInfo): Boolean = listOf(info.manufacturer, info.brand).any {
        it.contains("onyx", ignoreCase = true) || it.contains("boox", ignoreCase = true)
    }

    fun detect(info: BuildInfo): Profile {
        if (!isOnyx(info)) return Profile.TABLET
        val names = listOf(info.model, info.product, info.device)
        val color = names.any { it.contains("color", ignoreCase = true) } ||
            names.any { n -> KNOWN_COLOR_MODELS.any { it.equals(n.trim(), ignoreCase = true) } }
        return if (color) Profile.EINK_COLOR else Profile.EINK_MONO
    }

    /**
     * Perfil em uso: escolha manual > confirmação do primeiro uso no Onyx > detecção.
     */
    fun resolve(detected: Profile, choice: ProfileChoice, confirmed: Profile?): Profile =
        choice.toProfile() ?: confirmed ?: detected
}
