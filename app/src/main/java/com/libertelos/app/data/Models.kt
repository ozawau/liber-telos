package com.libertelos.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxnType { EXPENSE, INCOME, TRANSFER, REIMBURSEMENT }

enum class AccountKind(val label: String) {
    CASH("现金账户"),
    CREDIT("信用卡账户"),
    DEBIT("储蓄卡账户"),
    INVESTMENT("投资账户"),
    VIRTUAL("虚拟账户"),
    VIRTUAL_LIABILITY("虚拟负债账户")
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxnType,
    /** null 表示一级分类；非 null 表示二级分类 */
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val iconKey: String = "",
    val colorArgb: Long = 0xFF64748BL,
    /** 仅二级分类可用：记账选分类时，若尚未选账户则自动带入 */
    val defaultAccountId: Long? = null,
    /** 系统保留分类，不可删除（如其他收入 / 其他支出） */
    val isProtected: Boolean = false
) {
    val isParent: Boolean get() = parentId == null
    val isLeaf: Boolean get() = parentId != null
}

data class CategoryGroup(
    val parent: Category,
    val children: List<Category>
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    val currency: String = "CNY",
    val cardNumber: String = "",
    val issuer: String = "",
    val remark: String = "",
    /** 信用卡账单日（每月几号，1-31） */
    val billingDay: Int? = null,
    /** 信用卡还款日（每月几号，1-31） */
    val repaymentDay: Int? = null,
    val iconKey: String,
    val includeInTotal: Boolean = true,
    val hidden: Boolean = false,
    val balanceCents: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val type: TxnType,
    val categoryId: Long? = null,
    /** 收入/支出/报销到账账户 */
    val accountId: Long? = null,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    /** 报销关联的原支出记录 */
    val linkedTransactionId: Long? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 仅新建时写入，编辑不更新 */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** 由经纬度反查的可读地址，编辑不更新 */
    val locationAddress: String = ""
)

data class TransactionWithDetails(
    val transaction: Transaction,
    val category: Category? = null,
    val parentCategory: Category? = null,
    val account: Account? = null,
    val fromAccount: Account? = null,
    val toAccount: Account? = null,
    val linkedExpense: TransactionWithDetails? = null
)

data class ReimbursableExpense(
    val details: TransactionWithDetails,
    val reimbursedCents: Long
) {
    val remainingCents: Long
        get() = (details.transaction.amountCents - reimbursedCents).coerceAtLeast(0)
}

data class CategorySpend(
    val category: Category,
    val totalCents: Long
)

data class MonthSummary(
    val incomeCents: Long,
    val expenseCents: Long
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

data class IncomeExpenseReport(
    val today: MonthSummary = MonthSummary(0, 0),
    val week: MonthSummary = MonthSummary(0, 0),
    val month: MonthSummary = MonthSummary(0, 0),
    val year: MonthSummary = MonthSummary(0, 0)
)

enum class ReportPeriod(val label: String) {
    YEAR("本年"),
    MONTH("本月"),
    WEEK("本周"),
    DAY("今天")
}

/** 趋势图上的一个桶：按天或按小时 */
data class TrendPoint(
    val startMs: Long,
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long
)

data class CategoryRankItem(
    val category: Category,
    val totalCents: Long,
    /** 占总收入/总支出的比例，0~1 */
    val ratio: Float
)

data class CategoryRanking(
    val type: TxnType,
    val period: ReportPeriod,
    val txnCount: Int,
    val totalCents: Long,
    val items: List<CategoryRankItem>
)

data class AccountIconSpec(
    val key: String,
    val label: String,
    val colorArgb: Long
)

object AccountIcons {
    val all = listOf(
        AccountIconSpec("credit_card", "信用卡", 0xFF2F6FED),
        AccountIconSpec("debit_card", "储蓄卡", 0xFF0D4F4F),
        AccountIconSpec("yuebao", "余额宝", 0xFF1677FF),
        AccountIconSpec("huabei", "花呗", 0xFF1677FF),
        AccountIconSpec("meituan", "美团", 0xFFFFD100),
        AccountIconSpec("wechat", "微信余额", 0xFF07C160),
        AccountIconSpec("stock", "股票", 0xFFE11D48),
        AccountIconSpec("stable_fund", "稳定理财", 0xFF0EA5A4),
        AccountIconSpec("transit_card", "交通卡", 0xFF5B6B8C),
        AccountIconSpec("cash", "现金", 0xFFE07A3D)
    )

    fun byKey(key: String): AccountIconSpec =
        all.firstOrNull { it.key == key } ?: all.last()
}

object Currencies {
    val common = listOf("CNY", "USD", "HKD", "EUR", "JPY")
}

data class CategorySeedGroup(
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val children: List<String>,
    val protectedChildren: Set<String> = emptySet()
)
