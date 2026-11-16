package com.libertelos.app.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import org.json.JSONObject
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * 一次性导入 2013–2018 techlog 流水。
 * 只在 [files/import/techlog_historical.json] 存在且尚未导入时运行。
 * 直接插入流水，不调用会改余额的记账入口。
 */
object TechlogHistoricalImporter {
    private const val TAG = "TechlogImport"
    private const val PREFS = "liber_telos_prefs"
    private const val DONE = "techlog_historical_import_v1"
    private const val HIDDEN_DONE = "techlog_historical_accounts_hidden_v1"
    private const val ZEROED_DONE = "techlog_historical_accounts_zeroed_v1"
    private val zeroAt: Long = ZonedDateTime.of(
        2019, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Shanghai")
    ).toInstant().toEpochMilli()
    private const val RELATIVE = "import/techlog_historical.json"

    /** 本次历史导入新建的账户。已有账户不在此列。 */
    private val importedAccountNames = setOf(
        "港币",
        "西安交行储蓄卡",
        "内蒙古中行储蓄卡",
        "深圳招商银行储蓄卡",
        "京东商城",
        "房租押金",
        "交行太平洋卡",
        "挖财宝",
        "宜定盈",
        "已注销账户1",
        "已注销账户2",
        "已注销账户3",
        "已注销账户4",
        "已注销账户5",
        "已注销账户6",
        "已注销账户7",
        "已注销账户8",
        "已注销账户9",
        "已注销账户10",
        "已注销账户11",
        "已注销账户12",
        "已注销账户13",
        "已注销账户14",
        "已注销账户15",
        "已注销账户16",
        "已注销账户17",
        "已注销账户18"
    )

    private val parentColors = listOf(
        0xFFC44D7A, 0xFFE07A3D, 0xFF5B6B8C, 0xFF2F6FED,
        0xFF0EA5A4, 0xFF8B5CF6, 0xFFDC2626, 0xFF64748B,
        0xFF0D4F4F, 0xFF15803D, 0xFF2563EB, 0xFF475569
    )

