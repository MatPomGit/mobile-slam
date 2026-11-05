# Active context — Milestone 2 frame/pipeline contracts (#7, 2026-11-05)

## Goal and prior state
Milestone 1 hardware evidence was recorded on Samsung SM-S938B (Android 17): CameraX 640×480, 30.013 Hz from timestamps, processing median 2.088 ms / p95 3.382 ms; explicit IMU callbacks measured at 118.725 Hz accel and 47.490 Hz gyro. Camera source REALTIME is not temporal alignment calibration. PR #29 has merged at 32b219e; M1 device acceptance #4 remains open.

## Ordered plan (reviewed against systemPatterns.md)
1. Define immutable metadata/geometry, stable feature/track identifiers, tracking state, pose, map point, keyframe and metrics in `frontend`. Use image-coordinate pixels and a documented world←camera pose convention, with non-metric monocular translation scale.
2. Implement strict per-session frame ordering and geometry continuity, preserving CameraX KEEP_ONLY_LATEST (no secondary unbounded image queue). Introduce a latest-only UI snapshot dispatcher to prevent Compose queue growth.
3. Integrate the ordering gate and dispatcher into `CameraAnalyzer`; retain the existing FAST detection/preview behavior and processing on its existing single worker thread; ensure stale UI callbacks are suppressed on disposal.
4. Add deterministic JVM tests for ordering, geometry resets, dispatch coalescing, closure, and tracking state transitions. Do not introduce real KLT tracking until #8.
5. Update README, system patterns, progress and this active context; verify the exact PR head via Android CI assemble/unit/lint/ktlint/AndroidTest compile. Real-device validation remains separate.

## Technical invariants and risks
- No `org.opencv.core.Mat` or mutable image arrays in cross-thread immutable DTOs; current Y-plane copy and native Mat/MatOfKeyPoint remain owned and released on analyzer thread.
- Camera timestamp in ns is treated as opaque unless source is explicitly REALTIME; never pair latest Camera and IMU samples by accident.
- The frame gate accepts strictly increasing positive ns timestamps per session and starts new sequence when image geometry changes. A session restarts with a new analyzer.
- Latest UI dispatcher holds at most one pending immutable snapshot and at most one scheduled runnable; it drops stale samples, is cancellable and never calls UI after closing.
- Frame pose and metric scale are unavailable at M1; type contracts must not imply reconstructed geometry or calibrated intrinsics.
- This PR changes foundational code while preserving screen behavior. CI compilation is required before merge; on-device tests remain open.

## Acceptance
Zero change in student-visible preview and HUD semantics; invalid timestamps do not reach vision processing; bounded snapshot delivery; deterministic unit tests; verified Android CI head; docs and Memory Bank aligned. Milestone #7 can then unblock #8 KLT.
