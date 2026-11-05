package se.anders.athenapodcaster.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import se.anders.athenapodcaster.domain.ArtStyle

/**
 * Settings export/import (FR-065). Plain JSON the user keeps; nothing leaves the device (constitution §3).
 * It never holds feed URLs or credentials (constitution §4).
 */
@Serializable
data class SettingsBackup(
    /** Both are required when reading: a file that does not say it is ours is not ours. */
    val app: String? = null,
    val version: Int? = null,
    val themeMode: String? = null,
    val typeface: String? = null,
    val textScalePercent: Int? = null,
    val autoDownloadCount: Int? = null,
    val refreshIntervalHours: Int? = null,
    val wifiOnly: Boolean? = null,
    val chargingOnly: Boolean? = null,
    val deleteAfterPlayed: Boolean? = null,
    val artStyle: String? = null,
    val followLauncher: Boolean? = null,
)

const val BACKUP_APP = "athena-podcaster"
const val BACKUP_VERSION = 1

sealed interface BackupParse {
    data class Valid(val settings: Settings) : BackupParse
    data class Invalid(val reason: Reason) : BackupParse

    enum class Reason { NotJson, NotPodcasterBackup, NewerVersion }
}

private val json = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true // a newer or hand-edited file with extra fields still imports
}

fun exportSettings(settings: Settings): String = json.encodeToString(
    SettingsBackup.serializer(),
    SettingsBackup(
        app = BACKUP_APP,
        version = BACKUP_VERSION,
        themeMode = settings.themeMode.name,
        typeface = settings.typeface.name,
        textScalePercent = settings.textScalePercent,
        autoDownloadCount = settings.autoDownloadCount,
        refreshIntervalHours = settings.refreshIntervalHours,
        wifiOnly = settings.wifiOnly,
        chargingOnly = settings.chargingOnly,
        deleteAfterPlayed = settings.deleteAfterPlayed,
        artStyle = settings.artStyle.name,
        followLauncher = settings.followLauncher,
    ),
)

/** Reads a backup. A bad value in an otherwise valid file falls back to its default; numbers are clamped. */
fun parseSettingsBackup(text: String): BackupParse {
    val backup = try {
        json.decodeFromString(SettingsBackup.serializer(), text)
    } catch (_: SerializationException) {
        return BackupParse.Invalid(BackupParse.Reason.NotJson)
    } catch (_: IllegalArgumentException) {
        return BackupParse.Invalid(BackupParse.Reason.NotJson)
    }
    if (backup.app != BACKUP_APP) return BackupParse.Invalid(BackupParse.Reason.NotPodcasterBackup)
    val version = backup.version ?: return BackupParse.Invalid(BackupParse.Reason.NotPodcasterBackup)
    if (version > BACKUP_VERSION) return BackupParse.Invalid(BackupParse.Reason.NewerVersion)

    val defaults = Settings()
    return BackupParse.Valid(
        Settings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == backup.themeMode } ?: defaults.themeMode,
            typeface = TypefaceChoice.entries.firstOrNull { it.name == backup.typeface } ?: defaults.typeface,
            textScalePercent = clampTextScale(backup.textScalePercent ?: defaults.textScalePercent),
            autoDownloadCount = clampAutoDownload(backup.autoDownloadCount ?: defaults.autoDownloadCount),
            refreshIntervalHours = normalizeRefreshHours(backup.refreshIntervalHours ?: defaults.refreshIntervalHours),
            wifiOnly = backup.wifiOnly ?: defaults.wifiOnly,
            chargingOnly = backup.chargingOnly ?: defaults.chargingOnly,
            deleteAfterPlayed = backup.deleteAfterPlayed ?: defaults.deleteAfterPlayed,
            artStyle = ArtStyle.entries.firstOrNull { it.name == backup.artStyle } ?: defaults.artStyle,
            followLauncher = backup.followLauncher ?: defaults.followLauncher,
        ),
    )
}

/** What the launcher's own settings export file holds that matters here (FR-063). Everything else is ignored. */
@Serializable
private data class LauncherExport(
    val app: String? = null,
    val themeMode: String? = null,
    val typeface: String? = null,
    val textScalePercent: Int? = null,
)

const val LAUNCHER_BACKUP_APP = "athena-launcher"

/** Reads the appearance part of an Athena Launcher settings export, or null if the file is not one. */
fun parseLauncherExport(text: String): se.anders.athenapodcaster.data.launcher.LauncherAppearance? {
    val export = try {
        json.decodeFromString(LauncherExport.serializer(), text)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (export.app != LAUNCHER_BACKUP_APP) return null
    return se.anders.athenapodcaster.data.launcher.parseLauncherAppearance(export.themeMode, export.typeface, export.textScalePercent)
}
