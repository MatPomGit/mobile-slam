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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mobile.slam.calib.CalibrationProfileJson
import dev.mobile.slam.calib.CalibrationProfileRepository
import dev.mobile.slam.calib.ChessboardCalibrator
import dev.mobile.slam.calib.ChessboardSpec
import dev.mobile.slam.calib.MeasuredCalibration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User-directed photo calibration. Does not open CameraX or activate a calibrated
 * pose path. A chosen photo series must be from one known camera/lens/focus state.
 */
@Composable
internal fun CalibrationScreen(
    repository: CalibrationProfileRepository,
    savedProfile: MeasuredCalibration?,
    storageWarning: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraId by remember { mutableStateOf("0") }
    var columns by remember { mutableStateOf("9") }
    var rows by remember { mutableStateOf("6") }
    var automaticPattern by remember { mutableStateOf(true) }
    var squareMm by remember { mutableStateOf("20") }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Wybierz serię zdjęć wykonanych tym samym aparatem.") }
    var profile by remember { mutableStateOf<MeasuredCalibration?>(null) }

    val board = runCatching {
        ChessboardSpec(
            columns = if (automaticPattern) 9 else columns.toInt(),
            rows = if (automaticPattern) 6 else rows.toInt(),
            automaticPattern = automaticPattern,
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
                        val detected = result.pattern?.let {
                            "Wzorzec: " + it.columns + "×" + it.rows +
                                " narożników wewnętrznych (" + (it.columns + 1) +
                                "×" + (it.rows + 1) + " pól). "
                        } ?: ""
                        val rejections = result.rejectionCounts.entries.joinToString("; ") { (reason, count) ->
                            "${reason.label}: $count"
                        }
                        val report = detected + result.reason +
                            " Przyjęto: " + result.accepted + ", odrzucono: " + result.rejected + "." +
                            if (rejections.isEmpty()) "" else " Przyczyny: $rejections."
                        val measured = result.profile
                        status = if (measured != null) {
                            try {
                                repository.save(measured)
                                "$report Profil zapisano w aplikacji."
                            } catch (cause: Exception) {
                                if (cause is CancellationException) throw cause
                                "$report Nie udało się zapisać profilu w aplikacji."
                            }
                        } else {
                            report
                        }
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
                    status = try {
                        repository.save(loaded)
                        "Zaimportowano i zapisano profil. Zweryfikuj źródło zdjęć i zgodność aparatu."
                    } catch (cause: Exception) {
                        if (cause is CancellationException) throw cause
                        "Zaimportowano profil, ale nie udało się zapisać go w aplikacji."
                    }
                } catch (cause: Exception) {
                    if (cause is CancellationException) throw cause
                    status = "Nieprawidłowy profil JSON lub brak dostępu do pliku."
                }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val value = profile ?: savedProfile
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
            "Wykonaj 12–25 zdjęć szachownicy w różnych położeniach i nachyleniach, " +
                "bez zmiany zoomu i ostrości. Jeden wymiar wzorca to liczba pól minus jeden " +
                "(narożniki wewnętrzne). Wszystkie zdjęcia muszą mieć tę samą rozdzielczość " +
                "i orientację po uwzględnieniu obrotu EXIF.",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = automaticPattern,
                onCheckedChange = { automaticPattern = it },
                enabled = !running,
            )
            Text("Automatycznie rozpoznaj szachownicę (4–16 narożników na oś)")
        }
        if (!automaticPattern) {
            OutlinedTextField(
                value = columns,
                onValueChange = { columns = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Narożniki wewnętrzne poziomo (liczba pól − 1)") },
                enabled = !running,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = rows,
                onValueChange = { rows = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Narożniki wewnętrzne pionowo (liczba pól − 1)") },
                enabled = !running,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = squareMm,
            onValueChange = { squareMm = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Zmierzony rozmiar pojedynczego pola (mm)") },
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
        if (storageWarning != null) {
            Text(storageWarning, color = MaterialTheme.colorScheme.error)
        }
        val visibleProfile = profile ?: savedProfile
        visibleProfile?.let { result ->
            Text(
                if (profile == null) "Ostatni zapisany profil" else "Parametry profilu",
                style = MaterialTheme.typography.titleMedium,
            )
            CalibrationProfileSummary(result)
            OutlinedButton(
                onClick = { exporter.launch("mobile-slam-calibration.json") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Eksportuj profil JSON")
            }
        }
    }
}
