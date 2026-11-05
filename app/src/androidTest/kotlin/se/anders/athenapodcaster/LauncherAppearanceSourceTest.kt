package se.anders.athenapodcaster

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.launcher.LauncherAppearance
import se.anders.athenapodcaster.data.launcher.LauncherAppearanceSource
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice

/** The client side of the appearance bridge against a stand-in provider: live values, change notifications, every failure mode. */
@RunWith(AndroidJUnit4::class)
class LauncherAppearanceSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun reset() { FakeLauncherProvider.row = arrayOf(1, "Dark", "Inter", 120); FakeLauncherProvider.deny = false }
    @After fun cleanUp() = reset()

    private fun waitFor(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (check()) return; Thread.sleep(50) }
        throw AssertionError("Timed out waiting for $what")
    }

    @Test
    fun readsTheLaunchersValues() {
        val source = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        waitFor("first read") { source.appearance.value != null }
        assertEquals(LauncherAppearance(ThemeMode.Dark, TypefaceChoice.Inter, 120), source.appearance.value)
    }

    @Test
    fun followsChangesThroughTheContentObserver() {
        val source = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        waitFor("first read") { source.appearance.value != null }
        FakeLauncherProvider.row = arrayOf(1, "Light", "Serif", 90)
        context.contentResolver.notifyChange(FakeLauncherProvider.uri, null) // what the launcher does when a setting changes
        waitFor("updated values") { source.appearance.value?.typeface == TypefaceChoice.Serif }
        assertEquals(LauncherAppearance(ThemeMode.Light, TypefaceChoice.Serif, 90), source.appearance.value)
    }

    @Test
    fun refreshPicksUpAChangeWithoutANotification() {
        val source = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        waitFor("first read") { source.appearance.value != null }
        FakeLauncherProvider.row = arrayOf(1, "System", "Casual", 100)
        source.refresh()
        waitFor("refreshed values") { source.appearance.value?.typeface == TypefaceChoice.Casual }
    }

    @Test
    fun permissionDeniedMeansNoLauncherValues() {
        FakeLauncherProvider.deny = true
        val source = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        Thread.sleep(500)
        assertNull(source.appearance.value)
    }

    @Test
    fun noProviderAtAllMeansNoLauncherValues() {
        val source = LauncherAppearanceSource(context, Uri.parse("content://no.such.provider.here/appearance"))
        Thread.sleep(500)
        assertNull(source.appearance.value)
    }

    @Test
    fun emptyOrOddDataIsHandled() {
        FakeLauncherProvider.row = null // provider answers with no rows
        val empty = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        Thread.sleep(500)
        assertNull(empty.appearance.value)

        FakeLauncherProvider.row = arrayOf(2, "Neon", "Comic", null) // a newer launcher with values we do not know
        val odd = LauncherAppearanceSource(context, FakeLauncherProvider.uri)
        waitFor("odd read") { odd.appearance.value != null }
        assertEquals(LauncherAppearance(null, null, null), odd.appearance.value)
    }
}
