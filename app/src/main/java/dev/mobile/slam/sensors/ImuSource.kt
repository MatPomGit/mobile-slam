package dev.mobile.slam.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

internal data class ImuSample(
    val timestampNs: Long,
    val accelerationTimestampNs: Long?,
    val angularVelocityTimestampNs: Long?,
    val acceleration: List<Float>,
    val angularVelocity: List<Float>,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
)

/** Limits HUD snapshots without altering the independently timestamped sensor readings. */
internal class SamplingGate(private val intervalNs: Long) {
    init {
        require(intervalNs > 0L)
    }

    private var lastPublishedNs: Long? = null

    fun shouldPublish(nowNs: Long): Boolean {
        val last = lastPublishedNs
        if (last == null || nowNs < last || nowNs - last >= intervalNs) {
            lastPublishedNs = nowNs
            return true
        }
        return false
    }

    fun reset() {
        lastPublishedNs = null
    }
}

internal class ImuSource(
    context: Context,
    hudIntervalNs: Long,
    private val onSample: (ImuSample) -> Unit,
) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val samplingGate = SamplingGate(hudIntervalNs)
    private val acceleration = FloatArray(3)
    private val angularVelocity = FloatArray(3)
    private var accelerationTimestampNs: Long? = null
    private var angularVelocityTimestampNs: Long? = null
    private var hasAccelerometer = false
    private var hasGyroscope = false
    private var started = false

    fun start(): Boolean {
        if (started) return hasAccelerometer || hasGyroscope
        samplingGate.reset()
        // GAME is sufficient for live teaching; the HUD itself is capped to 10 Hz.
        hasAccelerometer = accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        hasGyroscope = gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        started = true
        return hasAccelerometer || hasGyroscope
    }

    fun stop() {
        if (!started) return
        sensorManager.unregisterListener(this)
        started = false
        hasAccelerometer = false
        hasGyroscope = false
        accelerationTimestampNs = null
        angularVelocityTimestampNs = null
        samplingGate.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started) return
        val target = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerationTimestampNs = event.timestamp
                acceleration
            }
            Sensor.TYPE_GYROSCOPE -> {
                angularVelocityTimestampNs = event.timestamp
                angularVelocity
            }
            else -> return
        }
        for (index in 0..2) {
            target[index] = event.values[index]
        }
        if (!samplingGate.shouldPublish(SystemClock.elapsedRealtimeNanos())) return
        onSample(
            ImuSample(
                timestampNs = event.timestamp,
                accelerationTimestampNs = accelerationTimestampNs,
                angularVelocityTimestampNs = angularVelocityTimestampNs,
                acceleration = acceleration.toList(),
                angularVelocity = angularVelocity.toList(),
                hasAccelerometer = hasAccelerometer,
                hasGyroscope = hasGyroscope,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
