package dev.mobile.slam

import android.content.pm.PackageManager
import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.frontend.Settings
import dev.mobile.slam.sensors.CameraAnalyzer
import dev.mobile.slam.sensors.FrameKeypoints
import dev.mobile.slam.sensors.ImuSample
import dev.mobile.slam.sensors.ImuSource
import dev.mobile.slam.ui.AboutScreen
import dev.mobile.slam.ui.AppDestination
import dev.mobile.slam.ui.AppNavigation
import dev.mobile.slam.ui.MainMenuScreen
import dev.mobile.slam.ui.SettingsOverviewScreen
import dev.mobile.slam.viz.PreviewCoordinates
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

/**
 * Hosts the student-facing menu and opens camera resources only inside the live destination.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(onExit = { finish() })
                }
            }
        }
    }
}

@Composable
private fun AppRoot(onExit: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.MENU) }
    val onBack = { destination = AppDestination.MENU }

    BackHandler(enabled = destination != AppDestination.MENU, onBack = onBack)

    when (destination) {
        AppDestination.MENU -> {
            MainMenuScreen(
                version = BuildConfig.VERSION_NAME,
                onNavigate = { requested -> destination = AppNavigation.navigate(destination, requested) },
                onExit = onExit,
            )
        }
        AppDestination.LIVE -> {
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Powrót do menu")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SlamScreen()
                }
            }
        }
        AppDestination.SETTINGS -> SettingsOverviewScreen(onBack = onBack)
        AppDestination.ABOUT -> AboutScreen(version = BuildConfig.VERSION_NAME, onBack = onBack)
        else -> {
            // Defensive fallback for a future destination restored from saved state.
            MainMenuScreen(
                version = BuildConfig.VERSION_NAME,
                onNavigate = { requested -> destination = AppNavigation.navigate(destination, requested) },
                onExit = onExit,
            )
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
    val settings = remember { Settings() }
    val intrinsics = remember { CameraIntrinsics.default640x480() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var keypointFrame by remember { mutableStateOf<FrameKeypoints?>(null) }
    var imuSample by remember { mutableStateOf<ImuSample?>(null) }
    var imuAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val imuSource = ImuSource(context, settings.imuHudIntervalMs * 1_000_000L) { sample ->
            imuSample = sample
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> imuAvailable = imuSource.start()
                Lifecycle.Event.ON_STOP -> {
                    imuSource.stop()
                    imuAvailable = false
                    imuSample = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            imuAvailable = imuSource.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            imuSource.stop()
        }
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
                settings = settings,
                callbackExecutor = ContextCompat.getMainExecutor(context),
            ) { frame ->
                if (!disposed.get()) {
                    keypointFrame = frame
                    cameraError = frame.error
                }
            }
            var cameraProvider: ProcessCameraProvider? = null
            var boundPreview: Preview? = null
            var boundAnalysis: ImageAnalysis? = null
            var boundWidth = 0
            var boundHeight = 0

            fun bindCamera() {
                val provider = cameraProvider ?: return
                if (disposed.get() || view.width <= 0 || view.height <= 0) return
                if (view.width == boundWidth && view.height == boundHeight) return
                val viewPort = view.viewPort ?: return
                try {
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
                    val useCases = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(analysis)
                        .setViewPort(viewPort)
                        .build()
                    val previousPreview = boundPreview
                    val previousAnalysis = boundAnalysis
                    if (previousPreview != null && previousAnalysis != null) {
                        provider.unbind(previousPreview, previousAnalysis)
                    }
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        useCases,
                    )
                    boundPreview = preview
                    boundAnalysis = analysis
                    boundWidth = view.width
                    boundHeight = view.height
                    keypointFrame = null
                    cameraError = null
                } catch (_: Exception) {
                    cameraError = "Nie udało się uruchomić kamery."
                }
            }

            val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    bindCamera()
                }
            }
            view.addOnLayoutChangeListener(layoutListener)
            cameraProviderFuture.addListener(
                {
                    if (!disposed.get()) {
                        try {
                            cameraProvider = cameraProviderFuture.get()
                            bindCamera()
                        } catch (_: Exception) {
                            cameraError = "Nie udało się uzyskać dostępu do kamery."
                        }
                    }
                },
                ContextCompat.getMainExecutor(context),
            )

            onDispose {
                disposed.set(true)
                view.removeOnLayoutChangeListener(layoutListener)
                val preview = boundPreview
                val analysis = boundAnalysis
                if (preview != null && analysis != null) {
                    cameraProvider?.unbind(preview, analysis)
                }
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
                    PreviewView(viewContext).also {
                        // TextureView is composited reliably below the Compose Canvas overlay.
                        it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        it.scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = it
                    }
                },
                modifier = Modifier.fillMaxSize(),
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
                    "IMU: a=$acceleration m/s² · ω=$angularVelocity rad/s · " +
                        "tA=${sample.accelerationTimestampNs} ns · tG=${sample.angularVelocityTimestampNs} ns"
                } ?: "IMU: ${if (imuAvailable) "czujnik dostępny" else "brak aktywnych czujników"}",
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

private fun List<Float>.asVector(): String = joinToString(prefix = "(", postfix = ")") { "%.2f".format(java.util.Locale.US, it) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFeaturePoints(frame: FrameKeypoints, canvasSize: IntSize) {
    frame.points.forEach { point ->
        val mapped = PreviewCoordinates.project(
            point = point,
            crop = frame.crop,
            rotationDegrees = frame.rotationDegrees,
            previewWidth = canvasSize.width,
            previewHeight = canvasSize.height,
        ) ?: return@forEach
        drawCircle(
            color = Color(0xFF54F28B),
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(mapped.x, mapped.y),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
