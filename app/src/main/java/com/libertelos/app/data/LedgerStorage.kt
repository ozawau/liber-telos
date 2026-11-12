package com.libertelos.app.data

import android.content.Context
import java.io.File

/**
 * 账本数据库独立目录：`files/ledger/`。
 * 部署安装 APK 时不得覆盖此目录下的任何文件。
 */
object LedgerStorage {
    const val DB_FILE_NAME = "liber_telos.db"
    private const val DIR_NAME = "ledger"

    fun directory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun databaseFile(context: Context): File =
        File(directory(context), DB_FILE_NAME)

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
}
