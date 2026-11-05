package se.anders.athenapodcaster.data.launcher

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.data.settings.TypefaceChoice
import se.anders.athenapodcaster.data.settings.ThemeMode
import se.anders.athenapodcaster.data.settings.clampTextScale

/**
 * What Athena Launcher publishes (FR-062). Each field is null when the launcher gave nothing usable for it,
 * so a bad or unknown value never breaks the look: that one field falls back to the local setting.
 */
data class LauncherAppearance(
    val themeMode: ThemeMode?,
    val typeface: TypefaceChoice?,
    val textScalePercent: Int?,
)

/** Where the launcher's read-only appearance values live (launcher spec FR-070). */
object LauncherBridge {
    const val AUTHORITY = "se.anders.athenalauncher.appearance"
    const val COL_THEME_MODE = "themeMode"
    const val COL_TYPEFACE = "typeface"
    const val COL_TEXT_SCALE = "textScalePercent"
    val uri: Uri get() = Uri.parse("content://$AUTHORITY/appearance")
}

/** Turns the raw column values into a [LauncherAppearance]: unknown names become null, the text size is clamped. */
fun parseLauncherAppearance(themeMode: String?, typeface: String?, textScalePercent: Int?): LauncherAppearance = LauncherAppearance(
    themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode },
    typeface = TypefaceChoice.entries.firstOrNull { it.name == typeface },
    textScalePercent = textScalePercent?.let(::clampTextScale),
)

interface AppearanceSource {
    /** The launcher's current values, or null while the launcher is missing, too old or refuses access. */
    val appearance: StateFlow<LauncherAppearance?>

    /** Look again now (for example when the app comes to the foreground). */
    fun refresh()
}

/**
 * Reads the launcher's appearance through its content provider and follows changes with a ContentObserver.
 * Every failure (no launcher, old launcher, permission not granted, odd data) just yields null: the app then
 * uses its own settings (FR-062). Nothing is ever written to the launcher.
 */
class LauncherAppearanceSource(context: Context, private val uri: Uri = LauncherBridge.uri) : AppearanceSource {
    private val resolver = context.applicationContext.contentResolver
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _appearance = MutableStateFlow<LauncherAppearance?>(null)
    override val appearance: StateFlow<LauncherAppearance?> = _appearance.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        try { resolver.registerContentObserver(uri, false, observer) } catch (_: Exception) { /* no provider: nothing to watch */ }
        refresh()
    }

    override fun refresh() {
        scope.launch { _appearance.value = load() }
    }

    private fun load(): LauncherAppearance? = try {
        resolver.query(uri, null, null, null, null)?.use { c -> if (c.moveToFirst()) read(c) else null }
    } catch (_: Exception) {
        null // SecurityException (permission not granted), IllegalArgumentException (no such provider), anything else
    }

    private fun read(c: Cursor): LauncherAppearance = parseLauncherAppearance(
        themeMode = c.stringOrNull(LauncherBridge.COL_THEME_MODE),
        typeface = c.stringOrNull(LauncherBridge.COL_TYPEFACE),
        textScalePercent = c.intOrNull(LauncherBridge.COL_TEXT_SCALE),
    )

    private fun Cursor.stringOrNull(name: String): String? = getColumnIndex(name).takeIf { it >= 0 }?.let { getString(it) }
    private fun Cursor.intOrNull(name: String): Int? = getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) null else getInt(it) }
}
