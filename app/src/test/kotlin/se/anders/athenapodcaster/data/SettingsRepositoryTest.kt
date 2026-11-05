package se.anders.athenapodcaster.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import se.anders.athenapodcaster.data.settings.MAX_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.MIN_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + Job())
        repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("s.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsWhenEmpty() = runBlocking {
        assertEquals(Settings(), repo.settings.first())
    }

    @Test
    fun settersPersistAndClamp() = runBlocking {
        repo.setThemeMode(ThemeMode.Dark)
        repo.setTypeface(TypefaceChoice.Inter)
        repo.setTextScalePercent(1000)
        assertEquals(Settings(ThemeMode.Dark, TypefaceChoice.Inter, MAX_TEXT_SCALE_PERCENT), repo.settings.first())
        repo.setTextScalePercent(0)
        assertEquals(MIN_TEXT_SCALE_PERCENT, repo.settings.first().textScalePercent)
    }

    @Test
    fun replaceAllReplacesEverything() = runBlocking {
        repo.setTypeface(TypefaceChoice.Serif)
        repo.replaceAll(Settings(ThemeMode.Light, TypefaceChoice.Atkinson, 90))
        assertEquals(Settings(ThemeMode.Light, TypefaceChoice.Atkinson, 90), repo.settings.first())
    }
}
