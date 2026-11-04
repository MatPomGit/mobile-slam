package dev.mobile.slam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.sensors.CameraAnalyzer
import dev.mobile.slam.sensors.FrameKeypoints
import dev.mobile.slam.sensors.ImuSample
import dev.mobile.slam.sensors.ImuSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Hosts the student-facing camera and sensor demonstration for the first SLAM milestone.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SlamScreen()
                }
            }
        }
    }
}

@Composable
private fun SlamScreen() {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (cameraGranted) {
        LiveSlamScreen()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Dostęp do kamery jest potrzebny do analizy obrazu.")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Przyznaj dostęp")
            }
        }
    }
}

@Composable
private fun LiveSlamScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val intrinsics = remember { CameraIntrinsics.default640x480() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var keypointFrame by remember { mutableStateOf<FrameKeypoints?>(null) }
    var imuSample by remember { mutableStateOf<ImuSample?>(null) }
    var imuAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val imuSource = ImuSource(context) { sample -> imuSample = sample }
        imuAvailable = imuSource.start()
        onDispose { imuSource.stop() }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val analyzerExecutor = Executors.newSingleThreadExecutor()
            val disposed = AtomicBoolean(false)
            val analyzer = CameraAnalyzer(
                settings = dev.mobile.slam.frontend.Settings(),
                callbackExecutor = ContextCompat.getMainExecutor(context),
            ) { frame ->
                keypointFrame = frame
                cameraError = frame.error
            }
            var cameraProvider: ProcessCameraProvider? = null
            val listener = Runnable {
                if (!disposed.get()) {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val preview = Preview.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .build()
                            .also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analyzerExecutor, analyzer) }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (_: Exception) {
                        cameraError = "Nie udało się uruchomić kamery."
                    }
                }
            }
            cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                disposed.set(true)
                cameraProvider?.unbindAll()
                analyzerExecutor.shutdown()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101820)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "Monokularny SLAM wizualny",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Klatka w skali szarości → detekcja cech FAST",
                color = Color(0xFFB8C7D1),
                fontSize = 13.sp,
            )
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black),
        ) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView = it }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView = it },
            )
            keypointFrame?.let { frame ->
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawFeaturePoints(frame, IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
            if (keypointFrame != null) {
                Text(
                    text = "${keypointFrame?.points?.size ?: 0} cech",
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF17232D)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = cameraError ?: "Stan: podgląd i analiza obrazu",
                color = if (cameraError == null) Color(0xFF8FE3A1) else Color(0xFFFF9A8B),
                fontSize = 13.sp,
            )
            Text(
                text = "Skala dowolna (monokular) · punkty FAST nie są jeszcze śledzone",
                color = Color(0xFFFFD166),
                fontSize = 12.sp,
            )
            Text(
                text = imuSample?.let { sample ->
                    val acceleration = if (sample.hasAccelerometer) {
                        sample.acceleration.asVector()
                    } else {
                        "n/d"
                    }
                    val angularVelocity = if (sample.hasGyroscope) {
                        sample.angularVelocity.asVector()
                    } else {
                        "n/d"
                    }
                    "IMU: a=$acceleration m/s² · ω=$angularVelocity rad/s · t=${sample.timestampNs} ns"
                } ?: "IMU: ${if (imuAvailable) "czujnik dostępny" else "brak czujników"}",
                color = Color.White,
                fontSize = 12.sp,
            )
            Text(
                text = "Obraz ${keypointFrame?.let { "${it.width}×${it.height}" } ?: "640×480"} · " +
                    "t=${keypointFrame?.timestampNs ?: 0L} ns · " +
                    "fx=${intrinsics.fx.toInt()} fy=${intrinsics.fy.toInt()} " +
                    "cx=${intrinsics.cx.toInt()} cy=${intrinsics.cy.toInt()} (wartości przykładowe)",
                color = Color(0xFFB8C7D1),
                fontSize = 11.sp,
            )
        }
    }
}

private fun List<Float>.asVector(): String =
    joinToString(prefix = "(", postfix = ")") { "%.2f".format(java.util.Locale.US, it) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFeaturePoints(
    frame: FrameKeypoints,
    canvasSize: IntSize,
) {
    if (frame.width <= 0 || frame.height <= 0) return

    val rotation = frame.rotationDegrees.mod(360)
    val rotatedWidth = if (rotation == 90 || rotation == 270) frame.height else frame.width
    val rotatedHeight = if (rotation == 90 || rotation == 270) frame.width else frame.height
    val scale = max(canvasSize.width.toFloat() / rotatedWidth, canvasSize.height.toFloat() / rotatedHeight)
    val offsetX = (canvasSize.width - rotatedWidth * scale) / 2f
    val offsetY = (canvasSize.height - rotatedHeight * scale) / 2f

    frame.points.forEach { point ->
        val rotated = when (rotation) {
            90 -> point.y.let { frame.height - it } to point.x
            180 -> (frame.width - point.x) to (frame.height - point.y)
            270 -> point.y to (frame.width - point.x)
            else -> point.x to point.y
        }
        drawCircle(
            color = Color(0xFF54F28B),
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                x = offsetX + rotated.first * scale,
                y = offsetY + rotated.second * scale,
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
