package se.anders.athenapodcaster

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.domain.EffectiveAppearance
import se.anders.athenapodcaster.widget.WidgetPicture
import java.io.File

/** FR-120: the widget picture uses the app's typeface and theme colours. */
class WidgetPictureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun draw(face: TypefaceChoice, scale: Int = 100, dark: Boolean = false): Bitmap =
        WidgetPicture.draw(context, "Episode 34: a rather long title that needs two lines", EffectiveAppearance(ThemeMode.System, face, scale, false), 300, 70, if (dark) Color.BLACK else Color.WHITE, if (dark) Color.WHITE else Color.BLACK)

    private fun Bitmap.sameAs2(o: Bitmap) = width == o.width && height == o.height && sameAs(o)

    @Test
    fun backgroundFollowsTheThemeAndTypefaceAndSizeChangeTheText() {
        val light = draw(TypefaceChoice.System)
        val dark = draw(TypefaceChoice.System, dark = true)
        assertEquals(Color.WHITE, light.getPixel(2, 2))
        assertEquals(Color.BLACK, dark.getPixel(2, 2))
        assertNotEquals("typeface changes the picture", true, light.sameAs2(draw(TypefaceChoice.Vt323)))
        assertNotEquals("text size changes the picture", true, light.sameAs2(draw(TypefaceChoice.System, scale = 150)))
        File(context.cacheDir, "widget-vt323-dark.png").outputStream().use { draw(TypefaceChoice.Vt323, dark = true).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
