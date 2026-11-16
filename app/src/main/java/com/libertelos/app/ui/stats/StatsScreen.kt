package com.libertelos.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.Account
import com.libertelos.app.data.AccountKind
import com.libertelos.app.data.AccountShareItem
import com.libertelos.app.data.CategoryRankItem
import com.libertelos.app.data.LedgerRepository
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.PeriodPickMode
import com.libertelos.app.ui.components.PeriodPickerDialog
import com.libertelos.app.ui.home.IncomeExpenseTrendChart
import com.libertelos.app.ui.ledger.LedgerTimeScope
import com.libertelos.app.ui.ledger.filteredIncomeExpense
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

private data class StatsPeriod(
    val scope: LedgerTimeScope,
    val year: Int,
    val month: Int
) {
    init {
        require(scope == LedgerTimeScope.MONTH || scope == LedgerTimeScope.YEAR)
    }

    fun shift(delta: Int): StatsPeriod = when (scope) {
        LedgerTimeScope.MONTH -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, delta)
            }
            copy(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1
            )
        }
        else -> copy(year = year + delta)
    }

    fun range(): Pair<Long, Long> = when (scope) {
        LedgerTimeScope.MONTH -> LedgerRepository.monthRange(year, month)
        else -> LedgerRepository.yearRange(year)
    }

    val label: String
        get() = when (scope) {
            LedgerTimeScope.MONTH -> "${year}年${month}月"
            else -> "${year}年"
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: LedgerViewModel,
    onBack: () -> Unit
) {
    val details by viewModel.allTransactionDetails.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val now = remember { Calendar.getInstance() }
    var period by remember {
        mutableStateOf(
            StatsPeriod(
                scope = LedgerTimeScope.YEAR,
                year = now.get(Calendar.YEAR),
                month = now.get(Calendar.MONTH) + 1
            )
        )
    }
    var showPeriodPicker by remember { mutableStateOf(false) }

    if (showPeriodPicker) {
        if (period.scope == LedgerTimeScope.MONTH) {
            PeriodPickerDialog(
                mode = PeriodPickMode.YEAR_MONTH,
                initialYear = period.year,
                initialMonth = period.month,
                onConfirm = { year, month ->
                    period = period.copy(year = year, month = month)
                    showPeriodPicker = false
                },
                onDismiss = { showPeriodPicker = false }
            )
        } else {
            PeriodPickerDialog(
                mode = PeriodPickMode.YEAR,
                initialYear = period.year,
                onConfirm = { year, _ ->
                    period = period.copy(year = year)
                    showPeriodPicker = false
                },
                onDismiss = { showPeriodPicker = false }
            )
        }
    }

    val (startMs, endMs) = remember(period) { period.range() }
    val inRange = remember(details, startMs, endMs) {
        details.filter {
            val t = it.transaction
            t.occurredAt >= startMs &&
                t.occurredAt < endMs &&
                t.type != TxnType.BALANCE_ADJUSTMENT
        }
    }
    val (incomeCents, expenseCents) = remember(inRange) { filteredIncomeExpense(inRange) }
    val balanceCents = incomeCents - expenseCents
    val txnCount = inRange.size
    val incomeCount = remember(inRange) {
        inRange.count { it.transaction.type == TxnType.INCOME }
    }
    val expenseCount = remember(inRange) {
        inRange.count { it.transaction.type == TxnType.EXPENSE }
    }

    val incomeBreakdown = remember(details, startMs, endMs) {
        viewModel.categoryBreakdown(startMs, endMs, TxnType.INCOME, details)
    }
    val expenseBreakdown = remember(details, startMs, endMs) {
        viewModel.categoryBreakdown(startMs, endMs, TxnType.EXPENSE, details)
    }
    val trendPoints = remember(details, period) {
        viewModel.statsTrendSeries(
            year = period.year,
            month = if (period.scope == LedgerTimeScope.MONTH) period.month else null,
            txns = details.map { it.transaction }
        )
    }
    val assetShares = remember(accounts) { buildAccountShares(accounts, liability = false) }
    val liabilityShares = remember(accounts) { buildAccountShares(accounts, liability = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    ScopeToggle(
                        scope = period.scope,
                        onScopeChange = { scope ->
                            period = period.copy(scope = scope)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PeriodNavRow(
                    label = period.label,
                    onPrev = { period = period.shift(-1) },
                    onNext = { period = period.shift(1) },
                    onPickPeriod = { showPeriodPicker = true },
                    prevDesc = if (period.scope == LedgerTimeScope.MONTH) "上月" else "上一年",
                    nextDesc = if (period.scope == LedgerTimeScope.MONTH) "下月" else "下一年"
                )
            }

            item {
                SummaryHeroCard(
                    balanceCents = balanceCents,
                    incomeCents = incomeCents,
                    expenseCents = expenseCents
                )
            }

            item {
                MilestoneCard(
                    txnCount = txnCount,
                    incomeCount = incomeCount,
                    expenseCount = expenseCount
                )
            }

            item {
                CategorySection(
                    title = "收入来源",
                    emptyText = "所选周期暂无收入",
                    items = incomeBreakdown.items
                )
            }

            item {
                CategorySection(
                    title = "支出分布",
                    emptyText = "所选周期暂无支出",
                    items = expenseBreakdown.items
                )
            }

            item {
                IncomeExpenseTrendChart(
                    points = trendPoints,
                    title = if (period.scope == LedgerTimeScope.YEAR) {
                        "月度收支趋势"
                    } else {
                        "每日收支趋势"
                    }
                )
            }

            item {
                AccountShareSection(
                    title = "资产类账户统计",
                    totalLabel = "总资产",
                    items = assetShares
                )
            }

            item {
                AccountShareSection(
                    title = "负债类账户统计",
                    totalLabel = "总负债",
                    items = liabilityShares
                )
            }
        }
    }
}

@Composable
private fun ScopeToggle(
    scope: LedgerTimeScope,
    onScopeChange: (LedgerTimeScope) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF0F3F7))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(LedgerTimeScope.MONTH to "按月", LedgerTimeScope.YEAR to "按年").forEach { (value, label) ->
            val selected = scope == value
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) InkTeal else Color(0xFF8A94A6),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onScopeChange(value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun PeriodNavRow(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickPeriod: () -> Unit,
    prevDesc: String,
    nextDesc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = prevDesc)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickPeriod)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextDesc)
        }
    }
}

