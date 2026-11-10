package dev.mobile.slam.calib

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.calibrationDataStore by preferencesDataStore(name = "mobile_slam_calibration")

/** A measured profile survives navigation and process restarts; it is never applied to CameraX implicitly. */
internal data class CalibrationStorageSnapshot(
    val profile: MeasuredCalibration?,
    val warning: String? = null,
    val bundledDefault: Boolean = false,
)

internal class CalibrationProfileRepository(context: Context) {
    private val store = context.applicationContext.calibrationDataStore

    val snapshots: Flow<CalibrationStorageSnapshot> = store.data
        .map { preferences ->
            val json = preferences[PROFILE_JSON]
            if (json == null) {
                CalibrationStorageSnapshot(BundledCalibrationProfile.measured, bundledDefault = true)
            } else {
                runCatching {
                    CalibrationStorageSnapshot(CalibrationProfileJson.decode(json))
                }.getOrElse {
                    CalibrationStorageSnapshot(
                        BundledCalibrationProfile.measured,
                        "Zapisany profil jest nieprawidłowy. Użyto domyślnej kalibracji pomiarowej.",
                        bundledDefault = true,
                    )
                }
            }
        }.catch { cause ->
            if (cause is IOException) {
                emit(
                    CalibrationStorageSnapshot(
                        BundledCalibrationProfile.measured,
                        "Nie udało się odczytać profilu użytkownika. Użyto domyślnej kalibracji pomiarowej.",
                        bundledDefault = true,
                    ),
                )
            } else {
                throw cause
            }
        }

    suspend fun save(profile: MeasuredCalibration) {
        val validatedJson = CalibrationProfileJson.encode(profile)
        store.edit { preferences -> preferences[PROFILE_JSON] = validatedJson }
    }

    companion object {
        private val PROFILE_JSON = stringPreferencesKey("measured_profile_json_v1")
    }
}
