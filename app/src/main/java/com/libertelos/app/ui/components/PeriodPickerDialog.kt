package com.libertelos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.libertelos.app.ui.theme.InkTeal
import java.util.Calendar

enum class PeriodPickMode {
    /** 只选年份 */
    YEAR,
    /** 选年月 */
    YEAR_MONTH
}

@Composable
fun PeriodPickerDialog(
    mode: PeriodPickMode,
    initialYear: Int,
    initialMonth: Int = 1,
    onConfirm: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit,
    minYear: Int = 2000,
    maxYear: Int = Calendar.getInstance().get(Calendar.YEAR) + 1
) {
    var year by remember(initialYear) {
        mutableIntStateOf(initialYear.coerceIn(minYear, maxYear))
    }
    var month by remember(initialMonth) {
        mutableIntStateOf(initialMonth.coerceIn(1, 12))
    }
    val years = remember(minYear, maxYear) { (maxYear downTo minYear).toList() }
    val yearListState = rememberLazyListState()

    LaunchedEffect(years, year) {
        val index = years.indexOf(year)
        if (index >= 0) {
            yearListState.scrollToItem((index - 2).coerceAtLeast(0))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (mode == PeriodPickMode.YEAR) "选择年份" else "选择年月")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "年份",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = yearListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F3F7)),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(years) { y ->
                        val selected = y == year
                        Text(
                            text = "${y}年",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) InkTeal else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { year = y }
                                .background(if (selected) Color.White else Color.Transparent)
                                .padding(vertical = 10.dp)
                        )
                    }
                }

                if (mode == PeriodPickMode.YEAR_MONTH) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "月份",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MonthGrid(
                        selectedMonth = month,
                        onSelect = { month = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(year, month) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = Color.White
    )
}

@Composable
private fun MonthGrid(
    selectedMonth: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0 until 3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..4).forEach { col ->
                    val m = row * 4 + col
                    val selected = m == selectedMonth
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) InkTeal else Color(0xFFF0F3F7))
                            .clickable { onSelect(m) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${m}月",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
