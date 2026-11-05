# Active context — CI formatting repair (#32, 2026-11-05)

## Goal
Fix the current PR's failing ktlint check without changing calibration behavior.

## Scope and result
- `app/src/main/java/dev/mobile/slam/calib/MeasuredCalibration.kt`: normalized the reported function signature to one line; no behavior changed.
- The project formatter, ktlint check, and JVM unit-test task could not start because Gradle could not resolve the existing Android Gradle Plugin.
- CI's full log directly identified the signature violations; source inspection confirms the edited declaration addresses each reported whitespace issue. Hosted checks on the published head remain pending.

## Risks and strategy
- Preserve behavior; only whitespace in a function signature changes.
- Keep the limitation explicit: the local toolchain could not validate the correction.

## Acceptance criteria / status
- CI-reported signature style violations are corrected without semantic changes.
- Only the requested style repair and this task's Memory Bank status are changed.
