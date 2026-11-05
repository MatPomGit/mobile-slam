# Active context — M2 calibration foundation (#9, 2026-11-05)

## Goal
PR #31 pyramidal KLT has merged into main at 5f306317 with successful CI. Begin Issue #9 with a safe, independent calibration foundation before essential matrix initialization (#10). Do not substitute teaching intrinsics for measured hardware calibration.

## Scope and ordered plan
1. Define immutable, validated calibrated camera metadata in calib, with explicit camera ID, reference dimensions, crop, distortion model, coefficients, provenance, RMS reprojection error and optional focus distance.
2. Implement pure geometric transforms of K for a compatible full-frame analysis resolution and crop; reject aspect-ratio mismatch, invalid calibration or camera-ID mismatch.
3. Provide normalized point undistortion for Brown-Conrady radial/tangential coefficients, with bounded iteration and failure for non-convergence. Do not claim that this alone validates a physical profile.
4. Add deterministic JVM tests for scaling, crop offsets, invalid provenance, projection and undistortion; update README and Memory Bank.
5. Open PR, check exact-head Android CI; leave chessboard acquisition, local SAF import/export and real-device reprojection acceptance for the next #9 increment.

## Invariants / risk
- Existing Live FAST/KLT path and its teaching-only intrinsics display stay unchanged. No new camera permissions, native image queues, dependencies or persistence schema changes.
- A calibrated profile is explicitly measured/imported; an estimate from Camera2 or default640x480 cannot be accepted as measured.
- CameraX image coordinates remain unrotated full-frame pixels. Crop is an explicit affine shift; rotation remains handled separately by the existing preview mapper.
- Geometry helpers return null on incompatible dimensions or invalid coordinates; no fabricated metric scale.
- This PR does not enable pose estimation, PnP or camera/IMU fusion. Hardware validation #4 and full calibration #9 remain open.

## Acceptance and status
Pure Kotlin calibration profile validation, deterministic geometry and tests committed. CI exact head is required before merging. No device calibration measurements available yet.
