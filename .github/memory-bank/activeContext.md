# Active context — device-test hardening (2026-11-05)

## Goal
Use first physical-device evidence (Samsung SM-S938B, Android 17, 20-second Live recording and hardware inventory screenshots) to stabilize Milestone 1. Do not misclassify visual evidence as completed hardware acceptance.

## Scope and plan
1. Preserve the original read-only hardware report fields and permission-free diagnostics entry, but make the long Camera2/sensor inventory navigable with collapsible sections and accessible expansion labels.
2. Revalidate CAMERA permission on each Live lifecycle ON_RESUME; when permission was revoked outside the app, dispose the camera pipeline and offer an explicit request action. Keep the first-entry camera permission flow.
3. Add a dated physical-device evidence report with observed, unverified and pending tests. Update README and progress.
4. Run available local verification; require Android CI assembleDebug, unit tests, lint, ktlint and AndroidTest APK compilation on the exact PR head.

## Risks and acceptance
- No new dependency, permissions, image recording, networking, or SLAM-stage claims.
- The UI must continue to display all provenance-labelled hardware fields; collapsing must not suppress JSON export data.
- Permission revocation must stop the Live camera path promptly on resume, not assume the stale Compose state is valid.
- Observed FAST detections must not be labelled tracks/pose/map. A short screen recording cannot verify camera alignment, clock synchronization or long-session stability.
- Close hardware acceptance #4 only after repeatable on-device tests (grant/deny/revoke, orientations, sensor lifecycle, 5–10 minutes, timestamp-domain comparison) and a JSON report.

## Implementation status
- Added Live CAMERA permission recheck at ON_START and ON_RESUME.
- Made hardware inventory sections expandable, defaulting to the first device section open.
- Added an Android UI test for expansion/collapse and corrected the hardware screen title assertion.
- Documented partial real-phone evidence in `docs/device-tests/sm-s938b-2026-11-05.md`.
- Local Android build unavailable (no Android SDK/Gradle dependency cache); rely on the PR's exact-head CI before merge. Physical lifecycle tests and exported diagnostics remain pending in #4.
