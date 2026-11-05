package se.anders.athenapodcaster

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Debug builds only: a stand-in for Athena Launcher's appearance provider, used by the instrumented tests. It lives
 * in the app itself so that the tests and the provider share one process and its test values.
 * Serves whatever [row] holds, or throws when [deny] is set.
 */
class FakeLauncherProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        if (deny) throw SecurityException("permission not granted")
        return MatrixCursor(arrayOf("version", "themeMode", "typeface", "textScalePercent")).apply { row?.let { addRow(it) } }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        @Volatile var row: Array<Any?>? = arrayOf(1, "Dark", "Inter", 120)
        @Volatile var deny = false
        val uri: Uri = Uri.parse("content://se.anders.athenapodcaster.test.fakelauncher/appearance")
    }
}
