# Progress

## Milestones

- [x] Milestone 0 — Agent instructions and complete Memory Bank bootstrap.
- [ ] Milestone 1 — CameraX + IMU, intrinsics, and keypoint overlay. Foundation implemented; build verification is blocked by task-runner network/SDK availability.
- [ ] Milestone 2 — KLT tracking, two-view initialization, and point-cloud viewer.
- [ ] Milestone 3 — PnP tracking, keyframes, map growth, trajectory, and charts.
- [ ] Milestone 4 — Step-by-step mode, session recording/replay, and Settings.
- [ ] Milestone 5 — Relocalization, loop closure, optional ARCore comparison, tests, and README completion.

## Known issues

- Android compilation and device testing remain unverified: the Android SDK is absent and DNS resolution for the Google Maven repository fails in the task environment.
- Intrinsics are display/teaching defaults, not calibrated device parameters; overlay alignment is an approximate center crop.
- The current milestone detects independent FAST features; it does not track, estimate poses, or build a map.

## Changelog

- 2026-11-04 — Added project guidance, Memory Bank, Android/Gradle foundation, CameraX grayscale FAST overlay, IMU HUD, intrinsics defaults, and README.
- 2026-11-04 — Ran assemble and unit-test tasks; both were blocked during plugin resolution by unavailable repository DNS. Whitespace validation passed.
