package com.libertelos.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.IncomeExpenseReport
import com.libertelos.app.data.MonthSummary
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.ui.theme.SeaIn
import com.libertelos.app.viewmodel.LedgerViewModel

@Composable
fun HomeScreen(
    viewModel: LedgerViewModel,
    onAdd: () -> Unit,
    onLedger: () -> Unit,
    onStats: () -> Unit,
    onAccounts: () -> Unit
) {
    val report by viewModel.incomeExpenseReport.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val allDetails by viewModel.allTransactionDetails.collectAsStateWithLifecycle()
    var balancePeriod by remember { mutableStateOf(ReportPeriod.YEAR) }
    var rankingType by remember { mutableStateOf(TxnType.EXPENSE) }
    val balanceSummary = remember(report, balancePeriod) {
        report.summaryFor(balancePeriod)
    }
    val trendPoints = remember(allTransactions, balancePeriod) {
        viewModel.trendSeries(balancePeriod, allTransactions)
    }
    val categoryRanking = remember(allDetails, balancePeriod, rankingType) {
        viewModel.categoryRanking(balancePeriod, rankingType, allDetails)
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFE8F0F2), Color(0xFFF2F4F7), Color(0xFFF7F8FA))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "liber-telos",
                                style = MaterialTheme.typography.headlineMedium,
                                color = InkTeal
                            )
                            Text(
                                text = "清醒记账，看见目的",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            TextButton(onClick = onAccounts) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("账户")
                            }
                            TextButton(onClick = onStats) {
                                Icon(Icons.Default.BarChart, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("统计")
                            }
                        }
                    }
                }

                item {
                    SummaryPanel(
                        period = balancePeriod,
                        onPeriodChange = { balancePeriod = it },
                        summary = balanceSummary
                    )
                }

                item {
                    IncomeExpenseTrendChart(points = trendPoints)
                }

                item {
                    CategoryRankingCard(
                        ranking = categoryRanking,
                        onToggleType = {
                            rankingType = if (rankingType == TxnType.EXPENSE) {
                                TxnType.INCOME
                            } else {
                                TxnType.EXPENSE
                            }
                        }
                    )
                }

                item {
                    Text(
                        text = "收支报告",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                item {
                    PeriodReportCard(title = "今天", summary = report.today)
                }
                item {
                    PeriodReportCard(title = "本周", summary = report.week)
                }
                item {
                    PeriodReportCard(title = "本月", summary = report.month)
                }

                item { Spacer(Modifier.height(88.dp)) }
            }

            FloatingActionButton(
                onClick = onLedger,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                containerColor = InkTeal,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "流水")
            }

            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = InkTeal,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        }
    }
}

private fun IncomeExpenseReport.summaryFor(period: ReportPeriod): MonthSummary =
    when (period) {
        ReportPeriod.YEAR -> year
        ReportPeriod.MONTH -> month
        ReportPeriod.WEEK -> week
        ReportPeriod.DAY -> today
    }

@Composable
private fun SummaryPanel(
    period: ReportPeriod,
    onPeriodChange: (ReportPeriod) -> Unit,
    summary: MonthSummary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D4F4F), Color(0xFF146363))
                )
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReportPeriod.entries.forEach { option ->
                val selected = option == period
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color(0xFF0D4F4F) else Color(0xFFB7DED9),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) Color.White.copy(alpha = 0.92f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable { onPeriodChange(option) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("结余", color = Color(0xFFB7DED9), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = summary,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "balance_summary"
        ) { s ->
            Column {
                Text(
                    text = formatYuanPlain(s.balanceCents),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "收入",
                            color = Color(0xFFB7DED9),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatYuanPlain(s.incomeCents),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "支出",
                            color = Color(0xFFB7DED9),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatYuanPlain(s.expenseCents),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodReportCard(title: String, summary: MonthSummary) {
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
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatYuanPlain(summary.incomeCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = SeaIn,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatYuanPlain(summary.expenseCents),
                    style = MaterialTheme.typography.titleLarge,
                    color = AmberOut,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
