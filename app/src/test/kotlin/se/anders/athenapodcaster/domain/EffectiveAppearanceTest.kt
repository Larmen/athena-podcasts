package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.launcher.LauncherAppearance
import se.anders.athenapodcaster.data.launcher.parseLauncherAppearance
import se.anders.athenapodcaster.data.settings.BackupParse
import se.anders.athenapodcaster.data.settings.MAX_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.data.settings.exportSettings
import se.anders.athenapodcaster.data.settings.parseLauncherExport
import se.anders.athenapodcaster.data.settings.parseSettingsBackup

class EffectiveAppearanceTest {
    private val local = Settings(themeMode = ThemeMode.Light, typeface = TypefaceChoice.Serif, textScalePercent = 90)
    private val launcher = LauncherAppearance(ThemeMode.Dark, TypefaceChoice.JetBrainsMono, 130)

    @Test
    fun followingUsesTheLaunchersValues() {
        val e = effectiveAppearance(local.copy(followLauncher = true), launcher)
        assertEquals(EffectiveAppearance(ThemeMode.Dark, TypefaceChoice.JetBrainsMono, 130, followingLauncher = true), e)
    }

    @Test
    fun notFollowingUsesLocalEvenWhenTheLauncherIsThere() {
        val e = effectiveAppearance(local.copy(followLauncher = false), launcher)
        assertEquals(EffectiveAppearance(ThemeMode.Light, TypefaceChoice.Serif, 90, followingLauncher = false), e)
    }

    @Test
    fun missingLauncherFallsBackToLocalAndIsNotFollowing() {
        val e = effectiveAppearance(local.copy(followLauncher = true), null)
        assertEquals(EffectiveAppearance(ThemeMode.Light, TypefaceChoice.Serif, 90, followingLauncher = false), e)
    }

    @Test
    fun aFieldTheLauncherDidNotGiveFallsBackToLocalForThatFieldOnly() {
        val e = effectiveAppearance(local, LauncherAppearance(ThemeMode.Dark, typeface = null, textScalePercent = null))
        assertEquals(ThemeMode.Dark, e.themeMode)
        assertEquals(TypefaceChoice.Serif, e.typeface)
        assertEquals(90, e.textScalePercent)
        assertTrue(e.followingLauncher)
    }

    @Test
    fun localValuesAreNeverTouchedSoSwitchingFollowingOffBringsThemBack() {
        val s = local.copy(followLauncher = true)
        effectiveAppearance(s, launcher)
        assertEquals(ThemeMode.Light, s.themeMode)
        assertEquals(ThemeMode.Light, effectiveAppearance(s.copy(followLauncher = false), launcher).themeMode)
    }

    @Test
    fun launcherValuesAreSanitised() {
        val p = parseLauncherAppearance("Neon", "Comic", 900)
        assertNull(p.themeMode)
        assertNull(p.typeface)
        assertEquals(MAX_TEXT_SCALE_PERCENT, p.textScalePercent)
        val ok = parseLauncherAppearance("Dark", "Inter", 110)
        assertEquals(LauncherAppearance(ThemeMode.Dark, TypefaceChoice.Inter, 110), ok)
        assertNull(parseLauncherAppearance(null, null, null).textScalePercent)
    }

    @Test
    fun followLauncherDefaultsToOnAndSurvivesBackup() {
        assertTrue(Settings().followLauncher)
        val off = Settings(followLauncher = false)
        assertFalse((parseSettingsBackup(exportSettings(off)) as BackupParse.Valid).settings.followLauncher)
    }

    @Test
    fun launcherExportFileIsReadForAppearanceOnly() {
        val file = """{"app":"athena-launcher","version":1,"themeMode":"Dark","typeface":"Vt323","textScalePercent":120,"favouriteSlots":["x"],"gestures":{"SwipeUp":"drawer"}}"""
        assertEquals(LauncherAppearance(ThemeMode.Dark, TypefaceChoice.Vt323, 120), parseLauncherExport(file))
        assertNull(parseLauncherExport("""{"app":"athena-podcaster","version":1}"""))
        assertNull(parseLauncherExport("not json"))
        assertNotNull(parseLauncherExport("""{"app":"athena-launcher","version":1}""")) // valid file with no appearance: all fields null
    }
}
