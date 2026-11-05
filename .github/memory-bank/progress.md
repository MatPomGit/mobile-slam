# Progress

## Milestones

- [x] Milestone 0 — Agent instructions and complete Memory Bank bootstrap.
- [ ] Milestone 1 — CameraX + IMU, FAST overlay, menu/navigation, persisted Settings and hardware diagnostics implemented; physical-device acceptance #4 remains (main CI repair has passed).
- [ ] Milestone 2 — KLT tracking, two-view initialization and point-cloud viewer.
- [ ] Milestone 3 — PnP tracking, keyframes, map growth, trajectory and charts.
- [ ] Milestone 4 — Step-by-step mode, session recording/replay and Settings.
- [ ] Milestone 5 — Relocalization, loop closure, optional ARCore comparison, tests and README completion.

## Current user-interface issues

- #21: main menu and navigation merged in PR #24; on-device acceptance remains pending.
- #22: persisted FAST/IMU Settings merged in PR #25 (CI #42031397100 PASS); device acceptance pending.
- #23: diagnostics merged in PR #26, and Camera2Interop opt-in fix merged via PR #28; main CI #42063929598 passed. Physical-device qualification remains #4.

## Known issues

- On Samsung SM-S938B (2026-11-05), preview/FAST/IMU HUD were observed and schema-v1 hardware JSON was exported: CameraX 640×480, 30.013 FPS timestamp-derived, analysis p50 2.088 ms/p95 3.382 ms; diagnostic IMU 4.936 s, accelerometer 118.725 Hz, gyroscope 47.490 Hz; camera timestamp source REALTIME but temporal alignment not verified. Permission recovery, controlled overlay alignment, lifecycle and long-run stability remain unverified.
- Default intrinsics are uncalibrated; sensor clocks are not synchronized with frames.
- M2 KLT uses FAST seeds and temporal image-space optical flow; it does not estimate pose or map points. The 20.43-second recording and static screenshots predate KLT and are not an M1 acceptance substitute (see docs/device-tests/sm-s938b-2026-11-05.md).

## Changelog

- 2026-11-05 — PR #32 CI formatting repair: ktlint reported only the `MeasuredCalibration.forAnalysis` signature; corrected its formatting. Local Gradle validation could not resolve the existing Android Gradle Plugin, so exact-head hosted CI remains pending.

- 2026-11-05 — PR #31 KLT tracking merged at 5f306317 after green main CI #42095053549. Issue #8 remains open pending physical-device KLT acceptance.
- 2026-11-05 — Issue #9 calibration foundation branch adds measured-only metadata, strict uniform-resize/crop compatibility and Brown-Conrady normalized undistortion with deterministic JVM tests. Chessboard acquisition, profile import/export and device validation remain pending.


- 2026-11-05 — PR #30 frame/pipeline contracts merged after CI (main 08144385). Issue #8 implementation branch adds pyramidal KLT, forward/backward quality gates, stable track IDs, grid bounded selection, overlays, and JVM tests; exact-head CI/device acceptance pending.

- 2026-11-05 — PR #29 merged on 32b219e after green exact-head CI; #4 hardware acceptance remains open.
- 2026-11-05 — M2 #7 branch introduces immutable contracts, frame timestamp/order gate, bounded latest-only UI dispatch, analyzer disposal and JVM regression tests. Awaiting exact-head PR CI.

- 2026-11-05 — Measured SM-S938B hardware JSON added to device-tests report; six checklist cases remain NOT_TESTED. PR #29 prior head CI green; new documentation head requires its own validation.

- 2026-11-05 — First partial physical-device evidence documented; Live permission checks added for resume, hardware inventory sections made collapsible; CI verification and physical acceptance #4 still pending.
- 2026-11-05 — PR #28 merged; main CI #42063929598 passed on 83fcac79.

- 2026-11-05 — PRs #25 and #26 merged; the #26 head and main run fail Android Lint on unacknowledged experimental Camera2Interop. Hotfix branch adds scoped androidx.annotation.OptIn; CI pending.

- 2026-11-04 — Initial project guidance, memory bank, CameraX/FAST/IMU HUD and intrinsics.
- 2026-11-04 — First build verification blocked by missing Android SDK / Google Maven in task runner.
- 2026-11-04 — Merged PR #2 at 94e76ded; main Android CI run #41824055488 passed. Physical-device evidence remains pending.
- 2026-11-05 — Implemented main menu/navigation in PR #24; assembleDebug, testDebugUnitTest, lintDebug, ktlintCheck and assembleDebugAndroidTest passed on code SHA 2c847644 (CI #42017116559). Instrumentation tests were **compiled but not executed** on a device.
