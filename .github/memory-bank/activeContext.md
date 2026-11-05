# Active context

## Current Plan (2026-11-05: Main menu, Issue #21)
- Goal: replace immediate live-camera startup with a Polish, accessible main menu and capability-gated navigation; preserve the current live FAST/IMU demo and avoid requesting camera permission on launch.
- Scope: `MainActivity.kt` routing, `ui/AppDestination.kt`, `ui/MainMenuScreen.kt`, `ui/SecondaryScreens.kt`, narrow build/test setup, README, tests and Memory Bank. Do **not** implement persistent settings or hardware diagnostics (#22/#23), nor new SLAM algorithms.
- Steps: (1) define typed routes and their explicit enabled states; (2) implement responsive menu with a replaceable logo slot and all future modes disabled; (3) route to live camera with permission requested only on entry, show Settings overview and About, handle Back and Finish; (4) add pure navigation tests and Android UI smoke coverage; (5) run Android CI, correct regressions, update README and Memory Bank.
- Risks: camera lifecycle and permission behavior across navigation/rotation require physical-device confirmation; Android UI instrumentation requires an emulator/device and cannot be claimed from JVM CI alone.
- Tests: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebugAndroidTest` in GitHub Actions (if toolchain supports it), plus manual phone checks for menu/live/menu, permission denied, Back and Exit.
- Acceptance: main menu is shown without camera/IMU acquisition, live remains functional, all planned modes visibly disabled until implemented, Settings/About/Exit navigation is functional, programmatic route gating is tested, CI passes, no untested hardware claims.

## Current State
- Feature branch now contains typed route gating, adaptive main menu with replaceable logo slot, Live navigation, read-only Settings overview, About, Exit, JVM route tests and Android instrumentation smoke-test source.
- Camera permission dialog remains only in the Live route; CI passed on code SHA 2c847644, but Android instrumentation tests were not run and physical-device acceptance is outstanding.
- Main at task start: `8f96a7cdbdc26af6fd837d103cf23281d4c12026`; merged PR #2 stabilizes milestone-1 camera/IMU and CI.
- Existing UI boots directly to camera and only offers teaching-only intrinsics, per-frame FAST detections and an IMU HUD.
- Physical device acceptance (#4) and persistent Settings (#22)/diagnostics (#23) remain pending.

## Next Step
- PR #24 implements #21. CI #42017116559 passed on code SHA 2c847644 (assembleDebug, JVM tests, lint, ktlint, AndroidTest APK assembly). Review PR and perform hardware UI acceptance (#4); keep persistent Settings and diagnostics for #22/#23.

## Assumptions
- Camera intrinsics are uncalibrated educational defaults.
- Camera and IMU clocks are not validated for sensor fusion.
- The HUD is rate-limited to ~10 Hz; it is not a full-rate IMU recorder.
