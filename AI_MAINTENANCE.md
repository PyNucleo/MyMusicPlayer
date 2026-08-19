# AI Maintenance Record

Machine-oriented implementation truth. Read `PROJECT_CONTEXT_AND_ROADMAP.md` and `AGENTS.md` first. Update this file whenever architecture, dependencies, persistence, playback, source adapters, build/release behavior, canary status, or known issues change.

## Current state — 2026-08-19

- v0.1 code is implemented and locally verified as a release candidate.
- Permanent application ID / namespace: `com.admin.mymusicplayer`.
- Version: `0.1.0`, `versionCode = 1`.
- Implementation checkpoint: `c8ca48b` (`Complete persistent playback and data safety features`).
- No `v0.1.0` tag exists. Do not create it until every item in `RELEASE_CHECKLIST.md` passes.
- Private repository: `https://github.com/PyNucleo/MyMusicPlayer`. Private debug prereleases `device-test-0.1.0-debug-0fe5538` and `device-test-0.1.0-newpipe-5da9662` remain available; each contains only `app-debug.apk`. The NewPipe prerelease tag resolves exactly to `5da96621f01351bddcb09445763200936bc322ec`; its 18,925,420-byte asset has SHA-256 `1C2E3E0B4476F5E9FEFE14F93AA63F389A04F85C78E15B327825E805A738AE46` and embeds Git SHA `5da96621f013`.
- Local deterministic suite: 45 discovered, 44 passed, 0 failed/errors, 1 skipped guarded live canary.
- Latest clean offline `lintDebug`: 0 issues, errors, or warnings. An earlier network-aware run reported 9 dependency-version availability notices only.
- `compileDebugAndroidTestKotlin`, `assembleDebug`, and unsigned `assembleRelease` pass.
- External gates: a Samsung Galaxy A36 debug-build smoke test passed for install, launch, navigation, playlist/queue interaction, and graceful visible source failure; the host public-source audio canary passes after the local NewPipe `v0.26.5` update, but the rebuilt debug APK has not been retested on-device; exact device/API/build and connected-test evidence are not recorded; no permanent user-controlled signing key/credentials exist.
- Roadmap lock SHA-256: `304FD58BF93FE7DF57FCD09C0BE5123DB5BD374455FABEB8CCB4B942A8F97584`.

## Toolchain and pinned dependencies

- JDK `17`; Android Gradle Plugin `9.2.1`; Gradle wrapper `9.4.1`.
- AGP built-in Kotlin plus Compose plugin `2.4.10`; KSP `2.3.10`.
- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 23`.
- Compose BOM `2026.06.00`; Activity Compose `1.13.0`; Navigation Compose `2.9.8`.
- Core KTX `1.18.0`; Lifecycle/ViewModel `2.11.0`; coroutines `1.10.2`.
- Room `2.8.4`; schema version `2`; exports: `app/schemas/com.admin.mymusicplayer.data.database.MusicDatabase/{1,2}.json`.
- Media3 `1.10.1`; OkHttp `5.2.1`.
- NewPipe Extractor `v0.26.5` from JitPack; Gson `2.14.0`.
- Coil Compose/network OkHttp `3.5.0`.
- JUnit `4.13.2`; Truth `1.4.5`; Robolectric `4.16.1`; AndroidX Test Core `1.7.0`, rules `1.7.0`, ext JUnit `1.3.0`, Espresso `3.7.0`.
- Lint currently reports newer Gradle/Core/Media3/coroutines/OkHttp versions. These are informational. Do not mix dependency upgrades into compatibility repair; update intentionally and rerun deterministic, live, and device acceptance suites.

Version references used for the baseline:

- `https://developer.android.com/build/releases/agp-9-2-0-release-notes`
- `https://kotlinlang.org/docs/releases.html`
- `https://developer.android.com/develop/ui/compose/bom`
- `https://developer.android.com/jetpack/androidx/releases/lifecycle`
- `https://developer.android.com/jetpack/androidx/releases/room`
- `https://developer.android.com/jetpack/androidx/releases/media3`
- `https://kotlinlang.org/docs/ksp-quickstart.html`
- `https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.5`
- `https://github.com/TeamNewPipe/NewPipeExtractor/releases/tag/v0.26.3`
- `https://github.com/TeamNewPipe/NewPipeExtractor/pull/1508`
- `https://coil-kt.github.io/coil/compose/`

