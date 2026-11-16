package com.libertelos.app.ui.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.libertelos.app.security.FaceGeometry
import com.libertelos.app.security.FaceRead
import com.libertelos.app.security.FaceSample
import com.libertelos.app.security.FaceTemplate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 面容采集。[template] 为空时录入本人；非空时只有几何特征对上才算通过。
 * 传入 [previewView] 时同时回显前置画面（录入用）。
 */
@Composable
fun SilentFaceScanner(
    enabled: Boolean,
    template: FaceTemplate?,
    previewView: PreviewView? = null,
    onVerified: () -> Unit = {},
    onEnrolled: (FaceTemplate) -> Unit = {},
    onStatus: (String) -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onAligned: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestVerified by rememberUpdatedState(onVerified)
    val latestEnrolled by rememberUpdatedState(onEnrolled)
    val latestStatus by rememberUpdatedState(onStatus)
    val latestProgress by rememberUpdatedState(onProgress)
    val latestAligned by rememberUpdatedState(onAligned)
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

    DisposableEffect(enabled, hasPermission, lifecycleOwner, template, previewView) {
        if (!enabled || !hasPermission) {
            onDispose { }
        } else {
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val finished = AtomicBoolean(false)
            val samples = mutableListOf<FaceSample>()
            var matchStreak = 0
            var misses = 0
            val enrolling = template == null
            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.12f)
                    .build()
            )
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            var cameraProvider: ProcessCameraProvider? = null

            latestStatus(if (enrolling) "请将脸放入人像框" else "请面向设备")
            cameraProviderFuture.addListener({
                if (finished.get()) return@addListener
                val provider = runCatching { cameraProviderFuture.get() }.getOrElse {
                    latestError(it.message ?: "无法打开摄像头")
                    return@addListener
                }
                cameraProvider = provider
                val preview = previewView?.let { view ->
                    Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                }
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
                        .addOnSuccessListener(mainExecutor) { faces ->
                            if (finished.get()) return@addOnSuccessListener
                            val face = faces.maxByOrNull {
                                val box = it.boundingBox
                                box.width() * box.height()
                            }
                            if (face == null) {
                                misses += 1
                                if (misses >= 4) {
                                    samples.clear()
                                    matchStreak = 0
                                    if (enrolling) latestProgress(0)
                                }
                                latestAligned(false)
                                latestStatus(if (enrolling) "请将脸放入人像框" else "请面向设备")
                                return@addOnSuccessListener
                            }
                            misses = 0
                            when (val read = FaceGeometry.read(face)) {
                                is FaceRead.Reject -> {
                                    latestAligned(false)
                                    latestStatus(read.message)
                                }
                                is FaceRead.Ok -> if (enrolling) {
                                    latestAligned(true)
                                    acceptEnrollmentSample(
                                        sample = read.sample,
                                        samples = samples,
                                        finished = finished,
                                        onStatus = latestStatus,
                                        onProgress = latestProgress,
                                        onEnrolled = latestEnrolled
                                    )
                                } else if (template.matches(read.sample)) {
                                    matchStreak += 1
                                    latestStatus("正在确认是本人…")
                                    if (matchStreak >= 4 &&
                                        finished.compareAndSet(false, true)
                                    ) {
                                        latestVerified()
                                    }
                                } else {
                                    matchStreak = 0
                                    latestStatus("面容不匹配")
                                }
                            }
                        }
                        .addOnFailureListener(mainExecutor) {
                            matchStreak = 0
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    if (preview != null) {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            preview,
                            analysis
                        )
                    } else {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            analysis
                        )
                    }
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
    onEnrolled: (FaceTemplate) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        FaceEnrollPane(
            title = title,
            onCancel = onDismiss,
            onEnrolled = onEnrolled
        )
    }
}

@Composable
fun FaceEnrollPane(
    title: String,
    onEnrolled: (FaceTemplate) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var status by remember { mutableStateOf("请将脸放入人像框") }
    var progress by remember { mutableIntStateOf(0) }
    var aligned by remember { mutableStateOf(false) }
    val completed = remember { AtomicBoolean(false) }

    SilentFaceScanner(
        enabled = true,
        template = null,
        previewView = previewView,
        onEnrolled = { template ->
            if (completed.compareAndSet(false, true)) onEnrolled(template)
        },
        onStatus = { status = it },
        onProgress = { progress = it },
        onAligned = { aligned = it },
        onError = { status = it }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        PersonGuideOverlay(aligned = aligned, progress = progress)
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 20.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = status,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("取消", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PersonGuideOverlay(
    aligned: Boolean,
    progress: Int
) {
    val guide = if (aligned) Color(0xFF3DDC97) else Color.White
    Canvas(Modifier.fillMaxSize()) {
        val headW = size.width * 0.66f
        val headH = headW * 1.28f
        val left = (size.width - headW) / 2f
        val top = size.height * 0.16f
        val head = Rect(left, top, left + headW, top + headH)
        val scrim = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(head)
            fillType = PathFillType.EvenOdd
        }
        drawPath(scrim, Color.Black.copy(alpha = 0.46f))
        drawOval(
            color = guide,
            topLeft = Offset(head.left, head.top),
            size = Size(head.width, head.height),
            style = Stroke(width = 4.dp.toPx())
        )
        val shoulders = Path().apply {
            moveTo(size.width * 0.06f, size.height * 0.80f)
            quadraticTo(
                size.width / 2f,
                head.bottom + 6.dp.toPx(),
                size.width * 0.94f,
                size.height * 0.80f
            )
        }
        drawPath(
            shoulders,
            color = guide,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
        if (progress > 0) {
            val inset = 12.dp.toPx()
            drawArc(
                color = Color(0xFF3DDC97),
                startAngle = -90f,
                sweepAngle = 360f * progress / 8f,
                useCenter = false,
                topLeft = Offset(head.left - inset, head.top - inset),
                size = Size(head.width + inset * 2, head.height + inset * 2),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun acceptEnrollmentSample(
    sample: FaceSample,
    samples: MutableList<FaceSample>,
    finished: AtomicBoolean,
    onStatus: (String) -> Unit,
    onProgress: (Int) -> Unit,
    onEnrolled: (FaceTemplate) -> Unit
) {
    if (samples.isNotEmpty()) {
        val ratioMean = FaceGeometry.mean(samples.map { it.ratios })
        val contourMean = FaceGeometry.mean(samples.map { it.contours })
        val drifted = FaceGeometry.l2(sample.ratios, ratioMean) > 0.28f ||
            FaceGeometry.l2(sample.contours, contourMean) > 0.85f
        if (drifted) {
            samples.clear()
            samples.add(sample)
            onProgress(samples.size)
            onStatus("请保持不动")
            return
        }
    }
    samples.add(sample)
    onProgress(samples.size)
    onStatus("正在录入 ${samples.size}/8")
    if (samples.size < 8) return
    val template = FaceTemplate.fromSamples(samples)
    if (template == null) {
        samples.clear()
        onProgress(0)
        onStatus("请保持不动")
        return
    }
    if (finished.compareAndSet(false, true)) onEnrolled(template)
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
