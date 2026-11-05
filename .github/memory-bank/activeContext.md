# Active context

## Current Plan — Issue #22 (2026-11-05)
- **Goal:** implement editable, validated and persisted FAST/IMU HUD parameters accessible through the existing Settings route, with a permission-free navigation entry to a separate preliminary read-only device information screen. Full hardware validation remains #23.
- **Files:** `frontend/Settings.kt`, new `frontend/SettingsRepository.kt`, `MainActivity.kt`, `ui/SecondaryScreens.kt` or dedicated Settings/Hardware screens, `ui/AppDestination.kt`, build dependencies, unit/instrumentation tests, README and Memory Bank. Avoid modifying SLAM geometry, camera metadata or experimental sensor fusion.
- **Steps:** (1) establish versioned Settings validation/defaults; (2) persist via Preferences DataStore and expose Flow; (3) bind the single authoritative Settings snapshot to Settings and Live, with save/reset handling and safe re-entry boundary; (4) display future controls as disabled and add preliminary read-only hardware route; (5) test invalid values, migration, process restart and UI navigation; (6) verify Android CI at exact SHA and document remaining device acceptance.
- **Architecture:** single Activity and Compose destination registry remain unchanged. `Settings` is immutable, `SettingsRepository` owns DataStore persistence; no shared mutable data on camera/IMU threads. New values take effect **only when opening/re-opening Live**, safely recreating `CameraAnalyzer` and `ImuSource`; do not claim live hot reconfiguration.
- **Validation:** FAST threshold integer [1,255], HUD refresh interval [100,1000] ms (max 10 Hz); rejection of invalid/unsupported settings; schema-v1 reset for unknown versions with visible warning. Camera intrinsics remain teaching-only.
- **Testing:** `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebugAndroidTest`; run unit tests for validation/migration, Compose instrumentation source for save/reset/disabled features, and document **unexecuted** hardware tests until a phone/emulator is used.
- **Acceptance:** Settings opens without camera permission; storage survives restart; valid values are applied to fresh Live analyzer/IMU, invalid values cannot persist, reset restores 20 and 100, hardware link opens a truthful preliminary read-only page, CI passes.

## Current State
- PR #24 (menu/navigation) is merged in `main` at commit `2eae80820c201df023eb13fcba1d93b943bfe31a`; associated Android CI #42019235629 passed.
- Current settings: `Settings(fastThreshold=20, imuHudIntervalMs=100)` instantiated separately in Live; no persistence or editing.
- Device acceptance #4 and detailed hardware diagnostics #23 remain open.

## Current Implementation
- Immutable Settings and schema-v1 codec reject invalid thresholds, unknown schema and malformed edits.
- Preferences DataStore plus Flow provides app-level state; Live receives effective settings on entry, recreating FAST detector and IMU HUD sampling gate.
- Settings screen supports validation, save and reset; unsupported future controls are disabled.
- Permission-free Hardware overview shows a limited static device/sensor inventory. Full measurement/JSON export remains #23.
- Unit validation tests and Android UI test source are included; CI result remains pending.

## Next Step
- Open a scoped PR for #22, verify Android CI at exact SHA, and document physical-device acceptance blockers.
