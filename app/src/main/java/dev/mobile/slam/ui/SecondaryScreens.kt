package dev.mobile.slam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mobile.slam.frontend.Settings

/**
 * Read-only settings entry point; persistent editors are implemented separately in issue #22.
 */
@Composable
fun SettingsOverviewScreen(onBack: () -> Unit) {
    val defaults = Settings()

    InfoScreenLayout(title = "Ustawienia", onBack = onBack) {
        Text(
            "Obecne wartości parametrów algorytmu (tylko do odczytu). " +
                "Edycja i trwały zapis zostaną dodane w następnym etapie.",
        )
        Text("Detekcja FAST: próg ${defaults.fastThreshold}")
        Text("Odświeżanie wskaźników IMU: ${defaults.imuHudIntervalMs} ms")
        Text("Pozostałe parametry: w przygotowaniu", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Parametry sprzętowe i diagnostyka · w przygotowaniu")
        }
        Text(
            "Diagnostyka kamery, IMU i synchronizacji zostanie udostępniona w osobnym etapie (#23).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Describes the app's learning purpose and the limits of its current implementation. */
@Composable
fun AboutScreen(version: String, onBack: () -> Unit) {
    InfoScreenLayout(title = "O aplikacji", onBack = onBack) {
        Text("Mobile SLAM · wersja $version")
        Text("Dydaktyczna aplikacja Android do nauki etapów monokularnego SLAM wizualnego.")
        Text(
            "W obecnym wydaniu wykrywane są punkty FAST w kolejnych klatkach obrazu. " +
                "Nie są jeszcze śledzone ani rekonstruowane w przestrzeni 3D.",
        )
        Text(
            "Parametry kamery są przykładowe, a skala monokularna pozostaje nieznana. " +
                "Znaczniki czasu kamery i IMU nie zostały zwalidowane do fuzji.",
        )
        Text("Licencja kodu: MIT.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoScreenLayout(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Powrót do menu")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}
