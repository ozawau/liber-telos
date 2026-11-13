package com.libertelos.app.security

import android.content.Context
import android.util.Base64
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

object SqlCipherSupport {
    private const val TAG = "SqlCipherSupport"

    fun loadLibs(context: Context) {
        SQLiteDatabase.loadLibs(context.applicationContext)
    }

    fun supportFactory(dbKey: ByteArray): SupportFactory =
        SupportFactory(passphraseBytes(dbKey))

    fun passphraseBytes(dbKey: ByteArray): ByteArray =
        Base64.encode(dbKey, Base64.NO_WRAP)

    fun passphraseString(dbKey: ByteArray): String =
        Base64.encodeToString(dbKey, Base64.NO_WRAP)

    fun looksLikeSqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val header = ByteArray(16)
        file.inputStream().use { input ->
            if (input.read(header) < 16) return false
        }
        return header.decodeToString().startsWith("SQLite format 3")
    }

    /** 能否用空口令正常读到 sqlite_master（明文库为 true）。 */
    fun canReadWithEmptyPassphrase(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { c ->
                    c.moveToFirst()
                }
                true
            } finally {
                db.close()
            }
        }.getOrDefault(false)
    }

    fun needsEncryptionMigration(file: File): Boolean =
        file.exists() && file.length() > 0L && canReadWithEmptyPassphrase(file)

    /**
     * 将明文 SQLite 迁移为 SQLCipher 加密库（原地替换主文件，并清理 wal/shm）。
     */
    fun migratePlaintextToEncrypted(context: Context, dbFile: File, dbKey: ByteArray): Boolean {
        loadLibs(context)
        if (!needsEncryptionMigration(dbFile)) return false

        runCatching {
            val plain = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            plain.rawExecSQL("PRAGMA wal_checkpoint(FULL);")
            plain.close()
        }.onFailure {
            Log.w(TAG, "wal checkpoint before encrypt failed", it)
        }
        File(dbFile.path + "-wal").takeIf { it.exists() }?.delete()
        File(dbFile.path + "-shm").takeIf { it.exists() }?.delete()

        // 清理历史失败残留
        File(dbFile.parentFile, "${dbFile.name}.rekey_tmp").takeIf { it.exists() }?.delete()
        val tmp = File(dbFile.parentFile, "${dbFile.name}.enc_tmp")
        if (tmp.exists()) tmp.delete()

        val pass = passphraseString(dbKey)
        // 先创建空的加密库，再从明文 export 进去（部分机型 ATTACH 空文件会失败）
        SQLiteDatabase.openOrCreateDatabase(tmp.absolutePath, pass, null).close()

        val tmpPath = tmp.absolutePath.replace("'", "''")
        val passSql = pass.replace("'", "''")
        val plain = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            plain.rawExecSQL("ATTACH DATABASE '$tmpPath' AS encrypted KEY '$passSql';")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            plain.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            runCatching { plain.close() }
        }

        require(tmp.exists() && tmp.length() > 0L) { "encrypted export empty" }
        require(!canReadWithEmptyPassphrase(tmp)) {
            "export still readable without passphrase"
        }
        // 用正确口令应能读
        require(
            runCatching {
                val db = SQLiteDatabase.openDatabase(
                    tmp.absolutePath,
                    pass,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                try {
                    db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                    true
                } finally {
                    db.close()
                }
            }.getOrDefault(false)
        ) { "encrypted db not readable with passphrase" }

        listOf("", "-wal", "-shm").forEach { suffix ->
            File(dbFile.path + suffix).takeIf { it.exists() }?.delete()
        }
        if (!tmp.renameTo(dbFile)) {
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
        }
        Log.i(TAG, "plaintext ledger encrypted via sqlcipher_export")
        return true
    }
}
