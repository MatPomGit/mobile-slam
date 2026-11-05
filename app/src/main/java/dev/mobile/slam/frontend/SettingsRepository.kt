package dev.mobile.slam.frontend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.mobileSlamSettings by preferencesDataStore(name = "mobile_slam_settings")

/**
 * Owns the one persistent settings file. Camera/IMU code receives immutable snapshots,
 * and never reads Android preferences on a processing thread.
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.mobileSlamSettings

    val snapshots: Flow<SettingsSnapshot> = store.data
        .map { preferences ->
            SettingsCodec.decode(
                version = preferences[SCHEMA_VERSION],
                fastThreshold = preferences[FAST_THRESHOLD],
                imuHudIntervalMs = preferences[IMU_HUD_INTERVAL_MS],
            )
        }.catch { cause ->
            if (cause is IOException) {
                emit(SettingsSnapshot(Settings(), "Nie udało się odczytać ustawień. Użyto wartości domyślnych."))
            } else {
                throw cause
            }
        }

    suspend fun save(settings: Settings) {
        store.edit { preferences ->
            preferences[SCHEMA_VERSION] = SettingsCodec.VERSION
            preferences[FAST_THRESHOLD] = settings.fastThreshold
            preferences[IMU_HUD_INTERVAL_MS] = settings.imuHudIntervalMs
        }
    }

    suspend fun reset() {
        store.edit { preferences ->
            preferences.clear()
            preferences[SCHEMA_VERSION] = SettingsCodec.VERSION
        }
    }

    companion object {
        private val SCHEMA_VERSION = intPreferencesKey("schema_version")
        private val FAST_THRESHOLD = intPreferencesKey("fast_threshold")
        private val IMU_HUD_INTERVAL_MS = longPreferencesKey("imu_hud_interval_ms")
    }
}
