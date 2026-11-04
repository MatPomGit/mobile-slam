# Active context

## Current Plan

- Goal: bootstrap the repository and deliver the first teaching slice: CameraX grayscale analysis, IMU sampling, teaching intrinsics, and a Polish FAST feature overlay.
- Files: `AGENTS.md`, `.github/memory-bank/*`, Android Gradle project and wrapper files, `app/src/main/**`, `.gitignore`, and `README.md`.
- Steps: (1) create guidance and Memory Bank with this plan; (2) add the Android app foundation; (3) document assumptions and build/use steps; (4) validate, update Memory Bank, and report.
- Risks: the repository started empty; device-specific preview alignment and intrinsic values are approximate; task-runner networking and Android SDK availability may block a full build.
- Test strategy: run `:app:assembleDebug` and `:app:testDebugUnitTest`, then perform static/whitespace checks. Report environment blockers without claiming an unverified build.
- Acceptance: required guidance and Memory Bank are populated; the app requests camera access, analyzes grayscale frames with OpenCV FAST, samples available gyro/accelerometer data with nanosecond timestamps, and displays features and sample intrinsics in Polish; README documents setup and limitations.

## Current State

- Added the Android app module, Gradle 8.10.2 wrapper, CameraX preview/ImageAnalysis, FAST keypoint extraction, sensor sampling, intrinsics defaults, and Compose HUD/overlay.
- Added README build instructions, architecture diagram, classroom usage, and assumptions.
- `git diff --check` passes. Assemble and unit-test tasks stop during Android Gradle Plugin resolution; DNS cannot resolve `dl.google.com`, and the Android SDK is absent from the task environment.
- CodeQL reported that no changed languages were analyzable, so no CodeQL analysis ran.
- No local unit-test infrastructure existed before the Android project was added; algorithm tests are planned for later milestones.

## Assumptions

- This delivery implements milestone 0 and the milestone-1 foundation; tracking, initialization, mapping, replay, and advanced visualization remain future work.
- Rear-camera capture is required, while either inertial sensor may be absent without preventing camera preview.
- Intrinsics are educational estimates for a nominal 640×480 stream and must be replaced with calibration values for quantitative use.
