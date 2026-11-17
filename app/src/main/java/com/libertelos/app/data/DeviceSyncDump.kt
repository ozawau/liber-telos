package com.libertelos.app.data

import android.content.Context
import android.util.Log
import com.libertelos.app.security.DbKeyStore
import com.libertelos.app.security.SqlCipherSupport
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * 调试同步：存在 [REQUEST_FILE] 时，把当前加密账本导出为明文 SQLite，
 * 供 adb 拉到另一台设备后再由对方启动时重新加密。
 *
 * 触发：`run-as … mkdir -p files/sync && touch files/sync/export_plain`
 * 产物：`files/sync/liber_telos_plain.db` + `export_plain.done`
 */
object DeviceSyncDump {
    private const val TAG = "DeviceSyncDump"
    private const val DIR = "sync"
    private const val REQUEST_FILE = "export_plain"
    private const val DONE_FILE = "export_plain.done"
    private const val OUT_DB = "liber_telos_plain.db"

    fun runIfRequested(context: Context) {
        val syncDir = File(context.filesDir, DIR)
        val request = File(syncDir, REQUEST_FILE)
        if (!request.exists()) return

        runCatching {
            SqlCipherSupport.loadLibs(context)
            val dbFile = LedgerStorage.databaseFile(context)
            require(dbFile.exists()) { "ledger missing: ${dbFile.absolutePath}" }
            val dbKey = DbKeyStore(context).getOrCreateDbKey()
            val pass = SqlCipherSupport.passphraseString(dbKey)

            // 先 checkpoint，把 WAL 合进主库
            val enc = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                pass,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            try {
                enc.rawExecSQL("PRAGMA wal_checkpoint(FULL);")
            } finally {
                runCatching { enc.close() }
            }

            syncDir.mkdirs()
            val out = File(syncDir, OUT_DB)
            if (out.exists()) out.delete()

            // 建空明文库，再从加密库 export
            SQLiteDatabase.openOrCreateDatabase(out.absolutePath, "", null).close()
            val outPath = out.absolutePath.replace("'", "''")
            val passSql = pass.replace("'", "''")
            // sqlcipher_export 需要可写连接（ATTACH / 临时写）
            val src = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                pass,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            try {
                src.rawExecSQL("ATTACH DATABASE '$outPath' AS plain KEY '';")
                src.rawExecSQL("SELECT sqlcipher_export('plain');")
                src.rawExecSQL("DETACH DATABASE plain;")
            } finally {
                runCatching { src.close() }
            }

            require(out.exists() && out.length() > 0L) { "plain export empty" }
            require(SqlCipherSupport.canReadWithEmptyPassphrase(out)) {
                "plain export still encrypted"
            }

            val counts = SQLiteDatabase.openDatabase(
                out.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                fun count(table: String): Long {
                    db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                        c.moveToFirst()
                        return c.getLong(0)
                    }
                }
                "accounts=${count("accounts")} categories=${count("categories")} " +
                    "transactions=${count("transactions")}"
            }

            File(syncDir, DONE_FILE).writeText(
                "ok size=${out.length()} $counts\n"
            )
            request.delete()
            Log.i(TAG, "plain export ready: $counts")
        }.onFailure {
            Log.e(TAG, "plain export failed", it)
            runCatching {
                syncDir.mkdirs()
                File(syncDir, DONE_FILE).writeText("error: ${it.message}\n")
                request.delete()
            }
        }
    }
}
