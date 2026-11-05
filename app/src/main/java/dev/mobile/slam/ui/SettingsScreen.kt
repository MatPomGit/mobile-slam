package dev.mobile.slam.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.mobile.slam.frontend.Settings
import dev.mobile.slam.frontend.SettingsCodec

/** Validated, persistent parameters for the currently implemented vision and IMU HUD paths. */
@Composable
fun SettingsScreen(
    settings: Settings,
    busy: Boolean,
    warning: String?,
    status: String?,
    onSave: (Settings) -> Unit,
    onReset: () -> Unit,
    onHardware: () -> Unit,
    onBack: () -> Unit,
) {
    var fastInput by rememberSaveable(settings.fastThreshold, settings.imuHudIntervalMs) {
        mutableStateOf(settings.fastThreshold.toString())
    }
    var imuInput by rememberSaveable(settings.fastThreshold, settings.imuHudIntervalMs) {
        mutableStateOf(settings.imuHudIntervalMs.toString())
    }
    val fastValue = fastInput.trim().toIntOrNull()
    val imuValue = imuInput.trim().toLongOrNull()
    val fastValid = fastValue != null && fastValue in Settings.MIN_FAST_THRESHOLD..Settings.MAX_FAST_THRESHOLD
    val imuValid = imuValue != null && imuValue in Settings.MIN_IMU_HUD_INTERVAL_MS..Settings.MAX_IMU_HUD_INTERVAL_MS
    val pending = SettingsCodec.parse(fastInput, imuInput)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Powrót do menu")
        }
        Text("Ustawienia", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Bieżące: FAST ${settings.fastThreshold}, IMU HUD ${settings.imuHudIntervalMs} ms. " +
                "Zmiany zostaną zastosowane przy kolejnym otwarciu trybu na żywo.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (warning != null) {
            Text(warning, color = MaterialTheme.colorScheme.error)
        }
        if (status != null) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }

        Text("Detekcja cech FAST", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = fastInput,
            onValueChange = { fastInput = it },
            modifier = Modifier.fillMaxWidth().testTag("fastThresholdInput"),
            label = { Text("Próg FAST (1–255)") },
            isError = !fastValid,
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(if (fastValid) "Niższy próg: więcej wykrywanych narożników." else "Podaj liczbę całkowitą 1–255.")
            },
        )
        FutureSetting(title = "Liczba i rozkład punktów", milestone = "M2")

        Text("IMU i interfejs", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = imuInput,
            onValueChange = { imuInput = it },
            modifier = Modifier.fillMaxWidth().testTag("imuHudInput"),
            label = { Text("Odświeżanie HUD (100–1000 ms)") },
            isError = !imuValid,
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(
                    if (imuValid) "Dotyczy tylko interfejsu (maks. 10 Hz), nie szybkości sensorów ani synchronizacji."
                    else "Podaj czas w milisekundach: 100–1000.",
                )
            },
        )
        FutureSetting(title = "Rejestracja IMU z pełną częstotliwością", milestone = "M4")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { pending?.let(onSave) },
                enabled = pending != null && pending != settings && !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(if (busy) "Zapisywanie…" else "Zapisz i zastosuj")
            }
            OutlinedButton(
                onClick = {
                    fastInput = Settings.DEFAULT_FAST_THRESHOLD.toString()
                    imuInput = Settings.DEFAULT_IMU_HUD_INTERVAL_MS.toString()
                    onReset()
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text("Przywróć domyślne")
            }
        }

        FutureSetting(title = "Obraz i kamera: rozdzielczość, FPS, wybór kamery", milestone = "M2")
        FutureSetting(title = "Śledzenie KLT: okno, piramida, próg błędu", milestone = "M2")
        FutureSetting(title = "Geometria: RANSAC i minimalna paralaksa", milestone = "M2")
        FutureSetting(title = "Pozycja i mapa: PnP, keyframes, limity mapy", milestone = "M3")
        FutureSetting(title = "Wizualizacja trajektorii i chmury 3D", milestone = "M3")
        FutureSetting(title = "Zapis i odtwarzanie sesji", milestone = "M4")

        Text("System i narzędzia", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = onHardware,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Parametry sprzętowe i diagnostyka")
        }
        Text(
            "Podgląd dostępnych informacji o urządzeniu działa bez uprawnień kamery. " +
                "Rozszerzone pomiary i raport walidacyjny powstaną w #23.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FutureSetting(title: String, milestone: String) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("$title · W przygotowaniu ($milestone)")
    }
}
