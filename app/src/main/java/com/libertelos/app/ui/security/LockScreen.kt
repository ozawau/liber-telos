package com.libertelos.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.libertelos.app.security.BiometricHelper
import com.libertelos.app.security.SecurityPrefs
import com.libertelos.app.security.UnlockMethod
import com.libertelos.app.ui.theme.InkTeal
import java.util.concurrent.atomic.AtomicBoolean

private enum class UnlockMode {
    Password,
    Pattern
}

@Composable
fun LockScreen(
    prefs: SecurityPrefs,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val last = prefs.lastUnlockMethod
    val unlockedOnce = remember { AtomicBoolean(false) }

    val modes = remember(prefs.passwordEnabled, prefs.patternEnabled) {
        buildList {
            if (prefs.passwordEnabled) add(UnlockMode.Password)
            if (prefs.patternEnabled) add(UnlockMode.Pattern)
        }
    }
    var mode by remember(modes, last) {
        mutableStateOf(
            when {
                last == UnlockMethod.PASSWORD && prefs.passwordEnabled -> UnlockMode.Password
                last == UnlockMethod.PATTERN && prefs.patternEnabled -> UnlockMode.Pattern
                else -> modes.firstOrNull() ?: UnlockMode.Password
            }
        )
    }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var patternKey by remember { mutableIntStateOf(0) }

    fun succeed(method: UnlockMethod) {
        if (!unlockedOnce.compareAndSet(false, true)) return
        prefs.setLastUnlockMethod(method)
        onUnlocked()
    }

    fun tryFingerprint() {
        BiometricHelper.authenticateFingerprint(
            activity = activity,
            title = "解锁 Liber Telos",
            subtitle = "请验证指纹",
            onSuccess = { succeed(UnlockMethod.FINGERPRINT) },
            onError = { error = it }
        )
    }

    // 只要开启面容：任意解锁界面下静默识别，成功直接进入
    if (prefs.faceEnabled) {
        SilentFaceScanner(
            enabled = true,
            onSuccess = { succeed(UnlockMethod.FACE) }
        )
    }

    LaunchedEffect(Unit) {
        when {
            last == UnlockMethod.FINGERPRINT && prefs.fingerprintEnabled -> tryFingerprint()
            last == null && prefs.fingerprintEnabled &&
                !prefs.passwordEnabled && !prefs.patternEnabled && !prefs.faceEnabled -> {
                tryFingerprint()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Liber Telos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请解锁后继续",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        if (modes.size > 1) {
            RowToggle(
                modes = modes,
                selected = mode,
                onSelect = {
                    mode = it
                    error = null
                    password = ""
                    patternKey++
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        when {
            modes.isEmpty() && prefs.fingerprintEnabled -> {
                Text(if (prefs.faceEnabled) "可使用指纹，或面向设备自动解锁" else "请使用指纹解锁")
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { tryFingerprint() },
                    colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                ) { Text("指纹解锁") }
            }
            modes.isEmpty() && prefs.faceEnabled && !prefs.fingerprintEnabled -> {
                Text("请面向设备")
            }
            mode == UnlockMode.Password && prefs.passwordEnabled -> {
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
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (prefs.verifyPassword(password)) {
                            succeed(UnlockMethod.PASSWORD)
                        } else {
                            error = "密码错误"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                ) { Text("解锁") }
            }
            mode == UnlockMode.Pattern && prefs.patternEnabled -> {
                Text("绘制解锁手势")
                Spacer(Modifier.height(8.dp))
                androidx.compose.runtime.key(patternKey) {
                    PatternLock(
                        onPatternComplete = { dots ->
                            if (prefs.verifyPattern(dots)) {
                                succeed(UnlockMethod.PATTERN)
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
            Spacer(Modifier.height(10.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        if (modes.isNotEmpty() && prefs.fingerprintEnabled) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { tryFingerprint() }) { Text("指纹") }
        }
    }
}

@Composable
private fun RowToggle(
    modes: List<UnlockMode>,
    selected: UnlockMode,
    onSelect: (UnlockMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.forEach { m ->
            val label = when (m) {
                UnlockMode.Password -> "密码"
                UnlockMode.Pattern -> "手势"
            }
            if (m == selected) {
                Button(
                    onClick = { onSelect(m) },
                    colors = ButtonDefaults.buttonColors(containerColor = InkTeal)
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(m) }) { Text(label) }
            }
        }
    }
}
