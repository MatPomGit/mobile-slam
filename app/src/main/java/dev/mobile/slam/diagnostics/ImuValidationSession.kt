package dev.mobile.slam.diagnostics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/** One sensor's explicitly measured callback cadence (not the 10 Hz UI HUD). */
internal data class SensorMeasurement(
    val registered: Boolean,
    val metrics: IntervalSummary,
    val lastTimestampNs: Long?,
    val accuracy: Int?,
)

/** Acceleration and gyro use independent timestamps; there is no inferred temporal fusion. */
internal data class ImuMeasurement(
    val durationNs: Long,
    val accelerometer: SensorMeasurement,
    val gyroscope: SensorMeasurement,
)

/**
 * A consented, bounded IMU callback test. Start/stop on the main thread and stop on lifecycle exit.
 */
internal class ImuValidationSession(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var accelTimes = TimestampWindow(600)
    private var gyroTimes = TimestampWindow(600)
    private var accelLast: Long? = null
    private var gyroLast: Long? = null
    private var accelAccuracy: Int? = null
    private var gyroAccuracy: Int? = null
    private var startedAtNs = 0L
    private var accelRegistered = false
    private var gyroRegistered = false
    var isRunning = false
        private set

    fun start(): Boolean {
        if (isRunning) return true
        accelTimes = TimestampWindow(600)
        gyroTimes = TimestampWindow(600)
        accelLast = null
        gyroLast = null
        accelAccuracy = null
        gyroAccuracy = null
        startedAtNs = SystemClock.elapsedRealtimeNanos()
        accelRegistered = accelerometer?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        gyroRegistered = gyroscope?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        isRunning = accelRegistered || gyroRegistered
        return isRunning
    }

    fun stop(): ImuMeasurement {
        if (isRunning) manager.unregisterListener(this)
        val now = SystemClock.elapsedRealtimeNanos()
        val result = ImuMeasurement(
            durationNs = if (startedAtNs > 0L) now - startedAtNs else 0L,
            accelerometer = SensorMeasurement(accelRegistered, accelTimes.summary(), accelLast, accelAccuracy),
            gyroscope = SensorMeasurement(gyroRegistered, gyroTimes.summary(), gyroLast, gyroAccuracy),
        )
        isRunning = false
        return result
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRunning) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelLast = event.timestamp
                accelTimes.record(event.timestamp)
                accelAccuracy = event.accuracy
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroLast = event.timestamp
                gyroTimes.record(event.timestamp)
                gyroAccuracy = event.accuracy
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        when (sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> accelAccuracy = accuracy
            Sensor.TYPE_GYROSCOPE -> gyroAccuracy = accuracy
        }
    }
}
