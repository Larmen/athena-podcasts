package se.anders.athenapodcaster.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.anders.athenapodcaster.domain.ArtStyle

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** FR-060 / FR-061: follow the system by default; the user can force light or dark. Names match Athena Launcher. */
enum class ThemeMode { System, Light, Dark }

/** FR-061: the same twelve typefaces as Athena Launcher, with the same names so values map one-to-one. */
enum class TypefaceChoice {
    System, Condensed, Serif, Monospace, Cursive, Casual,

    /** Bundled open-licence fonts; their licences are listed in [se.anders.athenapodcaster.data.licences.Licences]. */
    Vt323, PlexMono, SpaceMono, JetBrainsMono, Inter, Atkinson,
}

/** FR-061: text size on top of the system font size (same range and default as the launcher). */
const val DEFAULT_TEXT_SCALE_PERCENT = 100
const val MIN_TEXT_SCALE_PERCENT = 80
const val MAX_TEXT_SCALE_PERCENT = 140
const val TEXT_SCALE_STEP = 10

/** FR-031: how many of the newest episodes per feed are downloaded automatically (1 = only the latest, 0 = never). */
const val DEFAULT_AUTO_DOWNLOAD = 1
const val MAX_AUTO_DOWNLOAD = 10

/** FR-030: hours between background refreshes; 0 = only when asked. */
const val DEFAULT_REFRESH_HOURS = 6
val REFRESH_HOUR_CHOICES = listOf(0, 1, 3, 6, 12, 24)

/** The podcaster's own (local) settings. Album-art settings arrive with step two. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val typeface: TypefaceChoice = TypefaceChoice.System,
    val textScalePercent: Int = DEFAULT_TEXT_SCALE_PERCENT,
    val autoDownloadCount: Int = DEFAULT_AUTO_DOWNLOAD,
    val refreshIntervalHours: Int = DEFAULT_REFRESH_HOURS,
    /** FR-033: automatic downloads only on an unmetered network (Wi-Fi). */
    val wifiOnly: Boolean = true,
    /** FR-033: automatic downloads only while charging. Manual downloads ignore this. */
    val chargingOnly: Boolean = false,
    /** FR-013: remove the downloaded file of played episodes (done at the next refresh or app start). */
    val deleteAfterPlayed: Boolean = false,
    val artStyle: ArtStyle = ArtStyle.Grayscale,
    /** FR-062: take theme, typeface and text size from Athena Launcher when it is available. */
    val followLauncher: Boolean = true,
)

fun clampAutoDownload(count: Int): Int = count.coerceIn(0, MAX_AUTO_DOWNLOAD)
fun normalizeRefreshHours(hours: Int): Int = if (hours in REFRESH_HOUR_CHOICES) hours else DEFAULT_REFRESH_HOURS

fun clampTextScale(percent: Int): Int = percent.coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT)

/** User preferences, stored locally in DataStore (spec §6). Unknown or damaged values fall back to defaults. */
class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeMode.System,
            typeface = TypefaceChoice.entries.firstOrNull { it.name == prefs[TYPEFACE] } ?: TypefaceChoice.System,
            textScalePercent = clampTextScale(prefs[TEXT_SCALE] ?: DEFAULT_TEXT_SCALE_PERCENT),
            autoDownloadCount = clampAutoDownload(prefs[AUTO_DOWNLOAD] ?: DEFAULT_AUTO_DOWNLOAD),
            refreshIntervalHours = normalizeRefreshHours(prefs[REFRESH_HOURS] ?: DEFAULT_REFRESH_HOURS),
            wifiOnly = prefs[WIFI_ONLY] ?: true,
            chargingOnly = prefs[CHARGING_ONLY] ?: false,
            deleteAfterPlayed = prefs[DELETE_PLAYED] ?: false,
            artStyle = ArtStyle.entries.firstOrNull { it.name == prefs[ART_STYLE] } ?: ArtStyle.Grayscale,
            followLauncher = prefs[FOLLOW_LAUNCHER] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setTypeface(choice: TypefaceChoice) {
        store.edit { it[TYPEFACE] = choice.name }
    }

    suspend fun setTextScalePercent(percent: Int) {
        store.edit { it[TEXT_SCALE] = clampTextScale(percent) }
    }

    suspend fun setAutoDownloadCount(count: Int) { store.edit { it[AUTO_DOWNLOAD] = clampAutoDownload(count) } }
    suspend fun setRefreshIntervalHours(hours: Int) { store.edit { it[REFRESH_HOURS] = normalizeRefreshHours(hours) } }
    suspend fun setWifiOnly(value: Boolean) { store.edit { it[WIFI_ONLY] = value } }
    suspend fun setChargingOnly(value: Boolean) { store.edit { it[CHARGING_ONLY] = value } }
    suspend fun setDeleteAfterPlayed(value: Boolean) { store.edit { it[DELETE_PLAYED] = value } }
    suspend fun setFollowLauncher(value: Boolean) { store.edit { it[FOLLOW_LAUNCHER] = value } }
    suspend fun setArtStyle(style: ArtStyle) { store.edit { it[ART_STYLE] = style.name } }

    /** Used by import (FR-065): replaces every setting at once. */
    suspend fun replaceAll(new: Settings) {
        store.edit { prefs ->
            prefs[THEME] = new.themeMode.name
            prefs[TYPEFACE] = new.typeface.name
            prefs[TEXT_SCALE] = clampTextScale(new.textScalePercent)
            prefs[AUTO_DOWNLOAD] = clampAutoDownload(new.autoDownloadCount)
            prefs[REFRESH_HOURS] = normalizeRefreshHours(new.refreshIntervalHours)
            prefs[WIFI_ONLY] = new.wifiOnly
            prefs[CHARGING_ONLY] = new.chargingOnly
            prefs[DELETE_PLAYED] = new.deleteAfterPlayed
            prefs[ART_STYLE] = new.artStyle.name
            prefs[FOLLOW_LAUNCHER] = new.followLauncher
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val TYPEFACE = stringPreferencesKey("typeface")
        val TEXT_SCALE = intPreferencesKey("text_scale_percent")
        val AUTO_DOWNLOAD = intPreferencesKey("auto_download_count")
        val REFRESH_HOURS = intPreferencesKey("refresh_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val DELETE_PLAYED = booleanPreferencesKey("delete_after_played")
        val ART_STYLE = stringPreferencesKey("art_style")
        val FOLLOW_LAUNCHER = booleanPreferencesKey("follow_launcher")
    }
}
