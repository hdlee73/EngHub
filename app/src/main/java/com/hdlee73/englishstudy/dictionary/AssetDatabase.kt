package com.hdlee73.englishstudy.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Copies a bundled read-only SQLite asset to app storage and opens it.
 *
 * The cached copy is named after the installed app version, so installing an update replaces the
 * dictionary automatically; no version number has to be bumped by hand when the data changes.
 * The copy goes to a temporary file and is renamed only after it completes, so an interrupted first
 * launch can't leave a truncated database. A file that cannot be opened is deleted and copied again
 * once, and cached copies from other versions are removed to free storage.
 */
internal object AssetDatabase {
    @Suppress("DEPRECATION")
    private fun installedVersion(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) { 0L }

    fun open(context: Context, asset: String, prefix: String): SQLiteDatabase? {
        val file = context.getDatabasePath("${prefix}_${installedVersion(context)}.sqlite")
        file.parentFile?.listFiles()?.forEach { old ->
            if (old.name.startsWith(prefix) && old.name != file.name) old.delete()
        }
        repeat(2) {
            try {
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    val temporary = java.io.File(file.path + ".part")
                    context.assets.open(asset).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                    if (!temporary.renameTo(file)) { temporary.delete(); return null }
                }
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                return db
            } catch (_: Exception) {
                file.delete()
            }
        }
        return null
    }
}
