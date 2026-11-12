package com.libertelos.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.libertelos.app.data.TransactionWithDetails
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.ReimburseAmber
import com.libertelos.app.ui.theme.SeaIn
import com.libertelos.app.ui.theme.TransferBlue
import com.libertelos.app.util.LocationHelper
import com.libertelos.app.viewmodel.LedgerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: LedgerViewModel,
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var locationLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(transactionId) {
        details = viewModel.loadTransactionDetails(transactionId)
    }
    LaunchedEffect(details?.transaction?.id, details?.transaction?.locationAddress) {
        val txn = details?.transaction ?: return@LaunchedEffect
        when {
            txn.locationAddress.isNotBlank() -> locationLabel = txn.locationAddress
            txn.latitude != null && txn.longitude != null -> {
                locationLabel = "解析地址中…"
                val address = LocationHelper.reverseGeocode(
                    context,
                    txn.latitude,
                    txn.longitude
                )
                if (!address.isNullOrBlank()) {
                    locationLabel = address
                    viewModel.persistLocationAddress(txn.id, address)
                } else {
                    locationLabel = String.format(
                        Locale.CHINA,
                        "%.5f, %.5f",
                        txn.latitude,
                        txn.longitude
                    )
                }
            }
            else -> locationLabel = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账目详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val item = details
        if (item == null) {
            Spacer(Modifier.padding(padding))
            return@Scaffold
        }
        val txn = item.transaction
        val timeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
        val amountColor = when (txn.type) {
            TxnType.EXPENSE -> AmberOut
            TxnType.INCOME -> SeaIn
            TxnType.TRANSFER -> TransferBlue
            TxnType.REIMBURSEMENT -> ReimburseAmber
            TxnType.BALANCE_ADJUSTMENT -> Color(0xFF8A94A6)
        }
        val amountPrefix = when (txn.type) {
            TxnType.EXPENSE -> "-"
            TxnType.INCOME, TxnType.REIMBURSEMENT -> "+"
            TxnType.TRANSFER, TxnType.BALANCE_ADJUSTMENT -> ""
        }
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
                val target = when {
                    leaf != null && parent != null -> "$parent · $leaf"
                    leaf != null -> leaf
                    else -> "支出"
                }
                "报销 · $target"
            }
            TxnType.BALANCE_ADJUSTMENT ->
                txn.note.ifBlank { "余额变更" }
            else -> {
                val leaf = item.category?.name
                val parent = item.parentCategory?.name
                when {
                    leaf != null && parent != null -> "$parent · $leaf"
                    leaf != null -> leaf
                    else -> "未分类"
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Text(
                    text = when (txn.type) {
                        TxnType.EXPENSE -> "支出"
                        TxnType.INCOME -> "收入"
                        TxnType.TRANSFER -> "转账"
                        TxnType.REIMBURSEMENT -> "报销"
                        TxnType.BALANCE_ADJUSTMENT -> "余额变更"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = amountPrefix + formatYuanPlain(txn.amountCents).removePrefix("¥"),
                    style = MaterialTheme.typography.displayLarge,
                    color = amountColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(16.dp))
            DetailCard {
                DetailRow("发生时间", timeFmt.format(Date(txn.occurredAt)))
                when (txn.type) {
                    TxnType.TRANSFER -> {
                        DetailRow("转出账户", item.fromAccount?.name ?: "—")
                        DetailRow("转入账户", item.toAccount?.name ?: "—")
                    }
                    TxnType.REIMBURSEMENT -> {
                        DetailRow("到账账户", item.account?.name ?: "—")
                        val linked = item.linkedExpense
                        val linkedTitle = when {
                            linked?.parentCategory != null && linked.category != null ->
                                "${linked.parentCategory.name} · ${linked.category.name}"
                            linked?.category != null -> linked.category.name
                            else -> "—"
                        }
                        DetailRow("原支出", linkedTitle)
                    }
                    TxnType.BALANCE_ADJUSTMENT -> {
                        DetailRow("账户", item.account?.name ?: "—")
                        DetailRow("说明", "不计入收入/支出")
                    }
                    else -> {
                        DetailRow("分类", title)
                        DetailRow("账户", item.account?.name ?: "—")
                    }
                }
                DetailRow("备注", txn.note.ifBlank { "—" })
                DetailRow("创建时间", timeFmt.format(Date(txn.createdAt)))
                DetailRow("修改时间", timeFmt.format(Date(txn.updatedAt)))
                locationLabel?.let { DetailRow("位置", it) }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
