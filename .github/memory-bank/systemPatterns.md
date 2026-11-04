# System patterns

## Package structure

Use `dev.mobile.slam` as the root namespace. Keep camera acquisition and grayscale feature extraction in `sensors` / `frontend`, camera calibration in `calib`, SLAM initialization and tracking in `init` / `frontend`, map state in `mapping`, visualization in `viz`, recording in `record`, and Compose screens in `ui`.

## Data and threading

Frames carry monotonically increasing IDs and nanosecond timestamps; camera and IMU data must use a monotonic time base. The intended flow is camera capture -> tracking coroutine -> keyframe-only mapping coroutine -> render thread. Pass immutable snapshots to rendering and do not share mutable pipeline state without synchronization.

## Binding and code rules

Bind CameraX use cases to the current lifecycle owner and release/unbind them when the screen leaves composition. Deliver analyzer results as immutable values to UI state; render overlays from those values rather than accessing analyzer-owned OpenCV objects. Keep algorithm thresholds in `Settings`, UI strings in Polish, and code/comments in English. Public classes have KDoc, with math explained for vision algorithms.

## Decisions

- 2026-11-04 — Begin with a single Android application module — alternatives: a multi-module pipeline immediately; reason: milestone 1 is a small demonstrable camera/sensor foundation and module boundaries can follow when tracking is added.
- 2026-11-04 — Use CameraX `ImageAnalysis` and OpenCV FAST for visible grayscale keypoints — alternatives: custom native CV or opaque end-to-end tracking; reason: the prescribed Android API exposes the image processing stage while avoiding custom C++.
- 2026-11-04 — Keep camera intrinsics configurable with documented defaults — alternatives: assume calibrated hardware values; reason: device camera calibration is unavailable at startup and inaccurate defaults must be explicit to students.
