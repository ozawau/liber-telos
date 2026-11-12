package com.libertelos.app.ui.add

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.Account
import com.libertelos.app.data.CategoryGroup
import com.libertelos.app.data.DefaultCategories
import com.libertelos.app.data.ReimbursableExpense
import com.libertelos.app.data.Transaction
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.components.formatYuanPlain
import com.libertelos.app.ui.theme.AmberOut
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.ui.theme.ReimburseAmber
import com.libertelos.app.ui.theme.SeaIn
import com.libertelos.app.ui.theme.TransferBlue
import com.libertelos.app.util.LocationHelper
import com.libertelos.app.viewmodel.LedgerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddScreen(
    viewModel: LedgerViewModel,
    transactionId: Long? = null,
    onBack: () -> Unit,
    onEditCategories: (TxnType) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = transactionId != null
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.visibleAccounts.collectAsStateWithLifecycle()
    val reimbursable by viewModel.reimbursableExpenses.collectAsStateWithLifecycle()

    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var fromAccountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var linkedExpenseId by remember { mutableStateOf<Long?>(null) }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var existing by remember { mutableStateOf<Transaction?>(null) }
    var loaded by remember { mutableStateOf(!isEdit) }
    var saving by remember { mutableStateOf(false) }

    var accountExpanded by remember { mutableStateOf(false) }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showExpenseSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var lastReimburseAccountApplied by remember { mutableStateOf(false) }

    val leafCategories = remember(categories, type) {
        categories.filter { it.type == type && it.isLeaf }
    }
    val categoryGroups = remember(categories, type) {
        DefaultCategories.groupByParent(categories.filter { it.type == type })
    }
    val selectedCategory = leafCategories.firstOrNull { it.id == selectedCategoryId }
    val selectedParent = categories.firstOrNull { it.id == selectedCategory?.parentId }
    val selectedExpense = reimbursable.firstOrNull {
        it.details.transaction.id == linkedExpenseId
    }
    val occurredLabel = remember(occurredAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(occurredAt))
    }

    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            val txn = viewModel.loadTransaction(transactionId)
            if (txn != null) {
                existing = txn
                type = txn.type
                amount = String.format(Locale.CHINA, "%.2f", txn.amountCents / 100.0)
                note = txn.note
                selectedCategoryId = txn.categoryId
                accountId = txn.accountId
                fromAccountId = txn.fromAccountId
                toAccountId = txn.toAccountId
                linkedExpenseId = txn.linkedTransactionId
                occurredAt = txn.occurredAt
            }
            loaded = true
        }
    }

    LaunchedEffect(type) {
        if (isEdit) return@LaunchedEffect
        selectedCategoryId = null
        accountId = null
        linkedExpenseId = null
        // 切换类型时保留已填金额
        lastReimburseAccountApplied = false
        if (type == TxnType.REIMBURSEMENT) {
            val lastId = viewModel.lastReimbursementAccountId()
            if (lastId != null && accounts.any { it.id == lastId }) {
                accountId = lastId
                lastReimburseAccountApplied = true
            }
        }
    }

    LaunchedEffect(leafCategories, selectedCategoryId) {
        if (selectedCategoryId != null && leafCategories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }

    LaunchedEffect(reimbursable, linkedExpenseId) {
        if (linkedExpenseId != null &&
            reimbursable.none { it.details.transaction.id == linkedExpenseId } &&
            !isEdit
        ) {
            linkedExpenseId = null
        }
    }

    LaunchedEffect(accounts, accountId, type) {
        if (accountId != null && accounts.none { it.id == accountId }) {
            // keep selected even if hidden while editing
            if (!isEdit) accountId = null
        }
        if (type == TxnType.TRANSFER) {
            if (fromAccountId == null || (!isEdit && accounts.none { it.id == fromAccountId })) {
                fromAccountId = accounts.firstOrNull()?.id
            }
            if (toAccountId == null || (!isEdit && accounts.none { it.id == toAccountId })) {
                toAccountId = accounts.drop(1).firstOrNull()?.id ?: accounts.firstOrNull()?.id
            }
        }
        if (type == TxnType.REIMBURSEMENT && !lastReimburseAccountApplied && accountId == null && !isEdit) {
            val lastId = viewModel.lastReimbursementAccountId()
            if (lastId != null && accounts.any { it.id == lastId }) {
                accountId = lastId
                lastReimburseAccountApplied = true
            }
        }
    }

    val amountCents = remember(amount) {
        val yuan = amount.replace(",", "").trim().toDoubleOrNull()
        if (yuan != null && yuan > 0) Math.round(yuan * 100) else null
    }

    val reimbursableForEdit = remember(reimbursable, existing, linkedExpenseId, amountCents) {
        if (!isEdit || existing?.type != TxnType.REIMBURSEMENT) return@remember reimbursable
        val linkedId = linkedExpenseId ?: return@remember reimbursable
        val current = reimbursable.firstOrNull { it.details.transaction.id == linkedId }
        if (current != null) {
            // add back this record's amount into remaining ceiling
            listOf(
                current.copy(reimbursedCents = (current.reimbursedCents - existing!!.amountCents).coerceAtLeast(0))
            ) + reimbursable.filter { it.details.transaction.id != linkedId }
        } else {
            reimbursable
        }
    }
    val selectedExpenseForSave = reimbursableForEdit.firstOrNull {
        it.details.transaction.id == linkedExpenseId
    }

    val canSave = when (type) {
        TxnType.EXPENSE, TxnType.INCOME ->
            amountCents != null && selectedCategoryId != null && accountId != null
        TxnType.TRANSFER ->
            amountCents != null &&
                fromAccountId != null &&
                toAccountId != null &&
                fromAccountId != toAccountId &&
                (accounts.size >= 2 || isEdit)
        TxnType.REIMBURSEMENT -> {
            val remaining = selectedExpenseForSave?.remainingCents
            amountCents != null &&
                linkedExpenseId != null &&
                accountId != null &&
                remaining != null &&
                amountCents <= remaining
        }
        TxnType.BALANCE_ADJUSTMENT -> false
    }

    fun persist(lat: Double?, lng: Double?, address: String = "") {
        viewModel.saveTransaction(
            existingId = transactionId,
            amountYuan = amount,
            type = type,
            categoryId = selectedCategoryId,
            accountId = accountId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            linkedTransactionId = linkedExpenseId,
            maxReimbursableCents = selectedExpenseForSave?.remainingCents,
            note = note,
            occurredAt = occurredAt,
            latitude = lat,
            longitude = lng,
            locationAddress = address,
            onDone = {
                saving = false
                onBack()
            }
        )
    }

    fun saveWithLocation() {
        if (saving || !canSave) return
        saving = true
        if (isEdit) {
            persist(null, null)
            return
        }
        scope.launch {
            val point = runCatching { LocationHelper.currentOrLast(context) }.getOrNull()
            persist(point?.latitude, point?.longitude, point?.address.orEmpty())
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        saveWithLocation()
    }

    fun onSaveClick() {
        if (saving || !canSave) return
        if (isEdit) {
            saveWithLocation()
            return
        }
        if (LocationHelper.hasPermission(context)) {
            saveWithLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (showDatePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
        val state = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            val picked = Calendar.getInstance().apply { timeInMillis = millis }
                            cal.set(Calendar.YEAR, picked.get(Calendar.YEAR))
                            cal.set(Calendar.MONTH, picked.get(Calendar.MONTH))
                            cal.set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
                            occurredAt = cal.timeInMillis
                        }
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        cal.set(Calendar.HOUR_OF_DAY, state.hour)
                        cal.set(Calendar.MINUTE, state.minute)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        occurredAt = cal.timeInMillis
                        showTimePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = state) }
        )
    }

    if (showCategorySheet && (type == TxnType.EXPENSE || type == TxnType.INCOME)) {
        CategoryPickerSheet(
            groups = categoryGroups,
            selectedId = selectedCategoryId,
            onSelect = { catId ->
                selectedCategoryId = catId
                if (accountId == null) {
                    val defId = leafCategories.firstOrNull { it.id == catId }?.defaultAccountId
                    if (defId != null && accounts.any { it.id == defId }) {
                        accountId = defId
                    }
                }
                showCategorySheet = false
            },
            onEdit = {
                showCategorySheet = false
                onEditCategories(type)
            },
            onDismiss = { showCategorySheet = false }
        )
    }

    if (showExpenseSheet && type == TxnType.REIMBURSEMENT) {
        ExpensePickerSheet(
            expenses = reimbursableForEdit,
            selectedId = linkedExpenseId,
            onSelect = { item ->
                linkedExpenseId = item.details.transaction.id
                amount = String.format(Locale.CHINA, "%.2f", item.remainingCents / 100.0)
                showExpenseSheet = false
            },
            onDismiss = { showExpenseSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑账目" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            TypeToggle(
                type = type,
                enabled = !isEdit,
                onChange = { type = it }
            )
            Spacer(Modifier.height(20.dp))

            when (type) {
                TxnType.REIMBURSEMENT -> {
                    Text("原支出", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    val expenseLabel = selectedExpenseForSave?.let { item ->
                        val leaf = item.details.category?.name ?: "支出"
                        val parent = item.details.parentCategory?.name
                        val name = if (parent != null) "$parent · $leaf" else leaf
                        "$name  ${formatYuanPlain(item.details.transaction.amountCents)}"
                    }
                    PickerField(
                        text = expenseLabel,
                        placeholder = "点击选择支出记录",
                        onClick = { showExpenseSheet = true }
                    )
                    if (reimbursableForEdit.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "没有可报销的支出记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (selectedExpenseForSave != null &&
                        selectedExpenseForSave.reimbursedCents > 0
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "剩余可报 ${formatYuanPlain(selectedExpenseForSave.remainingCents)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    AmountField(amount = amount, onAmountChange = { amount = it })

                    if (selectedExpenseForSave != null &&
                        amountCents != null &&
                        amountCents > selectedExpenseForSave.remainingCents
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "报销金额不能超过剩余可报金额。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("到账账户", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AccountDropdown(
                        accounts = accounts,
                        selectedId = accountId,
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it },
                        onSelect = { accountId = it },
                        placeholder = "请选择到账账户"
                    )
                }

                TxnType.TRANSFER -> {
                    AmountField(amount = amount, onAmountChange = { amount = it })
                    Spacer(Modifier.height(20.dp))
                    Text("转出账户", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AccountDropdown(
                        accounts = accounts,
                        selectedId = fromAccountId,
                        expanded = fromExpanded,
                        onExpandedChange = { fromExpanded = it },
                        onSelect = { fromAccountId = it }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("转入账户", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AccountDropdown(
                        accounts = accounts,
                        selectedId = toAccountId,
                        expanded = toExpanded,
                        onExpandedChange = { toExpanded = it },
                        onSelect = { toAccountId = it }
                    )
                    if (accounts.size < 2 && !isEdit) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "转账至少需要两个账户，请先去账户页新建。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (fromAccountId != null && fromAccountId == toAccountId) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "转出与转入账户不能相同。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                TxnType.EXPENSE, TxnType.INCOME -> {
                    AmountField(amount = amount, onAmountChange = { amount = it })
                    Spacer(Modifier.height(20.dp))
                    Text("分类", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    val display = when {
                        selectedCategory != null && selectedParent != null ->
                            "${selectedParent.name} · ${selectedCategory.name}"
                        selectedCategory != null -> selectedCategory.name
                        else -> null
                    }
                    PickerField(
                        text = display,
                        placeholder = "点击选择分类",
                        onClick = { showCategorySheet = true }
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("账户", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    AccountDropdown(
                        accounts = accounts,
                        selectedId = accountId,
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it },
                        onSelect = { accountId = it },
                        placeholder = "请选择账户"
                    )
                    if (accounts.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "还没有可用账户，请先去账户页新建。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                TxnType.BALANCE_ADJUSTMENT -> Unit
            }

            Spacer(Modifier.height(20.dp))
            Text("发生时间", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            PickerField(
                text = occurredLabel,
                placeholder = "选择时间",
                onClick = { showDatePicker = true }
            )

            Spacer(Modifier.height(20.dp))
            Text("备注", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("可选") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onSaveClick() },
                enabled = canSave && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
            ) {
                Text(if (isEdit) "保存修改" else "保存", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AmountField(amount: String, onAmountChange: (String) -> Unit) {
    Text("金额", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = amount,
        onValueChange = { input ->
            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                onAmountChange(input)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("0.00") },
        prefix = { Text("¥ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun PickerField(
    text: String?,
    placeholder: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = if (text == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensePickerSheet(
    expenses: List<ReimbursableExpense>,
    selectedId: Long?,
    onSelect: (ReimbursableExpense) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val timeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "选择支出记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            if (expenses.isEmpty()) {
                Text(
                    "暂无可报销支出",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(expenses, key = { it.details.transaction.id }) { item ->
                        val txn = item.details.transaction
                        val leaf = item.details.category?.name ?: "支出"
                        val parent = item.details.parentCategory?.name
                        val title = if (parent != null) "$parent · $leaf" else leaf
                        val selected = txn.id == selectedId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) Color(0xFFD7EBE8)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    scope.launch { sheetState.hide() }
                                        .invokeOnCompletion { onSelect(item) }
                                }
                                .padding(14.dp)
                        ) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = buildString {
                                    append(formatYuanPlain(txn.amountCents))
                                    if (item.reimbursedCents > 0) {
                                        append(" · 剩余 ")
                                        append(formatYuanPlain(item.remainingCents))
                                    }
                                    append(" · ")
                                    append(timeFmt.format(Date(txn.occurredAt)))
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerSheet(
    groups: List<CategoryGroup>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择分类",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        scope.launch { sheetState.hide() }
                            .invokeOnCompletion { onEdit() }
                    }
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑分类",
                        tint = InkTeal
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(group.parent.colorArgb))
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = group.parent.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.children.forEach { child ->
                                val selected = child.id == selectedId
                                Text(
                                    text = child.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (selected) Color(group.parent.colorArgb)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) Color(group.parent.colorArgb)
                                            else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(999.dp)
                                        )
                                        .clickable {
                                            scope.launch { sheetState.hide() }
                                                .invokeOnCompletion { onSelect(child.id) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<Account>,
    selectedId: Long?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Long) -> Unit,
    placeholder: String = "请选择账户"
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selected?.name ?: placeholder,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            leadingIcon = {
                if (selected != null) {
                    AccountIconBadge(iconKey = selected.iconKey, size = 28.dp)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountIconBadge(iconKey = account.iconKey, size = 28.dp)
                            Spacer(Modifier.size(10.dp))
                            Column {
                                Text(account.name)
                                Text(
                                    text = account.kind.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(account.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun TypeToggle(
    type: TxnType,
    enabled: Boolean = true,
    onChange: (TxnType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        TypeChip(
            label = "支出",
            selected = type == TxnType.EXPENSE,
            selectedColor = AmberOut,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onChange(TxnType.EXPENSE) }
        )
        TypeChip(
            label = "收入",
            selected = type == TxnType.INCOME,
            selectedColor = SeaIn,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onChange(TxnType.INCOME) }
        )
        TypeChip(
            label = "转账",
            selected = type == TxnType.TRANSFER,
            selectedColor = TransferBlue,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onChange(TxnType.TRANSFER) }
        )
        TypeChip(
            label = "报销",
            selected = type == TxnType.REIMBURSEMENT,
            selectedColor = ReimburseAmber,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            onClick = { onChange(TxnType.REIMBURSEMENT) }
        )
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
