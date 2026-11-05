package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.art.sampleSizeFor
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.exportSettings
import se.anders.athenapodcaster.data.settings.parseSettingsBackup
import se.anders.athenapodcaster.data.settings.BackupParse

class ArtTest {
    @Test
    fun decodeSizeKeepsTheLongestSideAtOrAboveWhatIsNeeded() {
        assertEquals(1, sampleSizeFor(500, 500, 280))
        assertEquals(2, sampleSizeFor(1000, 1000, 280)) // 500 >= 280, 250 < 280
        assertEquals(8, sampleSizeFor(3000, 3000, 280)) // 375 >= 280, 187 < 280
        assertEquals(1, sampleSizeFor(100, 100, 280)) // never upscale by sampling
        assertEquals(4, sampleSizeFor(3000, 1000, 600)) // longest side counts
        assertEquals(1, sampleSizeFor(1000, 1000, 0))
    }

    @Test
    fun grayscaleMatrixGivesEveryColourTheSameValueAndLowersContrast() {
        fun apply(r: Float, g: Float, b: Float): List<Float> = (0..2).map { row ->
            val o = row * 5
            GRAYSCALE_MATRIX[o] * r + GRAYSCALE_MATRIX[o + 1] * g + GRAYSCALE_MATRIX[o + 2] * b + GRAYSCALE_MATRIX[o + 4]
        }
        val red = apply(255f, 0f, 0f)
        assertTrue(red.all { it == red[0] }) // r == g == b: no colour left
        val black = apply(0f, 0f, 0f)[0]
        val white = apply(255f, 255f, 255f)[0]
        assertTrue("black should be lifted", black > 20f)
        assertTrue("white should be dimmed", white < 235f)
        assertEquals(255f * 0.75f, white - black, 1f) // contrast 0.75
    }

    @Test
    fun artStyleDefaultsToGrayscaleAndSurvivesBackup() {
        assertEquals(ArtStyle.Grayscale, Settings().artStyle)
        val s = Settings(artStyle = ArtStyle.Original)
        assertEquals(s, (parseSettingsBackup(exportSettings(s)) as BackupParse.Valid).settings)
        val bad = parseSettingsBackup("""{"app":"athena-podcaster","version":1,"artStyle":"Neon"}""") as BackupParse.Valid
        assertEquals(ArtStyle.Grayscale, bad.settings.artStyle)
    }
}
