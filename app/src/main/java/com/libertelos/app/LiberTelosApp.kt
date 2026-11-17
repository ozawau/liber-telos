package com.libertelos.app

import android.app.Application
import android.content.Intent
import com.libertelos.app.data.AppDatabase
import com.libertelos.app.data.CategoryMigrations
import com.libertelos.app.data.ExpenseCategoryMerge
import com.libertelos.app.data.IncomeCategoryMerge
import com.libertelos.app.data.NoteEdits
import com.libertelos.app.data.DefaultCategories
import com.libertelos.app.data.LedgerRepository
import com.libertelos.app.data.LedgerStorage
import com.libertelos.app.data.DeviceSyncDump
import com.libertelos.app.data.TechlogHistoricalImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiberTelosApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // 必须在开库前：按需把加密账本导出明文，供 adb 同步到其他设备
        DeviceSyncDump.runIfRequested(this)
        bindRepository()
        appScope.launch {
            runCatching {
                val db = AppDatabase.get(this@LiberTelosApp)
                // 新设备：只种子分类；不创建账户、不写入流水
                DefaultCategories.seed(db.categoryDao())
                CategoryMigrations.migrateCategoryLabels(
                    categoryDao = db.categoryDao(),
                    transactionDao = db.transactionDao()
                )
                CategoryMigrations.migrateTransitCategories(
                    categoryDao = db.categoryDao(),
                    accountDao = db.accountDao(),
                    transactionDao = db.transactionDao()
                )
                repository.migrateBalanceAdjustments(
                    getSharedPreferences("liber_telos_prefs", MODE_PRIVATE)
                )
                TechlogHistoricalImporter.run(this@LiberTelosApp)
                TechlogHistoricalImporter.hideImportedAccounts(this@LiberTelosApp)
                TechlogHistoricalImporter.zeroImportedAccounts(this@LiberTelosApp)
                ExpenseCategoryMerge.run(this@LiberTelosApp)
                IncomeCategoryMerge.run(this@LiberTelosApp)
                NoteEdits.run(this@LiberTelosApp)
            }.onFailure {
                android.util.Log.e("LiberTelosApp", "startup migration failed", it)
            }
        }
    }

    private fun bindRepository() {
        val db = AppDatabase.get(this)
        repository = LedgerRepository(
            categoryDao = db.categoryDao(),
            accountDao = db.accountDao(),
            transactionDao = db.transactionDao()
        )
    }

    /**
     * 切换账本目录：关库 → 拷贝 → 写偏好 → 重启进程。
     */
    suspend fun relocateLedgerStorage(to: LedgerStorage.Location) {
        withContext(Dispatchers.IO) {
            val from = LedgerStorage.preferredLocation(this@LiberTelosApp)
            if (from == to) return@withContext
            val db = AppDatabase.get(this@LiberTelosApp)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            AppDatabase.closeAndClear()
            LedgerStorage.copyDatabaseFiles(this@LiberTelosApp, from, to)
            LedgerStorage.setPreferredLocation(this@LiberTelosApp, to)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) {
            startActivity(launch)
        }
        Runtime.getRuntime().exit(0)
    }
}
