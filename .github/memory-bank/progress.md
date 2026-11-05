# Progress

## Milestones

- [x] Milestone 0 — Agent instructions and complete Memory Bank bootstrap.
- [ ] Milestone 1 — CameraX + IMU, intrinsics and keypoint overlay, plus main menu/navigation PR in progress (#21). Android CI was verified for merged PR #2; physical-device acceptance remains.
- [ ] Milestone 2 — KLT tracking, two-view initialization and point-cloud viewer.
- [ ] Milestone 3 — PnP tracking, keyframes, map growth, trajectory and charts.
- [ ] Milestone 4 — Step-by-step mode, session recording/replay and Settings.
- [ ] Milestone 5 — Relocalization, loop closure, optional ARCore comparison, tests and README completion.

## Current user-interface issues

- #21: startup menu, typed navigation, About, read-only Settings overview and Exit implemented in PR #24. Android CI run #42017116559 passed for code SHA 2c847644; on-device acceptance remains pending.
- #22: persistent editable Settings is not yet implemented.
- #23: hardware capabilities, timing diagnostics and export are not yet implemented.

## Known issues

- Physical-device build/run and camera overlay alignment under different rotations/resolutions remain unverified.
- Default intrinsics are uncalibrated; sensor clocks are not synchronized with frames.
- Current FAST detections are independent per-frame features, not pose or map estimates.

## Changelog

- 2026-11-04 — Initial project guidance, memory bank, CameraX/FAST/IMU HUD and intrinsics.
- 2026-11-04 — First build verification blocked by missing Android SDK / Google Maven in task runner.
- 2026-11-04 — Merged PR #2 at 94e76ded; main Android CI run #41824055488 passed. Physical-device evidence remains pending.
- 2026-11-05 — Implemented main menu/navigation in PR #24; assembleDebug, testDebugUnitTest, lintDebug, ktlintCheck and assembleDebugAndroidTest passed on code SHA 2c847644 (CI #42017116559). Instrumentation tests were **compiled but not executed** on a device.
