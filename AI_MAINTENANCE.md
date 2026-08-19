# AI Maintenance Record

This file records implementation reality. The authoritative requirements and permanent invariants are in `PROJECT_CONTEXT_AND_ROADMAP.md`.

## Identity and repository

- Application ID / Kotlin namespace: `com.admin.mymusicplayer` (permanent)
- Gradle layout: one `:app` Android application module
- Version: `0.1.0`, `versionCode = 1`
- Git tags: annotated `vMAJOR.MINOR.PATCH`; `v0.1.0` is created only after every v0.1 release requirement passes
- Forward rollback: build the last-known-good tag with a higher `versionCode`; never change application ID, signing identity, or data compatibility
- Roadmap lock SHA-256: `304FD58BF93FE7DF57FCD09C0BE5123DB5BD374455FABEB8CCB4B942A8F97584`

## Verified toolchain and pinned dependency baseline

Verified 2026-08-19 against primary Android/Kotlin/GitHub release documentation:

- Android Gradle Plugin `9.2.1`; Gradle `9.4.1`; JDK `17`
- Kotlin/Compose compiler plugin `2.4.10`; AGP built-in Kotlin enabled; KSP `2.3.10` (KSP2)
- compileSdk / targetSdk `37`; minSdk `23` pending physical-device API confirmation
- Compose BOM `2026.06.00`; Activity Compose `1.13.0`; Navigation Compose `2.9.8`
- Lifecycle/ViewModel `2.11.0`
- Room `2.8.4`; database schema version planned as `1`; schema export directory `app/schemas`
- Media3 `1.10.1`
- NewPipe Extractor `v0.26.2` through JitPack
- Kotlin serialization JSON version will be pinned in the Gradle catalog and recorded here once the first build resolves
- Test versions will be pinned in the Gradle catalog and recorded here once the first build resolves

Primary version references:

- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://kotlinlang.org/docs/releases.html
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/media3
- https://kotlinlang.org/docs/ksp-quickstart.html
- https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.2

## Environment

- Host: Windows; Android Studio found at `C:\Program Files\Android\Android Studio`
- SDK: `C:\Users\Admin\AppData\Local\Android\Sdk`
- Installed platform: `android-37.0`; Build Tools `36.0.0`
- `adb` is installed under the SDK but not initially on `PATH`; invoke `C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Target: Samsung Galaxy A36; Android/API, model build, and device acceptance status are not yet verified because no authorized device has been queried

## Package boundaries

- `app/src/main/java/com/admin/mymusicplayer/domain`: source-neutral models and verified pure shuffle/repeat/queue policies
- `app/src/main/java/com/admin/mymusicplayer/search`: source-neutral `SearchProvider`, result/page contracts, deterministic `FakeSearchProvider`; real adapter pending Milestone 5
- `app/src/main/java/com/admin/mymusicplayer/resolver`: source-neutral `AudioResolver`/`PlayableAudio` and deterministic `FakeAudioResolver`; real adapter pending Milestone 5
- `app/src/main/java/com/admin/mymusicplayer/data/fake`: shared deterministic fake playlist store used only by the Milestone 2 UI
- `.../data/database`, `.../data/dao`, `.../data/repository`: Room entities, DAOs, transactional library/session persistence
- `.../data/backup`: versioned portable JSON backup/restore and validation
- `.../data/diagnostics`: bounded local-only diagnostic events/bundles
- `.../playback`: queue manager, persistent session repository, `PlayerController`, Media3 `PlaybackService`
- `app/src/main/java/com/admin/mymusicplayer/ui`: Navigation Compose shell with Search, Playlists/detail, Queue, and Now Playing screens
- `.../ui/search`, `.../ui/playlists`, `.../ui/queue`, `.../ui/player`: explicit immutable state/event ViewModels; settings pending Milestone 6

No NewPipe type may cross into domain models, database entities, UI contracts/state, queue, shuffle, or repeat logic.

Only the application shell and domain package exist at Milestone 1; later package paths below are locked targets and must be updated to actual filenames as implemented.

## Database and source identity

- Planned schema version `1`: `Track`, `Playlist`, `PlaylistEntry`, `PlaybackSession`, `QueueEntry`
- Permanent track uniqueness: `(sourceType, sourceMediaId)`; titles are metadata, never identity
- Playlist entries have stable manual positions and exact-source duplicate prevention per playlist
- Playlist `revision` changes with structural edits
- Bulk move/copy/remove is one transaction and preserves selected source order; Undo captures a reversible snapshot
- Unreferenced Track rows are retained
- No destructive production migration is allowed; every future migration needs schema export, migration test, counts, relationships, and relevant session validation

## Queue, shuffle, and repeat

- The Room-backed active queue is a playback snapshot separate from playlists. Playlist edits never rewrite it implicitly.
- Structural queue/session changes persist immediately; playback position checkpoints approximately every five seconds and on pause, item/service transition, and available shutdown hooks.
- Restore queue order/current index/position/shuffle/repeat/repeat-once state after ordinary process death.
- If transient session/cache restoration fails, reset only transient playback state and open the permanent library.
- Shuffle is a full permutation, never random-next sampling. It preserves input and playlist order, excludes consumed items when enabled mid-cycle, honors an explicit tapped current item, and prevents equal cross-cycle boundaries for size > 1.
- Repeat is per current queue entry. Repeat Once replays exactly once; Repeat Forever loops only that item; manual navigation/selection resets the new item to Play Once; queue entries are never duplicated for repeat.

## Search and resolution

- `SearchProvider` returns source-neutral paginated results. Latest request wins by cancellation plus monotonically checked intent ID.
- `AudioResolver` returns a transient source-neutral playable description. Latest playback intent wins by cancellation plus checked intent ID.
- NewPipe calls and types remain only in concrete adapters. Permanent storage contains source identity and metadata, never extracted stream URLs.
- Search/resolution failures must terminate visibly and remain independent from app/library startup.
- Real canary and source availability are not yet verified.
- Automation stops on DRM, authentication, bot/access-control, age/region, private/members-only, or anti-copying barriers; no circumvention is implemented.

## Playback and session

- Media3 `ExoPlayer` + `MediaSession` live in `MediaSessionService`; UI connects through a controller boundary.
- Required state model: `IDLE`, `RESOLVING`, `BUFFERING`, `PLAYING`, `PAUSED`, `FAILED`.
- Cache: Media3 `SimpleCache`, bounded LRU target `512 MiB`; Clear Cache is exposed in settings; cache failure cannot block database/library startup.
- Current item is resolved and only the immediate next item is pre-resolved.
- Transient failure has a bounded retry then one fresh resolve/resume attempt. Unavailable media remains stored, is visibly marked, and can be skipped without an infinite loop.
- Audio focus uses Media3 audio attributes/handling. `AUDIO_BECOMING_NOISY` pauses playback to prevent speaker blast.

## Backup and diagnostics

- Portable backup format version `1`: JSON containing `formatVersion`, creation timestamp, tracks, playlists, playlist entries, and minimal settings; transient playback session is excluded initially.
- Full parse/version/uniqueness/relationship/count validation occurs before mutation; restore is transactional.
- Android Storage Access Framework is used for manual backup/restore/location; successful automatic backups retain roughly ten rolling copies where the selected document-tree permissions allow it.
- Diagnostics are bounded and local only. Bundle includes app/version, Git commit, device/API, NewPipe/Media3/database versions, recent events/errors, and last canary result.

## Commands

Run from repository root in PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug assembleRelease
```

