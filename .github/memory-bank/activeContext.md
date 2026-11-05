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

## Additional physical-device export (2026-11-05)
- User supplied schema-v1 JSON at 2026-11-05T16:31:21Z from Samsung SM-S938B / app 0.1.0: active CameraX 0, ImageProxy 640x480 at 90 degrees, cropRect (25,0,616,480), 1261 recorded callbacks, timestamp-derived 30.013 FPS, processing p50 2.088 ms/p95 3.382 ms.
- Explicit IMU diagnostic: 4.936 s, 582 accel callbacks (118.725 Hz), 233 gyro callbacks (47.490 Hz); no nonmonotonic timestamps. Timestamp p95 jitter exported as 0.000 ms needs precision/measurement scrutiny.
- Camera source REALTIME permits clock-domain comparison but does not establish alignment. The last camera sample precedes export elapsedRealtime by 30.701 s and cannot be paired with latest IMU samples to infer offset.
- Exact APK SHA and all six operator validation checklist items remain unverified. Report appended; CI for updated PR head should pass before merge.
