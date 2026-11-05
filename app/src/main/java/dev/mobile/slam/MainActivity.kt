package dev.mobile.slam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mobile.slam.calib.CameraIntrinsics
import dev.mobile.slam.diagnostics.CameraObservationRecorder
import dev.mobile.slam.frontend.FeatureStatus
import dev.mobile.slam.frontend.Settings
import dev.mobile.slam.frontend.SettingsRepository
import dev.mobile.slam.sensors.CameraAnalyzer
import dev.mobile.slam.sensors.FrameKeypoints
import dev.mobile.slam.sensors.ImuSample
import dev.mobile.slam.sensors.ImuSource
import dev.mobile.slam.ui.AboutScreen
import dev.mobile.slam.ui.AppDestination
import dev.mobile.slam.ui.AppNavigation
import dev.mobile.slam.ui.CalibrationScreen
import dev.mobile.slam.ui.HardwareDiagnosticsScreen
import dev.mobile.slam.ui.MainMenuScreen
import dev.mobile.slam.ui.SettingsScreen
import dev.mobile.slam.viz.PreviewCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { SettingsRepository(context.applicationContext) }
    val diagnosticsRecorder = remember { CameraObservationRecorder() }
    val loaded by repository.snapshots.collectAsState(initial = null)
    var overrideSettings by remember { mutableStateOf<Settings?>(null) }
    val effectiveSettings = overrideSettings ?: loaded?.settings
    var destination by rememberSaveable { mutableStateOf(AppDestination.MENU) }
    var liveFromDiagnostics by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val onBack = {
        destination = when {
            destination == AppDestination.HARDWARE -> AppDestination.SETTINGS
            destination == AppDestination.LIVE && liveFromDiagnostics -> AppDestination.HARDWARE
            else -> AppDestination.MENU
        }
    }
    BackHandler(enabled = destination != AppDestination.MENU, onBack = onBack)

    when (destination) {
        AppDestination.MENU -> {
            MainMenuScreen(
                version = BuildConfig.VERSION_NAME,
                onNavigate = { requested ->
                    if (requested == AppDestination.LIVE) liveFromDiagnostics = false
                    destination = AppNavigation.navigate(destination, requested)
                },
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
                    if (effectiveSettings == null) {
                        Text("Wczytywanie ustawień…")
                    } else {
                        SlamScreen(effectiveSettings, diagnosticsRecorder)
                    }
                }
            }
        }
        AppDestination.SETTINGS -> {
            if (effectiveSettings == null) {
                Text("Wczytywanie ustawień…")
            } else {
                SettingsScreen(
                    settings = effectiveSettings,
                    busy = saving,
                    warning = if (overrideSettings == null) loaded?.warning else null,
                    status = saveStatus,
                    onSave = { next ->
                        if (!saving) {
                            saving = true
                            saveStatus = null
                            scope.launch {
                                try {
                                    repository.save(next)
                                    overrideSettings = next
                                    saveStatus = "Zapisano. Ustawienia zostaną użyte przy otwarciu trybu na żywo."
                                } catch (cause: Exception) {
                                    if (cause is CancellationException) throw cause
                                    saveStatus = "Nie udało się zapisać ustawień. Spróbuj ponownie."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    onReset = {
                        if (!saving) {
                            saving = true
                            saveStatus = null
                            scope.launch {
                                try {
                                    repository.reset()
                                    overrideSettings = Settings()
                                    saveStatus = "Przywrócono wartości domyślne."
                                } catch (cause: Exception) {
                                    if (cause is CancellationException) throw cause
                                    saveStatus = "Nie udało się przywrócić ustawień."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    onHardware = { destination = AppDestination.HARDWARE },
                    onBack = onBack,
                )
            }
        }
        AppDestination.HARDWARE -> HardwareDiagnosticsScreen(
            recorder = diagnosticsRecorder,
            appVersion = BuildConfig.VERSION_NAME,
            onCameraTest = {
                liveFromDiagnostics = true
                destination = AppDestination.LIVE
            },
            onBack = onBack,
        )
        AppDestination.CALIBRATION -> CalibrationScreen(onBack = onBack)
        AppDestination.ABOUT -> AboutScreen(version = BuildConfig.VERSION_NAME, onBack = onBack)
        else -> {
            MainMenuScreen(
                version = BuildConfig.VERSION_NAME,
                onNavigate = { requested ->
                    if (requested == AppDestination.LIVE) liveFromDiagnostics = false
                    destination = AppNavigation.navigate(destination, requested)
                },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun SlamScreen(settings: Settings, diagnostics: CameraObservationRecorder) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Permission can change while the app is in the background (e.g. Android settings).
    // Never reuse a stale grant when returning to the camera route.
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                cameraGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (cameraGranted) {
        LiveSlamScreen(settings, diagnostics)
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

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun LiveSlamScreen(settings: Settings, diagnostics: CameraObservationRecorder) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val intrinsics = remember { CameraIntrinsics.default640x480() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var keypointFrame by remember { mutableStateOf<FrameKeypoints?>(null) }
    var imuSample by remember { mutableStateOf<ImuSample?>(null) }
    var imuAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, settings.imuHudIntervalMs) {
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

    DisposableEffect(lifecycleOwner, previewView, settings.fastThreshold) {
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
                onObservation = diagnostics::observe,
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
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        useCases,
                    )
                    diagnostics.cameraBound(
                        try {
                            Camera2CameraInfo.from(camera.cameraInfo).cameraId
                        } catch (_: Exception) {
                            null
                        },
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
                // Retire pending UI snapshots and the cached native FAST detector
                // on the same serial worker that owns analysis and OpenCV state.
                analyzerExecutor.execute { analyzer.close() }
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
                text = "FAST → KLT · śledzenie punktów 2D",
                color = Color(0xFFB8C7D1),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                    text = keypointFrame?.trackingMetrics?.let { metrics ->
                        "KLT: ${metrics.tracked} śledzonych · ${metrics.detected} nowych · ${metrics.rejected} odrzuconych"
                    } ?: "${keypointFrame?.points?.size ?: 0} cech FAST",
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
                text = "KLT ≠ estymacja pozycji · brak rekonstrukcji 3D i skali metrycznej",
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
    frame.tracks.forEach { track ->
        val mapped = PreviewCoordinates.project(
            point = dev.mobile.slam.sensors.FeaturePoint(track.current.xPx, track.current.yPx),
            crop = frame.crop,
            rotationDegrees = frame.rotationDegrees,
            previewWidth = canvasSize.width,
            previewHeight = canvasSize.height,
        ) ?: return@forEach
        val color = if (track.status == FeatureStatus.TRACKED) Color(0xFF54F28B) else Color(0xFF61C7FF)
        track.previous?.let { previous ->
            val start = PreviewCoordinates.project(
                point = dev.mobile.slam.sensors.FeaturePoint(previous.xPx, previous.yPx),
                crop = frame.crop,
                rotationDegrees = frame.rotationDegrees,
                previewWidth = canvasSize.width,
                previewHeight = canvasSize.height,
            )
            if (start != null) {
                drawLine(
                    color = color.copy(alpha = 0.65f),
                    start = androidx.compose.ui.geometry.Offset(start.x, start.y),
                    end = androidx.compose.ui.geometry.Offset(mapped.x, mapped.y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(mapped.x, mapped.y),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