Sandbox-only note: this Codex workspace redirects `GRADLE_USER_HOME` and debug signing state into ignored project-local directories because the sandbox cannot write the normal user homes. Normal local/Android Studio use should run the wrapper commands unchanged.

Device checks (when connected and authorized):

```powershell
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell getprop ro.product.model
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell getprop ro.build.version.sdk
```

Canary command: not available until the isolated source adapter test is implemented. A healthy canary must exit without invoking a build or repair. Full network canary is manual/on-demand until v0.1 is verified; post-v0.1 scheduling is intentionally deferred.

## Signing and release

- Release signing identity is not generated yet. Before device release, generate one permanent private JKS outside Git, keep a primary and separate offline backup, and provide credentials only to the local deterministic signing step.
- Never commit keystores, passwords, generated local signing properties, or diagnostic/user data.
- Do not tag or claim `v0.1.0` until the signed APK installs and every roadmap release condition, including update-over-old-data and clean-restore tests, passes on the target phone.
- Release artifact must expose the source Git commit through BuildConfig/About/diagnostics.

## Known issues / blockers

- Milestone 1 verified 2026-08-19: `testDebugUnitTest` passed 20 tests with 0 failures/errors; `assembleDebug` produced `app/build/outputs/apk/debug/app-debug.apk`. The APK is only a bootstrap shell until later milestones.
- Milestone 2 verified 2026-08-19: deterministic four-screen Compose UI and playlist detail flow compile; rapid stale-search replacement, finite failure/retry, fake transactional order/duplicate behavior, and player reducer tests bring the unit total to 25/25. UI/device interaction remains pending physical-device verification.
- Physical Samsung Galaxy A36 connection, API level, install, background playback, media controls, noisy-device behavior, update survival, and clean restore are unverified.
- Network dependency resolution, NewPipe canary behavior, and legal/technical availability of individual public sources are unverified.
- Release signing identity requires user-controlled secret backup before a release can be called complete.
- No remote repository is configured or published.

## Last compatibility repair

- None. Initial implementation is in progress.
