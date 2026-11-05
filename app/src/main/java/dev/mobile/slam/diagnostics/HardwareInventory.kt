package dev.mobile.slam.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ImageFormat
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import java.time.Instant
import java.util.Locale

/** Structured, locally collected report without raw frames, device IDs or fingerprint. */
internal data class HardwareReport(
    val collectedAtUtc: String,
    val sections: List<DiagnosticSection>,
    val cameraClock: CameraClockSource,
)

/** Reads Android-reported capabilities without opening a camera or subscribing to sensors. */
internal class HardwareInventory(context: Context) {
    private val context = context.applicationContext

    fun collect(camera: CameraObservationSnapshot, imu: ImuMeasurement?): HardwareReport {
        val sections = mutableListOf<DiagnosticSection>()
        sections.add(deviceSection())
        sections.addAll(cameraSections())
        sections.add(cameraSessionSection(camera))
        sections.addAll(sensorSections())
        sections.add(imuSessionSection(imu))
        val source = cameraClockSource(camera.cameraId)
        sections.add(
            DiagnosticSection(
                "Źródła czasu i synchronizacja",
                listOf(
                    observed("SystemClock.elapsedRealtimeNanos()", SystemClock.elapsedRealtimeNanos(), "ns"),
                    if (camera.cameraId == null) missing("Źródło czasu aktywnej kamery", "Nie uruchomiono jeszcze trybu na żywo.")
                    else reported("Źródło czasu aktywnej kamery", source.name),
                    observed("Możliwość porównania zegarów", ClockDomainSafety.explanation(source)),
                    missing("Dokładność wyrównania ekspozycji i próbek IMU", "Nie przeprowadzono kalibracji czasowej."),
                ),
            ),
        )
        sections.add(
            DiagnosticSection(
                "Kalibracja i plan pomiarów",
                listOf(
                    missing("Rzeczywiste fx, fy, cx, cy oraz dystorsja", "Parametry demonstracyjne nie są kalibracją urządzenia (#9)."),
                    requested("Docelowe ImageAnalysis", "640×480", "px"),
                    requested("Backpressure", "KEEP_ONLY_LATEST"),
                    requested("Częstotliwość IMU w trybie Live", "SENSOR_DELAY_GAME (wskazówka Androida)"),
                    missing("Czas synchronizacji Camera–IMU", "Nie wyznaczono offsetu ani dryftu."),
                    missing("Test 0°/90°/180°/270° i stabilność 5–10 min", "Wymaga rzeczywistego telefonu i oceny operatora (#4)."),
                ),
            ),
        )
        return HardwareReport(Instant.now().toString(), sections, source)
    }

    private fun deviceSection(): DiagnosticSection {
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activity.getMemoryInfo(memory)
        val storage = StatFs(context.filesDir.path)
        val display = context.resources.displayMetrics
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val fields = mutableListOf(
            reported("Producent", Build.MANUFACTURER),
            reported("Model", Build.MODEL),
            reported("Android", Build.VERSION.RELEASE),
            reported("API", Build.VERSION.SDK_INT),
            reported("Poprawki bezpieczeństwa", Build.VERSION.SECURITY_PATCH),
            reported("ABI", Build.SUPPORTED_ABIS.joinToString()),
            reported("Procesory logiczne", Runtime.getRuntime().availableProcessors()),
            reported("RAM urządzenia", memory.totalMem / (1024 * 1024), "MiB"),
            observed("Dostępny RAM w chwili odczytu", memory.availMem / (1024 * 1024), "MiB"),
            observed("Dostępne miejsce dla aplikacji", storage.availableBytes / (1024 * 1024), "MiB"),
            reported("Ekran — bieżące metryki aplikacji", "${display.widthPixels}×${display.heightPixels}", "px"),
            reported("Gęstość ekranu", display.densityDpi, "dpi"),
            reported("OpenGL ES — deklarowana wersja", activity.deviceConfigurationInfo.glEsVersion),
            missing("OpenGL vendor/renderer", "Nie utworzono kontekstu EGL i nie odczytano właściwości GPU."),
            missing("Obciążenie CPU i temperatura SoC", "Brak wiarygodnego pomiaru w tym wydaniu."),
            reported("Stan kalibracji kamery", "DEMO / NOT CALIBRATED"),
        )
        if (batteryLevel >= 0 && batteryScale > 0) {
            fields.add(observed("Poziom baterii", batteryLevel * 100.0 / batteryScale, "%"))
        } else {
            fields.add(missing("Poziom baterii", "Android nie udostępnił danych."))
        }
        if (batteryTemp >= 0) {
            fields.add(observed("Temperatura baterii (nie SoC)", batteryTemp / 10.0, "°C"))
        } else {
            fields.add(missing("Temperatura baterii", "Android nie udostępnił danych."))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fields.add(observed("Stan termiczny systemu (kod Android)", power.currentThermalStatus))
        } else {
            fields.add(missing("Stan termiczny systemu", "Wymaga API 29+."))
        }
        return DiagnosticSection("Urządzenie i środowisko", fields)
    }

