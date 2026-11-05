# Active context — Camera2Interop CI hotfix (2026-11-05)

## Goal
Restore Android CI on main after PR #25 and PR #26 were merged while hardware validation work was still in progress. The latest main run #42048086684 is red. PR #26's preceding run #42048032952 confirms an Android Lint UnsafeOptInUsageError at MainActivity.kt (Camera2CameraInfo.from).

## Scope and plan
- Branch `fix/hardware-camera2interop-optin-20261105` from current main `1a1d3d82cfb58526173f70763c43d4ee9cab087f`.
- Explicitly opt in to Camera2Interop at the smallest safe code scope; do not suppress lint globally or drop tests.
- Verify exact-head Android CI assembleDebug, JVM unit tests, lint, ktlint, AndroidTest APK compilation.
- Update README/progress if needed to note merged PRs #25/#26 and remaining physical validation (#4).

## Risks and tests
The CameraX 1.4.2 Camera2Interop API is experimental; this change acknowledges opt-in but does not add new camera behavior. Lint must pass. Real-camera hardware behavior cannot be qualified from CI alone. No model/calibration/IMU-clock claims beyond current validated source labels.

## Acceptance
Main CI blocker is resolved in the new small PR, CI passes on exact SHA. Issue #4 remains open until device evidence is reviewed.