@Composable
private fun SummaryHeroCard(
    balanceCents: Long,
    incomeCents: Long,
    expenseCents: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D4F4F), Color(0xFF1A6B6B), Color(0xFF0F766E))
                )
            )
            .padding(20.dp)
    ) {
        Text(
            text = "账本流水统计",
            color = Color(0xFFB7DED9),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "结余",
            color = Color(0xFFB7DED9),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = formatAmountGrouped(balanceCents),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("总收入", color = Color(0xFFB7DED9), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatAmountGrouped(incomeCents),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("总支出", color = Color(0xFFB7DED9), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatAmountGrouped(expenseCents),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MilestoneCard(
    txnCount: Int,
    incomeCount: Int,
    expenseCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "记账里程碑",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MilestoneStat(
                label = "记账笔数",
                value = txnCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MilestoneStat(
                label = "收入笔数",
                value = incomeCount.toString(),
                modifier = Modifier.weight(1f)
            )
            MilestoneStat(
                label = "支出笔数",
                value = expenseCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MilestoneStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategorySection(
    title: String,
    emptyText: String,
    items: List<CategoryRankItem>
) {
    var expandedIds by remember(items) { mutableStateOf(setOf<Long>()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (items.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(6.dp))
        } else {
            Spacer(Modifier.height(14.dp))
            items.forEachIndexed { index, item ->
                val expandable = item.children.size > 1 ||
                    (item.children.size == 1 && item.children.first().category.id != item.category.id)
                val expanded = item.category.id in expandedIds
                CategoryShareRow(
                    item = item,
                    expandable = expandable,
                    expanded = expanded,
                    onToggle = {
                        expandedIds = if (expanded) {
                            expandedIds - item.category.id
                        } else {
                            expandedIds + item.category.id
                        }
                    }
                )
                if (expanded && expandable) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item.children.forEach { child ->
                            CategoryShareRow(
                                item = child,
                                expandable = false,
                                expanded = false,
                                onToggle = {},
                                compact = true
                            )
                        }
                    }
                }
                if (index != items.lastIndex) Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun CategoryShareRow(
    item: CategoryRankItem,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    compact: Boolean = false
) {
    val color = Color(item.category.colorArgb)
    val iconSize = if (compact) 28.dp else 36.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expandable) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggle)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.category.name.take(1),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    }
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category.name,
                    style = if (compact) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.txnCount} 笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expandable) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起子分类" else "展开子分类",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = String.format(Locale.CHINA, "%.2f%%", item.ratio * 100f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " · ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatAmountPlain(item.totalCents),
                style = if (compact) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 6.dp else 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFE8EDF3))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(item.ratio.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun AccountShareSection(
    title: String,
    totalLabel: String,
    items: List<AccountShareItem>
) {
    val total = items.sumOf { it.amountCents }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$totalLabel ${formatAmountPlain(total)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) {
            Text(
                text = "暂无账户",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            val sliceColors = remember(items) { chartSliceColors(items.size) }
            AccountShareDonut(
                items = items,
                colors = sliceColors,
                centerLabel = totalLabel,
                centerAmount = total,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(Modifier.height(14.dp))
            items.forEachIndexed { index, item ->
                AccountShareLegendRow(item = item, color = sliceColors[index])
                if (index != items.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AccountShareDonut(
    items: List<AccountShareItem>,
    colors: List<Color>,
    centerLabel: String,
    centerAmount: Long,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thickness = 28.dp.toPx()
            val diameter = min(size.width, size.height) - thickness
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var start = -90f
            items.forEachIndexed { index, item ->
                val sweep = (item.ratio * 360f).coerceAtLeast(0.5f)
                drawArc(
                    color = colors[index],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = thickness, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatAmountPlain(centerAmount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AccountShareLegendRow(item: AccountShareItem, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.account.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = String.format(Locale.CHINA, "%.2f%%", item.ratio * 100f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatAmountPlain(item.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 饼图专用色板：相邻块色相差大，避免同图标账户撞色。 */
private val ChartPalette = listOf(
    Color(0xFFE11D48),
    Color(0xFF0EA5A4),
    Color(0xFFF59E0B),
    Color(0xFF2F6FED),
    Color(0xFF8B5CF6),
    Color(0xFF10B981),
    Color(0xFFEC4899),
    Color(0xFF0D4F4F),
    Color(0xFFE07A3D),
    Color(0xFF64748B),
    Color(0xFF06B6D4),
    Color(0xFFA855F7),
    Color(0xFF84CC16),
    Color(0xFFF43F5E),
    Color(0xFF2563EB),
    Color(0xFF14B8A6)
)

private fun chartSliceColors(count: Int): List<Color> {
    if (count <= 0) return emptyList()
    return List(count) { index ->
        val base = ChartPalette[index % ChartPalette.size]
        if (index < ChartPalette.size) {
            base
        } else {
            // 超出色板时微调明度，继续保证可区分
            val cycle = index / ChartPalette.size
            val factor = 1f - (cycle % 3) * 0.12f
            Color(
                red = (base.red * factor).coerceIn(0.15f, 1f),
                green = (base.green * factor).coerceIn(0.15f, 1f),
                blue = (base.blue * factor).coerceIn(0.15f, 1f),
                alpha = 1f
            )
        }
    }
}

private fun buildAccountShares(
    accounts: List<Account>,
    liability: Boolean
): List<AccountShareItem> {
    val filtered = accounts
        .filter { !it.hidden && it.includeInTotal }
        .filter { liability == it.kind.isLiability }
        .map { it to kotlin.math.abs(it.balanceCents) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    val total = filtered.sumOf { it.second }
    return filtered.map { (account, amount) ->
        AccountShareItem(
            account = account,
            amountCents = amount,
            ratio = if (total > 0) amount.toFloat() / total.toFloat() else 0f
        )
    }
}

private val AccountKind.isLiability: Boolean
    get() = this == AccountKind.CREDIT || this == AccountKind.VIRTUAL_LIABILITY

private fun formatAmountGrouped(cents: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return nf.format(cents / 100.0)
}

private fun formatAmountPlain(cents: Long): String =
    String.format(Locale.CHINA, "%.2f", cents / 100.0)
