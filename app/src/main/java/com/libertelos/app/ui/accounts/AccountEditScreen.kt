package com.libertelos.app.ui.accounts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.libertelos.app.data.Account
import com.libertelos.app.data.AccountIcons
import com.libertelos.app.data.AccountKind
import com.libertelos.app.data.Currencies
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.viewmodel.LedgerViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(
    viewModel: LedgerViewModel,
    accountId: Long?,
    onBack: () -> Unit
) {
    val isEdit = accountId != null
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.CASH) }
    var currency by remember { mutableStateOf("CNY") }
    var cardNumber by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var billingDay by remember { mutableStateOf("") }
    var repaymentDay by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("cash") }
    var includeInTotal by remember { mutableStateOf(true) }
    var hidden by remember { mutableStateOf(false) }
    var balance by remember { mutableStateOf("0.00") }
    var createBalanceAdjustment by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(!isEdit) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var kindExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var iconExpanded by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(accountId) {
        if (accountId != null) {
            val account = viewModel.loadAccount(accountId)
            if (account != null) {
                existing = account
                name = account.name
                kind = account.kind
                currency = account.currency
                cardNumber = account.cardNumber
                issuer = account.issuer
                remark = account.remark
                billingDay = account.billingDay?.toString().orEmpty()
                repaymentDay = account.repaymentDay?.toString().orEmpty()
                iconKey = account.iconKey
                includeInTotal = account.includeInTotal
                hidden = account.hidden
                balance = String.format(Locale.CHINA, "%.2f", account.balanceCents / 100.0)
                createBalanceAdjustment = false
            }
            loaded = true
        }
    }

    val parsedBalanceCents = remember(balance) {
        val yuan = balance.replace(",", "").trim().toDoubleOrNull()
        if (yuan == null) null else Math.round(yuan * 100)
    }
    val balanceChanged = isEdit &&
        existing != null &&
        parsedBalanceCents != null &&
        parsedBalanceCents != existing!!.balanceCents

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除账户") },
            text = { Text("确定删除「${existing!!.name}」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(existing!!) { onBack() }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑账户" else "新建账户") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (!loaded) {
            Spacer(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            FieldLabel("账户分类")
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = kindExpanded,
                onExpandedChange = { kindExpanded = it }
            ) {
                OutlinedTextField(
                    value = kind.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) },
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = kindExpanded,
                    onDismissRequest = { kindExpanded = false }
                ) {
                    AccountKind.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                kind = option
                                kindExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("账户名称")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("例如：招商银行储蓄卡") },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(18.dp))
            FieldLabel("币种")
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = it }
            ) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false }
                ) {
                    Currencies.common.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = {
                                currency = code
                                currencyExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("发卡机构")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("可选，如招商银行 / 支付宝") },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(18.dp))
            FieldLabel("卡号")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("可选") },
                shape = RoundedCornerShape(14.dp)
            )

            if (kind == AccountKind.CREDIT) {
                Spacer(Modifier.height(18.dp))
                FieldLabel("账单日")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billingDay,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^([1-9]|[12]\\d|3[01])$"))) {
                            billingDay = input
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("每月几号，如 2") },
                    suffix = { Text("日") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(18.dp))
                FieldLabel("还款日")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repaymentDay,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^([1-9]|[12]\\d|3[01])$"))) {
                            repaymentDay = input
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("每月几号，如 12") },
                    suffix = { Text("日") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("备注")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("可选，如赎回日、份额说明") },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(18.dp))
            FieldLabel("余额")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = balance,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^-?\\d*\\.?\\d{0,2}$"))) {
                        balance = input
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("¥ ") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp)
            )
            if (isEdit && balanceChanged) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createBalanceAdjustment = !createBalanceAdjustment }
                ) {
                    Checkbox(
                        checked = createBalanceAdjustment,
                        onCheckedChange = { createBalanceAdjustment = it }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "同时新增收入/支出记录",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "余额增加记「其他收入」，减少记「损失 · 其他支出」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("账户图标")
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = iconExpanded,
                onExpandedChange = { iconExpanded = it }
            ) {
                OutlinedTextField(
                    value = " ",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    leadingIcon = {
                        AccountIconBadge(iconKey = iconKey, size = 28.dp)
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(iconExpanded) },
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = iconExpanded,
                    onDismissRequest = { iconExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 5
                    ) {
                        AccountIcons.all.forEach { spec ->
                            val selected = iconKey == spec.key
                            AccountIconBadge(
                                iconKey = spec.key,
                                size = 40.dp,
                                modifier = Modifier
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) InkTeal
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        iconKey = spec.key
                                        iconExpanded = false
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            ToggleRow(
                title = "计入总账",
                subtitle = "关闭后，该账户不参与净资产汇总",
                checked = includeInTotal,
                onCheckedChange = { includeInTotal = it }
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "隐藏账户",
                subtitle = "隐藏后仍保留数据，可在此页继续编辑",
                checked = hidden,
                onCheckedChange = { hidden = it }
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    viewModel.saveAccount(
                        id = accountId ?: 0L,
                        name = name,
                        kind = kind,
                        currency = currency,
                        cardNumber = cardNumber,
                        issuer = issuer,
                        remark = remark,
                        billingDay = billingDay.toIntOrNull(),
                        repaymentDay = repaymentDay.toIntOrNull(),
                        iconKey = iconKey,
                        includeInTotal = includeInTotal,
                        hidden = hidden,
                        balanceYuan = balance,
                        createBalanceAdjustment = createBalanceAdjustment && balanceChanged,
                        previousBalanceCents = existing?.balanceCents,
                        createdAt = existing?.createdAt,
                        onDone = onBack
                    )
                },
                enabled = name.trim().isNotEmpty() && parsedBalanceCents != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
            ) {
                Text("保存", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
