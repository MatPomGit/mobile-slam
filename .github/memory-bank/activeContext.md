# Active context — KLT physical-device observation and Live header regression (2026-11-05)

## Baseline
- PR #31 was merged to main at 5f3063178129e462331aaba876e6e1aa9981c030; main CI #42095053549 succeeded.
- The project owner supplied a 13.779 s (1080×2340 px) Android screen recording on 2026-11-05, with visible FAST/KLT overlays and movement in an indoor scene. Installed APK commit SHA was not independently established. This is video evidence of the current KLT UI, not an executed instrumentation test.
- Earlier Samsung SM-S938B inventory (Android 17) is documented separately; physical device identity cannot be independently verified from the new recording alone.

## Goal and ordered plan
1. Record the new KLT screen-recording observations in `docs/device-tests` without committing raw media, overstating pose/accuracy, or claiming that CI-executed Android instrumentation occurred.
2. Fix the clipped KLT subtitle in `MainActivity` by shortening it and enforcing a one-line responsive layout. Do not change CameraX, OpenCV, trackers or IMU.
3. Update README/progress, add reproducible on-device acceptance steps, review UI accessibility/wrapping.
4. Run exact-head Android CI (assembleDebug, JVM unit tests, lint, ktlint, AndroidTest APK compilation); merge only on green CI. Follow-up operator screen recording checks this clipping fix on the phone.

## Risks and acceptance
- Compose header must fit the available width (ellipsize on narrow devices) and remain separate from PreviewView; the new short caption does not imply visual odometry/3D.
- Do not infer numeric camera FPS from a 60 fps screen recording, and do not identify LK optical-flow accuracy without ground truth.
- Observation of active tracks does not prove every ID correspondence is correct. Existing forward–backward filter remains unmodified.
- Tests must remain green; no permissions/dependencies or architectural changes. Hardware acceptance #4 remains open.

## Work performed
- On-screen 13.779 s KLT recording reviewed. Example counters: 94 tracked/0 new/15 rejected (~0.4 s), 58/30/34 (~8.7 s), 77/5/6 (~13.4 s). Long subtitle is visibly covered by camera preview; no evidence of 3D pose or numerical flow accuracy.
- Reduced Live subtitle to `FAST → KLT · śledzenie punktów 2D`, enforced `maxLines=1` with ellipsis and inserted a clear 8 dp spacer before preview.
- Created `docs/device-tests/sm-s938b-2026-11-05-klt.md` with provenance, limitations and controlled follow-up test plan. Raw recording not committed.
- CI and real-device retest of updated APK are pending. Main CI for merged #31 was green.
