package com.libertelos.app.ui.security

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.libertelos.app.security.AppLockController
import com.libertelos.app.security.BiometricHelper
import com.libertelos.app.security.SecurityPrefs
import com.libertelos.app.ui.settings.SettingsCard

private enum class SecurityDialog {
    PasswordSetup,
    PatternSetup,
    FaceSetup,
    DisablePassword,
    DisablePattern,
    DisableFingerprint,
    DisableFace
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val prefs = remember { SecurityPrefs(context) }

    var passwordOn by remember { mutableStateOf(prefs.passwordEnabled) }
    var patternOn by remember { mutableStateOf(prefs.patternEnabled) }
    var fingerprintOn by remember { mutableStateOf(prefs.fingerprintEnabled) }
    var faceOn by remember { mutableStateOf(prefs.faceEnabled) }
    var dialog by remember { mutableStateOf<SecurityDialog?>(null) }

    val fingerprintAvailable = remember { BiometricHelper.fingerprintAvailable(context) }
    val faceAvailable = remember { BiometricHelper.faceAvailable(context) }

    // 硬件不可用时禁止开启；若过去已开启则仍允许关闭
    val fingerprintSwitchEnabled = fingerprintAvailable || fingerprintOn
    val faceSwitchEnabled = faceAvailable || faceOn

    fun showDisable(target: SecurityDialog) {
        dialog = target
    }

    when (dialog) {
        SecurityDialog.PasswordSetup -> PasswordSetupDialog(
            onDismiss = { dialog = null },
            onConfirmed = { pwd ->
                prefs.enablePassword(pwd)
                passwordOn = true
                AppLockController.unlock()
                dialog = null
                toast(context, "密码已开启")
            }
        )
        SecurityDialog.PatternSetup -> PatternSetupDialog(
            onDismiss = { dialog = null },
            onConfirmed = { dots ->
                prefs.enablePattern(dots)
                patternOn = true
                AppLockController.unlock()
                dialog = null
                toast(context, "手势已开启")
            }
        )
        SecurityDialog.FaceSetup -> FaceScanDialog(
            title = "开启面容解锁",
            onDismiss = { dialog = null },
            onSuccess = {
                prefs.setFaceEnabled(true)
                faceOn = true
                AppLockController.unlock()
                dialog = null
                toast(context, "面容已开启")
            }
        )
        SecurityDialog.DisablePassword,
        SecurityDialog.DisablePattern,
        SecurityDialog.DisableFingerprint,
        SecurityDialog.DisableFace -> {
            val title = when (dialog) {
                SecurityDialog.DisablePassword -> "关闭密码"
                SecurityDialog.DisablePattern -> "关闭手势"
                SecurityDialog.DisableFingerprint -> "关闭指纹"
                else -> "关闭面容"
            }
            val verifyPassword = passwordOn
            val verifyPattern = patternOn
            val verifyFingerprint = fingerprintOn && fingerprintAvailable
            val verifyFace = faceOn && faceAvailable
            val canVerify = verifyPassword || verifyPattern || verifyFingerprint || verifyFace

            fun applyDisable() {
                when (dialog) {
                    SecurityDialog.DisablePassword -> {
                        prefs.disablePassword()
                        passwordOn = false
                        toast(context, "密码已关闭")
                    }
                    SecurityDialog.DisablePattern -> {
                        prefs.disablePattern()
                        patternOn = false
                        toast(context, "手势已关闭")
                    }
                    SecurityDialog.DisableFingerprint -> {
                        prefs.setFingerprintEnabled(false)
                        fingerprintOn = false
                        toast(context, "指纹已关闭")
                    }
                    SecurityDialog.DisableFace -> {
                        prefs.setFaceEnabled(false)
                        faceOn = false
                        toast(context, "面容已关闭")
                    }
                    else -> Unit
                }
                dialog = null
            }

            if (canVerify) {
                ConfirmIdentityDialog(
                    title = title,
                    prefs = prefs,
                    passwordEnabled = verifyPassword,
                    patternEnabled = verifyPattern,
                    fingerprintEnabled = verifyFingerprint,
                    faceEnabled = verifyFace,
                    onDismiss = { dialog = null },
                    onConfirmed = { applyDisable() }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(title) },
                    text = { Text("当前没有可用的验证方式，确认直接关闭？") },
                    confirmButton = {
                        TextButton(onClick = { applyDisable() }) { Text("关闭") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("取消") }
                    }
                )
            }
        }
        null -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全设置") },
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
                Text(
                    text = "可同时开启多种方式，进入应用时任选一种即可解锁。不支持的硬件能力将置灰。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                SettingsCard(title = "解锁方式") {
                    SecuritySwitchRow(
                        title = "密码",
                        subtitle = "数字或字母密码",
                        checked = passwordOn,
                        enabled = true,
                        onCheckedChange = { checked ->
                            dialog = if (checked) {
                                SecurityDialog.PasswordSetup
                            } else {
                                SecurityDialog.DisablePassword
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SecuritySwitchRow(
                        title = "手势",
                        subtitle = "九宫格手势，至少 4 个点",
                        checked = patternOn,
                        enabled = true,
                        onCheckedChange = { checked ->
                            dialog = if (checked) {
                                SecurityDialog.PatternSetup
                            } else {
                                SecurityDialog.DisablePattern
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SecuritySwitchRow(
                        title = "指纹",
                        subtitle = when {
                            !fingerprintAvailable && fingerprintOn -> "设备暂不支持，可关闭"
                            fingerprintAvailable -> "开启时将进行一次系统指纹验证"
                            else -> "当前设备不支持指纹"
                        },
                        checked = fingerprintOn,
                        enabled = fingerprintSwitchEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!fingerprintAvailable) return@SecuritySwitchRow
                                BiometricHelper.authenticateFingerprint(
                                    activity = activity,
                                    title = "开启指纹解锁",
                                    subtitle = "请验证指纹以确认开启",
                                    onSuccess = {
                                        prefs.setFingerprintEnabled(true)
                                        fingerprintOn = true
                                        AppLockController.unlock()
                                        toast(context, "指纹已开启")
                                    },
                                    onError = { toast(context, it) }
                                )
                            } else {
                                showDisable(SecurityDialog.DisableFingerprint)
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    SecuritySwitchRow(
                        title = "面容",
                        subtitle = when {
                            !faceAvailable && faceOn -> "设备暂不支持，可关闭"
                            faceAvailable -> "开启时将用前置摄像头识别面容"
                            else -> "当前设备不支持面容（无前置摄像头）"
                        },
                        checked = faceOn,
                        enabled = faceSwitchEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!faceAvailable) return@SecuritySwitchRow
                                dialog = SecurityDialog.FaceSetup
                            } else {
                                showDisable(SecurityDialog.DisableFace)
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SecuritySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val dimmed = !enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
