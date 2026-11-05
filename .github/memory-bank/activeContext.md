# Active context — M2 camera calibration (#9, 2026-11-05)

## Goal and baseline
PR #32 now contains main at 07c5f62 (PR #33 KLT subtitle fix/device evidence) and is mergeable. It extends the measured-only camera intrinsics contract with a real on-device photograph workflow and small portable JSON profiles, without silently applying calibration to KLT or pose.

## Implemented and files
- `calib/MeasuredCalibration.kt`: physically meaningful pinhole/Brown-Conrady profile with reference crop and robust dimension/ID checks.
- `calib/ChessboardCalibrator.kt`: user-selected JPEG/PNG, no EXIF reorientation, bounded sequential decoding, OpenCV corner detection + subpixel refinement, spatial diversity/coverage checks, `calibrateCamera` and RMS gate. All native Mats/bitmaps released.
- `calib/CalibrationProfileJson.kt`: schema-v1 validated import/export.
- `ui/CalibrationScreen.kt`, `ui/AppDestination.kt`, `MainActivity.kt`: menu-accessible, permission-free calibration UI with manual camera-ID and grid/square parameters, background processing and SAF profile handling. No active runtime calibration in CameraX.
- JVM tests for measured intrinsics and chessboard parameter validation; Android instrumentation JSON roundtrip and menu navigation compilation.
- README, Memory Bank and Issue #9 documenting optical equivalence restrictions.

## Validation and risks
- Host Gradle could not resolve AGP in the earlier local runner; exact-head Android CI must compile, run JVM tests, lint and ktlint before any merge. AndroidTest is compiled but not device-executed in CI.
- Physical Samsung SM-S938B acceptance remains open: capture sufficiently diverse chessboard images, measure RMS under real conditions, record exact APK SHA, check CameraX ImageProxy crop/rotation and physical camera ID/focus/zoom pipeline, test SAF import/export and correct for EXIF limitations.
- The resulting K and distortion profile must not be used in essential-matrix/PnP without matching hardware stream geometry; #10 still blocked by this acceptance.
- No additional modules, permissions, unbounded image queues or secret uploads.

## Next
Fix any exact-head CI failures in this PR, then merge only after green status. Keep #9 open for the physical measurement and optical-path acceptance step.
