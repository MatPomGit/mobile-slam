# Active context — CameraX bind recovery (2026-11-21, stage 04/12)

## Baseline and goal
Main HEAD: `6cb44c4a88c15fa5fd8d42d9e174516f54360c65` (merged PR #42). Current Android CI PASS (run #42836309244); physical acceptance #4 remains open. Review of `MainActivity.kt` identified a stale use-case reference after a failed rebind, and a missing bounded retry when `bindToLifecycle` throws.

## Approved scope
- `MainActivity.kt`: clear detached use-case references before rebind, never skip a failed bind, apply bounded retry for transient bind exceptions; cancel callbacks on disposal and avoid changing CameraX camera selection or calibration logic.
- `frontend/CameraBindRetryBudget.kt` and JVM test: deterministic bounded retry and reset behavior.
- README plus this Memory Bank/progress: document changes and physical acceptance limit.
- No change to permissions, dependencies, geometry, pose, background capture or fusion.

## Plan and acceptance
1. Preserve original branch baseline and read all five Memory Bank files.
2. Introduce small testable retry budget and guard against stale binding state after unbind/failure.
3. Add JVM regression tests for limit/reset and disposal guard.
4. Run exact-head Android CI on branch/PR: assembleDebug, testDebugUnitTest, lintDebug, ktlintCheck, assembleDebugAndroidTest.
5. Review diff and document outcomes; do not merge without green exact-head gates.

## Risks and evidence
Repeated failures may represent missing permission or missing hardware: retries are strictly bounded; the UI retains a Polish error. Rebind after navigation, permission revocation, rotation and sustained session still require Samsung SM-S938B or equivalent physical-device acceptance. No hardware PASS is inferred from CI.

## Status
IN_PROGRESS; scoped code, JVM retry-budget tests, README and progress notes committed on feature branch. Exact-head CI and physical device acceptance pending.
