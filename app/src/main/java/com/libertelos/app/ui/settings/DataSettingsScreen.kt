package com.libertelos.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.libertelos.app.LiberTelosApp
import com.libertelos.app.data.DataExporter
import com.libertelos.app.data.DataImporter
import com.libertelos.app.data.LedgerStorage
import com.libertelos.app.ui.theme.InkTeal
import com.libertelos.app.viewmodel.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    viewModel: LedgerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LiberTelosApp
    val scope = rememberCoroutineScope()
    var location by remember { mutableStateOf(LedgerStorage.preferredLocation(context)) }
    var pendingLocation by remember { mutableStateOf<LedgerStorage.Location?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmImportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmImportIsExcel by remember { mutableStateOf(false) }

    val pickCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            confirmImportIsExcel = false
            confirmImportUri = uri
        }
    }
    val pickExcel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            confirmImportIsExcel = true
            confirmImportUri = uri
        }
    }

    pendingLocation?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingLocation = null },
            title = { Text("切换数据目录") },
            text = {
                Text("将把账本复制到「${target.label}」并重启应用。请确认已保存其他工作。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val to = target
                        pendingLocation = null
                        busy = true
                        scope.launch {
                            runCatching { app.relocateLedgerStorage(to) }
                                .onFailure {
                                    busy = false
                                    toast(context, it.message ?: "切换失败")
                                }
                        }
                    }
                ) { Text("切换并重启") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocation = null }) { Text("取消") }
            }
        )
    }

    confirmImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImportUri = null },
            title = { Text("导入数据") },
            text = {
                Text("导入将覆盖当前全部账户、分类与流水，且不可撤销。确定继续？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmImportUri = null
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val tmp = File(
                                        context.cacheDir,
                                        if (confirmImportIsExcel) "import.xlsx" else "import.zip"
                                    )
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        tmp.outputStream().use { output -> input.copyTo(output) }
                                    } ?: error("无法读取文件")
                                    val bundle = if (confirmImportIsExcel) {
                                        DataImporter.parseExcel(tmp)
                                    } else {
                                        DataImporter.parseCsvZip(tmp)
                                    }
                                    viewModel.importBundle(bundle)
                                    tmp.delete()
                                    bundle
                                }
                            }
                            busy = false
                            result.onSuccess { bundle ->
                                toast(
                                    context,
                                    "已导入：账户 ${bundle.accounts.size}，分类 ${bundle.categories.size}，流水 ${bundle.transactions.size}"
                                )
                            }.onFailure {
                                toast(context, it.message ?: "导入失败")
                            }
                        }
                    }
                ) { Text("覆盖导入", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportUri = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据设置") },
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
            item {
                SettingsCard(title = "数据目录") {
                    Text(
                        text = LedgerStorage.displayPath(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LedgerStorage.Location.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !busy) {
                                    if (option != location) pendingLocation = option
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = location == option,
                                onClick = {
                                    if (option != location) pendingLocation = option
                                },
                                enabled = !busy
                            )
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(option.label, fontWeight = FontWeight.Medium)
                                Text(
                                    text = LedgerStorage.directory(context, option).absolutePath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(title = "导出全部数据") {
                    Text(
                        text = "导出账户、分类与流水。CSV 为 ZIP 压缩包，Excel 为多工作表 xlsx。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val bundle = viewModel.exportSnapshot()
                                            val file = File(
                                                context.cacheDir,
                                                "liber-telos_${DataExporter.stamp()}.zip"
                                            )
                                            DataExporter.writeCsvZip(file, bundle)
                                            file
                                        }
                                    }
                                    busy = false
                                    result.onSuccess { shareFile(context, it, "application/zip") }
                                        .onFailure { toast(context, it.message ?: "导出失败") }
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                        ) { Text("导出 CSV") }
                        OutlinedButton(
                            onClick = {
                                if (busy) return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            val bundle = viewModel.exportSnapshot()
                                            val file = File(
                                                context.cacheDir,
                                                "liber-telos_${DataExporter.stamp()}.xlsx"
                                            )
                                            DataExporter.writeExcel(file, bundle)
                                            file
                                        }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        shareFile(
                                            context,
                                            it,
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        )
                                    }.onFailure { toast(context, it.message ?: "导出失败") }
                                }
                            },
                            enabled = !busy
                        ) { Text("导出 Excel") }
                    }
                }
            }

            item {
                SettingsCard(title = "导入全部数据") {
                    Text(
                        text = "支持本应用导出的 CSV ZIP 或 Excel。导入会覆盖现有数据。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                pickCsv.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                        ) { Text("导入 CSV") }
                        OutlinedButton(
                            onClick = {
                                pickExcel.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !busy
                        ) { Text("导入 Excel") }
                    }
                }
            }

            if (busy) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("处理中…")
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
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
        Spacer(Modifier.height(10.dp))
        content()
    }
}

private fun shareFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享导出文件"))
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
