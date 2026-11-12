package com.libertelos.app.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
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

private data class CategoryDayGroup(
    val dayStartMs: Long,
    val label: String,
    val items: List<TransactionWithDetails>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryLedgerScreen(
    viewModel: LedgerViewModel,
    categoryId: Long,
    type: TxnType,
    period: ReportPeriod,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val details by viewModel.allTransactionDetails.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val category = remember(categories, categoryId) {
        categories.firstOrNull { it.id == categoryId }
    }
    val typeLabel = if (type == TxnType.EXPENSE) "支出" else "收入"
    val (startMs, endMs) = remember(period) { viewModel.periodRange(period) }

    val filtered = remember(details, categoryId, type, startMs, endMs) {
        details
            .filter { item ->
                val t = item.transaction
                if (t.type != type) return@filter false
                if (t.occurredAt < startMs || t.occurredAt >= endMs) return@filter false
                val parentId = item.parentCategory?.id
                val leafId = item.category?.id
                parentId == categoryId || leafId == categoryId
            }
            .sortedByDescending { it.transaction.occurredAt }
    }
    val totalCents = remember(filtered, type, details) {
        if (type == TxnType.EXPENSE) {
            val reimbursedByExpense = details
                .filter { it.transaction.type == TxnType.REIMBURSEMENT }
                .groupBy { it.transaction.linkedTransactionId }
                .mapValues { (_, list) -> list.sumOf { it.transaction.amountCents } }
            filtered.sumOf { item ->
                val reimbursed = reimbursedByExpense[item.transaction.id] ?: 0L
                (item.transaction.amountCents - reimbursed).coerceAtLeast(0)
            }
        } else {
            filtered.sumOf { it.transaction.amountCents }
        }
    }
    val dayGroups = remember(filtered) { groupByDay(filtered) }

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
                title = {
                    Text(
                        text = category?.name ?: "分类流水",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                CategoryLedgerHeader(
                    period = period,
                    typeLabel = typeLabel,
                    totalCents = totalCents,
                    txnCount = filtered.size,
                    accent = if (type == TxnType.EXPENSE) SeaIn else AmberOut
                )
            }

            if (dayGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${period.label}暂无${typeLabel}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            CategoryTxnRow(
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
private fun CategoryLedgerHeader(
    period: ReportPeriod,
    typeLabel: String,
    totalCents: Long,
    txnCount: Int,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = period.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatAmountGrouped(totalCents),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            color = accent
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "共 $txnCount 笔$typeLabel",
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
private fun CategoryTxnRow(
    item: TransactionWithDetails,
    onClick: () -> Unit
) {
    val txn = item.transaction
    val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(txn.occurredAt))
    val leaf = item.category?.name
    val parent = item.parentCategory?.name
    val title = when {
        leaf != null && parent != null && leaf != parent -> leaf
        leaf != null -> leaf
        parent != null -> parent
        else -> "未分类"
    }
    val subtitle = buildList {
        item.account?.name?.let { add(it) }
        if (txn.note.isNotBlank()) add(txn.note)
        add(timeText)
    }.joinToString(" · ")

    val amountColor = when (txn.type) {
        TxnType.EXPENSE -> SeaIn
        TxnType.INCOME -> AmberOut
        TxnType.TRANSFER -> TransferBlue
        TxnType.REIMBURSEMENT -> ReimburseAmber
        TxnType.BALANCE_ADJUSTMENT -> Color(0xFF8A94A6)
    }
    val category = item.category ?: item.parentCategory
    val color = Color(category?.colorArgb ?: 0xFF64748BL)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

private fun groupByDay(transactions: List<TransactionWithDetails>): List<CategoryDayGroup> {
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
            CategoryDayGroup(
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
