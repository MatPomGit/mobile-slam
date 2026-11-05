# Progress

## Milestones

- [x] Milestone 0 — Agent instructions and complete Memory Bank bootstrap.
- [ ] Milestone 1 — CameraX + IMU, FAST overlay, menu/navigation, persisted Settings and hardware diagnostics implemented; physical-device acceptance #4 and final CI repair remain.
- [ ] Milestone 2 — KLT tracking, two-view initialization and point-cloud viewer.
- [ ] Milestone 3 — PnP tracking, keyframes, map growth, trajectory and charts.
- [ ] Milestone 4 — Step-by-step mode, session recording/replay and Settings.
- [ ] Milestone 5 — Relocalization, loop closure, optional ARCore comparison, tests and README completion.

## Current user-interface issues

- #21: main menu and navigation merged in PR #24; on-device acceptance remains pending.
- #22: persisted FAST/IMU Settings merged in PR #25 (CI #42031397100 PASS); device acceptance pending.
- #23: diagnostics merged in PR #26, but main CI #42048086684 FAILED on Camera2Interop lint; scoped hotfix branch adds explicit AndroidX opt-in. Physical-device qualification remains #4.

## Known issues

- Physical-device build/run and camera overlay alignment under different rotations/resolutions remain unverified.
- Default intrinsics are uncalibrated; sensor clocks are not synchronized with frames.
- Current FAST detections are independent per-frame features, not pose or map estimates.

## Changelog

- 2026-11-05 — PRs #25 and #26 merged; the #26 head and main run fail Android Lint on unacknowledged experimental Camera2Interop. Hotfix branch adds scoped androidx.annotation.OptIn; CI pending.

- 2026-11-04 — Initial project guidance, memory bank, CameraX/FAST/IMU HUD and intrinsics.
- 2026-11-04 — First build verification blocked by missing Android SDK / Google Maven in task runner.
- 2026-11-04 — Merged PR #2 at 94e76ded; main Android CI run #41824055488 passed. Physical-device evidence remains pending.
- 2026-11-05 — Implemented main menu/navigation in PR #24; assembleDebug, testDebugUnitTest, lintDebug, ktlintCheck and assembleDebugAndroidTest passed on code SHA 2c847644 (CI #42017116559). Instrumentation tests were **compiled but not executed** on a device.
