package com.libertelos.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.Account
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.ReimburseAmber
import com.libertelos.app.ui.theme.SeaIn
import com.libertelos.app.ui.theme.TransferBlue
import com.libertelos.app.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayGroup(
    val dayStartMs: Long,
    val label: String,
    val year: Int,
    val month: Int,
    val items: List<TransactionWithDetails>
)

private sealed class LedgerListItem {
    data class MonthHeader(
        val year: Int,
        val month: Int,
        val incomeCents: Long,
        val expenseCents: Long
    ) : LedgerListItem()

    data class DayHeader(val dayStartMs: Long, val label: String, val showDivider: Boolean) :
        LedgerListItem()

    data class Txn(val item: TransactionWithDetails) : LedgerListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLedgerScreen(
    viewModel: LedgerViewModel,
    accountId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val details by viewModel.allTransactionDetails.collectAsStateWithLifecycle()
    var account by remember { mutableStateOf<Account?>(null) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(accountId) {
        account = viewModel.loadAccount(accountId)
    }
    // 账户余额可能在别处更新后刷新
    LaunchedEffect(details) {
        account = viewModel.loadAccount(accountId) ?: account
    }

    val accountTxns = remember(details, accountId) {
        details
            .filter { item ->
                val t = item.transaction
                t.accountId == accountId ||
                    t.fromAccountId == accountId ||
                    t.toAccountId == accountId
            }
            .sortedByDescending { it.transaction.occurredAt }
    }
    val listItems = remember(accountTxns, accountId) {
        buildLedgerList(accountTxns, accountId)
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除账目") },
            text = { Text("确定删除这条账目吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete?.let { viewModel.deleteTransaction(it) }
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    val titleName = account?.name ?: "账户流水"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑账户")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                AccountLedgerHeader(account = account)
            }

            if (listItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该账户还没有流水",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = listItems,
                    key = { row ->
                        when (row) {
                            is LedgerListItem.MonthHeader -> "m_${row.year}_${row.month}"
                            is LedgerListItem.DayHeader -> "d_${row.dayStartMs}"
                            is LedgerListItem.Txn -> "t_${row.item.transaction.id}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is LedgerListItem.MonthHeader -> {
                            MonthSectionHeader(
                                year = row.year,
                                month = row.month,
                                incomeCents = row.incomeCents,
                                expenseCents = row.expenseCents
                            )
                        }
                        is LedgerListItem.DayHeader -> {
                            if (row.showDivider) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = Color(0xFFE8EDF3),
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 12.dp,
                                    bottom = 4.dp
                                )
                            )
                        }
                        is LedgerListItem.Txn -> {
                            SwipeDeleteRow(
                                onRequestDelete = { pendingDelete = row.item.transaction }
                            ) {
                                AccountTxnRow(
                                    item = row.item,
                                    accountId = accountId,
                                    onClick = { onOpenTransaction(row.item.transaction.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSectionHeader(
    year: Int,
    month: Int,
    incomeCents: Long,
    expenseCents: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${year}年${month}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "收入 ${formatAmountGrouped(incomeCents)}",
            style = MaterialTheme.typography.labelMedium,
            color = AmberOut
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "支出 ${formatAmountGrouped(expenseCents)}",
            style = MaterialTheme.typography.labelMedium,
            color = SeaIn
        )
    }
}

@Composable
private fun AccountLedgerHeader(account: Account?) {
    if (account == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconBadge(iconKey = account.iconKey, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "余额",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatYuanPlain(account.balanceCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp
            )
            val meta = buildList {
                add(account.kind.label)
                add(account.currency)
                if (account.issuer.isNotBlank()) add(account.issuer)
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteRow(onRequestDelete: () -> Unit, content: @Composable () -> Unit) {
    val latestRequest = rememberUpdatedState(onRequestDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                latestRequest.value()
                false
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFB42318))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        content()
    }
}

@Composable
private fun AccountTxnRow(
    item: TransactionWithDetails,
    accountId: Long,
    onClick: () -> Unit
) {
    val txn = item.transaction
    val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(txn.occurredAt))
    val title = when (txn.type) {
        TxnType.TRANSFER -> {
            val from = item.fromAccount?.name ?: "账户"
            val to = item.toAccount?.name ?: "账户"
            when (accountId) {
                txn.fromAccountId -> "转出 → $to"
                txn.toAccountId -> "$from → 转入"
                else -> "$from → $to"
            }
        }
        TxnType.REIMBURSEMENT -> {
            val leaf = item.linkedExpense?.category?.name
            if (leaf != null) "报销 · $leaf" else "报销"
        }
        TxnType.BALANCE_ADJUSTMENT ->
            txn.note.ifBlank { "余额变更" }
        else -> {
            item.category?.name
                ?: item.parentCategory?.name
                ?: "未分类"
        }
    }
    val subtitle = buildList {
        when (txn.type) {
            TxnType.TRANSFER -> add("转账")
            TxnType.REIMBURSEMENT -> add("报销")
            TxnType.EXPENSE -> add("支出")
            TxnType.INCOME -> add("收入")
            TxnType.BALANCE_ADJUSTMENT -> add("不计入收支")
        }
        if (txn.type != TxnType.BALANCE_ADJUSTMENT && txn.note.isNotBlank()) {
            add(txn.note)
        }
        add(timeText)
    }.joinToString(" · ")

    val amountColor = when (txn.type) {
        TxnType.EXPENSE -> SeaIn
        TxnType.INCOME -> AmberOut
        TxnType.TRANSFER -> TransferBlue
        TxnType.REIMBURSEMENT -> ReimburseAmber
        TxnType.BALANCE_ADJUSTMENT -> Color(0xFF8A94A6)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (txn.type) {
            TxnType.TRANSFER -> {
                AccountIconBadge(
                    iconKey = when (accountId) {
                        txn.fromAccountId -> item.toAccount?.iconKey
                        else -> item.fromAccount?.iconKey
                    } ?: "cash",
                    size = 40.dp
                )
            }
            TxnType.REIMBURSEMENT -> {
                AccountIconBadge(iconKey = item.account?.iconKey ?: "cash", size = 40.dp)
            }
            else -> {
                val category = item.category ?: item.parentCategory
                val color = Color(category?.colorArgb ?: 0xFF64748BL)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (category?.name ?: "?").take(1),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatAmountGrouped(txn.amountCents),
            color = amountColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun buildLedgerList(
    transactions: List<TransactionWithDetails>,
    accountId: Long
): List<LedgerListItem> {
    if (transactions.isEmpty()) return emptyList()
    val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val cal = Calendar.getInstance(Locale.CHINA)
    val dayGroups = transactions
        .groupBy { item ->
            cal.timeInMillis = item.transaction.occurredAt
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        .entries
        .sortedByDescending { it.key }
        .map { (dayStart, items) ->
            cal.timeInMillis = dayStart
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val week = weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            DayGroup(
                dayStartMs = dayStart,
                label = "${day}日 周$week",
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                items = items.sortedByDescending { it.transaction.occurredAt }
            )
        }

    val monthTotals = LinkedHashMap<Pair<Int, Int>, Pair<Long, Long>>()
    dayGroups.forEach { group ->
        val key = group.year to group.month
        var income = monthTotals[key]?.first ?: 0L
        var expense = monthTotals[key]?.second ?: 0L
        group.items.forEach { item ->
            val (inDelta, outDelta) = accountFlowDelta(item.transaction, accountId)
            income += inDelta
            expense += outDelta
        }
        monthTotals[key] = income to expense
    }

    val result = ArrayList<LedgerListItem>()
    var lastYearMonth = 0 to 0
    dayGroups.forEachIndexed { index, group ->
        val ym = group.year to group.month
        if (ym != lastYearMonth) {
            lastYearMonth = ym
            val (income, expense) = monthTotals[ym] ?: (0L to 0L)
            result += LedgerListItem.MonthHeader(
                year = group.year,
                month = group.month,
                incomeCents = income,
                expenseCents = expense
            )
        }
        result += LedgerListItem.DayHeader(
            dayStartMs = group.dayStartMs,
            label = group.label,
            showDivider = index > 0
        )
        group.items.forEach { result += LedgerListItem.Txn(it) }
    }
    return result
}

/** 对该账户：收入（进账）、支出（出账）增量 */
private fun accountFlowDelta(
    txn: Transaction,
    accountId: Long
): Pair<Long, Long> {
    return when (txn.type) {
        TxnType.INCOME ->
            if (txn.accountId == accountId) txn.amountCents to 0L else 0L to 0L
        TxnType.EXPENSE ->
            if (txn.accountId == accountId) 0L to txn.amountCents else 0L to 0L
        TxnType.REIMBURSEMENT ->
            if (txn.accountId == accountId) txn.amountCents to 0L else 0L to 0L
        TxnType.TRANSFER -> when (accountId) {
            txn.toAccountId -> txn.amountCents to 0L
            txn.fromAccountId -> 0L to txn.amountCents
            else -> 0L to 0L
        }
        TxnType.BALANCE_ADJUSTMENT -> 0L to 0L
    }
}

private fun formatAmountGrouped(cents: Long): String {
    val yuan = cents / 100.0
    return NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(yuan)
}