## Host environment

- Windows workspace: `C:\Users\Admin\Documents\MyMusicPlayer`.
- Android Studio: `C:\Program Files\Android\Android Studio`.
- Android SDK: `C:\Users\Admin\AppData\Local\Android\Sdk`; installed platform `android-37.0`; Build Tools `36.0.0`.
- ADB: `C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe`; 2026-08-19 query returned no devices.
- Codex sandbox cannot write normal Gradle/Android user homes. It uses ignored `.gradle-user-home`, `.android-user-home`, `.tools`, and `.kotlin` paths.
- Robolectric's ignored offline SDK jar is `.tools/robolectric/android-all-instrumented-15-robolectric-13954326-i7.jar`; verified against published SHA-1 `d684f4e55d30465793a4a9e783f50266097b84df`. The Gradle task `prepareRobolectricDependencies` fetches it through Gradle only when absent.
- Android's metrics warning about `C:\Users\Admin\.android\analytics.settings` is sandbox-only and non-fatal.

## Runtime dependency graph

- `MusicPlayerApplication` creates one `AppContainer`, initializes NewPipe without network I/O, restores only transient session state, starts automatic-backup observation, and owns the app-scoped `PlaybackClient`.
- `AppContainer` constructs `MusicDatabase`, Room repositories, `RoomBackupStore`, `BackupManager`, bounded `DiagnosticLogger`, concrete NewPipe adapters, and playback queue controller.
- Compose ViewModels receive real repositories/controllers from `AppRoot`. Fake providers/repository remain only as deterministic constructor defaults for unit tests and milestone fixtures.
- Startup isolation: Room/library can open without network, source resolution, cache health, or successful playback-session restore. Corrupt transient state is cleared without destructive database recovery.

## Package map

- `domain/`: source-neutral `Track`, identity, availability, repeat/shuffle state, queue transformations, `ShufflePlanner`, `RepeatPolicy`.
- `search/`, `resolver/`, `importer/`: source-neutral interfaces/contracts and deterministic test fakes.
- `source/newpipe/`: the only concrete extractor boundary (`NewPipeRuntime`, search, audio resolution, playlist import, failure mapping).
- `data/database/`, `data/dao/`: Room schema, projections, DAO queries/transactions.
- `data/repository/`: `LibraryRepository`, `SessionRepository`, Room implementations, mappers, fake library fixture.
- `playback/`: app-scoped MediaController client, persistent queue controller, `MediaSessionService`, cache-clear request bus.
- `backup/`: portable models, validation/transactional restore store, SAF/automatic backup manager.
- `diagnostics/`: bounded local NDJSON event log and export bundle.
- `ui/search`, `ui/playlists`, `ui/queue`, `ui/player`, `ui/settings`: immutable state/event ViewModels and Compose screens.

No NewPipe type may cross into domain models, Room entities, UI state/contracts, queue, shuffle, repeat, backup, or diagnostics records. No extracted media URL may become permanent state.

## Room schema version 2

Tables:

- `tracks`: stable unique `(source_type, source_media_id)`, metadata, availability, creation time.
- `playlists`: unique name, timestamps, monotonic structural `revision`.
- `playlist_entries`: playlist/track relationship, manual `position`, uniqueness per exact source through track identity; cascade on playlist deletion.
- `playback_sessions`: singleton session row; current index/position, shuffle state/permutation/cycle, persisted comma-delimited set of actually consumed stable queue-entry IDs, repeat state/consumption, next queue-entry ID. Session updates use `@Upsert`; never `REPLACE`, which would cascade-delete queue rows.
- `queue_entries`: persistent queue snapshot with stable queue-entry ID and position; independent from playlists.

Rules:

- Structural library and session writes are transactional and immediate.
- Playlist bulk move/copy/remove preserves selected source order and returns a reversible snapshot for Undo.
- Playlist edits never rewrite an active queue.
- Unreferenced track rows are retained.
- No destructive migration fallback exists. Migration `1→2` adds `playback_sessions.consumed_queue_entry_ids`, preserves every existing entity and relationship, and conservatively seeds the migrated cycle with only the current queue entry because version 1 cannot prove any earlier consumption. Every later version requires an explicit migration, exported schema, and migration/relationship/session tests.

