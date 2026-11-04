# Progress

## Milestones

- [x] Milestone 0 — Agent instructions and complete Memory Bank bootstrap.
- [ ] Milestone 1 — CameraX + IMU, intrinsics and keypoint overlay. Android CI verified; physical-device acceptance remains.
- [ ] Milestone 2 — KLT tracking, two-view initialization and point-cloud viewer.
- [ ] Milestone 3 — PnP tracking, keyframes, map growth, trajectory and charts.
- [ ] Milestone 4 — Step-by-step mode, session recording/replay and Settings.
- [ ] Milestone 5 — Relocalization, loop closure, optional ARCore comparison, tests and README completion.

## Known issues

- Physical-device build/run and camera overlay alignment under different rotations/resolutions remain unverified.
- Default intrinsics are uncalibrated; sensor clocks are not synchronized with frames.
- Current FAST detections are independent per-frame features, not pose or map estimates.

## Changelog

- 2026-11-04 — Initial project guidance, memory bank, CameraX/FAST/IMU HUD and intrinsics.
- 2026-11-04 — First build verification blocked by missing Android SDK / Google Maven in task runner.
- 2026-11-04 — Stabilization branch fixes Kotlin blockers, lifecycle/cropping, OpenCV initialization and Y-plane copies; adds sampling gate, JVM tests and Android CI. Assemble, JVM tests, Android lint and ktlint passed in GitHub Actions #41813784899.
