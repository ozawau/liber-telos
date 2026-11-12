package com.libertelos.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.libertelos.app.data.Category
import com.libertelos.app.data.DefaultCategories
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.InkTeal
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

private sealed class RowItem {
    data class MonthHeader(val year: Int, val month: Int, val income: Long, val expense: Long) :
        RowItem()

    data class DayHeader(val dayStartMs: Long, val label: String, val showDivider: Boolean) :
        RowItem()

    data class Txn(val item: TransactionWithDetails) : RowItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    initialFilter: LedgerFilter,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onEditAccount: ((Long) -> Unit)? = null
) {
    val details by viewModel.allTransactionDetails.collectAsStateWithLifecycle()
    val accounts by viewModel.visibleAccounts.collectAsStateWithLifecycle()
    val allAccounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(initialFilter) }
    var showFilters by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    var account by remember { mutableStateOf<Account?>(null) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(filter.accountId, details) {
        account = filter.accountId?.let { viewModel.loadAccount(it) }
    }

    val filtered = remember(details, filter) {
        details.applyLedgerFilter(filter) { viewModel.periodRange(it) }
    }
    val (incomeCents, expenseCents) = remember(filtered) { filteredIncomeExpense(filtered) }
    val rows = remember(filtered, filter.timeScope) {
        buildRows(
            filtered,
            showMonthHeaders = filter.timeScope == LedgerTimeScope.ALL ||
                filter.timeScope == LedgerTimeScope.YEAR ||
                (filter.timeScope == LedgerTimeScope.PERIOD && filter.period == ReportPeriod.YEAR)
        )
    }
    val title = remember(filter, account, categories) {
        ledgerTitle(filter, account, categories)
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
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = if (hasCustomFilter(filter)) InkTeal
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (filter.accountId != null && onEditAccount != null) {
                        IconButton(onClick = { onEditAccount(filter.accountId!!) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑账户")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
        if (showFilters) {
            ModalBottomSheet(
                onDismissRequest = { showFilters = false },
                sheetState = filterSheetState,
                containerColor = Color.White
            ) {
                FilterPanel(
                    filter = filter,
                    accounts = accounts.ifEmpty { allAccounts },
                    categories = categories,
                    onChange = { filter = it },
                    onDone = { showFilters = false }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                SummaryHeader(
                    filter = filter,
                    incomeCents = incomeCents,
                    expenseCents = expenseCents,
                    txnCount = filtered.size,
                    account = account,
                    onPrevMonth = { filter = filter.shiftMonth(-1) },
                    onNextMonth = { filter = filter.shiftMonth(1) },
                    onPrevYear = { filter = filter.shiftYear(-1) },
                    onNextYear = { filter = filter.shiftYear(1) }
                )
            }

            if (rows.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有符合条件的流水",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = rows,
                    key = { row ->
                        when (row) {
                            is RowItem.MonthHeader -> "m_${row.year}_${row.month}"
                            is RowItem.DayHeader -> "d_${row.dayStartMs}"
                            is RowItem.Txn -> "t_${row.item.transaction.id}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is RowItem.MonthHeader -> MonthHeaderRow(row)
                        is RowItem.DayHeader -> {
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
                        is RowItem.Txn -> {
                            SwipeDeleteRow(
                                onRequestDelete = { pendingDelete = row.item.transaction }
                            ) {
                                LedgerTxnRow(
                                    item = row.item,
                                    focusAccountId = filter.accountId,
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
private fun FilterPanel(
    filter: LedgerFilter,
    accounts: List<Account>,
    categories: List<Category>,
    onChange: (LedgerFilter) -> Unit,
    onDone: () -> Unit
) {
    val parents = remember(categories) {
        DefaultCategories.groupByParent(categories)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "筛选流水",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text("时间", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "全部",
                selected = filter.timeScope == LedgerTimeScope.ALL
            ) { onChange(filter.copy(timeScope = LedgerTimeScope.ALL)) }
            FilterChip(
                label = "按月",
                selected = filter.timeScope == LedgerTimeScope.MONTH
            ) { onChange(filter.copy(timeScope = LedgerTimeScope.MONTH)) }
            FilterChip(
                label = "按年",
                selected = filter.timeScope == LedgerTimeScope.YEAR
            ) { onChange(filter.copy(timeScope = LedgerTimeScope.YEAR)) }
            listOf(ReportPeriod.WEEK, ReportPeriod.DAY).forEach { p ->
                FilterChip(
                    label = p.label,
                    selected = filter.timeScope == LedgerTimeScope.PERIOD && filter.period == p
                ) {
                    onChange(filter.copy(timeScope = LedgerTimeScope.PERIOD, period = p))
                }
            }
        }

        Text("类型", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "全部",
                selected = filter.types == LedgerFilter.allTypes
            ) { onChange(filter.copy(types = LedgerFilter.allTypes)) }
            typeOptions.forEach { (type, label) ->
                val selected = filter.types == setOf(type)
                FilterChip(label = label, selected = selected) {
                    onChange(filter.copy(types = setOf(type)))
                }
            }
        }

        Text("账户", style = MaterialTheme.typography.labelLarge)
        FilterDropdown(
            label = accounts.firstOrNull { it.id == filter.accountId }?.name ?: "全部账户",
            options = listOf(null to "全部账户") + accounts.map { it.id to it.name }
        ) { id -> onChange(filter.copy(accountId = id)) }

        Text("分类", style = MaterialTheme.typography.labelLarge)
        val categoryOptions = buildList {
            add(null to "全部分类")
            parents.forEach { group ->
                add(group.parent.id to group.parent.name)
                group.children.forEach { child ->
                    add(child.id to "${group.parent.name} · ${child.name}")
                }
            }
        }
        FilterDropdown(
            label = categoryOptions.firstOrNull { it.first == filter.categoryId }?.second
                ?: "全部分类",
            options = categoryOptions
        ) { id -> onChange(filter.copy(categoryId = id)) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onChange(LedgerFilter.homeDefault()) }) {
                Text("重置")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onDone) {
                Text("完成", color = InkTeal)
            }
        }
    }
}

private val typeOptions = listOf(
    TxnType.EXPENSE to "支出",
    TxnType.INCOME to "收入",
    TxnType.TRANSFER to "转账",
    TxnType.REIMBURSEMENT to "报销",
    TxnType.BALANCE_ADJUSTMENT to "余额变更"
)

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) InkTeal else Color.White)
            .border(
                1.dp,
                if (selected) InkTeal else Color(0xFFD7DEE7),
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<Pair<Long?, String>>,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFD7DEE7), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    filter: LedgerFilter,
    incomeCents: Long,
    expenseCents: Long,
    txnCount: Int,
    account: Account?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (filter.accountId != null && account != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountIconBadge(iconKey = account.iconKey, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("余额", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A94A6))
                    Text(
                        text = formatYuanPlain(account.balanceCents),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        when (filter.timeScope) {
            LedgerTimeScope.MONTH -> {
                PeriodNavHeader(
                    title = "${filter.month}月",
                    subtitle = "${filter.year}",
                    incomeCents = incomeCents,
                    expenseCents = expenseCents,
                    onPrev = onPrevMonth,
                    onNext = onNextMonth,
                    prevDesc = "上月",
                    nextDesc = "下月"
                )
            }
            LedgerTimeScope.YEAR -> {
                PeriodNavHeader(
                    title = "${filter.year}年",
                    subtitle = "共 $txnCount 笔",
                    incomeCents = incomeCents,
                    expenseCents = expenseCents,
                    onPrev = onPrevYear,
                    onNext = onNextYear,
                    prevDesc = "上一年",
                    nextDesc = "下一年"
                )
            }
            LedgerTimeScope.PERIOD, LedgerTimeScope.ALL -> {
                val timeLabel = when (filter.timeScope) {
                    LedgerTimeScope.ALL -> "全部时间"
                    LedgerTimeScope.PERIOD -> filter.period.label
                    else -> ""
                }
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "共 $txnCount 笔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        text = "收入 ${formatAmountGrouped(incomeCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberOut,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "支出 ${formatAmountGrouped(expenseCents)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = SeaIn,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavHeader(
    title: String,
    subtitle: String,
    incomeCents: Long,
    expenseCents: Long,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevDesc: String,
    nextDesc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = prevDesc)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmountGrouped(incomeCents - expenseCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Row {
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
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextDesc)
        }
    }
}

@Composable
private fun MonthHeaderRow(row: RowItem.MonthHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${row.year}年${row.month}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "收入 ${formatAmountGrouped(row.income)}",
            style = MaterialTheme.typography.labelMedium,
            color = AmberOut
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "支出 ${formatAmountGrouped(row.expense)}",
            style = MaterialTheme.typography.labelMedium,
            color = SeaIn
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
    ) { content() }
}

@Composable
private fun LedgerTxnRow(
    item: TransactionWithDetails,
    focusAccountId: Long?,
    onClick: () -> Unit
) {
    val txn = item.transaction
    val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(txn.occurredAt))
    val title = when (txn.type) {
        TxnType.TRANSFER -> {
            val from = item.fromAccount?.name ?: "账户"
            val to = item.toAccount?.name ?: "账户"
            when (focusAccountId) {
                txn.fromAccountId -> "转出 → $to"
                txn.toAccountId -> "$from → 转入"
                else -> "$from → $to"
            }
        }
        TxnType.REIMBURSEMENT -> {
            val leaf = item.linkedExpense?.category?.name
            if (leaf != null) "报销 · $leaf" else "报销"
        }
        TxnType.BALANCE_ADJUSTMENT -> txn.note.ifBlank { "余额变更" }
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
        if (txn.type != TxnType.TRANSFER && txn.type != TxnType.BALANCE_ADJUSTMENT) {
            item.account?.name?.let { add(it) }
        }
        if (txn.type != TxnType.BALANCE_ADJUSTMENT && txn.note.isNotBlank()) add(txn.note)
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
                    iconKey = when (focusAccountId) {
                        txn.fromAccountId -> item.toAccount?.iconKey
                        else -> item.fromAccount?.iconKey
                    } ?: "cash",
                    size = 40.dp
                )
            }
            TxnType.REIMBURSEMENT, TxnType.BALANCE_ADJUSTMENT -> {
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
            text = formatAmountGrouped(txn.amountCents),
            color = amountColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun buildRows(
    items: List<TransactionWithDetails>,
    showMonthHeaders: Boolean
): List<RowItem> {
    if (items.isEmpty()) return emptyList()
    val dayGroups = groupByDay(items)
    if (!showMonthHeaders) {
        return buildList {
            dayGroups.forEachIndexed { index, group ->
                add(
                    RowItem.DayHeader(
                        dayStartMs = group.dayStartMs,
                        label = group.label,
                        showDivider = index > 0
                    )
                )
                group.items.forEach { add(RowItem.Txn(it)) }
            }
        }
    }
    val monthTotals = LinkedHashMap<Pair<Int, Int>, Pair<Long, Long>>()
    dayGroups.forEach { group ->
        val key = group.year to group.month
        var income = monthTotals[key]?.first ?: 0L
        var expense = monthTotals[key]?.second ?: 0L
        val (i, e) = filteredIncomeExpense(group.items)
        income += i
        expense += e
        monthTotals[key] = income to expense
    }
    return buildList {
        var lastYm: Pair<Int, Int>? = null
        dayGroups.forEachIndexed { index, group ->
            val ym = group.year to group.month
            if (ym != lastYm) {
                lastYm = ym
                val (income, expense) = monthTotals[ym] ?: (0L to 0L)
                add(RowItem.MonthHeader(group.year, group.month, income, expense))
            }
            add(
                RowItem.DayHeader(
                    dayStartMs = group.dayStartMs,
                    label = group.label,
                    showDivider = index > 0
                )
            )
            group.items.forEach { add(RowItem.Txn(it)) }
        }
    }
}

private fun groupByDay(transactions: List<TransactionWithDetails>): List<DayGroup> {
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
        .map { (dayStart, list) ->
            cal.timeInMillis = dayStart
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val week = weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            DayGroup(
                dayStartMs = dayStart,
                label = "${day}日 周$week",
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                items = list.sortedByDescending { it.transaction.occurredAt }
            )
        }
}

private fun ledgerTitle(
    filter: LedgerFilter,
    account: Account?,
    categories: List<Category>
): String {
    val category = filter.categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    return when {
        account != null && category != null -> "${account.name} · ${category.name}"
        account != null -> account.name
        category != null -> category.name
        else -> "流水"
    }
}

private fun hasCustomFilter(filter: LedgerFilter): Boolean {
    val baseline = LedgerFilter.homeDefault()
    return filter.accountId != null ||
        filter.categoryId != null ||
        filter.types != baseline.types ||
        filter.timeScope != baseline.timeScope
}

private fun formatAmountGrouped(cents: Long): String {
    val yuan = cents / 100.0
    return NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(yuan)
}
