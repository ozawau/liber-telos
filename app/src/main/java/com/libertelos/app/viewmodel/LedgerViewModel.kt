package com.libertelos.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.libertelos.app.data.Account
import com.libertelos.app.data.AccountKind
import com.libertelos.app.data.Category
import com.libertelos.app.data.CategoryRanking
import com.libertelos.app.data.CategorySpend
import com.libertelos.app.data.IncomeExpenseReport
import com.libertelos.app.data.LedgerRepository
import com.libertelos.app.data.MonthSummary
import com.libertelos.app.data.ReimbursableExpense
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TrendPoint
import com.libertelos.app.data.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class MonthKey(val year: Int, val month: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: LedgerRepository
) : ViewModel() {

    private val calendar = Calendar.getInstance()
    private val _month = MutableStateFlow(
        MonthKey(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1
        )
    )
    val month: StateFlow<MonthKey> = _month

    val summary: StateFlow<MonthSummary> = _month
        .flatMapLatest { repository.observeMonthSummary(it.year, it.month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary(0, 0))

    val incomeExpenseReport: StateFlow<IncomeExpenseReport> =
        repository.observeIncomeExpenseReport()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomeExpenseReport())

    val allTransactions: StateFlow<List<Transaction>> =
        repository.observeTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTransactionDetails: StateFlow<List<TransactionWithDetails>> =
        repository.observeAllTransactionDetails()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun trendSeries(period: ReportPeriod, txns: List<Transaction> = allTransactions.value): List<TrendPoint> =
        repository.buildTrendSeries(txns, period)

    fun categoryRanking(
        period: ReportPeriod,
        type: TxnType,
        details: List<TransactionWithDetails> = allTransactionDetails.value
    ): CategoryRanking = repository.buildCategoryRanking(details, period, type)

    fun periodRange(period: ReportPeriod): Pair<Long, Long> =
        repository.periodRange(period)

    val transactions: StateFlow<List<TransactionWithDetails>> = _month
        .flatMapLatest { repository.observeMonthTransactions(it.year, it.month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorySpend: StateFlow<List<CategorySpend>> = _month
        .flatMapLatest { repository.observeCategorySpend(it.year, it.month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<Account>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleAccounts: StateFlow<List<Account>> = repository.observeVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reimbursableExpenses: StateFlow<List<ReimbursableExpense>> =
        repository.observeReimbursableExpenses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftMonth(delta: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, _month.value.year)
            set(Calendar.MONTH, _month.value.month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        _month.value = MonthKey(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1
        )
    }

    fun saveTransaction(
        existingId: Long?,
        amountYuan: String,
        type: TxnType,
        categoryId: Long? = null,
        accountId: Long? = null,
        fromAccountId: Long? = null,
        toAccountId: Long? = null,
        linkedTransactionId: Long? = null,
        maxReimbursableCents: Long? = null,
        note: String,
        occurredAt: Long,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAddress: String = "",
        onDone: () -> Unit = {}
    ) {
        val normalized = amountYuan.replace(",", "").trim()
        val yuan = normalized.toDoubleOrNull() ?: return
        if (yuan <= 0) return
        val cents = Math.round(yuan * 100)
        when (type) {
            TxnType.EXPENSE, TxnType.INCOME -> {
                if (categoryId == null || accountId == null) return
            }
            TxnType.TRANSFER -> {
                if (fromAccountId == null || toAccountId == null) return
                if (fromAccountId == toAccountId) return
            }
            TxnType.REIMBURSEMENT -> {
                if (linkedTransactionId == null || accountId == null) return
                val max = maxReimbursableCents ?: return
                if (cents > max) return
            }
            TxnType.BALANCE_ADJUSTMENT -> return
        }
        viewModelScope.launch {
            val resolvedCategoryId = when (type) {
                TxnType.EXPENSE, TxnType.INCOME -> categoryId
                else -> null
            }
            val resolvedAccountId = when (type) {
                TxnType.EXPENSE, TxnType.INCOME, TxnType.REIMBURSEMENT -> accountId
                else -> null
            }
            val resolvedFrom = if (type == TxnType.TRANSFER) fromAccountId else null
            val resolvedTo = if (type == TxnType.TRANSFER) toAccountId else null
            val resolvedLinked =
                if (type == TxnType.REIMBURSEMENT) linkedTransactionId else null
            if (existingId != null) {
                val existing = repository.getTransaction(existingId) ?: return@launch
                repository.updateTransaction(
                    existing = existing,
                    amountCents = cents,
                    categoryId = resolvedCategoryId,
                    accountId = resolvedAccountId,
                    fromAccountId = resolvedFrom,
                    toAccountId = resolvedTo,
                    linkedTransactionId = resolvedLinked,
                    note = note.trim(),
                    occurredAt = occurredAt
                )
            } else {
                repository.addTransaction(
                    amountCents = cents,
                    type = type,
                    categoryId = resolvedCategoryId,
                    accountId = resolvedAccountId,
                    fromAccountId = resolvedFrom,
                    toAccountId = resolvedTo,
                    linkedTransactionId = resolvedLinked,
                    note = note.trim(),
                    occurredAt = occurredAt,
                    latitude = latitude,
                    longitude = longitude,
                    locationAddress = locationAddress
                )
            }
            onDone()
        }
    }

    fun persistLocationAddress(transactionId: Long, address: String) {
        viewModelScope.launch {
            repository.updateLocationAddress(transactionId, address)
        }
    }

    /** @deprecated use saveTransaction */
    fun addTransaction(
        amountYuan: String,
        type: TxnType,
        categoryId: Long? = null,
        accountId: Long? = null,
        fromAccountId: Long? = null,
        toAccountId: Long? = null,
        linkedTransactionId: Long? = null,
        maxReimbursableCents: Long? = null,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
        onDone: () -> Unit = {}
    ) {
        saveTransaction(
            existingId = null,
            amountYuan = amountYuan,
            type = type,
            categoryId = categoryId,
            accountId = accountId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            linkedTransactionId = linkedTransactionId,
            maxReimbursableCents = maxReimbursableCents,
            note = note,
            occurredAt = occurredAt,
            onDone = onDone
        )
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    suspend fun loadAccount(id: Long): Account? = repository.getAccount(id)

    suspend fun loadTransaction(id: Long): Transaction? = repository.getTransaction(id)

    suspend fun loadTransactionDetails(id: Long): TransactionWithDetails? =
        repository.getTransactionDetails(id)

    suspend fun lastReimbursementAccountId(): Long? =
        repository.lastReimbursementAccountId()

    fun saveAccount(
        id: Long,
        name: String,
        kind: AccountKind,
        currency: String,
        cardNumber: String,
        issuer: String,
        remark: String,
        billingDay: Int?,
        repaymentDay: Int?,
        iconKey: String,
        includeInTotal: Boolean,
        hidden: Boolean,
        balanceYuan: String,
        createBalanceAdjustment: Boolean = false,
        previousBalanceCents: Long? = null,
        createdAt: Long? = null,
        onDone: () -> Unit = {}
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val balanceText = balanceYuan.replace(",", "").trim()
        val balanceValue = if (balanceText.isEmpty()) 0.0 else balanceText.toDoubleOrNull() ?: return
        val balanceCents = Math.round(balanceValue * 100)
        val creditFields = kind == AccountKind.CREDIT
        viewModelScope.launch {
            repository.upsertAccount(
                account = Account(
                    id = id,
                    name = trimmed,
                    kind = kind,
                    currency = currency.trim().ifBlank { "CNY" },
                    cardNumber = cardNumber.trim(),
                    issuer = issuer.trim(),
                    remark = remark.trim(),
                    billingDay = if (creditFields) billingDay else null,
                    repaymentDay = if (creditFields) repaymentDay else null,
                    iconKey = iconKey,
                    includeInTotal = includeInTotal,
                    hidden = hidden,
                    balanceCents = balanceCents,
                    createdAt = createdAt ?: System.currentTimeMillis()
                ),
                createBalanceAdjustment = createBalanceAdjustment && id != 0L,
                previousBalanceCents = previousBalanceCents
            )
            onDone()
        }
    }

    fun deleteAccount(account: Account, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAccount(account)
            onDone()
        }
    }

    fun addParentCategory(type: TxnType, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addParentCategory(type, trimmed)
        }
    }

    fun addChildCategory(parentId: Long, name: String, defaultAccountId: Long? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addChildCategory(parentId, trimmed, defaultAccountId)
        }
    }

    fun updateChildCategory(category: Category, name: String, defaultAccountId: Long?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !category.isLeaf) return
        viewModelScope.launch {
            repository.updateChildCategory(category, trimmed, defaultAccountId)
        }
    }

    fun moveChildCategory(category: Category, newParentId: Long) {
        if (!category.isLeaf || category.parentId == newParentId) return
        viewModelScope.launch {
            runCatching { repository.moveChildCategory(category, newParentId) }
        }
    }

    fun deleteCategory(category: Category, onError: (String) -> Unit = {}) {
        if (category.isProtected) {
            onError("系统分类不可删除")
            return
        }
        viewModelScope.launch {
            runCatching { repository.deleteCategory(category) }
                .onFailure { onError(it.message ?: "删除失败") }
        }
    }

    class Factory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LedgerViewModel(repository) as T
        }
    }
}
