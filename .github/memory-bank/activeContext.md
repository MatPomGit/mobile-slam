# Active context

## Current plan — Issue #23 hardware validation (2026-11-05)
- **Goal:** replace the preliminary hardware overview with a factually sourced, read-only capability inventory; implement consented IMU sampling diagnostics, per-frame CameraX observations, timestamp-domain safety and a versioned local JSON report.
- **Branch/dependency:** `feat/hardware-validation-m1-20261105` branches from open PR #25 (`feat/settings-persistence-m1-20261105`); open a **stacked PR** against the parent branch. Do not merge or rewrite PR #25 while implementing diagnostics.
- **Files:** new `diagnostics/` immutable DTOs, API-bound inventory collector, bounded synchronized camera/IMU observations, report writer; replace `ui/HardwareOverviewScreen`, wire `MainActivity` and `CameraAnalyzer`; unit/instrumented tests, README and Memory Bank.
- **Steps:** (1) typed capability rows with reported/configured/observed/unavailable provenance, clock-source gate and bounded interval statistics; (2) read Android/Camera2/SensorManager data with permission/API guards and missing-data reasons; (3) collect real CameraX metadata at frame-analysis stage and explicit, separately sampled IMU callbacks in a consented, lifecycle-scoped test; (4) display sections, run/stop controls, guided checklist and offline SAF JSON export; (5) test pure transforms and JSON on Android; verify Android CI at exact PR SHA.
- **Safety:** no camera open or sensor subscription on entering diagnostics; Camera2 properties are hardware reported, not necessarily the active CameraX stream; only bound-camera ID identifies the active lens. Never compare CAMERA to IMU/elapsed time if camera `SENSOR_INFO_TIMESTAMP_SOURCE` is `UNKNOWN`; REALTIME enables same clock-domain comparisons but **does not prove sync calibration**. Do not export unique identifiers, raw images, serials, fingerprints, locations or sensitive user data. Unknown metrics shown as `nie zmierzono`, not PASS.
- **Performance:** camera observer stores only bounded scalar timestamps/geometry; IMU test stores bounded timestamp deltas per sensor and unregisters on screen disposal/background. Avoid joining camera and IMU raw samples; no artificial absolute time alignment.
- **Test strategy:** JVM tests for clock-source gate, sample intervals and bounded history; Android instrumentation JSON export shape/camera-permission-free rendering; CI `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebugAndroidTest`. Device tests with a real camera/IMU remain #4 until evidence is attached.
- **Acceptance:** a student can open detailed offline hardware inventory and save a provenance/unit-aware JSON report, run/stop IMU test explicitly, enter Live for actual CameraX metrics, see timestamp limitations and untested checklist items. CI passes; no physical-device acceptance claims without a test report.

## Current state
- PR #24 merged, main CI #42019235629 success; PR #25 remains open, CI #42031397100 success, and provides the Settings→Hardware destination.
- Existing HardwareOverviewScreen shows only Build and accelerometer/gyroscope names. No Camera2 metadata, true measured FPS, synchronized clock checks or export.
- Full physical-device validation remains issue #4.

## Current implementation
- `diagnostics/DiagnosticsModels.kt`: provenance-aware rows, bounded camera/IMU timestamps, nonmonotonic counts and interval statistics; conservative clock-domain gate.
- `diagnostics/HardwareInventory.kt`: Android/Camera2/IMU hardware inventory with unavailable reasons, recorded ImageProxy metadata and observed frame cadence/cost.
- `diagnostics/ImuValidationSession.kt`: explicit, lifecycle-scoped IMU test independent of 10 Hz HUD.
- `diagnostics/HardwareJson.kt`: locally exported schema-v1 JSON, source labels, units, missing reasons, NOT_TESTED acceptance checklist.
- `ui/HardwareDiagnosticsScreen.kt`: refresh, start/stop IMU, enter Live for camera test, SAF export. Existing Live CameraAnalyzer publishes scalar-only observations and records actual bound camera ID.
- JVM clock/interval tests and Android JSON test source; CI and physical-device checks not yet confirmed.

## Next step
- Create stacked PR against #25 branch, fix Android CI findings at exact SHA, and keep real-device acceptance #4 explicitly open.
