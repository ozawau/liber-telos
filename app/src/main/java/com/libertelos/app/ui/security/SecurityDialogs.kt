package com.libertelos.app.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PasswordSetupDialog(
    onDismiss: () -> Unit,
    onConfirmed: (String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = {
                        first = it
                        error = null
                    },
                    label = { Text("输入密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = second,
                    onValueChange = {
                        second = it
                        error = null
                    },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        first.isBlank() -> error = "密码不能为空"
                        first != second -> error = "两次输入不一致"
                        else -> onConfirmed(first)
                    }
                }
            ) { Text("开启") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun PasswordVerifyDialog(
    title: String = "验证密码",
    onDismiss: () -> Unit,
    onVerified: (String) -> Boolean
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onVerified(value)) {
                        error = "密码错误"
                    }
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun PatternSetupDialog(
    onDismiss: () -> Unit,
    onConfirmed: (List<Int>) -> Unit
) {
    var first by remember { mutableStateOf<List<Int>?>(null) }
    var message by remember { mutableStateOf("请绘制解锁手势") }
    var error by remember { mutableStateOf<String?>(null) }
    var redrawKey by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置手势") },
        text = {
            Column {
                Text(message)
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                PatternLock(
                    key = redrawKey,
                    onPatternComplete = { dots ->
                        if (!isValidPattern(dots)) {
                            error = "至少连接 4 个点"
                            return@PatternLock
                        }
                        val currentFirst = first
                        if (currentFirst == null) {
                            first = dots
                            message = "请再绘制一次确认"
                            error = null
                            redrawKey++
                        } else if (currentFirst == dots) {
                            onConfirmed(dots)
                        } else {
                            first = null
                            message = "请绘制解锁手势"
                            error = "两次手势不一致，请重试"
                            redrawKey++
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun PatternVerifyDialog(
    title: String = "验证手势",
    onDismiss: () -> Unit,
    onVerified: (List<Int>) -> Boolean
) {
    var error by remember { mutableStateOf<String?>(null) }
    var redrawKey by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("请绘制已设置的手势")
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                PatternLock(
                    key = redrawKey,
                    onPatternComplete = { dots ->
                        if (!onVerified(dots)) {
                            error = "手势错误"
                            redrawKey++
                        }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PatternLock(
    key: Int,
    onPatternComplete: (List<Int>) -> Unit
) {
    // key 变化时重建内部状态
    androidx.compose.runtime.key(key) {
        PatternLock(onPatternComplete = onPatternComplete)
    }
}
