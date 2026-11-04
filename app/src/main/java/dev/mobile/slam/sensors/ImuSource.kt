package dev.mobile.slam.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

internal data class ImuSample(
    val timestampNs: Long,
    val acceleration: List<Float>,
    val angularVelocity: List<Float>,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
)

internal class ImuSource(
    context: Context,
    private val onSample: (ImuSample) -> Unit,
) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var acceleration = listOf(0f, 0f, 0f)
    private var angularVelocity = listOf(0f, 0f, 0f)

    fun start(): Boolean {
        val hasAccelerometer = accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        } == true
        val hasGyroscope = gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        } == true
        return hasAccelerometer || hasGyroscope
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values.toList()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> acceleration = values
            Sensor.TYPE_GYROSCOPE -> angularVelocity = values
            else -> return
        }
        onSample(
            ImuSample(
                timestampNs = event.timestamp,
                acceleration = acceleration.toList(),
                angularVelocity = angularVelocity.toList(),
                hasAccelerometer = accelerometer != null,
                hasGyroscope = gyroscope != null,
            ),
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
