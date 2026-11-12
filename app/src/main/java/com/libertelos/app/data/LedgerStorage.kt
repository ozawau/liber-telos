package com.libertelos.app.data

import android.content.Context
import java.io.File

/**
 * 账本数据库目录。默认：`files/ledger/`。
 * 可切换到应用外部私有目录：`Android/data/<pkg>/files/ledger/`。
 * 部署安装 APK 时不得覆盖此目录下的任何文件。
 */
object LedgerStorage {
    const val DB_FILE_NAME = "liber_telos.db"
    private const val DIR_NAME = "ledger"
    private const val PREFS = "ledger_storage"
    private const val KEY_LOCATION = "location"

    enum class Location(val label: String) {
        INTERNAL("内部私有目录"),
        EXTERNAL_APP("外部应用目录")
    }

    fun preferredLocation(context: Context): Location {
        val raw = prefs(context).getString(KEY_LOCATION, Location.INTERNAL.name)
        return runCatching { Location.valueOf(raw ?: Location.INTERNAL.name) }
            .getOrDefault(Location.INTERNAL)
    }

    fun setPreferredLocation(context: Context, location: Location) {
        prefs(context).edit().putString(KEY_LOCATION, location.name).apply()
    }

    fun directory(context: Context, location: Location = preferredLocation(context)): File {
        val base = when (location) {
            Location.INTERNAL -> context.applicationContext.filesDir
            Location.EXTERNAL_APP ->
                context.applicationContext.getExternalFilesDir(null)
                    ?: context.applicationContext.filesDir
        }
        val dir = File(base, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun databaseFile(context: Context, location: Location = preferredLocation(context)): File =
        File(directory(context, location), DB_FILE_NAME)

    fun displayPath(context: Context): String =
        directory(context).absolutePath

    /**
     * 将库文件从 [from] 拷到 [to]（含 wal/shm）。返回是否拷贝了主库。
     */
    fun copyDatabaseFiles(context: Context, from: Location, to: Location): Boolean {
        if (from == to) return false
        val srcDir = directory(context, from)
        val dstDir = directory(context, to)
        var copied = false
        listOf("", "-wal", "-shm").forEach { suffix ->
            val src = File(srcDir, DB_FILE_NAME + suffix)
            val dst = File(dstDir, DB_FILE_NAME + suffix)
            if (src.exists() && src.length() > 0L) {
                src.copyTo(dst, overwrite = true)
                copied = copied || suffix.isEmpty()
            } else if (dst.exists() && suffix.isNotEmpty()) {
                dst.delete()
            }
        }
        return copied
    }

    /**
     * 若新目录尚无库，而旧版 `databases/liber_telos.db` 仍在，则迁移过来。
     */
    fun migrateFromLegacyIfNeeded(context: Context) {
        val target = databaseFile(context)
        if (target.exists() && target.length() > 0L) return

        val legacy = context.applicationContext.getDatabasePath(DB_FILE_NAME)
        if (!legacy.exists() || legacy.length() == 0L) return

        val dir = directory(context)
        listOf("", "-wal", "-shm").forEach { suffix ->
            val src = File(legacy.path + suffix)
            if (src.exists()) {
                src.copyTo(File(dir, DB_FILE_NAME + suffix), overwrite = true)
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
