package dev.mobile.slam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.mobile.slam.diagnostics.CameraObservationRecorder
import dev.mobile.slam.diagnostics.DiagnosticField
import dev.mobile.slam.diagnostics.DiagnosticSection
import dev.mobile.slam.diagnostics.EvidenceSource
import dev.mobile.slam.diagnostics.HardwareInventory
import dev.mobile.slam.diagnostics.HardwareJson
import dev.mobile.slam.diagnostics.HardwareReport
import dev.mobile.slam.diagnostics.ImuMeasurement
import dev.mobile.slam.diagnostics.ImuValidationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full read-only hardware inventory and consented IMU test.
 * Opening this screen never opens the camera or subscribes to sensor events.
 */
@Composable
internal fun HardwareDiagnosticsScreen(
    recorder: CameraObservationRecorder,
    appVersion: String,
    onCameraTest: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sensorTest = remember(context.applicationContext) { ImuValidationSession(context.applicationContext) }
    val inventory = remember(context.applicationContext) { HardwareInventory(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var measured by remember { mutableStateOf<ImuMeasurement?>(null) }
    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<HardwareReport?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, sensorTest) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && sensorTest.isRunning) {
                measured = sensorTest.stop()
                running = false
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (sensorTest.isRunning) sensorTest.stop()
        }
    }

    LaunchedEffect(refresh, measured) {
        report = withContext(Dispatchers.IO) {
            inventory.collect(recorder.snapshot(), measured)
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = report
        if (uri != null && snapshot != null) {
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        val payload = HardwareJson.encode(snapshot, appVersion)
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("No writable stream")
                        stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    }
                    "Raport JSON zapisano w wybranej lokalizacji."
                } catch (_: Exception) {
                    "Nie udało się zapisać raportu."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Powrót do ustawień")
        }
        Text("Parametry sprzętowe i diagnostyka", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zgłoszone ≠ żądane ≠ zmierzone. Brak wartości oznacza „n/d” z przyczyną. " +
                "Parametry optyki nie są jeszcze skalibrowane.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Podgląd nie otwiera kamery ani nie uruchamia IMU. Pomiar IMU wymaga przycisku Start. " +
                "Raport nie zawiera danych osobowych ani nagrań.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { refresh++ },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Odśwież dane")
            }
            Button(
                onClick = { report?.let { export.launch("mobile-slam-hardware.json") } },
                enabled = report != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Eksportuj JSON")
            }
        }
        Button(
            onClick = onCameraTest,
            enabled = !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Uruchom test kamery (tryb na żywo)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    measured = null
                    running = sensorTest.start()
                    status = if (running) {
                        "Trwa pomiar IMU. Poruszaj telefonem i zakończ pomiar."
                    } else {
                        measured = sensorTest.stop()
                        "Nie udało się aktywować czujników IMU."
                    }
                },
                enabled = !running,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Uruchom pomiar IMU")
            }
            OutlinedButton(
                onClick = {
                    measured = sensorTest.stop()
                    running = false
                    status = "Pomiar IMU zakończony. Odświeżono statystyki."
                    refresh++
                },
                enabled = running,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Zatrzymaj pomiar")
            }
        }
        if (status != null) Text(status!!, style = MaterialTheme.typography.bodyMedium)
        if (running) Text("Próbkowanie SENSOR_DELAY_GAME, rzeczywistą częstotliwość ustalimy po zatrzymaniu.")

        report?.let { snapshot ->
            Text("Odczyt UTC: ${snapshot.collectedAtUtc}", style = MaterialTheme.typography.labelSmall)
            snapshot.sections.forEach { section ->
                DiagnosticGroup(section)
            }
        } ?: Text("Wczytywanie parametrów urządzenia…")

        Text("Lista kontroli fizycznej (nie przeprowadzono):", style = MaterialTheme.typography.titleMedium)
        Text(
            "Uprawnienia kamery, orientacje 0°/90°/180°/270°, zgodność overlay FAST, " +
                "praca w tle, 5–10 minut stabilności, analiza opóźnień i porównanie zegarów. " +
                "Wyniki wymagają oddzielnego potwierdzenia na telefonie (#4).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DiagnosticGroup(section: DiagnosticSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(section.name, style = MaterialTheme.typography.titleMedium)
        section.fields.forEach { field ->
            DiagnosticValue(field)
        }
    }
}

@Composable
private fun DiagnosticValue(field: DiagnosticField) {
    val provenance = when (field.source) {
        EvidenceSource.REPORTED -> "deklarowane"
        EvidenceSource.REQUESTED -> "żądane"
        EvidenceSource.OBSERVED -> "zmierzone"
        EvidenceSource.UNAVAILABLE -> "brak"
    }
    val value = field.value?.let { v -> "$v${field.unit?.let { " $it" } ?: ""}" }
        ?: "n/d: ${field.reason ?: "brak danych"}"
    Text(
        text = "${field.name}: $value [$provenance]",
        style = MaterialTheme.typography.bodySmall,
    )
}
