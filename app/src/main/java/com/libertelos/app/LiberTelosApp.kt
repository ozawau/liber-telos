package com.libertelos.app

import android.app.Application
import com.libertelos.app.data.AppDatabase
import com.libertelos.app.data.CategoryMigrations
import com.libertelos.app.data.DefaultCategories
import com.libertelos.app.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiberTelosApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        repository = LedgerRepository(
            categoryDao = db.categoryDao(),
            accountDao = db.accountDao(),
            transactionDao = db.transactionDao()
        )
        appScope.launch {
            runCatching {
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
            }.onFailure {
                android.util.Log.e("LiberTelosApp", "startup migration failed", it)
            }
        }
    }
}
