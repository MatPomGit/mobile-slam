package dev.mobile.slam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.mobile.slam.calib.CalibrationDisplayText
import dev.mobile.slam.calib.MeasuredCalibration
import java.util.Locale

/** Shared read-only display; saved intrinsics are not the live camera's active intrinsics. */
@Composable
internal fun CalibrationProfileSummary(profile: MeasuredCalibration) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Kamera Camera2: ${profile.cameraId} · obraz: ${profile.referenceWidth}×${profile.referenceHeight} px",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Macierz wewnętrzna K (px)", style = MaterialTheme.typography.titleSmall)
        Text(
            CalibrationDisplayText.matrix(profile),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text("Dystorsja Brown–Conrady", style = MaterialTheme.typography.titleSmall)
        Text(
            CalibrationDisplayText.distortion(profile),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "RMS reprojekcji: ${String.format(Locale.US, "%.3f", profile.rmsReprojectionPx)} px · " +
                "źródło: ${profile.provenance.name}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Parametry dotyczą geometrii zdjęć kalibracyjnych. Nie są automatycznie używane " +
                "w CameraX, KLT, PnP ani SLAM. Przed użyciem sprawdź obiektyw, zoom, ostrość, obrót i crop.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
