# Active context — calibration and KLT acceptance (2026-11-05)

## Goal and baseline
PR #32 retains the measured-calibration foundation and now includes `main` at `07c5f62ac280f24cb2a9b0787d8c96d4972db7a6`, including PR #33's Live subtitle layout fix and KLT recording report.

## Next steps
1. Run exact-head Android CI when the merged branch is published.
2. Retest the Live subtitle, rotations/crop mapping, camera/IMU lifecycle and sustained operation on a physical device; record the exact APK SHA.
3. Continue #9 with calibration capture/profile persistence and measured-device validation.

## Risks and status
- The screen recording supports visible FAST/KLT behavior, not quantitative tracking accuracy or completed hardware acceptance.
- The merge conflicts were limited to Memory Bank documentation; code from `main` merged without conflicts.
- Local Gradle validation could not resolve Android Gradle Plugin 8.8.2, including after one dependency-refresh retry. The merged UI code was source-inspected; whitespace/conflict-marker checks passed. Exact-head hosted validation remains pending.
