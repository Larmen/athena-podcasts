package se.anders.athenapodcaster.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.settings.DEFAULT_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.data.settings.ThemeMode

/** Same colour tokens as Athena Launcher (FR-060). Light: black text on white. */
private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF444444),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF444444),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    outline = Color(0xFF777777),
)

/** Dark: white text on pure black. */
private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFBBBBBB),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFBBBBBB),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF888888),
)

/** Whether to draw dark: the system setting unless the user forced light or dark (FR-060). */
@Composable
fun useDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** The system font family for a typeface setting (FR-061). A family the device lacks resolves to the default. */
fun TypefaceChoice.fontFamily(): FontFamily = when (this) {
    TypefaceChoice.System -> FontFamily.Default
    TypefaceChoice.Condensed -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    TypefaceChoice.Serif -> FontFamily.Serif
    TypefaceChoice.Monospace -> FontFamily.Monospace
    TypefaceChoice.Cursive -> FontFamily.Cursive
    TypefaceChoice.Casual -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
    TypefaceChoice.Vt323 -> FontFamily(Font(R.font.vt323_regular, FontWeight.Normal))
    TypefaceChoice.PlexMono -> FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )
    TypefaceChoice.SpaceMono -> FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )
    TypefaceChoice.JetBrainsMono -> variableFamily(R.font.jetbrains_mono)
    TypefaceChoice.Inter -> variableFamily(R.font.inter)
    TypefaceChoice.Atkinson -> FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )
}

/** Some fonts draw much smaller than others at the same size; this evens them out (VT323 is a pixel font). */
fun TypefaceChoice.sizeAdjust(): Float = if (this == TypefaceChoice.Vt323) 1.3f else 1f

/** One variable font file served at the weights Material text styles use. */
private fun variableFamily(resId: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun AthenaTheme(
    themeMode: ThemeMode = ThemeMode.System,
    typeface: TypefaceChoice = TypefaceChoice.System,
    textScalePercent: Int = DEFAULT_TEXT_SCALE_PERCENT,
    content: @Composable () -> Unit,
) {
    val colors = if (useDarkTheme(themeMode)) DarkColors else LightColors
    val typography = remember(typeface) { Typography().withFamily(typeface.fontFamily()) }
    // FR-061: the user's size multiplies the system font size, so large system sizes stay large.
    val density = LocalDensity.current
    val scaled = remember(density, textScalePercent, typeface) { Density(density.density, density.fontScale * textScalePercent / 100f * typeface.sizeAdjust()) }
    CompositionLocalProvider(LocalDensity provides scaled) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        // Gives every Text and Icon the right default colour, and paints the whole window.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
    }
}
