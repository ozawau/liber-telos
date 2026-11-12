package com.libertelos.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

class LedgerRepository(
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {
    fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()

    fun observeCategories(type: TxnType): Flow<List<Category>> =
        categoryDao.observeByType(type)

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun observeVisibleAccounts(): Flow<List<Account>> = accountDao.observeVisible()

    suspend fun getAccount(id: Long): Account? = accountDao.getById(id)

    suspend fun exportSnapshot(): ExportBundle = ExportBundle(
        accounts = accountDao.listAll(),
        categories = categoryDao.listAll(),
        transactions = transactionDao.listAll()
    )

    /**
     * 全量覆盖导入：先清空流水/账户/分类，再按导出包写回（保留原 id）。
     */
    suspend fun importBundle(bundle: ExportBundle) {
        transactionDao.deleteAll()
        accountDao.deleteAll()
        categoryDao.deleteAll()
        val parents = bundle.categories.filter { it.parentId == null }.sortedBy { it.id }
        val children = bundle.categories.filter { it.parentId != null }.sortedBy { it.id }
        if (parents.isNotEmpty()) categoryDao.insertAll(parents)
        if (children.isNotEmpty()) categoryDao.insertAll(children)
        if (bundle.accounts.isNotEmpty()) accountDao.insertAll(bundle.accounts)
        if (bundle.transactions.isNotEmpty()) transactionDao.insertAll(bundle.transactions)
    }

    suspend fun upsertAccount(
        account: Account,
        createBalanceAdjustment: Boolean = false,
        previousBalanceCents: Long? = null
    ): Long {
        val accountId = if (account.id == 0L) {
            accountDao.insert(account)
        } else {
            accountDao.update(account)
            account.id
        }
        if (createBalanceAdjustment && previousBalanceCents != null) {
            val delta = account.balanceCents - previousBalanceCents
            if (delta != 0L) {
                createBalanceAdjustmentTransaction(accountId, delta)
            }
        }
        return accountId
    }

    private suspend fun createBalanceAdjustmentTransaction(accountId: Long, deltaCents: Long) {
        // 账户余额已由 upsertAccount 直接写入；流水仅作记录，不改余额、不计入收支
        val now = System.currentTimeMillis()
        val category = if (deltaCents > 0) {
            categoryDao.getProtectedLeaf(TxnType.INCOME)
                ?: error("缺少受保护分类：其他收入")
        } else {
            categoryDao.getProtectedLeaf(TxnType.EXPENSE)
                ?: error("缺少受保护分类：其他支出")
        }
        transactionDao.insert(
            Transaction(
                amountCents = kotlin.math.abs(deltaCents),
                type = TxnType.BALANCE_ADJUSTMENT,
                categoryId = category.id,
                accountId = accountId,
                note = "账户余额调整",
                occurredAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun isLiability(kind: AccountKind): Boolean =
        kind == AccountKind.CREDIT || kind == AccountKind.VIRTUAL_LIABILITY

    /** 计算某笔流水对单个账户余额的增量（资产账户与负债账户符号相反） */
    private fun balanceDelta(
        kind: AccountKind,
        type: TxnType,
        amountCents: Long,
        asFrom: Boolean = false,
        asTo: Boolean = false
    ): Long {
        val liability = isLiability(kind)
        return when (type) {
            TxnType.EXPENSE -> if (liability) amountCents else -amountCents
            TxnType.INCOME, TxnType.REIMBURSEMENT ->
                if (liability) -amountCents else amountCents
            TxnType.TRANSFER -> when {
                asFrom -> if (liability) amountCents else -amountCents
                asTo -> if (liability) -amountCents else amountCents
                else -> 0L
            }
            // 余额已由账户字段直接写入，流水本身不再驱动余额
            TxnType.BALANCE_ADJUSTMENT -> 0L
        }
    }

    private suspend fun adjustAccountBalance(accountId: Long?, delta: Long) {
        if (accountId == null || delta == 0L) return
        val account = accountDao.getById(accountId) ?: return
        accountDao.update(account.copy(balanceCents = account.balanceCents + delta))
    }

    private suspend fun applyTransactionBalance(txn: Transaction, sign: Int) {
        require(sign == 1 || sign == -1)
        when (txn.type) {
            TxnType.EXPENSE, TxnType.INCOME, TxnType.REIMBURSEMENT -> {
                val account = txn.accountId?.let { accountDao.getById(it) } ?: return
                val delta = balanceDelta(account.kind, txn.type, txn.amountCents) * sign
                adjustAccountBalance(account.id, delta)
            }
            TxnType.TRANSFER -> {
                txn.fromAccountId?.let { fromId ->
                    val from = accountDao.getById(fromId) ?: return@let
                    val delta = balanceDelta(
                        from.kind,
                        TxnType.TRANSFER,
                        txn.amountCents,
                        asFrom = true
                    ) * sign
                    adjustAccountBalance(from.id, delta)
                }
                txn.toAccountId?.let { toId ->
                    val to = accountDao.getById(toId) ?: return@let
                    val delta = balanceDelta(
                        to.kind,
                        TxnType.TRANSFER,
                        txn.amountCents,
                        asTo = true
                    ) * sign
                    adjustAccountBalance(to.id, delta)
                }
            }
            TxnType.BALANCE_ADJUSTMENT -> Unit
        }
    }

    suspend fun getTransaction(id: Long): Transaction? = transactionDao.getById(id)

    suspend fun getTransactionDetails(id: Long): TransactionWithDetails? {
        val txn = transactionDao.getById(id) ?: return null
        return getTransactionDetailsOnce(txn)
    }

    private suspend fun getTransactionDetailsOnce(txn: Transaction): TransactionWithDetails {
        val category = txn.categoryId?.let { categoryDao.getById(it) }
        val parent = category?.parentId?.let { categoryDao.getById(it) }
        val linked = txn.linkedTransactionId?.let { linkedId ->
            transactionDao.getById(linkedId)?.let { linkedTxn ->
                val linkedCat = linkedTxn.categoryId?.let { categoryDao.getById(it) }
                val linkedParent = linkedCat?.parentId?.let { categoryDao.getById(it) }
                TransactionWithDetails(
                    transaction = linkedTxn,
                    category = linkedCat,
                    parentCategory = linkedParent,
                    account = linkedTxn.accountId?.let { accountDao.getById(it) },
                    fromAccount = linkedTxn.fromAccountId?.let { accountDao.getById(it) },
                    toAccount = linkedTxn.toAccountId?.let { accountDao.getById(it) }
                )
            }
        }
        return TransactionWithDetails(
            transaction = txn,
            category = category,
            parentCategory = parent,
            account = txn.accountId?.let { accountDao.getById(it) },
            fromAccount = txn.fromAccountId?.let { accountDao.getById(it) },
            toAccount = txn.toAccountId?.let { accountDao.getById(it) },
            linkedExpense = linked
        )
    }

    suspend fun deleteAccount(account: Account) {
        categoryDao.clearDefaultAccount(account.id)
        transactionDao.clearAccount(account.id)
        transactionDao.clearFromAccount(account.id)
        transactionDao.clearToAccount(account.id)
        accountDao.delete(account)
    }

    suspend fun addParentCategory(type: TxnType, name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val sort = categoryDao.maxParentSortOrder(type) + 1
        val color = ParentColors.next(sort)
        return categoryDao.insert(
            Category(
                name = trimmed,
                type = type,
                parentId = null,
                sortOrder = sort,
                iconKey = "custom",
                colorArgb = color
            )
        )
    }

    suspend fun addChildCategory(
        parentId: Long,
        name: String,
        defaultAccountId: Long? = null
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val parent = categoryDao.getById(parentId) ?: error("parent missing")
        val sort = categoryDao.maxChildSortOrder(parentId) + 1
        return categoryDao.insert(
            Category(
                name = trimmed,
                type = parent.type,
                parentId = parentId,
                sortOrder = sort,
                iconKey = parent.iconKey,
                colorArgb = parent.colorArgb,
                defaultAccountId = defaultAccountId
            )
        )
    }

    suspend fun updateChildCategory(
        category: Category,
        name: String,
        defaultAccountId: Long?
    ) {
        require(category.isLeaf)
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        categoryDao.update(
            category.copy(
                name = trimmed,
                defaultAccountId = defaultAccountId
            )
        )
    }

    /**
     * 将二级分类挪到另一个一级分类下。
     * 只改 categories.parentId（及排序/外观），流水仍引用原 categoryId，无需同步。
     */
    suspend fun moveChildCategory(category: Category, newParentId: Long) {
        require(category.isLeaf)
        if (category.parentId == newParentId) return
        val parent = categoryDao.getById(newParentId) ?: error("parent missing")
        require(parent.isParent)
        require(parent.type == category.type)
        val sort = categoryDao.maxChildSortOrder(newParentId) + 1
        categoryDao.update(
            category.copy(
                parentId = newParentId,
                sortOrder = sort,
                iconKey = parent.iconKey,
                colorArgb = parent.colorArgb
            )
        )
    }

    suspend fun deleteCategory(category: Category) {
        if (category.isProtected) {
            error("系统分类不可删除")
        }
        if (category.isParent) {
            val children = categoryDao.getChildren(category.id)
            if (children.any { it.isProtected }) {
                error("该分类下包含系统分类，不可删除")
            }
            val ids = children.map { it.id } + category.id
            if (ids.isNotEmpty()) {
                transactionDao.clearCategories(ids)
            }
            categoryDao.deleteChildren(category.id)
            categoryDao.deleteById(category.id)
        } else {
            transactionDao.clearCategory(category.id)
            categoryDao.deleteById(category.id)
        }
    }

    private object ParentColors {
        private val palette = listOf(
            0xFFC44D7A, 0xFFE07A3D, 0xFF5B6B8C, 0xFF2F6FED,
            0xFF0EA5A4, 0xFF8B5CF6, 0xFFDC2626, 0xFF64748B,
            0xFF0D4F4F, 0xFF15803D, 0xFF2563EB, 0xFF475569
        )

        fun next(index: Int): Long = palette[index.mod(palette.size)]
    }

    fun observeMonthSummary(year: Int, month: Int): Flow<MonthSummary> {
        val (start, end) = monthRange(year, month)
        return observeRangeSummary(start, end)
    }

    fun observeRangeSummary(startMs: Long, endMs: Long): Flow<MonthSummary> {
        return combine(
            transactionDao.observeSumByType(TxnType.INCOME, startMs, endMs),
            transactionDao.observeSumByType(TxnType.EXPENSE, startMs, endMs),
            transactionDao.observeSumByType(TxnType.REIMBURSEMENT, startMs, endMs)
        ) { income, expense, reimbursement ->
            MonthSummary(
                incomeCents = income,
                expenseCents = (expense - reimbursement).coerceAtLeast(0)
            )
        }
    }

    /** 今天 / 本周 / 本月收支；流水变更时按当前日历重算区间 */
    fun observeIncomeExpenseReport(): Flow<IncomeExpenseReport> {
        return transactionDao.observeAll().map { txns ->
            val (dayStart, dayEnd) = todayRange()
            val (weekStart, weekEnd) = thisWeekRange()
            val (monthStart, monthEnd) = thisMonthRange()
            val (yearStart, yearEnd) = thisYearRange()
            IncomeExpenseReport(
                today = summarizeInRange(txns, dayStart, dayEnd),
                week = summarizeInRange(txns, weekStart, weekEnd),
                month = summarizeInRange(txns, monthStart, monthEnd),
                year = summarizeInRange(txns, yearStart, yearEnd)
            )
        }
    }

    fun observeTransactions(): Flow<List<Transaction>> = transactionDao.observeAll()

    fun periodRange(period: ReportPeriod, now: Long = System.currentTimeMillis()): Pair<Long, Long> =
        when (period) {
            ReportPeriod.DAY -> todayRange(now)
            ReportPeriod.WEEK -> thisWeekRange(now)
            ReportPeriod.MONTH -> thisMonthRange(now)
            ReportPeriod.YEAR -> thisYearRange(now)
        }

    fun buildCategoryRanking(
        details: List<TransactionWithDetails>,
        period: ReportPeriod,
        type: TxnType,
        limit: Int = 5,
        now: Long = System.currentTimeMillis()
    ): CategoryRanking {
        require(type == TxnType.INCOME || type == TxnType.EXPENSE)
        val (start, end) = periodRange(period, now)
        val reimbursedByExpense = details
            .filter { it.transaction.type == TxnType.REIMBURSEMENT }
            .groupBy { it.transaction.linkedTransactionId }
            .mapValues { (_, list) -> list.sumOf { it.transaction.amountCents } }

        val inRange = details.filter { item ->
            val t = item.transaction
            t.occurredAt >= start && t.occurredAt < end && t.type == type && item.category != null
        }

        val grouped = inRange.groupBy { item -> item.parentCategory ?: item.category!! }
        val ranked = grouped.map { (category, list) ->
            val total = when (type) {
                TxnType.EXPENSE -> list.sumOf { item ->
                    val reimbursed = reimbursedByExpense[item.transaction.id] ?: 0L
                    (item.transaction.amountCents - reimbursed).coerceAtLeast(0)
                }
                else -> list.sumOf { it.transaction.amountCents }
            }
            category to total
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val totalCents = ranked.sumOf { it.second }
        val items = ranked.take(limit).map { (category, cents) ->
            CategoryRankItem(
                category = category,
                totalCents = cents,
                ratio = if (totalCents > 0) cents.toFloat() / totalCents.toFloat() else 0f
            )
        }
        return CategoryRanking(
            type = type,
            period = period,
            txnCount = inRange.size,
            totalCents = totalCents,
            items = items
        )
    }

    fun buildTrendSeries(
        txns: List<Transaction>,
        period: ReportPeriod,
        now: Long = System.currentTimeMillis()
    ): List<TrendPoint> {
        val buckets = trendBuckets(period, now)
        if (buckets.isEmpty()) return emptyList()
        val rangeStart = buckets.first().startMs
        val rangeEnd = buckets.last().endMs
        val incomeByBucket = LongArray(buckets.size)
        val expenseByBucket = LongArray(buckets.size)
        val reimbursementByBucket = LongArray(buckets.size)
        txns.forEach { txn ->
            if (txn.occurredAt < rangeStart || txn.occurredAt >= rangeEnd) return@forEach
            val index = when (period) {
                ReportPeriod.DAY -> {
                    val hour = ((txn.occurredAt - rangeStart) / HOUR_MS).toInt()
                    hour.coerceIn(0, buckets.lastIndex)
                }
                ReportPeriod.YEAR -> {
                    Calendar.getInstance().apply { timeInMillis = txn.occurredAt }
                        .get(Calendar.MONTH)
                        .coerceIn(0, buckets.lastIndex)
                }
                else -> {
                    val day = ((txn.occurredAt - rangeStart) / DAY_MS).toInt()
                    day.coerceIn(0, buckets.lastIndex)
                }
            }
            when (txn.type) {
                TxnType.INCOME -> incomeByBucket[index] += txn.amountCents
                TxnType.EXPENSE -> expenseByBucket[index] += txn.amountCents
                TxnType.REIMBURSEMENT -> reimbursementByBucket[index] += txn.amountCents
                TxnType.TRANSFER, TxnType.BALANCE_ADJUSTMENT -> Unit
            }
        }
        return buckets.mapIndexed { index, bucket ->
            TrendPoint(
                startMs = bucket.startMs,
                label = bucket.label,
                incomeCents = incomeByBucket[index],
                expenseCents = (expenseByBucket[index] - reimbursementByBucket[index])
                    .coerceAtLeast(0)
            )
        }
    }

    private fun summarizeInRange(
        txns: List<Transaction>,
        startMs: Long,
        endMs: Long
    ): MonthSummary {
        var income = 0L
        var expense = 0L
        var reimbursement = 0L
        txns.forEach { txn ->
            if (txn.occurredAt < startMs || txn.occurredAt >= endMs) return@forEach
            when (txn.type) {
                TxnType.INCOME -> income += txn.amountCents
                TxnType.EXPENSE -> expense += txn.amountCents
                TxnType.REIMBURSEMENT -> reimbursement += txn.amountCents
                TxnType.TRANSFER, TxnType.BALANCE_ADJUSTMENT -> Unit
            }
        }
        return MonthSummary(
            incomeCents = income,
            expenseCents = (expense - reimbursement).coerceAtLeast(0)
        )
    }

    private data class TrendBucket(val startMs: Long, val endMs: Long, val label: String)

    private fun trendBuckets(period: ReportPeriod, now: Long): List<TrendBucket> {
        val dayFmt = java.text.SimpleDateFormat("M月d日", Locale.CHINA)
        val hourFmt = java.text.SimpleDateFormat("H:00", Locale.CHINA)
        return when (period) {
            ReportPeriod.DAY -> {
                val (start, _) = todayRange(now)
                (0 until 24).map { hour ->
                    val bucketStart = start + hour * HOUR_MS
                    TrendBucket(
                        startMs = bucketStart,
                        endMs = bucketStart + HOUR_MS,
                        label = hourFmt.format(bucketStart)
                    )
                }
            }
            ReportPeriod.WEEK -> {
                val (start, end) = thisWeekRange(now)
                buildDailyBuckets(start, end, dayFmt)
            }
            ReportPeriod.MONTH -> {
                val (start, end) = thisMonthRange(now)
                buildDailyBuckets(start, end, dayFmt)
            }
            ReportPeriod.YEAR -> {
                val (start, end) = thisYearRange(now)
                buildMonthlyBuckets(start, end)
            }
        }
    }

    private fun buildDailyBuckets(
        startMs: Long,
        endMs: Long,
        dayFmt: java.text.SimpleDateFormat
    ): List<TrendBucket> {
        val result = ArrayList<TrendBucket>()
        var cursor = startMs
        while (cursor < endMs) {
            val next = cursor + DAY_MS
            result.add(
                TrendBucket(
                    startMs = cursor,
                    endMs = next,
                    label = dayFmt.format(cursor)
                )
            )
            cursor = next
        }
        return result
    }

    private fun buildMonthlyBuckets(startMs: Long, endMs: Long): List<TrendBucket> {
        val monthFmt = java.text.SimpleDateFormat("M月", Locale.CHINA)
        val result = ArrayList<TrendBucket>()
        val cal = Calendar.getInstance().apply { timeInMillis = startMs }
        while (cal.timeInMillis < endMs) {
            val bucketStart = cal.timeInMillis
            val label = monthFmt.format(bucketStart)
            cal.add(Calendar.MONTH, 1)
            result.add(
                TrendBucket(
                    startMs = bucketStart,
                    endMs = cal.timeInMillis,
                    label = label
                )
            )
        }
        return result
    }

    fun observeAllTransactionDetails(): Flow<List<TransactionWithDetails>> {
        return combine(
            transactionDao.observeAll(),
            categoryDao.observeAll(),
            accountDao.observeAll()
        ) { txns, categories, accounts ->
            enrich(txns, categories, accounts)
        }
    }

    fun observeMonthTransactions(year: Int, month: Int): Flow<List<TransactionWithDetails>> {
        val (start, end) = monthRange(year, month)
        return combine(
            transactionDao.observeInRange(start, end),
            categoryDao.observeAll(),
            accountDao.observeAll(),
            transactionDao.observeAll()
        ) { txns, categories, accounts, allTxns ->
            // 月流水只展示真实收支/转账/报销；余额变更留在账户流水里
            val incomeExpense = txns.filter { it.type != TxnType.BALANCE_ADJUSTMENT }
            enrich(incomeExpense, categories, accounts, allTxns)
        }
    }

    /** 一次性：历史初始余额 / 余额调整流水改为 BALANCE_ADJUSTMENT */
    suspend fun migrateBalanceAdjustments() {
        transactionDao.reclassifyBalanceAdjustments()
    }

    fun observeReimbursableExpenses(): Flow<List<ReimbursableExpense>> {
        return observeAllTransactionDetails().map { items ->
            val reimbursedByExpense = items
                .filter { it.transaction.type == TxnType.REIMBURSEMENT }
                .groupBy { it.transaction.linkedTransactionId }
                .mapValues { (_, list) -> list.sumOf { it.transaction.amountCents } }
            items
                .filter { it.transaction.type == TxnType.EXPENSE }
                .map { details ->
                    ReimbursableExpense(
                        details = details,
                        reimbursedCents = reimbursedByExpense[details.transaction.id] ?: 0L
                    )
                }
                .filter { it.remainingCents > 0 }
                .sortedByDescending { it.details.transaction.occurredAt }
        }
    }

    suspend fun lastReimbursementAccountId(): Long? =
        transactionDao.latestAccountIdByType(TxnType.REIMBURSEMENT)

    fun observeCategorySpend(year: Int, month: Int): Flow<List<CategorySpend>> {
        return combine(
            observeMonthTransactions(year, month),
            observeAllTransactionDetails()
        ) { monthItems, allItems ->
            val reimbursedByExpense = allItems
                .filter { it.transaction.type == TxnType.REIMBURSEMENT }
                .groupBy { it.transaction.linkedTransactionId }
                .mapValues { (_, list) -> list.sumOf { it.transaction.amountCents } }

            monthItems
                .filter { it.transaction.type == TxnType.EXPENSE && it.category != null }
                .groupBy { item -> item.parentCategory ?: item.category!! }
                .map { (category, list) ->
                    val net = list.sumOf { item ->
                        val reimbursed = reimbursedByExpense[item.transaction.id] ?: 0L
                        (item.transaction.amountCents - reimbursed).coerceAtLeast(0)
                    }
                    CategorySpend(category = category, totalCents = net)
                }
                .filter { it.totalCents > 0 }
                .sortedByDescending { it.totalCents }
        }
    }

    suspend fun addTransaction(
        amountCents: Long,
        type: TxnType,
        categoryId: Long?,
        accountId: Long?,
        fromAccountId: Long?,
        toAccountId: Long?,
        linkedTransactionId: Long?,
        note: String,
        occurredAt: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAddress: String = ""
    ) {
        if (type == TxnType.REIMBURSEMENT) {
            val expenseId = linkedTransactionId ?: error("reimbursement requires expense")
            val already = transactionDao.sumByLinked(TxnType.REIMBURSEMENT, expenseId)
            require(amountCents > 0)
            require(already >= 0)
        }
        val now = System.currentTimeMillis()
        val txn = Transaction(
            amountCents = amountCents,
            type = type,
            categoryId = categoryId,
            accountId = accountId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            linkedTransactionId = linkedTransactionId,
            note = note,
            occurredAt = occurredAt,
            createdAt = now,
            updatedAt = now,
            latitude = latitude,
            longitude = longitude,
            locationAddress = locationAddress
        )
        val id = transactionDao.insert(txn)
        applyTransactionBalance(txn.copy(id = id), sign = 1)
    }

    suspend fun updateLocationAddress(transactionId: Long, address: String) {
        if (address.isBlank()) return
        val existing = transactionDao.getById(transactionId) ?: return
        if (existing.locationAddress.isNotBlank()) return
        transactionDao.update(existing.copy(locationAddress = address.trim()))
    }

    suspend fun updateTransaction(
        existing: Transaction,
        amountCents: Long,
        categoryId: Long?,
        accountId: Long?,
        fromAccountId: Long?,
        toAccountId: Long?,
        linkedTransactionId: Long?,
        note: String,
        occurredAt: Long
    ) {
        if (existing.type == TxnType.REIMBURSEMENT) {
            val expenseId = linkedTransactionId ?: error("reimbursement requires expense")
            require(amountCents > 0)
            val already = transactionDao.sumByLinked(TxnType.REIMBURSEMENT, expenseId) -
                if (existing.linkedTransactionId == expenseId) existing.amountCents else 0L
            require(already >= 0)
        }
        applyTransactionBalance(existing, sign = -1)
        val updated = existing.copy(
            amountCents = amountCents,
            categoryId = categoryId,
            accountId = accountId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            linkedTransactionId = linkedTransactionId,
            note = note,
            occurredAt = occurredAt,
            updatedAt = System.currentTimeMillis()
            // latitude / longitude / locationAddress intentionally unchanged
        )
        transactionDao.update(updated)
        applyTransactionBalance(updated, sign = 1)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        if (transaction.type == TxnType.EXPENSE) {
            // 先冲回关联报销对到账账户的影响，再删报销流水
            val linked = transactionDao.listLinked(transaction.id)
            linked.forEach { applyTransactionBalance(it, sign = -1) }
            transactionDao.deleteLinkedTo(transaction.id)
        }
        applyTransactionBalance(transaction, sign = -1)
        transactionDao.delete(transaction)
    }

    private fun enrich(
        txns: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>,
        allTxns: List<Transaction> = txns
    ): List<TransactionWithDetails> {
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val txnMap = allTxns.associateBy { it.id }

        fun detailOf(txn: Transaction, resolveLinked: Boolean): TransactionWithDetails {
            val category = txn.categoryId?.let { categoryMap[it] }
            val parent = category?.parentId?.let { categoryMap[it] }
            val linked = if (resolveLinked) {
                txn.linkedTransactionId?.let { txnMap[it] }?.let { detailOf(it, false) }
            } else {
                null
            }
            return TransactionWithDetails(
                transaction = txn,
                category = category,
                parentCategory = parent,
                account = txn.accountId?.let { accountMap[it] },
                fromAccount = txn.fromAccountId?.let { accountMap[it] },
                toAccount = txn.toAccountId?.let { accountMap[it] },
                linkedExpense = linked
            )
        }

        return txns.map { detailOf(it, true) }
    }

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val DAY_MS = 24L * HOUR_MS

        fun monthRange(year: Int, month: Int): Pair<Long, Long> {
            val start = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.MONTH, 1)
            }.timeInMillis
            return start to end
        }

        fun todayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val start = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
            return start to end
        }

        /** 周一为一周起始（中国习惯） */
        fun thisWeekRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val start = Calendar.getInstance(Locale.CHINA).apply {
                timeInMillis = now
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.DAY_OF_MONTH, 7)
            }.timeInMillis
            return start to end
        }

        fun thisMonthRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            return monthRange(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }

        fun thisYearRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
            val start = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.YEAR, 1)
            }.timeInMillis
            return start to end
        }
    }
}