## Queue, shuffle, repeat, and session

- Queue is a persistent playback snapshot, not a live playlist view.
- Position checkpoints occur approximately every five seconds while playing and on pause, transition, task removal, and service teardown hooks.
- Restore queue order/current item/position/shuffle permutation/cycle/actually consumed queue-entry IDs/repeat-once consumption after ordinary process death.
- Shuffle is a complete permutation: playlist order remains untouched; eligibility is derived from persisted queue entries actually visited in the current cycle, never from their position before the current index; enabling mid-cycle keeps consumed entries plus current before the cursor and shuffles every other snapshot entry exactly once; explicit selection starts history at the selected entry; a new/explicitly reshuffled cycle resets history to its first/current entry; cross-cycle boundaries differ for size > 1.
- Play Once uses normal progression. Repeat Once loops the current Media3 item until exactly one automatic replay is consumed. Repeat Forever loops only the current item. Manual selection/next/previous resets the newly selected item to Play Once. Repeat never duplicates queue entries.

## Search, import, and source failure behavior

- `SearchViewModel` uses cancellation plus a monotonically checked request ID; stale completion cannot replace a newer request.
- Direct public watch URLs and Android `ACTION_SEND text/plain` are accepted. Search results remain source-neutral.
- Playlist import validates a public YouTube playlist URL, paginates with cancellation and a safety limit, preserves source order, and reports duplicate/inaccessible skips before a transactional create/add confirmation.
- `NewPipeAudioResolver` fetches `StreamInfo`, selects the highest-bitrate nonblank URL from audio-only streams, and returns transient URI/MIME/expiry only.
- Resolver work is lazy for current playback; only the immediate next entry is pre-resolved. `PlaybackClient.playNow` waits for the expected media ID before play so an old timeline cannot start.
- Failures are finite and visible. 429/reCAPTCHA/sign-in-not-bot map to `ANTI_BOT_CHALLENGE`; age/region/private/paid map to `ACCESS_RESTRICTED`; unavailable/network/extractor compatibility remain distinct.
- Hard stop: never add cookies, accounts, tokens, PoToken helpers, challenge solving, proxying, DRM workarounds, or other access-control circumvention.

### Current live canary

Command:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.admin.mymusicplayer.source.newpipe.NewPipeLiveIntegrationTest' `
  -DliveSourceTests=true
```

2026-08-19 fresh baseline: failed on pinned NewPipe Extractor `v0.26.2`. Public search returned results, but resolution threw `SourceFailure(EXTRACTOR_COMPATIBILITY)` caused by `IllegalStateException: No playable audio-only stream was returned`.

2026-08-19 upstream validation: passed after the smallest official update to `v0.26.5`. TeamNewPipe shipped the relevant YouTube fix in `v0.26.3` via PR #1508, using a visionOS player-client fallback to obtain ordinary adaptive formats when the former client receives SABR-only responses. The merged path uses anonymous visitor data and does not add a PoToken provider, account/authentication, challenge solving, DRM handling, or an app-side bypass. `v0.26.5` is the current official release and includes that fix. No source-adapter logic changed.

Healthy canary behavior: exits 0 after a public search result resolves to a transient HTTPS audio stream; it does not build, mutate Room, repair code, sign, or release.

### Samsung Galaxy A36 debug-build smoke test

2026-08-19 user-reported manual result on the physical Samsung Galaxy A36:

- The debug build installed and launched.
- Search, Playlists, Queue, Now Playing, and Settings opened.
- Playlist and queue interactions worked during the smoke-test scope.
- Attempted source playback produced a visible `Source Error` and did not crash the app.

This confirms the debug build's basic device launch/navigation path and finite visible source-failure behavior. It does not demonstrate successful source audio resolution or playback, and it is not signed-release acceptance. Android version/API, build fingerprint, ADB authorization, connected tests, background/media controls, persistence/recovery, backup/restore, upgrade, and the rest of the device matrix remain unverified.

## Media3 playback

- `PlaybackService` extends `MediaSessionService` and owns `ExoPlayer`, `MediaSession`, audio focus/noisy handling, and notification/lock-screen/Bluetooth transport integration.
- Full queue is represented as Media3 timeline items with stable queue-entry IDs.
- A `ResolvingDataSource` converts internal `mymusicplayer://queue/<id>` URIs to fresh transient audio URIs. Cache keys use stable source identity.
- `SimpleCache` uses `LeastRecentlyUsedCacheEvictor` capped at `512 MiB`; cache errors are ignored for upstream playback; Settings can clear cache.
- Playback error path is bounded: retry once, then discard cached resolution and resolve once fresh. No infinite unavailable-item loop.
- Android 13+ notification permission is requested at runtime; declining does not block in-app playback.

