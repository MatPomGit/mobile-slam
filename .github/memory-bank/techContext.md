# Technical context

- Build: Kotlin 2.1.20, Android Gradle Plugin 8.8.2, Gradle 8.10.2 (wrapper), Kotlin DSL, JDK 17.
- Checks: ktlint-gradle 12.3.0, JUnit 4.13.2, Compose Android instrumentation tests (AndroidX Test runner), Android lint and GitHub Actions (ubuntu-latest).
- UI/vision: Compose BOM 2025.03.00, CameraX 1.4.2, OpenCV Android 4.10.0, coroutines 1.10.1 (planned).
- Android: minSdk 26, targetSdk 34, compileSdk 35, build tools 35.0.0.
- Device: rear camera, optional accelerometer/gyroscope; CameraX PreviewView uses COMPATIBLE mode to draw Compose overlays above the preview.
- Build/test: `./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:ktlintCheck :app:assembleDebugAndroidTest`. AndroidTest compilation is not an executed emulator/device test. Use `./gradlew :app:ktlintFormat` only for explicit formatting fixes.
- CI: `.github/workflows/android-ci.yml` installs Android SDK platform/build tools 35, executes Gradle with `--continue` on PRs, `main` and `fix/**` pushes, and uploads lint/test reports plus an installable debug APK. Run #41813784899 passed all checks on commit fb46d380.
- Navigation: typed in-app Compose screen switch (no Navigation library), Android BuildConfig version enabled. Device acceptance includes menu→live→menu, permission timing, back/finish, orientation/crop overlay, IMU/camera lifecycle and clocks. No hardware acceptance evidence is recorded yet.
