package com.libertelos.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.libertelos.app.data.TrendPoint
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.SeaIn
import kotlin.math.hypot
import kotlin.math.roundToInt

private val LegendOff = Color(0xFFB0B8C4)

@Composable
fun IncomeExpenseTrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    var showIncome by remember { mutableStateOf(true) }
    var showExpense by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val chartHeight = 168.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "收支趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendToggle(
                    color = SeaIn,
                    label = "收入",
                    enabled = showIncome,
                    onClick = { showIncome = !showIncome }
                )
                LegendToggle(
                    color = AmberOut,
                    label = "支出",
                    enabled = showExpense,
                    onClick = { showExpense = !showExpense }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val maxValue = remember(points, showIncome, showExpense) {
            points.maxOf { point ->
                maxOf(
                    if (showIncome) point.incomeCents else 0L,
                    if (showExpense) point.expenseCents else 0L
                )
            }.coerceAtLeast(1L)
        }

        var chartWidthPx by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .onSizeChanged { chartWidthPx = it.width }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(points, maxValue, showIncome, showExpense) {
                        detectTapGestures { tap ->
                            if (!showIncome && !showExpense) {
                                selectedIndex = null
                                return@detectTapGestures
                            }
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val padX = with(density) { 8.dp.toPx() }
                            val padY = with(density) { 12.dp.toPx() }
                            val chartW = (width - padX * 2).coerceAtLeast(1f)
                            val chartH = (height - padY * 2).coerceAtLeast(1f)
                            val stepX = if (points.size == 1) 0f else chartW / (points.size - 1)
                            var bestIndex = 0
                            var bestScore = Float.MAX_VALUE
                            points.forEachIndexed { index, point ->
                                val x = padX + stepX * index
                                val incomeY =
                                    padY + chartH * (1f - point.incomeCents.toFloat() / maxValue)
                                val expenseY =
                                    padY + chartH * (1f - point.expenseCents.toFloat() / maxValue)
                                val dIncome =
                                    if (showIncome) hypot(tap.x - x, tap.y - incomeY)
                                    else Float.MAX_VALUE
                                val dExpense =
                                    if (showExpense) hypot(tap.x - x, tap.y - expenseY)
                                    else Float.MAX_VALUE
                                val columnBias = hypot(tap.x - x, 0f)
                                val score = minOf(dIncome, dExpense) * 0.35f + columnBias
                                if (score < bestScore) {
                                    bestScore = score
                                    bestIndex = index
                                }
                            }
                            selectedIndex =
                                if (selectedIndex == bestIndex) null else bestIndex
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val padX = 8.dp.toPx()
                val padY = 12.dp.toPx()
                val chartW = (width - padX * 2).coerceAtLeast(1f)
                val chartH = (height - padY * 2).coerceAtLeast(1f)
                val stepX = if (points.size == 1) 0f else chartW / (points.size - 1)

                val gridColor = Color(0xFFD8DEE8).copy(alpha = 0.7f)
                for (i in 0..3) {
                    val y = padY + chartH * i / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(padX, y),
                        end = Offset(padX + chartW, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                fun yOf(cents: Long): Float =
                    padY + chartH * (1f - cents.toFloat() / maxValue)

                val incomePath = Path()
                val expensePath = Path()
                points.forEachIndexed { index, point ->
                    val x = padX + stepX * index
                    val incomeY = yOf(point.incomeCents)
                    val expenseY = yOf(point.expenseCents)
                    if (index == 0) {
                        incomePath.moveTo(x, incomeY)
                        expensePath.moveTo(x, expenseY)
                    } else {
                        incomePath.lineTo(x, incomeY)
                        expensePath.lineTo(x, expenseY)
                    }
                }

                val stroke = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                if (showIncome) {
                    drawPath(path = incomePath, color = SeaIn, style = stroke)
                }
                if (showExpense) {
                    drawPath(path = expensePath, color = AmberOut, style = stroke)
                }

                val showAllDots = points.size <= 40
                points.forEachIndexed { index, point ->
                    val x = padX + stepX * index
                    val incomeY = yOf(point.incomeCents)
                    val expenseY = yOf(point.expenseCents)
                    val selected = selectedIndex == index
                    if (showAllDots || selected) {
                        val r = if (selected) 5.dp.toPx() else 3.dp.toPx()
                        if (showIncome) {
                            drawCircle(color = SeaIn, radius = r, center = Offset(x, incomeY))
                        }
                        if (showExpense) {
                            drawCircle(color = AmberOut, radius = r, center = Offset(x, expenseY))
                        }
                        if (selected) {
                            if (showIncome) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x, incomeY)
                                )
                            }
                            if (showExpense) {
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = Offset(x, expenseY)
                                )
                            }
                            drawLine(
                                color = Color(0xFF5B6B8C).copy(alpha = 0.45f),
                                start = Offset(x, padY),
                                end = Offset(x, padY + chartH),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            }

            selectedIndex?.let { index ->
                if (!showIncome && !showExpense) return@let
                val point = points[index]
                val fraction = if (points.size <= 1) {
                    0.5f
                } else {
                    index.toFloat() / (points.size - 1).toFloat()
                }
                TrendTooltip(
                    point = point,
                    showIncome = showIncome,
                    showExpense = showExpense,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp)
                        .offset {
                            val tipW = 148.dp.roundToPx()
                            val x = (fraction * chartWidthPx - tipW / 2f)
                                .roundToInt()
                                .coerceIn(0, (chartWidthPx - tipW).coerceAtLeast(0))
                            IntOffset(x, 0)
                        }
                )
            }
        }
    }
}

@Composable
private fun TrendTooltip(
    point: TrendPoint,
    showIncome: Boolean,
    showExpense: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF01C2430))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = point.label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        if (showIncome) {
            Text(
                text = "收入 ${formatYuanPlain(point.incomeCents)}",
                color = Color(0xFF7FCFC6),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (showExpense) {
            Text(
                text = "支出 ${formatYuanPlain(point.expenseCents)}",
                color = Color(0xFFFFB485),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LegendToggle(
    color: Color,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (enabled) color else LegendOff
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        LegendOff
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
