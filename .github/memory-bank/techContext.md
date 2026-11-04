# Technical context

- Language/build: Kotlin 2.1.20, Android Gradle Plugin 8.8.2, Gradle 8.10.2, Kotlin DSL.
- UI/vision: Compose BOM 2025.03.00, CameraX 1.4.2, OpenCV Android API 4.10.0, coroutines 1.10.1 (planned for pipeline stages).
- Android: minSdk 26, targetSdk 34; compileSdk 35.
- Device: rear camera required for live capture; accelerometer and gyroscope are sampled when present and are not required to preview frames.
- Environment: Android Studio with JDK 17 and Android SDK platform/build tools for API 35. Grant camera permission at runtime. Hardware camera testing requires a physical device or an emulator with a configured camera.
- Build/test: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`.
- Current limitation: the task runner has Java and Gradle but no Android SDK at its configured SDK path, and DNS lookup for Google's Maven repository fails; Android compilation cannot be confirmed here.