    private fun cameraSections(): List<DiagnosticSection> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val ids = try {
            manager.cameraIdList.toList()
        } catch (_: Exception) {
            return listOf(DiagnosticSection("Kamery Camera2", listOf(missing("Lista kamer", "Brak dostępu do listy Camera2."))))
        }
        if (ids.isEmpty()) {
            return listOf(DiagnosticSection("Kamery Camera2", listOf(missing("Lista kamer", "Android nie zgłasza dostępnych kamer."))))
        }
        return ids.map { id ->
            val characteristics = try {
                manager.getCameraCharacteristics(id)
            } catch (_: Exception) {
                null
            }
            if (characteristics == null) {
                DiagnosticSection("Camera2: $id", listOf(missing("Właściwości", "Brak danych lub uprawnień do metadanych kamery.")))
            } else {
                val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = config?.getOutputSizes(ImageFormat.YUV_420_888)
                    ?.joinToString(limit = 24, truncated = "…") { "${it.width}×${it.height}" }
                val fps = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.joinToString { "${it.lower}–${it.upper}" }
                val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.joinToString { format(it.toDouble()) }
                val lens = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "tylna"
                    CameraCharacteristics.LENS_FACING_FRONT -> "przednia"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "zewnętrzna"
                    else -> "nieznana"
                }
                val physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val distortion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    characteristics.get(CameraCharacteristics.LENS_DISTORTION)?.joinToString { format(it.toDouble()) }
                } else {
                    null
                }
                DiagnosticSection(
                    "Camera2: $id ($lens)",
                    listOf(
                        reported("Camera2 ID", id),
                        reported("Położenie obiektywu", lens),
                        reported("Hardware level", characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)),
                        reported("Orientacja sensora", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION), "°"),
                        reported("Fizyczny sensor", physical?.let { "${format(it.width.toDouble())}×${format(it.height.toDouble())}" }, "mm"),
                        reported("Active array", characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.toShortString(), "px"),
                        reported("Ogniskowe z metadanych", focal, "mm"),
                        reported("Rozdzielczości YUV_420_888", sizes, "px"),
                        reported("Zakresy docelowego AE FPS", fps, "fps"),
                        reported("Dostępne tryby AF", characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.joinToString()),
                        reported("Dostępne tryby stabilizacji", characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.joinToString()),
                        reported("Intrinsics producenta (fx,fy,cx,cy,s)", characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)?.joinToString { format(it.toDouble()) }),
                        reported("Dystorsja producenta", distortion),
                        reported("Źródło timestampów", clockSource(characteristics).name),
                    ),
                )
            }
        }
    }

    private fun cameraSessionSection(camera: CameraObservationSnapshot): DiagnosticSection {
        val last = camera.lastFrame
        val cadence = camera.cadence
        return DiagnosticSection(
            "Kamera — ostatnia obserwacja ImageAnalysis",
            listOf(
                if (camera.cameraId != null) observed("ID rzeczywiście związanej kamery CameraX", camera.cameraId)
                else missing("ID rzeczywiście związanej kamery CameraX", "Tryb na żywo nie został uruchomiony."),
                if (last != null) observed("Rozdzielczość ImageProxy", "${last.widthPx}×${last.heightPx}", "px")
                else missing("Rozdzielczość ImageProxy", "Nie przetworzono jeszcze klatki."),
                if (last != null) observed("Obrót ImageProxy", last.rotationDegrees, "°")
                else missing("Obrót ImageProxy", "Brak klatek."),
                if (last != null) observed("CropRect", last.crop, "px")
                else missing("CropRect", "Brak klatek."),
                if (last?.rowStrideBytes != null) observed("Y rowStride", last.rowStrideBytes, "B")
                else missing("Y rowStride", "Brak Y plane."),
                if (last?.pixelStrideBytes != null) observed("Y pixelStride", last.pixelStrideBytes, "B")
                else missing("Y pixelStride", "Brak Y plane."),
                if (last != null) observed("Ostatni znacznik czasu CameraX", last.timestampNs, "ns")
                else missing("Ostatni znacznik czasu CameraX", "Brak klatek."),
                observed("Przeanalizowane klatki w historii", cadence.sampleCount),
                cadence.frequencyHz?.let { observed("Szacowany FPS analizy (timestamp delta)", format(it.toDouble()), "fps") }
                    ?: missing("Szacowany FPS analizy", "Za mało kolejnych klatek."),
                cadence.medianPeriodMs?.let { observed("Mediana interwału klatek", format(it), "ms") }
                    ?: missing("Mediana interwału klatek", "Za mało danych."),
                cadence.p95AbsoluteJitterMs?.let { observed("p95 odchylenia interwału", format(it), "ms") }
                    ?: missing("p95 odchylenia interwału", "Za mało danych."),
                camera.medianProcessingMs?.let { observed("Mediana czasu analizy", format(it), "ms") }
                    ?: missing("Mediana czasu analizy", "Brak pomiarów."),
                camera.p95ProcessingMs?.let { observed("p95 czasu analizy", format(it), "ms") }
                    ?: missing("p95 czasu analizy", "Brak pomiarów."),
                observed("Nie-monotoniczne timestampy", cadence.nonMonotonic),
                missing("Odsetek odrzuconych klatek", "KEEP_ONLY_LATEST nie raportuje liczby pominiętych buforów."),
            ),
        )
    }

    private fun sensorSections(): List<DiagnosticSection> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val types = listOf(
            Sensor.TYPE_ACCELEROMETER to "Akcelerometr",
            Sensor.TYPE_GYROSCOPE to "Żyroskop",
            Sensor.TYPE_MAGNETIC_FIELD to "Magnetometr",
            Sensor.TYPE_ROTATION_VECTOR to "Wektor rotacji",
            Sensor.TYPE_PRESSURE to "Barometr",
        )
        return types.map { (type, title) ->
            val sensor = manager.getDefaultSensor(type)
            if (sensor == null) {
                DiagnosticSection(title, listOf(missing("Dostępność", "Nie zgłoszono czujnika tego typu.")))
            } else {
                DiagnosticSection(
                    title,
                    listOf(
                        reported("Nazwa", sensor.name),
                        reported("Producent", sensor.vendor),
                        reported("Wersja", sensor.version),
                        reported("Typ", sensor.type),
                        reported("Rozdzielczość", format(sensor.resolution.toDouble()), sensorUnit(type)),
                        reported("Maksymalny zakres", format(sensor.maximumRange.toDouble()), sensorUnit(type)),
                        reported("Moc", format(sensor.power.toDouble()), "mA"),
                        reported("minDelay", sensor.minDelay, "µs"),
                        reported("maxDelay", sensor.maxDelay, "µs"),
                        reported("FIFO reserved", sensor.fifoReservedEventCount, "próbek"),
                        reported("FIFO maximum", sensor.fifoMaxEventCount, "próbek"),
                        reported("Reporting mode", sensor.reportingMode),
                        reported("Wake-up", sensor.isWakeUpSensor),
                    ),
                )
            }
        }
    }

    private fun imuSessionSection(imu: ImuMeasurement?): DiagnosticSection {
        val fields = mutableListOf<DiagnosticField>()
        if (imu == null) {
            fields.add(missing("Pomiar IMU", "Uruchom test IMU za zgodą użytkownika. Wskaźnik HUD 10 Hz nie jest pomiarem próbkowania."))
        } else {
            fields.add(observed("Czas obserwacji", format(imu.durationNs / 1_000_000_000.0), "s"))
            for ((name, sample) in listOf("Akcelerometr" to imu.accelerometer, "Żyroskop" to imu.gyroscope)) {
                fields.add(if (sample.registered) observed("$name — rejestracja", "tak") else missing("$name — rejestracja", "Czujnik niedostępny lub rejestracja odrzucona."))
                fields.add(observed("$name — liczba callbacków", sample.metrics.sampleCount))
                fields.add(sample.metrics.frequencyHz?.let { observed("$name — mediana częstotliwości", format(it), "Hz") }
                    ?: missing("$name — częstotliwość", "Za mało zdarzeń."))
                fields.add(sample.metrics.p95AbsoluteJitterMs?.let { observed("$name — p95 jitter", format(it), "ms") }
                    ?: missing("$name — p95 jitter", "Za mało zdarzeń."))
                fields.add(observed("$name — niemonotoniczne znaczniki", sample.metrics.nonMonotonic))
                fields.add(sample.lastTimestampNs?.let { observed("$name — ostatni timestamp", it, "ns") }
                    ?: missing("$name — ostatni timestamp", "Brak zdarzeń."))
                fields.add(sample.accuracy?.let { observed("$name — ostatnia dokładność", it) }
                    ?: missing("$name — dokładność", "Nie zgłoszono zmiany dokładności."))
            }
        }
        fields.add(missing("Fuzja visual-inertial", "Żaden pomiar nie dowodzi korekcji opóźnienia ani zsynchronizowanej fuzji."))
        return DiagnosticSection("IMU — próbki za zgodą użytkownika", fields)
    }

    private fun cameraClockSource(activeId: String?): CameraClockSource {
        if (activeId == null) return CameraClockSource.UNAVAILABLE
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            clockSource(manager.getCameraCharacteristics(activeId))
        } catch (_: Exception) {
            CameraClockSource.UNAVAILABLE
        }
    }

    private fun clockSource(characteristics: CameraCharacteristics): CameraClockSource =
        when (characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> CameraClockSource.REALTIME
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> CameraClockSource.UNKNOWN
            else -> CameraClockSource.UNAVAILABLE
        }

    private fun sensorUnit(type: Int): String =
        when (type) {
            Sensor.TYPE_ACCELEROMETER -> "m/s²"
            Sensor.TYPE_GYROSCOPE -> "rad/s"
            Sensor.TYPE_MAGNETIC_FIELD -> "µT"
            Sensor.TYPE_PRESSURE -> "hPa"
            else -> "jednostka zależna od sensora"
        }

    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)
}

internal fun reported(name: String, value: Any?, unit: String? = null): DiagnosticField =
    if (value == null) missing(name, "Nieudostępnione przez Android API lub wymaga uprawnienia.")
    else DiagnosticField(name, value.toString(), unit, EvidenceSource.REPORTED)

internal fun observed(name: String, value: Any?, unit: String? = null): DiagnosticField =
    if (value == null) missing(name, "Nie zaobserwowano.")
    else DiagnosticField(name, value.toString(), unit, EvidenceSource.OBSERVED)

internal fun requested(name: String, value: Any?, unit: String? = null): DiagnosticField =
    if (value == null) missing(name, "Nie skonfigurowano.")
    else DiagnosticField(name, value.toString(), unit, EvidenceSource.REQUESTED)

internal fun missing(name: String, reason: String): DiagnosticField =
    DiagnosticField(name, null, source = EvidenceSource.UNAVAILABLE, reason = reason)
