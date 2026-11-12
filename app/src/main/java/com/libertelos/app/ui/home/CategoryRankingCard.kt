package com.libertelos.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libertelos.app.data.CategoryRankItem
import com.libertelos.app.data.CategoryRanking
import com.libertelos.app.data.ReportPeriod
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.theme.SeaIn
import java.util.Locale

@Composable
fun CategoryRankingCard(
    ranking: CategoryRanking,
    onToggleType: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpense = ranking.type == TxnType.EXPENSE
    val typeLabel = if (isExpense) "支出" else "收入"
    val title = "${ranking.period.rankTitlePrefix}各分类${typeLabel}排行"
    val totalColor = if (isExpense) Color(0xFFE07A3D) else SeaIn

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleType
                    )
                    .padding(vertical = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "记账笔数 ${ranking.txnCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "总$typeLabel ${formatAmountPlain(ranking.totalCents)}",
                style = MaterialTheme.typography.labelMedium,
                color = totalColor,
                fontWeight = FontWeight.Medium
            )
        }

        if (ranking.items.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "所选周期暂无${typeLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(14.dp))
            ranking.items.forEachIndexed { index, item ->
                CategoryRankRow(
                    rank = index + 1,
                    item = item
                )
                if (index != ranking.items.lastIndex) {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryRankRow(rank: Int, item: CategoryRankItem) {
    val color = Color(item.category.colorArgb)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.category.name.take(1),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.category.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(22.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
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
}

private val ReportPeriod.rankTitlePrefix: String
    get() = when (this) {
        ReportPeriod.YEAR -> "本年"
        ReportPeriod.MONTH -> "本月"
        ReportPeriod.WEEK -> "本周"
        ReportPeriod.DAY -> "今日"
    }

private fun formatAmountPlain(cents: Long): String =
    String.format(Locale.CHINA, "%.2f", cents / 100.0)
