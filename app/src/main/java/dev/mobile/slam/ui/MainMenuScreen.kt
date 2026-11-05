package dev.mobile.slam.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Start-up screen; it deliberately does not request camera permission or start any sensors.
 */
@Composable
fun MainMenuScreen(version: String, onNavigate: (AppDestination) -> Unit, onExit: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns =
            when {
                maxWidth < 360.dp -> 1
                maxWidth < 700.dp -> 2
                else -> 3
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "Miejsce na logo aplikacji" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("LOGO", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("MOBILE SLAM", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Dydaktyczny monokularny SLAM",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Text("Wersja $version", style = MaterialTheme.typography.labelSmall)
            }

            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("TRYBY PRACY", style = MaterialTheme.typography.titleMedium)
                AppDestination.modes.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { destination ->
                            ModeButton(
                                destination = destination,
                                modifier = Modifier.weight(1f),
                                onNavigate = onNavigate,
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onNavigate(AppDestination.SETTINGS) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Ustawienia")
                    }
                    OutlinedButton(
                        onClick = { onNavigate(AppDestination.ABOUT) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("O aplikacji")
                    }
                }
                TextButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Zamknij aplikację")
                }
            }
        }
    }
}

@Composable
private fun ModeButton(destination: AppDestination, modifier: Modifier, onNavigate: (AppDestination) -> Unit) {
    OutlinedButton(
        onClick = { if (destination.isAvailable) onNavigate(destination) },
        enabled = destination.isAvailable,
        modifier = modifier.heightIn(min = 92.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                destination.title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                if (destination.isAvailable) {
                    "Dostępny"
                } else {
                    "W przygotowaniu · ${destination.releaseNote}"
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
