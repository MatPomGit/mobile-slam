package dev.mobile.slam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mobile.slam.calib.CalibrationProfileJson
import dev.mobile.slam.calib.ChessboardCalibrator
import dev.mobile.slam.calib.ChessboardSpec
import dev.mobile.slam.calib.MeasuredCalibration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * User-directed photo calibration. Does not open CameraX or activate a calibrated
 * pose path. A chosen photo series must be from one known camera/lens/focus state.
 */
@Composable
internal fun CalibrationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraId by remember { mutableStateOf("0") }
    var columns by remember { mutableStateOf("9") }
    var rows by remember { mutableStateOf("6") }
    var squareMm by remember { mutableStateOf("20") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Wybierz serię zdjęć wykonanych tym samym aparatem.") }
    var profile by remember { mutableStateOf<MeasuredCalibration?>(null) }

    val board = runCatching {
        ChessboardSpec(
            columns = columns.toInt(),
            rows = rows.toInt(),
            squareMm = squareMm.replace(',', '.').toDouble(),
        )
    }.getOrNull()
    val validInputs = cameraId.isNotBlank() && board != null

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val spec = board
        if (!running && spec != null && cameraId.isNotBlank() && uris.isNotEmpty()) {
            if (uris.size !in spec.minViews..40) {
                status = "Wybierz od 8 do 40 zdjęć z wyraźną szachownicą."
            } else {
                val selectedCamera = cameraId.trim()
                running = true
                status = "Trwa wykrywanie narożników i estymacja parametrów…"
                profile = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.Default) {
                            ChessboardCalibrator.calibrate(context.contentResolver, uris, selectedCamera, spec)
                        }
                        profile = result.profile
                        status = result.reason +
                            " Przyjęto: " + result.accepted + ", odrzucono: " + result.rejected + "."
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        status = "Nie udało się przeprowadzić kalibracji na wybranych zdjęciach."
                    } finally {
                        running = false
                    }
                }
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !running) {
            scope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("No input")
                        val json = input.bufferedReader().use { reader ->
                            val output = StringBuilder()
                            val buffer = CharArray(4096)
                            while (true) {
                                val read = reader.read(buffer)
                                if (read < 0) break
                                output.append(buffer, 0, read)
                                if (output.length > 65536) throw IllegalArgumentException("Profile too large")
                            }
                            output.toString()
                        }
                        CalibrationProfileJson.decode(json)
                    }
                    profile = loaded
                    status = "Zaimportowano profil. Zweryfikuj źródło zdjęć i zgodność aparatu."
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    status = "Nieprawidłowy profil JSON lub brak dostępu do pliku."
                }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val value = profile
        if (uri != null && value != null) {
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("No output")
                        stream.use { it.write(CalibrationProfileJson.encode(value).toByteArray(Charsets.UTF_8)) }
                    }
                    "Zapisano profil kalibracyjny JSON."
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    "Nie udało się zapisać profilu."
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
            Text("Powrót do menu")
        }
        Text("Kalibracja kamery", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Szachownica: 9×6 wewnętrznych narożników (10×7 pól), kwadraty 20 mm. " +
                "Wykonaj 12–25 zdjęć w różnych położeniach i nachyleniach, bez zmiany zoomu i ostrości. " +
                "Wybieraj pliki zapisane w tej samej orientacji, bez obrotu EXIF.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "ID Camera2 wpisz zgodnie z diagnostyką. Same zdjęcia ani wpisane ID nie dowodzą " +
                "zgodności z aktywnym strumieniem CameraX. Zapisane parametry nie są jeszcze używane do estymacji pozy.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = cameraId,
            onValueChange = { cameraId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ID Camera2 (np. 0)") },
            enabled = !running,
            singleLine = true,
        )
        OutlinedTextField(
            value = columns,
            onValueChange = { columns = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Wewnętrzne narożniki poziomo") },
            enabled = !running,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = rows,
            onValueChange = { rows = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Wewnętrzne narożniki pionowo") },
            enabled = !running,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = squareMm,
            onValueChange = { squareMm = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Rozmiar pola (mm)") },
            enabled = !running,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            onClick = { photoPicker.launch(arrayOf("image/jpeg", "image/png")) },
            enabled = !running && validInputs,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (running) "Obliczanie…" else "Wybierz zdjęcia i oblicz kalibrację")
        }
        OutlinedButton(
            onClick = { importer.launch(arrayOf("application/json")) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Importuj profil JSON")
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        profile?.let { result ->
            Text("Parametry profilu", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kamera " + result.cameraId + " · " +
                    result.referenceWidth + "×" + result.referenceHeight + " px\n" +
                    "fx=" + format(result.fx) + "  fy=" + format(result.fy) +
                    "  cx=" + format(result.cx) + "  cy=" + format(result.cy) + "\n" +
                    "RMS reprojekcji: " + format(result.rmsReprojectionPx) + " px\n" +
                    "Źródło: " + result.provenance.name,
            )
            OutlinedButton(
                onClick = { exporter.launch("mobile-slam-calibration.json") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Eksportuj profil JSON")
            }
            Text(
                "Profil nie jest automatycznie stosowany do CameraX, triangulacji ani PnP. " +
                    "Przed zastosowaniem sprawdź ten sam aparat, crop, orientację i parametry obiektywu.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)
