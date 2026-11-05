# Active context — Finish M2 camera calibration (#9), 2026-11-05

## Baseline and goal
PR #32 includes base main 07c5f62 (merged PR #33 KLT subtitle and device evidence), as confirmed by Git compare (ahead, behind=0). GitHub's prior "dirty" mergeability snapshot is stale; a new exact-head check is required. Existing PR #32 provides measured-only intrinsics, model validation and undistortion. Implement the missing chessboard workflow, local profile import/export and instrumented verification without conflating Camera2 factory metadata or demonstration values with measurement.

## Scoped ordered work
1. Preserve main UI and KLT changes. Add pure calibration dataset quality/geometry checks and tests, with explicit image dimensions, camera ID, focus/crop/provenance and reprojection RMS.
2. Implement on-device SAF photo selection (chessboard photos), sequential off-main OpenCV 4.10 corner detection/subpixel fitting and calibrateCamera. Release all native Mat resources, limit inputs and reject incompatible dimensions/insufficient diverse views/high error.
3. Add schema-v1 JSON read/write for measured profiles with strict validation, local SAF import/export, no unauthorized persistent camera access and no default fallback to quantitative pose.
4. Expose the calibration page via an enabled menu route with a clearly labelled setup/limitations. Keep CameraX/FAST/KLT Live behavior unchanged until a validated compatible profile is explicitly selected for geometry.
5. Add JVM tests and compile Android instrumentation tests for routing and calibration UI, update README and Memory Bank, verify exact-head Android CI before possible merge.

## Risks and decisions
Photo files may not have the same Camera2 pipeline crop, zoom, focus or orientation as CameraX; neither manual ID nor file provenance proves these equivalent. Do not use a profile for pose estimation until this relationship is validated. Use full-frame unrotated pixel conventions; avoid EXIF/crop ambiguity by checking normalized files and labeling unsupported workflows. Images stay on-device; only corner detections and small JSON profiles are retained temporarily. The user must capture images physically for calibration; host CI cannot do so. No extra Gradle module, no changes to ImageAnalysis queue.
