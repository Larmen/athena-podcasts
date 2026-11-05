package se.anders.athenapodcaster.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.settings.MAX_AUTO_DOWNLOAD
import se.anders.athenapodcaster.data.settings.MAX_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.REFRESH_HOUR_CHOICES
import se.anders.athenapodcaster.data.settings.MIN_TEXT_SCALE_PERCENT
import se.anders.athenapodcaster.data.settings.Settings
import se.anders.athenapodcaster.data.settings.TEXT_SCALE_STEP
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.domain.ArtStyle
import se.anders.athenapodcaster.domain.EffectiveAppearance
import se.anders.athenapodcaster.ui.BackupMessage
import se.anders.athenapodcaster.ui.OpmlOutcome
import se.anders.athenapodcaster.ui.theme.fontFamily
import se.anders.athenapodcaster.ui.theme.sizeAdjust

class SettingsCallbacks(
    val onBack: () -> Unit,
    val onThemeChange: (ThemeMode) -> Unit,
    val onTypefaceChange: (TypefaceChoice) -> Unit,
    val onTextScaleChange: (Int) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onConfirmImport: () -> Unit,
    val onCancelImport: () -> Unit,
    val onImportOpml: () -> Unit,
    val onExportOpml: () -> Unit,
    val onIncludePrivateChange: (Boolean) -> Unit,
    val onOpenLicences: () -> Unit,
    val onFollowLauncherChange: (Boolean) -> Unit,
    val onImportLauncherFile: () -> Unit,
    val onArtStyleChange: (ArtStyle) -> Unit,
    val onAutoDownloadChange: (Int) -> Unit,
    val onRefreshHoursChange: (Int) -> Unit,
    val onWifiOnlyChange: (Boolean) -> Unit,
    val onChargingOnlyChange: (Boolean) -> Unit,
    val onDeleteAfterPlayedChange: (Boolean) -> Unit,
)

