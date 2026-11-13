package com.libertelos.app.ui.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 静默面容识别：不显示摄像头画面，识别稳定后回调成功。
 */
@Composable
fun SilentFaceScanner(
    enabled: Boolean,
    neededFrames: Int = 8,
    onSuccess: () -> Unit,
    onStatus: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSuccess by rememberUpdatedState(onSuccess)
    val latestStatus by rememberUpdatedState(onStatus)
    val latestError by rememberUpdatedState(onError)

    var hasPermission by remember {
        mutableStateOf(hasCameraPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) latestError("需要摄像头权限")
    }

    LaunchedEffect(enabled) {
        if (enabled && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(enabled, hasPermission, lifecycleOwner) {
        if (!enabled || !hasPermission) {
            onDispose { }
        } else {
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val hitCount = AtomicInteger(0)
            val finished = AtomicBoolean(false)
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setMinFaceSize(0.15f)
                    .build()
            )
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            var cameraProvider: ProcessCameraProvider? = null

            latestStatus("正在识别面容…")
            cameraProviderFuture.addListener({
                if (finished.get()) return@addListener
                val provider = runCatching { cameraProviderFuture.get() }.getOrElse {
                    latestError(it.message ?: "无法打开摄像头")
                    return@addListener
                }
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    if (finished.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = proxy.image
                    if (mediaImage == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        proxy.imageInfo.rotationDegrees
                    )
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            val ok = faces.any { face ->
                                val box = face.boundingBox
                                box.width() * box.height() > 10_000
                            }
                            val count = if (ok) hitCount.incrementAndGet() else {
                                hitCount.set(0)
                                0
                            }
                            if (count > 0) {
                                mainExecutor.execute {
                                    latestStatus("正在识别面容…")
                                }
                            }
                            if (count >= neededFrames && finished.compareAndSet(false, true)) {
                                mainExecutor.execute { latestSuccess() }
                            }
                        }
                        .addOnFailureListener {
                            hitCount.set(0)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis
                    )
                }.onFailure {
                    latestError(it.message ?: "前置摄像头不可用")
                }
            }, mainExecutor)

            onDispose {
                finished.set(true)
                runCatching { cameraProvider?.unbindAll() }
                analysisExecutor.shutdown()
                detector.close()
            }
        }
    }
}

@Composable
fun FaceScanDialog(
    title: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var status by remember { mutableStateOf("请面向设备") }
    val completed = remember { AtomicBoolean(false) }

    SilentFaceScanner(
        enabled = true,
        onSuccess = {
            if (completed.compareAndSet(false, true)) onSuccess()
        },
        onStatus = { status = it },
        onError = { status = it }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
