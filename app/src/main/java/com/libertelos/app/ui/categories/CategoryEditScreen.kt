package com.libertelos.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libertelos.app.data.Account
import com.libertelos.app.data.Category
import com.libertelos.app.data.DefaultCategories
import com.libertelos.app.data.TxnType
import com.libertelos.app.ui.components.AccountIconBadge
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.viewmodel.LedgerViewModel

private sealed class CategoryDialog {
    data object AddParent : CategoryDialog()
    data class AddChild(val parent: Category) : CategoryDialog()
    data class EditChild(val category: Category) : CategoryDialog()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(
    viewModel: LedgerViewModel,
    type: TxnType,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.visibleAccounts.collectAsStateWithLifecycle()
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val groups = remember(categories, type) {
        DefaultCategories.groupByParent(categories.filter { it.type == type })
    }
    var dialog by remember { mutableStateOf<CategoryDialog?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var inputName by remember { mutableStateOf("") }
    var defaultAccountId by remember { mutableStateOf<Long?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }

    val title = when (type) {
        TxnType.EXPENSE -> "编辑支出分类"
        TxnType.INCOME -> "编辑收入分类"
        TxnType.TRANSFER, TxnType.REIMBURSEMENT -> "编辑分类"
    }

    pendingDelete?.let { target ->
        val tip = if (target.isParent) {
            "删除「${target.name}」及其全部二级分类？相关记账将变为未分类。"
        } else {
            "删除二级分类「${target.name}」？相关记账将变为未分类。"
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类") },
            text = { Text(tip) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(target)
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    dialog?.let { current ->
        val dialogTitle = when (current) {
            CategoryDialog.AddParent -> "新增一级分类"
            is CategoryDialog.AddChild -> "在「${current.parent.name}」下新增二级分类"
            is CategoryDialog.EditChild -> "编辑二级分类"
        }
        val showDefaultAccount = current !is CategoryDialog.AddParent
        AlertDialog(
            onDismissRequest = {
                dialog = null
                inputName = ""
                defaultAccountId = null
                accountExpanded = false
            },
            title = { Text(dialogTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        singleLine = true,
                        placeholder = { Text("分类名称") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showDefaultAccount) {
                        Spacer(Modifier.height(12.dp))
                        Text("默认账户（可选）", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        DefaultAccountPicker(
                            accounts = accounts,
                            selectedId = defaultAccountId,
                            expanded = accountExpanded,
                            onExpandedChange = { accountExpanded = it },
                            onSelect = { defaultAccountId = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (current) {
                            CategoryDialog.AddParent ->
                                viewModel.addParentCategory(type, inputName)
                            is CategoryDialog.AddChild ->
                                viewModel.addChildCategory(
                                    parentId = current.parent.id,
                                    name = inputName,
                                    defaultAccountId = defaultAccountId
                                )
                            is CategoryDialog.EditChild ->
                                viewModel.updateChildCategory(
                                    category = current.category,
                                    name = inputName,
                                    defaultAccountId = defaultAccountId
                                )
                        }
                        dialog = null
                        inputName = ""
                        defaultAccountId = null
                        accountExpanded = false
                    },
                    enabled = inputName.trim().isNotEmpty()
                ) {
                    Text(if (current is CategoryDialog.EditChild) "保存" else "添加")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        inputName = ""
                        defaultAccountId = null
                        accountExpanded = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(groups, key = { it.parent.id }) { group ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(group.parent.colorArgb))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = group.parent.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                inputName = ""
                                defaultAccountId = null
                                dialog = CategoryDialog.AddChild(group.parent)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "新增二级分类",
                                tint = InkTeal
                            )
                        }
                        if (group.children.none { it.isProtected }) {
                            IconButton(
                                onClick = { pendingDelete = group.parent },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除一级分类",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        group.children.forEach { child ->
                            val defaultName = child.defaultAccountId?.let { accountMap[it]?.name }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable {
                                        inputName = child.name
                                        defaultAccountId = child.defaultAccountId
                                        dialog = CategoryDialog.EditChild(child)
                                    }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = child.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (defaultName != null) {
                                        Text(
                                            text = defaultName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!child.isProtected) {
                                    IconButton(
                                        onClick = { pendingDelete = child },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除二级分类",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = {
                        inputName = ""
                        defaultAccountId = null
                        dialog = CategoryDialog.AddParent
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加一级分类")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultAccountPicker(
    accounts: List<Account>,
    selectedId: Long?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Long?) -> Unit
) {
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selected?.name ?: "无默认账户",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            leadingIcon = {
                if (selected != null) {
                    AccountIconBadge(iconKey = selected.iconKey, size = 24.dp)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("无默认账户") },
                onClick = {
                    onSelect(null)
                    onExpandedChange(false)
                }
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccountIconBadge(iconKey = account.iconKey, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(account.name)
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
