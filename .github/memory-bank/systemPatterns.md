# System patterns

## Package structure

Use `dev.mobile.slam` as root namespace. Camera acquisition and grayscale feature extraction: `sensors`; algorithm settings and later tracking: `frontend`; calibration: `calib`; camera-to-overlay coordinate projection: `viz`; future initialization: `init`; map state: `mapping`; recording: `record`; Compose screens: `ui` (initial single-screen UI remains in `MainActivity`).

## Data and threading

Frames carry camera timestamps in nanoseconds; CameraX and IMU timestamps must be checked for a shared monotonic clock before fusion. Accelerometer and gyroscope timestamps are stored separately. The intended full pipeline is camera capture -> tracking coroutine -> keyframe-only mapping coroutine -> rendering. Pass immutable feature/sensor snapshots to rendering.

Camera analysis uses one dedicated serial executor; the analyzer posts snapshot callbacks to the main thread. IMU listeners are registered on the main thread only while the lifecycle is started. This milestone uses SENSOR_DELAY_GAME and a gate for <=10 Hz HUD updates. Future visual-inertial fusion must collect full-rate sensor samples independently of the UI.

## Binding and code rules

Bind preview and image analysis with a shared `UseCaseGroup` and `PreviewView.viewPort`. Use `ImageProxy.cropRect` and `rotationDegrees` when projecting detector points into the preview. Release only the use cases owned by the screen; shut down the analyzer executor and unregister IMU on lifecycle stop. Keep algorithm thresholds in `Settings`, UI strings Polish, and source/comments English. Public classes have KDoc with mathematics when relevant.

## Navigation and screen lifecycles

The single-Activity Compose app uses a typed `ui/AppDestination` enum for all menu routes and a small `AppNavigation` availability guard. Menu, Settings overview and About are lightweight screens that require no camera permission. Live screen and its camera/IMU resources exist only while the Live route is composed. Back navigates to the main menu, and Exit finishes the Activity. Unimplemented destinations are rendered as disabled buttons; their presence does not imply implementation. Settings are shared via an immutable app-level snapshot backed by Preferences DataStore schema v1. Valid FAST [1,255] and HUD [100,1000] ms settings apply on next Live entry. Hardware overview is read-only and permission-free; full validation (#23) remains separate.

## Hardware diagnostics

Hardware information is collected into immutable `DiagnosticSection`/`DiagnosticField` values with explicit reported, requested, observed and unavailable provenance. `HardwareInventory` reads Camera2/SensorManager/Build metadata without opening camera or subscribing to IMU on screen entry. Scalar-only `CameraObservationRecorder` records bounded ImageProxy geometry, timing and analysis durations from Live's existing analyzer; the active camera ID comes from bound CameraX Camera2Interop. `ImuValidationSession` subscribes only after an explicit action and unregisters at stop, onDispose or ON_STOP; its full callback timestamps are independent from the 10 Hz teaching HUD. `HardwareJson` exports schema v1 locally through SAF. Unknown Camera2 timestamp source blocks cross-camera/IMU clock comparisons; REALTIME does not imply synchronization calibration.

## Decisions

- 2026-11-04 — Single app module: simple first-milestone foundation rather than premature multi-module decomposition.
- 2026-11-04 — CameraX ImageAnalysis and OpenCV FAST: transparent classroom demonstration rather than native CV.
- 2026-11-04 — Teaching intrinsics defaults: no calibrated hardware profile at startup.
- 2026-11-04 — Shared UseCaseGroup/ViewPort with crop-aware projection: prevents misaligned FAST overlay across supported rotations/crops.
- 2026-11-04 — Lifecycle-managed IMU, SENSOR_DELAY_GAME and rate-limited HUD: minimize power and UI workload, while keeping per-sensor timestamps.
- 2026-11-04 — GitHub Actions assemble/unit/lint/ktlint gates: validation should fail before an uncompilable commit is merged.
- 2026-11-05 — Typed Compose navigation without a new dependency: minimize M1 changes and enforce disabled future modes in both UI and route guard.
