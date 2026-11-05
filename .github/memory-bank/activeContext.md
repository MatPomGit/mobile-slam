# Active context — M2 pyramidal KLT tracking (Issue #8, 2026-11-05)

## Goal
Build the first actual temporal correspondence stage after PR #30 was merged to main (0814438). Preserve the M1 CameraX, FAST, IMU and device-diagnostics behavior while distinguishing per-frame detections from tracked features.

## Ordered plan (self-reviewed against systemPatterns.md)
1. Extend immutable Settings with validated KLT/FAST selection thresholds (defaults only; UI persistence remains for FAST/HUD). No new dependencies or Gradle modules.
2. Add a pure JVM-testable track manager: stable IDs and ages, crop/bounds, quality gates, spatial distribution, bounded selection, reset on geometry/time discontinuity.
3. Add a serial-thread OpenCV tracker using pyramidal LK, forward-backward consistency and bounded lifetime for previous grayscale Mat. Keep native Mats entirely on analysis executor.
4. Integrate with CameraAnalyzer frame segment IDs. Publish immutable tracking snapshots and show separate counts/overlays for newly seeded versus tracked features; no pose/map/synchronization claims.
5. Add deterministic JVM tests; validate Android assemble/unit/lint/ktlint/AndroidTest compile on exact PR head. Update README/systemPatterns/progress; link open device-acceptance tasks.

## Technical risks and acceptance
- CameraX KEEP_ONLY_LATEST stays the sole image acquisition buffer. No extra asynchronous worker and no state shared across background and main threads.
- OpenCV LK requires >=2 consecutive gray frames with consistent geometry; reset across new segment or >250 ms gap, and release old grayscale Mat exactly once.
- Forward and backward statuses and finite/error/bounds checks must all pass; no fabricated stable IDs from FAST proximity alone.
- New seeds are distributed across crop cells, capped at maximum track count and separated from tracked points.
- Frame/track coordinates are unrotated ImageProxy pixels; existing PreviewCoordinates handles overlay transformation.
- Tracked KLT points are not PnP inliers, 3D map points, or globally localized pose. Exact APK/device validation #4 remains open.
- CI is required before merge. If test/compile fails, fix within the same PR.

## Implementation status
- Implemented KLT thresholds in `frontend/Settings` (not yet persisted or editable).
- Implemented JVM-testable `TrackManager` with stable IDs/ages and bounded spatial replenishment.
- Added OpenCV pyramidal LK with forward/backward check and previous grayscale Mat ownership on analyzer worker.
- Integrated KLT in CameraAnalyzer and rendered cyan FAST seeds / green tracked points with separate counts in Live; clarified no camera pose/3D.
- JVM regression tests for track lifecycle and filtering added. Android CI and physical device testing remain pending before merge.
