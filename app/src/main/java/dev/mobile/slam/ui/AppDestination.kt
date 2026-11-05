package dev.mobile.slam.ui

/**
 * All planned application destinations and their actual availability in the current release.
 * Unimplemented modes must remain disabled until their acceptance criteria are met.
 */
enum class AppDestination(
    val title: String,
    val isAvailable: Boolean,
    val releaseNote: String? = null,
) {
    MENU("Menu główne", true),
    LIVE("Tryb na żywo", true),
    STEP_BY_STEP("Krok po kroku", false, "M4"),
    MAP_3D("Mapa 3D", false, "M2"),
    CALIBRATION("Kalibracja kamery", true),
    RECORD("Nagrywanie sesji", false, "M4"),
    REPLAY("Odtwarzanie sesji", false, "M4"),
    RESULTS("Wyniki i wykresy", false, "M3"),
    ARCORE("Porównanie z ARCore", false, "opcjonalnie"),
    SETTINGS("Ustawienia", true),
    HARDWARE("Parametry sprzętowe", true),
    ABOUT("O aplikacji", true),
    ;

    companion object {
        /** Fixed order of the main screen's feature tiles. */
        val modes: List<AppDestination> =
            listOf(LIVE, STEP_BY_STEP, MAP_3D, CALIBRATION, RECORD, REPLAY, RESULTS, ARCORE)
    }
}

/** Keeps the UI and any programmatic caller from opening a route not implemented yet. */
object AppNavigation {
    fun navigate(current: AppDestination, requested: AppDestination): AppDestination {
        return if (requested.isAvailable) requested else current
    }
}
