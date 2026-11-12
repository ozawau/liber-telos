package com.libertelos.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import com.libertelos.app.data.MonthSummary
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.ui.theme.ReimburseAmber
import com.libertelos.app.ui.theme.SeaIn
import com.libertelos.app.ui.theme.TransferBlue
import com.libertelos.app.viewmodel.LedgerViewModel
import com.libertelos.app.viewmodel.MonthKey
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DayGroup(
    val dayStartMs: Long,
    val label: String,
    val items: List<TransactionWithDetails>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: LedgerViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val dayGroups = remember(transactions) {
        groupTransactionsByDay(transactions)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("流水") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = InkTeal,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        },
        containerColor = Color.White
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                MonthSummaryHeader(
                    month = month,
                    summary = summary,
                    onPrev = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) }
                )
            }

            if (dayGroups.isEmpty()) {
                item {
                    EmptyLedgerHint()
                }
            } else {
                dayGroups.forEachIndexed { groupIndex, group ->
                    item(key = "day_${group.dayStartMs}") {
                        if (groupIndex > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = Color(0xFFE8EDF3),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = if (groupIndex == 0) 4.dp else 14.dp,
                                bottom = 6.dp
                            )
                        )
                    }
                    items(
                        items = group.items,
                        key = { it.transaction.id }
                    ) { item ->
                        SwipeDeleteRow(
                            onRequestDelete = { pendingDelete = item.transaction }
                        ) {
                            LedgerTransactionRow(
                                item = item,
                                onClick = { onOpenTransaction(item.transaction.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryHeader(
    month: MonthKey,
    summary: MonthSummary,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(
                text = "${month.month}月",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = "${month.year}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmountGrouped(summary.balanceCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "收入 ${formatAmountGrouped(summary.incomeCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberOut
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "支出 ${formatAmountGrouped(summary.expenseCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeaIn
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLedgerHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("本月还没有记录", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点右下角加号，记下第一笔。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun LedgerTransactionRow(
    item: TransactionWithDetails,
    onClick: () -> Unit
) {
    val txn = item.transaction
    val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(txn.occurredAt))
    val title = when (txn.type) {
        TxnType.TRANSFER -> {
            val from = item.fromAccount?.name ?: "账户"
            val to = item.toAccount?.name ?: "账户"
            "$from → $to"
        }
        TxnType.REIMBURSEMENT -> {
            val linked = item.linkedExpense
            val leaf = linked?.category?.name
            val parent = linked?.parentCategory?.name
            when {
                leaf != null && parent != null -> "报销 · $parent · $leaf"
                leaf != null -> "报销 · $leaf"
                else -> "报销"
            }
        }
        TxnType.BALANCE_ADJUSTMENT ->
            txn.note.ifBlank { "余额变更" }
        else -> {
            val leaf = item.category?.name
            val parent = item.parentCategory?.name
            when {
                leaf != null -> leaf
                parent != null -> parent
                else -> "未分类"
            }
        }
    }
    val subtitle = buildList {
        when (txn.type) {
            TxnType.TRANSFER -> {
                add("转账")
                add(timeText)
            }
            TxnType.REIMBURSEMENT -> {
                item.account?.name?.let { add(it) }
                add(timeText)
            }
            TxnType.BALANCE_ADJUSTMENT -> {
                item.account?.name?.let { add(it) }
                add("不计入收支")
                add(timeText)
            }
            else -> {
                item.account?.name?.let { add(it) }
                if (txn.note.isNotBlank()) add(txn.note)
                add(timeText)
            }
        }
    }.joinToString(" · ")

    val amountColor = when (txn.type) {
        TxnType.EXPENSE -> SeaIn
        TxnType.INCOME -> AmberOut
        TxnType.TRANSFER -> TransferBlue
        TxnType.REIMBURSEMENT -> ReimburseAmber
        TxnType.BALANCE_ADJUSTMENT -> Color(0xFF8A94A6)
    }
    val amountPrefix = when (txn.type) {
        TxnType.EXPENSE -> ""
        TxnType.INCOME, TxnType.REIMBURSEMENT -> ""
        TxnType.TRANSFER, TxnType.BALANCE_ADJUSTMENT -> ""
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
                    iconKey = item.fromAccount?.iconKey ?: "cash",
                    size = 40.dp
                )
            }
            TxnType.REIMBURSEMENT -> {
                AccountIconBadge(
                    iconKey = item.account?.iconKey ?: "cash",
                    size = 40.dp
                )
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
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
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
            text = amountPrefix + formatAmountGrouped(txn.amountCents),
            color = amountColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun groupTransactionsByDay(
    transactions: List<TransactionWithDetails>
): List<DayGroup> {
    if (transactions.isEmpty()) return emptyList()
    val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
    val cal = Calendar.getInstance(Locale.CHINA)
    return transactions
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
                items = items.sortedByDescending { it.transaction.occurredAt }
            )
        }
}

private fun formatAmountGrouped(cents: Long): String {
    val yuan = cents / 100.0
    val nf = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return nf.format(yuan)
}
