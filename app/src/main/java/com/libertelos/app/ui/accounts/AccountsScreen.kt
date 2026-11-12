package com.libertelos.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.Account
import com.libertelos.app.data.AccountKind
import com.libertelos.app.data.AccountKindOrderStore
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.viewmodel.LedgerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: LedgerViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var showExcluded by remember { mutableStateOf(false) }
    var kindOrder by remember { mutableStateOf(AccountKindOrderStore.load(context)) }
    var draggingKind by remember { mutableStateOf<AccountKind?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val visibleAccounts = remember(accounts, showExcluded) {
        if (showExcluded) accounts else accounts.filter { it.includeInTotal }
    }
    val grouped = remember(visibleAccounts) {
        visibleAccounts.groupBy { it.kind }
    }
    val visibleKinds = remember(kindOrder, grouped) {
        kindOrder.filter { grouped[it].orEmpty().isNotEmpty() }
    }
    val totals = remember(accounts) {
        val counted = accounts.filter { it.includeInTotal }
        val assets = counted.filterNot { it.kind.isLiability }.sumOf { it.balanceCents }
        val liabilities = counted.filter { it.kind.isLiability }.sumOf { it.balanceCents }
        AccountTotals(
            netAssetsCents = assets - liabilities,
            totalAssetsCents = assets,
            totalLiabilitiesCents = liabilities
        )
    }

    fun persistOrder(order: List<AccountKind>) {
        kindOrder = order
        AccountKindOrderStore.save(context, order)
    }

    fun moveKind(fromKind: AccountKind, toIndexInVisible: Int) {
        val full = kindOrder.toMutableList()
        val fromFull = full.indexOf(fromKind)
        if (fromFull < 0) return
        val targetKind = visibleKinds.getOrNull(toIndexInVisible) ?: return
        val toFull = full.indexOf(targetKind)
        if (toFull < 0 || fromFull == toFull) return
        full.removeAt(fromFull)
        full.add(toFull, fromKind)
        persistOrder(full)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showExcluded = !showExcluded }) {
                        Icon(
                            imageVector = if (showExcluded) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (showExcluded) {
                                "隐藏不计入总账的账户"
                            } else {
                                "显示不计入总账的账户"
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = InkTeal,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建账户")
            }
        }
    ) { padding ->
        if (visibleAccounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (accounts.isEmpty()) {
                        "还没有账户，点右下角新建"
                    } else {
                        "当前没有计入总账的账户\n点右上角可显示不计入总账的账户"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            val isReordering = draggingKind != null
            val swapThresholdPx = with(density) { 48.dp.toPx() }
            val latestVisibleKinds by rememberUpdatedState(visibleKinds)
            val latestMoveKind by rememberUpdatedState<(AccountKind, Int) -> Unit> { from, to ->
                moveKind(from, to)
            }
            LazyColumn(
                state = listState,
                userScrollEnabled = !isReordering,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (isReordering) 6.dp else 10.dp
                )
            ) {
                if (!isReordering) {
                    item(key = "account_totals") {
                        AccountTotalsCard(totals)
                    }
                }
                items(visibleKinds, key = { it.name }) { kind ->
                    val list = grouped[kind].orEmpty()
                    val isDragging = draggingKind == kind
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                IntOffset(
                                    0,
                                    if (isDragging) dragOffsetY.roundToInt() else 0
                                )
                            }
                    ) {
                        // Modifier 链类型保持稳定，避免收起时重建 pointerInput 节点导致拖动立刻取消
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isDragging -> MaterialTheme.colorScheme.surfaceVariant
                                        isReordering -> MaterialTheme.colorScheme.surface
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(
                                    horizontal = if (isReordering) 12.dp else 0.dp,
                                    vertical = if (isReordering) 12.dp else 0.dp
                                )
                                .padding(
                                    top = if (isReordering) 0.dp else 10.dp,
                                    bottom = if (isReordering) 0.dp else 2.dp
                                )
                                .pointerInput(kind) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingKind = kind
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingKind = null
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingKind = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val kinds = latestVisibleKinds
                                            val currentIndex = kinds.indexOf(kind)
                                            if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                            val steps = (dragOffsetY / swapThresholdPx).toInt()
                                            if (steps != 0) {
                                                val target = (currentIndex + steps)
                                                    .coerceIn(0, kinds.lastIndex)
                                                if (target != currentIndex) {
                                                    latestMoveKind(kind, target)
                                                    dragOffsetY -= steps * swapThresholdPx
                                                }
                                            }
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = kind.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "长按拖动排序",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        if (!isReordering) {
                            list.forEach { account ->
                                Spacer(Modifier.height(8.dp))
                                AccountRow(
                                    account = account,
                                    onClick = { onOpen(account.id) },
                                    onEdit = { onEdit(account.id) }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

private data class AccountTotals(
    val netAssetsCents: Long,
    val totalAssetsCents: Long,
    val totalLiabilitiesCents: Long
)

private val AccountKind.isLiability: Boolean
    get() = this == AccountKind.CREDIT || this == AccountKind.VIRTUAL_LIABILITY

@Composable
private fun AccountTotalsCard(totals: AccountTotals) {
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
        Text("净资产", color = Color(0xFFB7DED9), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatYuanPlain(totals.netAssetsCents),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "总资产",
                    color = Color(0xFFB7DED9),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatYuanPlain(totals.totalAssetsCents),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "总负债",
                    color = Color(0xFFB7DED9),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatYuanPlain(totals.totalLiabilitiesCents),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountIconBadge(iconKey = account.iconKey)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, style = MaterialTheme.typography.titleMedium)
                if (account.hidden) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "已隐藏",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            val meta = buildList {
                add(formatYuanPlain(account.balanceCents))
                add(account.currency)
                if (account.issuer.isNotBlank()) add(account.issuer)
                if (account.cardNumber.isNotBlank()) {
                    add(maskCard(account.cardNumber))
                }
                if (!account.includeInTotal) add("不计入总账")
            }.joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "编辑账户",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun maskCard(raw: String): String {
    val digits = raw.filter { it.isDigit() || it.isLetter() }
    return if (digits.length <= 4) digits else "**** ${digits.takeLast(4)}"
}
