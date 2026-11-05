package se.anders.athenapodcaster.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.licences.Licences

class LicencesTest {
    @Test
    fun everyListedLicenceHasItsTextFile() {
        // Unit tests run with the module directory as the working directory.
        Licences.all.forEach {
            val file = File("src/main/assets/licenses/${it.assetFile}")
            assertTrue("missing ${it.assetFile}", file.isFile && file.length() > 0)
        }
    }

    @Test
    fun noLicenceFileShipsUnlisted() {
        // Anything under assets/licenses must be on the Licences screen (constitution §9, FR-066).
        val listed = Licences.all.map { it.assetFile }.toSet()
        val shipped = File("src/main/assets/licenses").listFiles().orEmpty().map { it.name }.toSet()
        assertTrue("unlisted licence files: ${shipped - listed}", listed.containsAll(shipped))
    }

    @Test
    fun everyBundledFontIsListed() {
        val fonts = File("src/main/res/font").listFiles().orEmpty().map { it.nameWithoutExtension }
        val names = Licences.all.joinToString(" ") { it.name.lowercase().replace(" ", "_") }
        // Each font family (vt323, ibm_plex_mono, space_mono, jetbrains_mono, inter, atkinson_hyperlegible) has an entry.
        fonts.forEach { f ->
            val family = f.removeSuffix("_regular").removeSuffix("_bold")
            assertTrue("font $family has no licence entry", names.contains(family))
        }
    }
}
