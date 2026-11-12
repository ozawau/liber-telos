package com.libertelos.app.ui.ledger

import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
import com.libertelos.app.viewmodel.MonthKey
import java.util.Calendar

enum class LedgerTimeScope {
    /** 不限时间 */
    ALL,
    /** 指定年月 */
    MONTH,
    /** 指定年份 */
    YEAR,
    /** 相对周期：今天 / 本周 */
    PERIOD
}

data class LedgerFilter(
    val timeScope: LedgerTimeScope = LedgerTimeScope.MONTH,
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val period: ReportPeriod = ReportPeriod.MONTH,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    /** 空集表示不过滤类型 */
    val types: Set<TxnType> = allTypes
) {
    val monthKey: MonthKey get() = MonthKey(year, month)

    fun shiftMonth(delta: Int): LedgerFilter {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        return copy(
            timeScope = LedgerTimeScope.MONTH,
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1
        )
    }

    fun shiftYear(delta: Int): LedgerFilter = copy(
        timeScope = LedgerTimeScope.YEAR,
        year = year + delta
    )

    companion object {
        val allTypes: Set<TxnType> = TxnType.entries.toSet()

        fun homeDefault(): LedgerFilter = LedgerFilter(timeScope = LedgerTimeScope.MONTH)

        fun forAccount(accountId: Long): LedgerFilter = LedgerFilter(
            timeScope = LedgerTimeScope.ALL,
            accountId = accountId,
            types = allTypes
        )

        fun forCategory(
            categoryId: Long,
            type: TxnType,
            period: ReportPeriod
        ): LedgerFilter {
            val now = Calendar.getInstance()
            return when (period) {
                ReportPeriod.YEAR -> LedgerFilter(
                    timeScope = LedgerTimeScope.YEAR,
                    year = now.get(Calendar.YEAR),
                    categoryId = categoryId,
                    types = setOf(type)
                )
                ReportPeriod.MONTH -> LedgerFilter(
                    timeScope = LedgerTimeScope.MONTH,
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH) + 1,
                    categoryId = categoryId,
                    types = setOf(type)
                )
                ReportPeriod.WEEK, ReportPeriod.DAY -> LedgerFilter(
                    timeScope = LedgerTimeScope.PERIOD,
                    period = period,
                    categoryId = categoryId,
                    types = setOf(type)
                )
            }
        }
    }
}

fun List<TransactionWithDetails>.applyLedgerFilter(
    filter: LedgerFilter,
    periodRange: (ReportPeriod) -> Pair<Long, Long>
): List<TransactionWithDetails> {
    val (startMs, endMs) = when (filter.timeScope) {
        LedgerTimeScope.ALL -> null to null
        LedgerTimeScope.MONTH -> monthBounds(filter.year, filter.month)
        LedgerTimeScope.YEAR -> yearBounds(filter.year)
        LedgerTimeScope.PERIOD -> periodRange(filter.period)
    }
    return asSequence()
        .filter { item ->
            val t = item.transaction
            if (startMs != null && endMs != null) {
                if (t.occurredAt < startMs || t.occurredAt >= endMs) return@filter false
            }
            if (filter.types.isNotEmpty() && t.type !in filter.types) return@filter false
            if (filter.accountId != null) {
                val id = filter.accountId
                if (t.accountId != id && t.fromAccountId != id && t.toAccountId != id) {
                    return@filter false
                }
            }
            if (filter.categoryId != null) {
                val id = filter.categoryId
                val parentId = item.parentCategory?.id
                val leafId = item.category?.id
                if (parentId != id && leafId != id) return@filter false
            }
            true
        }
        .sortedByDescending { it.transaction.occurredAt }
        .toList()
}

fun filteredIncomeExpense(
    items: List<TransactionWithDetails>
): Pair<Long, Long> {
    var income = 0L
    var expense = 0L
    var reimbursement = 0L
    items.forEach { item ->
        when (item.transaction.type) {
            TxnType.INCOME -> income += item.transaction.amountCents
            TxnType.EXPENSE -> expense += item.transaction.amountCents
            TxnType.REIMBURSEMENT -> reimbursement += item.transaction.amountCents
            TxnType.TRANSFER, TxnType.BALANCE_ADJUSTMENT -> Unit
        }
    }
    return income to (expense - reimbursement).coerceAtLeast(0)
}

private fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
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

private fun yearBounds(year: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.YEAR, 1)
    }.timeInMillis
    return start to end
}
