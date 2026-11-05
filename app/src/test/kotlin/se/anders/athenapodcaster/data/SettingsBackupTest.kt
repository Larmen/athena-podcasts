package se.anders.athenapodcaster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.settings.BackupParse
import se.anders.athenapodcaster.data.settings.MAX_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.MIN_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.data.settings.exportSettings
import se.anders.athenapodcaster.data.settings.parseSettingsBackup

class SettingsBackupTest {
    private fun valid(text: String) = (parseSettingsBackup(text) as BackupParse.Valid).settings
    private fun reason(text: String) = (parseSettingsBackup(text) as BackupParse.Invalid).reason

    @Test
    fun roundTrip() {
        val s = Settings(ThemeMode.Dark, TypefaceChoice.JetBrainsMono, 120)
        assertEquals(s, valid(exportSettings(s)))
    }

    @Test
    fun exportNamesTheApp() {
        assertTrue(exportSettings(Settings()).contains("\"app\": \"athena-podcaster\""))
    }

    @Test
    fun rejectsLaunchersFile() {
        assertEquals(BackupParse.Reason.NotPodcasterBackup, reason("""{"app":"athena-launcher","version":1}"""))
    }

    @Test
    fun rejectsMissingAppOrVersion() {
        assertEquals(BackupParse.Reason.NotPodcasterBackup, reason("""{"version":1}"""))
        assertEquals(BackupParse.Reason.NotPodcasterBackup, reason("""{"app":"athena-podcaster"}"""))
    }

    @Test
    fun rejectsNewerVersionAndNonJson() {
        assertEquals(BackupParse.Reason.NewerVersion, reason("""{"app":"athena-podcaster","version":99}"""))
        assertEquals(BackupParse.Reason.NotJson, reason("not json"))
    }

    @Test
    fun badValuesFallBackAndNumbersClamp() {
        val low = valid("""{"app":"athena-podcaster","version":1,"themeMode":"Pink","typeface":"Comic","textScalePercent":5}""")
        assertEquals(Settings(ThemeMode.System, TypefaceChoice.System, MIN_TEXT_SCALE_PERCENT), low)
        val high = valid("""{"app":"athena-podcaster","version":1,"textScalePercent":900}""")
        assertEquals(MAX_TEXT_SCALE_PERCENT, high.textScalePercent)
    }

    @Test
    fun downloadSettingsRoundTripAndClamp() {
        val s = Settings(autoDownloadCount = 3, refreshIntervalHours = 12, wifiOnly = false, chargingOnly = true, deleteAfterPlayed = true)
        assertEquals(s, valid(exportSettings(s)))
        val bad = valid("""{"app":"athena-podcaster","version":1,"autoDownloadCount":99,"refreshIntervalHours":5}""")
        assertEquals(10, bad.autoDownloadCount)
        assertEquals(6, bad.refreshIntervalHours) // not one of the offered choices: back to the default
        assertEquals(Settings(), valid("""{"app":"athena-podcaster","version":1}"""))
        assertEquals(1, Settings().autoDownloadCount)
        assertEquals(true, Settings().wifiOnly)
    }

    @Test
    fun unknownKeysAreIgnored() {
        assertEquals(ThemeMode.Light, valid("""{"app":"athena-podcaster","version":1,"themeMode":"Light","future":true}""").themeMode)
    }
}