## Backup and diagnostics

- Portable backup JSON `formatVersion = 1`, application ID, creation time, source-neutral tracks, playlists/ordered entries, minimal settings. Playback session/cache/temporary URLs are excluded.
- Validation precedes mutation: format/app ID, timestamp, max bytes/counts/pages, required arrays, supported sources, unique identities/names, valid metadata/durations, and complete relationships.
- Restore transaction replaces playlists/entries, reuses/upserts track identities, clears only transient playback state, and never performs partial mutation.
- Manual backup/restore uses SAF. Optional document-tree automatic backups are debounced and retain about ten files where the provider supports document operations.
- Platform cloud/device-transfer backup is excluded; portable JSON is the deliberate recovery mechanism.
- Diagnostics retain at most 500 local events in app-private NDJSON. Export includes app/version/Git commit/device/API/NewPipe/Media3/Room provenance and recent sanitized events/errors. No telemetry or automatic upload.

## Commands

Normal local/Android Studio PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Codex sandbox equivalent prefix:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
$env:ANDROID_USER_HOME = "$PWD\.android-user-home"
& "$PWD\.tools\gradle-9.4.1\bin\gradle.bat" <tasks> --no-daemon
```

Device discovery:

```powershell
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell getprop ro.product.model
& 'C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe' shell getprop ro.build.version.sdk
```

## Verified evidence

- `testDebugUnitTest`: build success; 45 discovered, 44 passed, 0 failed/errors, 1 skipped guarded canary; 13 suites.
- Covered: source identity, queue transformations, shuffle cycles/mid-cycle/explicit-second-track eligibility/Previous/boundaries, repeat policy/reducer, rapid stale search, fake bulk ordering/Undo, Room duplicate/reorder/move/copy/remove/Undo, queue/playlist separation, consumed-history recreation, corrupt-session isolation, host-executed Room `1→2` entity/relationship/session migration, Room backup validation/round-trip restore, URL parsing.
- `lintDebug`: latest clean offline build success with 0 issues, errors, or warnings; an earlier network-aware run reported 9 newer dependency/tool version notices only.
- `compileDebugAndroidTestKotlin`: build success. Tests: Room version-1 schema-open sentinel and Compose launch/navigation smoke test.
- `assembleDebug`: build success; `app/build/outputs/apk/debug/app-debug.apk`.
- `assembleRelease`: build success without signing environment; `app/build/outputs/apk/release/app-release-unsigned.apk`.
- `connectedDebugAndroidTest`: not run; no authorized device/emulator.
- Live canary: run separately; `v0.26.2` baseline failed and `v0.26.5` passed as documented above.

## Release signing

- Build config reads signing only when all four environment variables exist: `MY_MUSIC_PLAYER_KEYSTORE_PATH`, `MY_MUSIC_PLAYER_KEYSTORE_PASSWORD`, `MY_MUSIC_PLAYER_KEY_ALIAS`, `MY_MUSIC_PLAYER_KEY_PASSWORD`.
- With none present, `assembleRelease` intentionally produces `app-release-unsigned.apk`.
- Permanent JKS must be user-controlled, stored outside Git, and copied to a separate offline backup before first release. Never expose secrets to autonomous repair jobs or store them in repository files/logs.
- The same JKS/application ID must sign every future update. Forward rollback uses known-good source with a higher version code; never change identity or destroy data compatibility.

## Remaining release gates

- Install and retest the rebuilt `v0.26.5` debug APK on the Samsung Galaxy A36; host resolution now passes, but successful device playback has not yet been observed.
- Record the Samsung Galaxy A36 Android version/API/build fingerprint and ADB authorization; the debug-build smoke test above does not replace connected-test evidence.
- Run connected tests and the remaining full manual device matrix in `RELEASE_CHECKLIST.md`: successful real search/playback, background/lock/Bluetooth/noisy/focus, long queue, process death, shuffle/repeat, complete playlist operations/Undo, backup/clean restore, update-over-old-data, and diagnostics.
- Create and offline-back up permanent user-controlled JKS; set environment credentials; build and verify signed release APK.
- Install signed APK on target; confirm embedded Git commit; only then create annotated `v0.1.0` tag.
- `origin` is the private `PyNucleo/MyMusicPlayer` repository. The existing debug prereleases are for device testing only; neither is a signed release or a last-known-good `v0.1.0` release.

## Last compatibility repair

2026-08-19 upstream dependency repair:

- Reproduced the `v0.26.2` failure: search succeeded, but resolution returned no URL-backed audio-only stream.
- Verified TeamNewPipe's official `v0.26.3` SABR/player-client compatibility fix and current official release `v0.26.5`.
- Updated only the pinned extractor dependency and reported BuildConfig/diagnostic version from `v0.26.2` to `v0.26.5`; NewPipe adapter logic is unchanged.
- Did not add authentication, cookies/tokens, a PoToken provider, challenge solving, proxying, DRM handling, or any access-control bypass.
- The same guarded live canary passes on `v0.26.5`.
- At commit `5da96621f01351bddcb09445763200936bc322ec`, the forced guarded live canary passed 1/1 with no skip, failure, or error. A subsequent clean offline run passed `testDebugUnitTest`, `lintDebug`, `compileDebugAndroidTestKotlin`, and `assembleDebug`: 39 deterministic tests discovered, 38 passed, 1 guarded live canary skipped, 0 failures/errors, and lint reported 0 issues.
- Private prerelease `device-test-0.1.0-newpipe-5da9662` is tagged at that exact commit and contains exactly one asset, the rebuilt `app-debug.apk`; GitHub's recorded digest and an independently downloaded copy both match local SHA-256 `1C2E3E0B4476F5E9FEFE14F93AA63F389A04F85C78E15B327825E805A738AE46`.
- Commit compatibility changes before rebuilding device-test artifacts; never release an APK built from a dirty tree because its embedded Git SHA would not identify the complete source.

## Last P0 shuffle repair

2026-08-19 persistent shuffle-eligibility repair:

- Root cause: `ShufflePlanner.enableMidCycle` treated `currentOrder.take(currentIndex + 1)` as consumed history. An explicit tap on Song 2 therefore falsely consumed Song 1 solely because Song 1 preceded the current index.
- `PersistentPlaybackState` now carries the stable queue-entry IDs actually visited in the current cycle. Playback transitions add the reached entry; new queues and explicit selections start with only the selected/current entry; reshuffle/new-cycle state resets consistently; queue mutations retain only history IDs still present.
- Enabling shuffle builds the consumed/current prefix from that persisted set, places the current entry last in the prefix for normal Previous behavior, and shuffles every other queue-snapshot entry exactly once. Playlist order and repeat behavior are unchanged.
- Room schema `2` and explicit migration `1→2` preserve tracks, playlists, playlist relationships, queue rows, and session fields. The migration seeds only the current queue entry as consumed, favoring a possible replay over silently skipping an unplayed track. No destructive fallback exists.
- Deterministic regressions cover the exact three-track Song 2 selection, repository recreation before shuffle, consumption/restoration after shuffle, Previous behavior, exact coverage, no duplicates, and prior mid-cycle behavior. The host migration test validates entity counts, playlist/queue relationships, session fields, migrated history, and foreign keys; the Android migration test additionally uses Room schema validation and compiles successfully.
- Final verification passed `testDebugUnitTest`, `lintDebug`, `compileDebugAndroidTestKotlin`, and `assembleDebug`: 45 tests discovered, 44 passed, 1 guarded live canary skipped, 0 failures/errors, and 0 lint issues.
