# My Music Player

My Music Player is a private, single-user Android music player built for a Samsung Galaxy A36. It is a native Kotlin/Jetpack Compose app with a Room-backed library and playback session, Media3 background playback, exact permutation shuffle, per-item repeat modes, portable JSON backup/restore, local diagnostics, and an isolated NewPipe Extractor source adapter.

The v0.1 implementation is complete as a local release candidate, but it is not tagged or release-ready yet. Physical-device acceptance, a successful public-source audio canary, and user-controlled permanent release signing are still required. See [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) for the exact state.

## Product boundaries

- Permanent application ID and namespace: `com.admin.mymusicplayer`.
- Private personal APK; no backend, account, analytics, telemetry, advertising, or cloud service.
- The permanent library remains usable offline and when a public source fails.
- Temporary extracted stream URLs are never stored in Room, playlists, backups, or domain models.
- The app does not bypass DRM, authentication, bot/access-control challenges, age or region restrictions, private/member-only content, payment restrictions, or anti-copying mechanisms.
- NewPipe Extractor is GPL-3.0 licensed. Review its obligations before distributing this app beyond private use.

## Implemented v0.1 capabilities

- Search, direct YouTube watch/share URL handling, thumbnails, pagination, latest-request-wins cancellation, and finite retry/error states.
- Playlist create/rename/delete/filter/import, exact-source duplicate prevention, manual ordering, ordered multi-select copy/move/remove, and Undo.
- Persistent queue snapshot independent from playlists, ordered queue editing, and process-death restoration.
- Full-permutation shuffle with mid-cycle enable, reshuffle, explicit selection, and cross-cycle boundary protection.
- Play Once, Repeat Once, and Repeat Forever without duplicating queue entries.
- Media3 `MediaSessionService`, background/lock-screen/Bluetooth controls, audio focus, becoming-noisy pause, lazy current resolution, immediate-next pre-resolution, bounded retry, and a 512 MiB LRU cache.
- Versioned JSON backup/validated transactional restore through Android's Storage Access Framework, optional rolling automatic backups, and cache clearing.
- Bounded local-only diagnostics with an exportable build/device/dependency/event bundle.

## Toolchain

- JDK 17
- Android Gradle Plugin 9.2.1
- Gradle 9.4.1
- Kotlin/Compose compiler plugin 2.4.10
- Android SDK 37; minimum SDK 23

Open the repository in Android Studio or run from PowerShell in the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
```

The connected test command requires an authorized Android device or emulator. The current connected suite contains a Room schema-open/migration sentinel and a Compose launch/primary-navigation smoke test.

The guarded live source canary is deliberately separate from deterministic regression tests:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.admin.mymusicplayer.source.newpipe.NewPipeLiveIntegrationTest' `
  -DliveSourceTests=true
```

As of 2026-08-19, search succeeds but NewPipe Extractor v0.26.2 returns no playable audio-only stream for the canary. This is recorded as an upstream compatibility gate; do not add tokens, cookies, sign-in, challenge solving, or another access-control bypass.

## Release signing

`assembleRelease` remains unsigned unless all four environment variables below are present. The permanent JKS must live outside Git and have a separate offline backup.

```powershell
$env:MY_MUSIC_PLAYER_KEYSTORE_PATH = 'D:\secure\my-music-player-release.jks'
$env:MY_MUSIC_PLAYER_KEYSTORE_PASSWORD = '<secret>'
$env:MY_MUSIC_PLAYER_KEY_ALIAS = '<alias>'
$env:MY_MUSIC_PLAYER_KEY_PASSWORD = '<secret>'
.\gradlew.bat assembleRelease
```

Never commit the JKS, passwords, local signing files, exported diagnostics, backups, or user data. Do not create `v0.1.0` until the signed APK and every unchecked item in [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) pass on the target phone.

## Repository source of truth

- [`PROJECT_CONTEXT_AND_ROADMAP.md`](PROJECT_CONTEXT_AND_ROADMAP.md): authoritative product, architecture, invariant, and acceptance contract.
- [`AGENTS.md`](AGENTS.md): mandatory repository instructions for future coding agents.
- [`AI_MAINTENANCE.md`](AI_MAINTENANCE.md): exact current implementation and operational handoff.
- [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md): v0.1 evidence and remaining external gates.
