package com.libertelos.app.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.libertelos.app.security.BiometricHelper
import com.libertelos.app.security.SecurityPrefs
import com.libertelos.app.ui.theme.InkTeal
import java.util.concurrent.atomic.AtomicBoolean

private enum class ConfirmMode {
    Password,
    Pattern
}

/**
 * 关闭某项安全能力前的身份确认：可用当前已开启的任意方式验证。
 */
@Composable
fun ConfirmIdentityDialog(
    title: String,
    prefs: SecurityPrefs,
    passwordEnabled: Boolean,
    patternEnabled: Boolean,
    fingerprintEnabled: Boolean,
    faceEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val done = remember { AtomicBoolean(false) }

    fun succeed() {
        if (done.compareAndSet(false, true)) onConfirmed()
    }

    val modes = remember(passwordEnabled, patternEnabled) {
        buildList {
            if (passwordEnabled) add(ConfirmMode.Password)
            if (patternEnabled) add(ConfirmMode.Pattern)
        }
    }
    var mode by remember(modes) { mutableStateOf(modes.firstOrNull() ?: ConfirmMode.Password) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var patternKey by remember { mutableIntStateOf(0) }

    if (faceEnabled) {
        SilentFaceScanner(
            enabled = true,
            onSuccess = { succeed() }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "请使用任意已开启的方式验证身份",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (modes.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { m ->
                            val label = if (m == ConfirmMode.Password) "密码" else "手势"
                            if (m == mode) {
                                Button(
                                    onClick = { mode = m },
                                    colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                                ) { Text(label) }
                            } else {
                                OutlinedButton(onClick = {
                                    mode = m
                                    error = null
                                    password = ""
                                    patternKey++
                                }) { Text(label) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                when {
                    modes.isEmpty() && fingerprintEnabled -> {
                        Text(if (faceEnabled) "可使用指纹，或面向设备" else "请使用指纹验证")
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                BiometricHelper.authenticateFingerprint(
                                    activity = activity,
                                    title = title,
                                    subtitle = "请验证指纹",
                                    onSuccess = { succeed() },
                                    onError = { error = it }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                        ) { Text("指纹验证") }
                    }
                    modes.isEmpty() && faceEnabled && !fingerprintEnabled -> {
                        Text("请面向设备")
                    }
                    mode == ConfirmMode.Password && passwordEnabled -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (prefs.verifyPassword(password)) {
                                    succeed()
                                } else {
                                    error = "密码错误"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                        ) { Text("验证") }
                    }
                    mode == ConfirmMode.Pattern && patternEnabled -> {
                        Text("绘制解锁手势")
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.runtime.key(patternKey) {
                            PatternLock(
                                onPatternComplete = { dots ->
                                    if (prefs.verifyPattern(dots)) {
                                        succeed()
                                    } else {
                                        error = "手势错误"
                                        patternKey++
                                    }
                                }
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                if (modes.isNotEmpty() && fingerprintEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            BiometricHelper.authenticateFingerprint(
                                activity = activity,
                                title = title,
                                subtitle = "请验证指纹",
                                onSuccess = { succeed() },
                                onError = { error = it }
                            )
                        }
                    ) { Text("指纹") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
