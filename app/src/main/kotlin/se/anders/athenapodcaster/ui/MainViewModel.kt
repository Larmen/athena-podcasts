package se.anders.athenapodcaster.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import se.anders.athenapodcaster.data.launcher.AppearanceSource
import se.anders.athenapodcaster.data.settings.parseLauncherExport
import se.anders.athenapodcaster.domain.EffectiveAppearance
import se.anders.athenapodcaster.domain.effectiveAppearance
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.domain.ArtStyle
import se.anders.athenapodcaster.data.settings.BackupParse
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.data.settings.exportSettings
import se.anders.athenapodcaster.data.settings.parseSettingsBackup
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY

enum class BackupMessage { Exported, Imported, NotJson, NotPodcasterBackup, NewerVersion, CouldNotRead, CouldNotWrite, LauncherImported, NotLauncherFile }

sealed interface Screen {
    data object Feeds : Screen
    data object AddFeed : Screen
    data class Episodes(val feedId: Long) : Screen
    data object Player : Screen
    /** [back] is where the Back button and the system back go: the podcast list or the player. */
    data class Queue(val back: Screen) : Screen
    data object Settings : Screen
    data object Licences : Screen
}

class MainViewModel(private val repository: SettingsRepository, private val launcher: AppearanceSource) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Theme, typeface and text size actually in use: the launcher's while following it, else the local ones (FR-062). */
    val appearance: StateFlow<EffectiveAppearance> = combine(repository.settings, launcher.appearance, ::effectiveAppearance)
        .stateIn(viewModelScope, SharingStarted.Eagerly, effectiveAppearance(Settings(), null))

    val launcherAvailable: StateFlow<Boolean> = launcher.appearance.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun refreshLauncher() = launcher.refresh()
    fun setFollowLauncher(value: Boolean) = viewModelScope.launch { repository.setFollowLauncher(value) }

    /** FR-063: one-off import of the appearance part of the launcher's settings file; it switches following off so the values apply. */
    fun importLauncherFile(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val text = try {
            withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        } catch (_: Exception) {
            _backupMessage.value = BackupMessage.CouldNotRead
            return@launch
        }
        val imported = parseLauncherExport(text)
        if (imported == null) {
            _backupMessage.value = BackupMessage.NotLauncherFile
            return@launch
        }
        imported.themeMode?.let { repository.setThemeMode(it) }
        imported.typeface?.let { repository.setTypeface(it) }
        imported.textScalePercent?.let { repository.setTextScalePercent(it) }
        repository.setFollowLauncher(false)
        _backupMessage.value = BackupMessage.LauncherImported
    }

    private val _screen = MutableStateFlow<Screen>(Screen.Feeds)
    val screen: StateFlow<Screen> = _screen

    private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
    val backupMessage: StateFlow<BackupMessage?> = _backupMessage

    /** A parsed import waiting for the user to confirm replacing their settings. */
    private val _pendingImport = MutableStateFlow<Settings?>(null)
    val pendingImport: StateFlow<Settings?> = _pendingImport

    fun open(screen: Screen) { _screen.value = screen }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setTypeface(choice: TypefaceChoice) = viewModelScope.launch { repository.setTypeface(choice) }
    fun setTextScale(percent: Int) = viewModelScope.launch { repository.setTextScalePercent(percent) }
    fun setArtStyle(style: ArtStyle) = viewModelScope.launch { repository.setArtStyle(style) }
    fun setAutoDownload(count: Int) = viewModelScope.launch { repository.setAutoDownloadCount(count) }
    fun setRefreshHours(hours: Int) = viewModelScope.launch { repository.setRefreshIntervalHours(hours) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { repository.setWifiOnly(value) }
    fun setChargingOnly(value: Boolean) = viewModelScope.launch { repository.setChargingOnly(value) }
    fun setDeleteAfterPlayed(value: Boolean) = viewModelScope.launch { repository.setDeleteAfterPlayed(value) }

    fun exportTo(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _backupMessage.value = try {
            val text = exportSettings(settings.value)
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }
            BackupMessage.Exported
        } catch (_: Exception) {
            BackupMessage.CouldNotWrite
        }
    }

    fun readImport(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val text = try {
            withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        } catch (_: Exception) {
            _backupMessage.value = BackupMessage.CouldNotRead
            return@launch
        }
        when (val parsed = parseSettingsBackup(text)) {
            is BackupParse.Valid -> { _backupMessage.value = null; _pendingImport.value = parsed.settings }
            is BackupParse.Invalid -> _backupMessage.value = when (parsed.reason) {
                BackupParse.Reason.NotJson -> BackupMessage.NotJson
                BackupParse.Reason.NotPodcasterBackup -> BackupMessage.NotPodcasterBackup
                BackupParse.Reason.NewerVersion -> BackupMessage.NewerVersion
            }
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val new = _pendingImport.value ?: return@launch
        repository.replaceAll(new)
        _pendingImport.value = null
        _backupMessage.value = BackupMessage.Imported
    }

    fun cancelImport() { _pendingImport.value = null }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AthenaPodcasterApplication
                MainViewModel(app.container.settings, app.container.launcherAppearance)
            }
        }
    }
}
