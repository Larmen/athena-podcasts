package se.anders.athenapodcaster.domain

import se.anders.athenapodcaster.data.launcher.LauncherAppearance
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice

/** The theme, typeface and text size actually drawn, and whether they come from Athena Launcher. */
data class EffectiveAppearance(
    val themeMode: ThemeMode,
    val typeface: TypefaceChoice,
    val textScalePercent: Int,
    val followingLauncher: Boolean,
)

/**
 * FR-062: with "Follow launcher" on and the launcher available, its values win, field by field (a field the
 * launcher did not give falls back to the local value). Otherwise the local values apply. The local values are
 * never overwritten by the launcher's, so switching following off brings back the last local choice.
 */
fun effectiveAppearance(settings: Settings, launcher: LauncherAppearance?): EffectiveAppearance {
    val following = settings.followLauncher && launcher != null
    return EffectiveAppearance(
        themeMode = (if (following) launcher?.themeMode else null) ?: settings.themeMode,
        typeface = (if (following) launcher?.typeface else null) ?: settings.typeface,
        textScalePercent = (if (following) launcher?.textScalePercent else null) ?: settings.textScalePercent,
        followingLauncher = following,
    )
}