    suspend fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DONE, false)) return
        val file = File(context.filesDir, RELATIVE)
        if (!file.exists()) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction {
                import(db, JSONObject(file.readText()))
            }
            prefs.edit().putBoolean(DONE, true).commit()
            file.renameTo(File(file.parentFile, "techlog_historical.json.done"))
            File(context.filesDir, "import/techlog_historical_report.txt").writeText(report)
            Log.i(TAG, "historical import ok\n$report")
        } catch (t: Throwable) {
            Log.e(TAG, "historical import failed", t)
            File(context.filesDir, "import/techlog_historical_error.txt").writeText(
                t.stackTraceToString()
            )
        }
    }

    /** 把本次导入新建的账户标为隐藏，不改余额、不改是否计入总账。 */
    suspend fun hideImportedAccounts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(HIDDEN_DONE, false)) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction {
                val dao = db.accountDao()
                val accounts = dao.listAll()
                val matched = accounts.filter { it.name in importedAccountNames }
                matched.filter { !it.hidden }.forEach { account ->
                    dao.update(account.copy(hidden = true))
                }
                val missing = importedAccountNames - accounts.map { it.name }.toSet()
                buildString {
                    append("已隐藏导入账户 ").append(matched.size).append('\n')
                    matched.sortedBy { it.name }.forEach { account ->
                        append(account.name).append('\n')
                    }
                    if (missing.isNotEmpty()) {
                        append("未找到\n")
                        missing.sorted().forEach { append(it).append('\n') }
                    }
                }
            }
            prefs.edit().putBoolean(HIDDEN_DONE, true).commit()
            File(context.filesDir, "import/techlog_hide_accounts_report.txt").writeText(report)
            Log.i(TAG, report)
        } catch (t: Throwable) {
            Log.e(TAG, "hide imported accounts failed", t)
            File(context.filesDir, "import/techlog_hide_accounts_error.txt").writeText(
                t.stackTraceToString()
            )
        }
    }

    /**
     * 导入时新建的账户，在 2019-01-01 把余额调到 0。
     * 只改这些账户的余额字段，并补一条余额调整记录；已有账户的余额不动。
     */
    suspend fun zeroImportedAccounts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ZEROED_DONE, false)) return
        val db = AppDatabase.get(context)
        try {
            val report = db.withTransaction {
                val accountDao = db.accountDao()
                val categoryDao = db.categoryDao()
                val transactionDao = db.transactionDao()
                val accounts = accountDao.listAll()
                val before = accounts.associate { it.id to it.balanceCents }
                val imported = accounts.filter { it.name in importedAccountNames }
                val expenseLeaf = categoryDao.getProtectedLeaf(TxnType.EXPENSE)
                    ?: error("缺少受保护分类：其他支出")
                val incomeLeaf = categoryDao.getProtectedLeaf(TxnType.INCOME)
                    ?: error("缺少受保护分类：其他收入")
                val lines = StringBuilder()
                imported.filter { it.balanceCents != 0L }.forEach { account ->
                    val balance = account.balanceCents
                    val direction = if (balance > 0) expenseLeaf else incomeLeaf
                    transactionDao.insert(
                        Transaction(
                            amountCents = abs(balance),
                            type = TxnType.BALANCE_ADJUSTMENT,
                            categoryId = direction.id,
                            accountId = account.id,
                            note = "余额调整到 0",
                            occurredAt = zeroAt,
                            createdAt = zeroAt,
                            updatedAt = zeroAt
                        )
                    )
                    accountDao.update(account.copy(balanceCents = 0L))
                    lines.append(account.name)
                        .append('\t')
                        .append(yuan(balance))
                        .append(" -> 0.00\n")
                }
                val after = accountDao.listAll()
                val drifted = after.filter { account ->
                    account.name !in importedAccountNames &&
                        before[account.id] != account.balanceCents
                }
                if (drifted.isNotEmpty()) {
                    error(
                        "现有账户余额发生变化：" + drifted.joinToString {
                            "${it.name} ${before[it.id]} -> ${it.balanceCents}"
                        }
                    )
                }
                val still = after.filter {
                    it.name in importedAccountNames && it.balanceCents != 0L
                }
                if (still.isNotEmpty()) {
                    error("导入账户余额未清零：" + still.joinToString { it.name })
                }
                buildString {
                    append("导入账户已于 2019-01-01 调到 0\n")
                    if (lines.isEmpty()) append("没有非零余额\n") else append(lines)
                }
            }
            prefs.edit().putBoolean(ZEROED_DONE, true).commit()
            File(context.filesDir, "import/techlog_zero_accounts_report.txt").writeText(report)
            Log.i(TAG, report)
        } catch (t: Throwable) {
            Log.e(TAG, "zero imported accounts failed", t)
            File(context.filesDir, "import/techlog_zero_accounts_error.txt").writeText(
                t.stackTraceToString()
            )
        }
    }

    private suspend fun import(db: AppDatabase, payload: JSONObject): String {
        val accountDao = db.accountDao()
        val categoryDao = db.categoryDao()
        val transactionDao = db.transactionDao()

        val existing = accountDao.listAll()
        val beforeBalances = existing.associate { it.id to it.balanceCents }
        val beforeCount = transactionDao.listAll().size
        val byName = existing.associate { it.name to it.id }.toMutableMap()

        val required = payload.getJSONArray("requiredAccounts")
        val missing = buildList {
            for (i in 0 until required.length()) {
                val name = required.getString(i)
                if (name !in byName) add(name)
            }
        }
        if (missing.isNotEmpty()) {
            error(
                "缺少现有账户 $missing。现有账户：${existing.joinToString { it.name }}"
            )
        }

        val categories = categoryDao.listAll().toMutableList()
        val newLines = StringBuilder()
        val created = payload.getJSONArray("newAccounts")
        for (i in 0 until created.length()) {
            val item = created.getJSONObject(i)
            val name = item.getString("name")
            if (name in byName) error("账户已存在，停止导入以免重复：$name")
            val kind = AccountKind.valueOf(item.getString("kind"))
            val balance = item.getLong("balanceCents")
            val id = accountDao.insert(
                Account(
                    name = name,
                    kind = kind,
                    currency = item.optString("currency", "CNY").ifBlank { "CNY" },
                    cardNumber = item.optString("cardNumber", ""),
                    issuer = "",
                    remark = item.optString("remark", ""),
                    iconKey = item.optString("iconKey", "cash").ifBlank { "cash" },
                    includeInTotal = true,
                    hidden = false,
                    balanceCents = balance,
                    createdAt = item.getLong("createdAt")
                )
            )
            byName[name] = id
            newLines.append(name)
                .append('\t')
                .append(kind.name)
                .append('\t')
                .append(yuan(balance))
                .append('\n')
        }

        val txns = payload.getJSONArray("transactions")
        val typeCounts = linkedMapOf<String, Int>()
        for (i in 0 until txns.length()) {
            val item = txns.getJSONObject(i)
            val type = TxnType.valueOf(item.getString("type"))
            val occurredAt = item.getLong("occurredAt")
            val createdAt = if (item.has("createdAt")) item.getLong("createdAt") else occurredAt
            val note = item.optString("note", "")
            val amount = item.getLong("amountCents")
            require(amount >= 0L) { "金额为负 index=$i" }
            val txn = when (type) {
                TxnType.TRANSFER -> Transaction(
                    amountCents = amount,
                    type = type,
                    fromAccountId = accountId(byName, item.getString("fromAccount")),
                    toAccountId = accountId(byName, item.getString("toAccount")),
                    note = note,
                    occurredAt = occurredAt,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
                TxnType.BALANCE_ADJUSTMENT -> {
                    val direction = TxnType.valueOf(item.getString("adjustDirection"))
                    val category = categoryDao.getProtectedLeaf(direction)
                        ?: error("缺少受保护分类 $direction")
                    Transaction(
                        amountCents = amount,
                        type = type,
                        categoryId = category.id,
                        accountId = accountId(byName, item.getString("account")),
                        note = note,
                        occurredAt = occurredAt,
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                }
                TxnType.EXPENSE, TxnType.INCOME -> {
                    val categoryType = TxnType.valueOf(item.getString("categoryType"))
                    Transaction(
                        amountCents = amount,
                        type = type,
                        categoryId = ensureLeaf(
                            categoryDao,
                            categories,
                            categoryType,
                            item.getString("parent"),
                            item.getString("child")
                        ),
                        accountId = accountId(byName, item.getString("account")),
                        note = note,
                        occurredAt = occurredAt,
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                }
                TxnType.REIMBURSEMENT -> error("历史导入不写报销")
            }
            transactionDao.insert(txn)
            typeCounts[type.name] = (typeCounts[type.name] ?: 0) + 1
        }

        val after = accountDao.listAll()
        val drifted = after.filter { account ->
            val previous = beforeBalances[account.id] ?: return@filter false
            previous != account.balanceCents
        }
        if (drifted.isNotEmpty()) {
            error(
                "现有账户余额发生变化：" + drifted.joinToString {
                    "${it.name} ${beforeBalances[it.id]} -> ${it.balanceCents}"
                }
            )
        }
        require(beforeBalances.keys.all { id -> after.any { it.id == id } }) {
            "现有账户丢失"
        }
        for (i in 0 until created.length()) {
            val item = created.getJSONObject(i)
            val live = accountDao.getById(byName.getValue(item.getString("name")))
                ?: error("新账户丢失")
            require(live.balanceCents == item.getLong("balanceCents")) {
                "${live.name} 余额 ${live.balanceCents} != ${item.getLong("balanceCents")}"
            }
        }

        val afterCount = transactionDao.listAll().size
        return buildString {
            append("历史导入完成\n")
            append("流水 ").append(beforeCount).append(" -> ").append(afterCount).append('\n')
            append("本次写入 ").append(txns.length()).append('\n')
            typeCounts.forEach { (type, count) ->
                append(type).append('\t').append(count).append('\n')
            }
            append("现有账户余额未改变\n")
            existing.sortedBy { it.id }.forEach { account ->
                append(account.name)
                    .append('\t')
                    .append(yuan(account.balanceCents))
                    .append('\n')
            }
            append("新建账户\n")
            append(newLines)
        }
    }

    private fun accountId(byName: Map<String, Long>, name: String): Long =
        byName[name] ?: error("找不到账户：$name")

    private suspend fun ensureLeaf(
        categoryDao: CategoryDao,
        categories: MutableList<Category>,
        type: TxnType,
        parentName: String,
        childName: String
    ): Long {
        var parent = categories.firstOrNull {
            it.type == type && it.parentId == null && it.name == parentName
        }
        if (parent == null) {
            val sort = categoryDao.maxParentSortOrder(type) + 1
            val id = categoryDao.insert(
                Category(
                    name = parentName,
                    type = type,
                    parentId = null,
                    sortOrder = sort,
                    iconKey = "custom",
                    colorArgb = parentColors[sort.mod(parentColors.size)]
                )
            )
            parent = categoryDao.getById(id) ?: error("一级分类写入失败 $parentName")
            categories.add(parent)
        }
        val resolvedParent = parent ?: error("一级分类缺失 $parentName")
        val child = categories.firstOrNull {
            it.type == type && it.parentId == resolvedParent.id && it.name == childName
        }
        if (child != null) return child.id
        val sort = categoryDao.maxChildSortOrder(resolvedParent.id) + 1
        val id = categoryDao.insert(
            Category(
                name = childName,
                type = type,
                parentId = resolvedParent.id,
                sortOrder = sort,
                iconKey = resolvedParent.iconKey,
                colorArgb = resolvedParent.colorArgb
            )
        )
        val created = categoryDao.getById(id) ?: error("二级分类写入失败 $parentName/$childName")
        categories.add(created)
        return created.id
    }

    private fun yuan(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val value = abs(cents)
        return sign + (value / 100) + "." + (value % 100).toString().padStart(2, '0')
    }
}