/** Settings skeleton (T1.3): Appearance and Backup now; Downloads, Refresh and About arrive with their tasks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    appearance: EffectiveAppearance,
    launcherAvailable: Boolean,
    backupMessage: BackupMessage?,
    importPending: Boolean,
    opmlOutcome: OpmlOutcome?,
    opmlProgress: Pair<Int, Int>?,
    includePrivate: Boolean,
    callbacks: SettingsCallbacks,
) {
    Column(
        modifier = Modifier.fillMaxWidth().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = callbacks.onBack) { Text(stringResource(R.string.back)) }
        }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

        val textSmaller = stringResource(R.string.a11y_text_smaller)
        val textLarger = stringResource(R.string.a11y_text_larger)
        val autoFewer = stringResource(R.string.a11y_auto_fewer)
        val autoMore = stringResource(R.string.a11y_auto_more)

        Section(R.string.settings_section_appearance)
        // FR-062: follow Athena Launcher's theme, typeface and text size, or use the local ones below.
        SwitchRow(stringResource(R.string.settings_follow_launcher), settings.followLauncher, callbacks.onFollowLauncherChange)
        if (appearance.followingLauncher) {
            Muted(R.string.settings_following_note)
            Text(
                stringResource(
                    R.string.settings_following_values,
                    stringResource(appearance.themeMode.labelRes()),
                    stringResource(appearance.typeface.labelRes()),
                    appearance.textScalePercent,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        } else {
            if (settings.followLauncher && !launcherAvailable) Muted(R.string.settings_launcher_missing)
            Label(R.string.settings_theme)
            ChoiceRow(stringResource(R.string.settings_theme_system), settings.themeMode == ThemeMode.System) { callbacks.onThemeChange(ThemeMode.System) }
            ChoiceRow(stringResource(R.string.settings_theme_light), settings.themeMode == ThemeMode.Light) { callbacks.onThemeChange(ThemeMode.Light) }
            ChoiceRow(stringResource(R.string.settings_theme_dark), settings.themeMode == ThemeMode.Dark) { callbacks.onThemeChange(ThemeMode.Dark) }

            Label(R.string.settings_typeface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypefaceChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.typeface == choice,
                        onClick = { callbacks.onTypefaceChange(choice) },
                        label = {
                            // Each name is drawn in its own font so the choice previews itself.
                            Text(
                                stringResource(choice.labelRes()),
                                fontFamily = choice.fontFamily(),
                                // The theme already scales text for the selected font; this keeps each name at its own size.
                                fontSize = 14.sp * (choice.sizeAdjust() / settings.typeface.sizeAdjust()),
                            )
                        },
                    )
                }
            }

            val scale = settings.textScalePercent
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_text_size, scale), modifier = Modifier.weight(1f))
                TextButton(enabled = scale > MIN_TEXT_SCALE_PERCENT, onClick = { callbacks.onTextScaleChange(scale - TEXT_SCALE_STEP) }, modifier = Modifier.semantics { contentDescription = textSmaller }) { Text("−") }
                TextButton(enabled = scale < MAX_TEXT_SCALE_PERCENT, onClick = { callbacks.onTextScaleChange(scale + TEXT_SCALE_STEP) }, modifier = Modifier.semantics { contentDescription = textLarger }) { Text("+") }
            }
            TextButton(onClick = callbacks.onImportLauncherFile) { Text(stringResource(R.string.settings_import_launcher)) }
        }

        Label(R.string.settings_art_style)
        ChoiceRow(stringResource(R.string.settings_art_grayscale), settings.artStyle == ArtStyle.Grayscale) { callbacks.onArtStyleChange(ArtStyle.Grayscale) }
        ChoiceRow(stringResource(R.string.settings_art_original), settings.artStyle == ArtStyle.Original) { callbacks.onArtStyleChange(ArtStyle.Original) }

        Section(R.string.settings_section_downloads)
        val auto = settings.autoDownloadCount
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (auto == 0) stringResource(R.string.settings_auto_download_off) else pluralStringResource(R.plurals.settings_auto_download, auto, auto),
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = auto > 0, onClick = { callbacks.onAutoDownloadChange(auto - 1) }, modifier = Modifier.semantics { contentDescription = autoFewer }) { Text("−") }
            TextButton(enabled = auto < MAX_AUTO_DOWNLOAD, onClick = { callbacks.onAutoDownloadChange(auto + 1) }, modifier = Modifier.semantics { contentDescription = autoMore }) { Text("+") }
        }
        SwitchRow(stringResource(R.string.settings_wifi_only), settings.wifiOnly, callbacks.onWifiOnlyChange)
        SwitchRow(stringResource(R.string.settings_charging_only), settings.chargingOnly, callbacks.onChargingOnlyChange)
        SwitchRow(stringResource(R.string.settings_delete_played), settings.deleteAfterPlayed, callbacks.onDeleteAfterPlayedChange)

        Section(R.string.settings_section_refresh)
        REFRESH_HOUR_CHOICES.forEach { hours ->
            ChoiceRow(
                if (hours == 0) stringResource(R.string.settings_refresh_manual) else pluralStringResource(R.plurals.settings_refresh_hours, hours, hours),
                settings.refreshIntervalHours == hours,
            ) { callbacks.onRefreshHoursChange(hours) }
        }

        Section(R.string.settings_section_subscriptions)
        Muted(R.string.settings_opml_hint)
        TextButton(onClick = callbacks.onImportOpml, enabled = opmlProgress == null) { Text(stringResource(R.string.settings_opml_import)) }
        TextButton(onClick = callbacks.onExportOpml) { Text(stringResource(R.string.settings_opml_export)) }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = includePrivate, role = Role.Checkbox) { callbacks.onIncludePrivateChange(!includePrivate) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = includePrivate, onCheckedChange = null)
            Text(stringResource(R.string.settings_opml_include_private), modifier = Modifier.padding(start = 12.dp))
        }
        opmlProgress?.let { (done, total) -> Text(stringResource(R.string.opml_progress, done, total), style = MaterialTheme.typography.bodyMedium) }
        opmlOutcome?.let { Text(opmlMessage(it), style = MaterialTheme.typography.bodyMedium) }

        Section(R.string.settings_section_backup)
        Muted(R.string.settings_backup_hint)
        TextButton(onClick = callbacks.onExport) { Text(stringResource(R.string.settings_export)) }
        TextButton(onClick = callbacks.onImport) { Text(stringResource(R.string.settings_import)) }
        backupMessage?.let { Text(stringResource(it.textRes()), style = MaterialTheme.typography.bodyMedium) }

        Section(R.string.settings_section_about)
        TextButton(onClick = callbacks.onOpenLicences) { Text(stringResource(R.string.settings_licences)) }
    }

    if (importPending) {
        AlertDialog(
            onDismissRequest = callbacks.onCancelImport,
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_body)) },
            confirmButton = { TextButton(onClick = callbacks.onConfirmImport) { Text(stringResource(R.string.backup_import_confirm)) } },
            dismissButton = { TextButton(onClick = callbacks.onCancelImport) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.settings_theme_system
    ThemeMode.Light -> R.string.settings_theme_light
    ThemeMode.Dark -> R.string.settings_theme_dark
}

private fun TypefaceChoice.labelRes(): Int = when (this) {
    TypefaceChoice.System -> R.string.settings_typeface_system
    TypefaceChoice.Condensed -> R.string.settings_typeface_condensed
    TypefaceChoice.Serif -> R.string.settings_typeface_serif
    TypefaceChoice.Monospace -> R.string.settings_typeface_monospace
    TypefaceChoice.Cursive -> R.string.settings_typeface_cursive
    TypefaceChoice.Casual -> R.string.settings_typeface_casual
    TypefaceChoice.Vt323 -> R.string.settings_typeface_vt323
    TypefaceChoice.PlexMono -> R.string.settings_typeface_plex_mono
    TypefaceChoice.SpaceMono -> R.string.settings_typeface_space_mono
    TypefaceChoice.JetBrainsMono -> R.string.settings_typeface_jetbrains_mono
    TypefaceChoice.Inter -> R.string.settings_typeface_inter
    TypefaceChoice.Atkinson -> R.string.settings_typeface_atkinson
}

private fun BackupMessage.textRes(): Int = when (this) {
    BackupMessage.Exported -> R.string.backup_exported
    BackupMessage.Imported -> R.string.backup_imported
    BackupMessage.NotJson -> R.string.backup_not_json
    BackupMessage.NotPodcasterBackup -> R.string.backup_not_podcaster
    BackupMessage.NewerVersion -> R.string.backup_newer_version
    BackupMessage.CouldNotRead -> R.string.backup_could_not_read
    BackupMessage.CouldNotWrite -> R.string.backup_could_not_write
    BackupMessage.LauncherImported -> R.string.backup_launcher_imported
    BackupMessage.NotLauncherFile -> R.string.backup_not_launcher
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Section(titleRes: Int) {
    Spacer(Modifier.height(32.dp))
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Muted(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
}

@Composable
private fun opmlMessage(o: OpmlOutcome): String = when {
    o.notOpml -> stringResource(R.string.opml_not_opml)
    o.couldNotRead -> stringResource(R.string.backup_could_not_read)
    o.exported -> stringResource(R.string.opml_exported)
    o.exportFailed -> stringResource(R.string.backup_could_not_write)
    else -> stringResource(R.string.opml_result, o.added, o.skipped, o.failed)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
