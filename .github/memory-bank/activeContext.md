# Active context

## Current Plan

- Goal: stabilize milestone-1 CameraX/FAST/IMU demo after code review, with reproducible Android CI and testable geometry.
- Files: `MainActivity.kt`, camera/IMU acquisition, `viz/PreviewCoordinates.kt`, `frontend/Settings.kt`, JVM tests, Gradle, `.github/workflows/android-ci.yml`, README, and the Memory Bank.
- Steps: (1) correct compiler errors and CameraX binding; (2) fix crop/rotation projection and IMU lifecycle; (3) factor stride-aware Y-plane extraction and OpenCV initialization; (4) add JVM tests and Android CI; (5) verify CI, document residual hardware acceptance.
- Risks: CI requires Android SDK/Google Maven access; mobile device-specific camera transforms and camera/sensor timestamp alignment require hardware checks.
- Test strategy: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck` in GitHub Actions; test rotation/crop geometry, Y-plane copy and sampling cadence.
- Acceptance: compilation, JVM tests, lint and ktlint pass; no global camera unbind; preview and analysis share ViewPort; foreground-only IMU; separate sensor timestamps; user-facing documentation reflects behavior.

## Current Milestone and Task

- Milestone 1 stabilization. Camera and IMU fixes, JVM tests and CI have been added on a feature branch for PR review.
- This iteration does not implement the tracking, map initialization or mapping milestone.

## Current State

- Kotlin constructor-property and invalid Compose imports corrected.
- Camera binds use cases as a group with a shared ViewPort; only the screen's use cases are released.
- FAST overlay projects raw image coordinates through the CameraX crop and rotation into the displayed view.
- OpenCV is initialized once per analyzer; luma copying respects row padding/pixel stride.
- IMU listener lifetime follows ON_START / ON_STOP; HUD cadence is limited to 100 ms and sensor timestamps are separate.
- JVM geometry, pixel-copy and sampling tests added, alongside Android CI (assemble, unit tests, lint and ktlint).
- CI execution and physical-device acceptance remain to be confirmed.

## Next Step

- Inspect GitHub Actions results for the PR and fix any build/lint/test failures.
- Verify overlay registration, permission handling, sensor availability and lifecycle transitions on a physical Android device.

## Assumptions

- Camera intrinsics are educational defaults, not calibrated parameters.
- Shared CameraX ViewPort with default FILL_CENTER preview is the basis of overlay registration.
- The IMU HUD is not the full-rate data path intended for future visual-inertial fusion.
