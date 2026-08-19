# My Music Player

A private, single-user Android music player for a Samsung Galaxy A36. It is a native Kotlin/Jetpack Compose app with local Room storage, a persistent queue, exact permutation shuffle and three-state per-item repeat, Media3 background playback, portable JSON backup, and isolated YouTube search/audio resolution through NewPipe Extractor.

The authoritative product and engineering contract is [`PROJECT_CONTEXT_AND_ROADMAP.md`](PROJECT_CONTEXT_AND_ROADMAP.md). Future coding agents must also read [`AGENTS.md`](AGENTS.md) and [`AI_MAINTENANCE.md`](AI_MAINTENANCE.md) before making changes.

## Product boundaries

- Private personal APK; no backend, accounts, analytics, telemetry, advertisements, or cloud service.
- One Android application module with permanent application ID `com.admin.mymusicplayer`.
- Public source access is best-effort and may break when upstream behavior changes. Local library access must remain available offline and through source failures.
- The app does not attempt to bypass DRM, authentication, access controls, age/region restrictions, private content, or anti-copying mechanisms.
- NewPipe Extractor is GPL-3.0 licensed. Review licensing obligations before distributing the app to anyone else.

## Toolchain

- Android Studio with JDK 17
- Android SDK platform API 37 and Build Tools 36.0.0
- Gradle wrapper (pinned in the repository)
- Git

## Build and test

From PowerShell in the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

The connected test requires an authorized Android device or emulator. Release, canary, backup, schema, signing, and device commands are maintained in `AI_MAINTENANCE.md` as they become available.

## Development status

v0.1 is under implementation. Do not treat an APK as release-ready until all roadmap release conditions are verified on the target device.

