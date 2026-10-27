package app.gibiflix.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gibiflix.R
import app.gibiflix.core.device.Profile

object GibiColors {
    val Black = Color(0xFF000000)
    val Red = Color(0xFFE1261C)
    val SurfaceDark = Color(0xFF161616)
    val SurfaceDark2 = Color(0xFF222222)
    val TextSecondaryDark = Color(0xFFB5B5B5)
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    val DetectionBlue = Color(0xFF1F5FA8)
    val SelectionOrange = Color(0xFFD9731A)
}

val Anton = FontFamily(Font(R.font.anton_regular))
val Atkinson = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

/** Perfil em uso, disponível para qualquer tela. */
val LocalProfile = staticCompositionLocalOf { Profile.TABLET }

/** Falso no e-ink: nada de transições animadas. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

private val TabletColors = darkColorScheme(
    primary = GibiColors.Red,
    onPrimary = Color.White,
    primaryContainer = GibiColors.Red,
    onPrimaryContainer = Color.White,
    secondary = GibiColors.TextSecondaryDark,
    onSecondary = GibiColors.Black,
    secondaryContainer = GibiColors.Red,
    onSecondaryContainer = Color.White,
    background = GibiColors.Black,
    onBackground = Color.White,
    surface = GibiColors.Black,
    onSurface = Color.White,
    surfaceVariant = GibiColors.SurfaceDark,
    onSurfaceVariant = GibiColors.TextSecondaryDark,
    surfaceContainerLowest = GibiColors.Black,
    surfaceContainerLow = GibiColors.SurfaceDark,
    surfaceContainer = GibiColors.SurfaceDark,
    surfaceContainerHigh = GibiColors.SurfaceDark2,
    surfaceContainerHighest = GibiColors.SurfaceDark2,
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF6B60),
)

private fun einkColors(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent,
    onPrimaryContainer = Color.White,
    secondary = GibiColors.Ink,
    onSecondary = Color.White,
    // Aba ativa e chips selecionados: inversão (fundo preto, texto branco), nunca só cor.
    secondaryContainer = GibiColors.Ink,
    onSecondaryContainer = Color.White,
    tertiary = GibiColors.Ink,
    background = GibiColors.Paper,
    onBackground = GibiColors.Ink,
    surface = GibiColors.Paper,
    onSurface = GibiColors.Ink,
    surfaceVariant = GibiColors.Paper,
    onSurfaceVariant = GibiColors.Ink,
    surfaceTint = GibiColors.Paper,
    surfaceBright = GibiColors.Paper,
    surfaceDim = GibiColors.Paper,
    surfaceContainerLowest = GibiColors.Paper,
    surfaceContainerLow = GibiColors.Paper,
    surfaceContainer = GibiColors.Paper,
    surfaceContainerHigh = GibiColors.Paper,
    surfaceContainerHighest = GibiColors.Paper,
    inverseSurface = GibiColors.Ink,
    inverseOnSurface = GibiColors.Paper,
    outline = GibiColors.Ink,
    outlineVariant = GibiColors.Ink,
    scrim = Color(0x66000000),
    error = GibiColors.Ink,
    onError = Color.White,
)

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.atk() = copy(fontFamily = Atkinson)
    fun TextStyle.anton() = copy(fontFamily = Anton, fontWeight = FontWeight.Normal, letterSpacing = 1.sp)
    return Typography(
        displayLarge = base.displayLarge.anton(),
        displayMedium = base.displayMedium.anton(),
        displaySmall = base.displaySmall.anton(),
        headlineLarge = base.headlineLarge.anton(),
        headlineMedium = base.headlineMedium.anton(),
        headlineSmall = base.headlineSmall.anton(),
        titleLarge = base.titleLarge.atk().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.atk().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.atk().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.atk(),
        bodyMedium = base.bodyMedium.atk(),
        bodySmall = base.bodySmall.atk(),
        labelLarge = base.labelLarge.atk().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.atk(),
        labelSmall = base.labelSmall.atk(),
    )
}

private val GibiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GibiflixTheme(profile: Profile, content: @Composable () -> Unit) {
    val colors = when (profile) {
        Profile.TABLET -> TabletColors
        Profile.EINK_COLOR -> einkColors(GibiColors.Red)
        Profile.EINK_MONO -> einkColors(GibiColors.Ink)
    }
    MaterialTheme(colorScheme = colors, typography = typography(), shapes = GibiShapes) {
        if (profile.isEink) {
            CompositionLocalProvider(
                LocalProfile provides profile,
                LocalAnimationsEnabled provides false,
                LocalRippleConfiguration provides null,
                LocalOverscrollFactory provides null,
                content = content,
            )
        } else {
            CompositionLocalProvider(
                LocalProfile provides profile,
                LocalAnimationsEnabled provides true,
                content = content,
            )
        }
    }
}

/** Cor de destaque da marca: vermelho no tablet e no colorido, preto no mono. */
val Profile.accent: Color get() = if (this == Profile.EINK_MONO) GibiColors.Ink else GibiColors.Red
