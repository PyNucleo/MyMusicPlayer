# v0.1 Release Checklist

Status: local release candidate; not signed, tagged, or device-approved.

Recorded 2026-08-19. A checked item has direct local evidence. Device/source/signing items remain unchecked until executed in the stated environment. Do not create `v0.1.0` while any required item is unchecked.

## Deterministic local gates

- [x] Permanent application ID is `com.admin.mymusicplayer`.
- [x] Roadmap/source-of-truth files exist and the copied roadmap SHA-256 matches `304FD58BF93FE7DF57FCD09C0BE5123DB5BD374455FABEB8CCB4B942A8F97584`.
- [x] `testDebugUnitTest`: 39 discovered, 38 passed, 0 failed/errors, one intentionally skipped live canary.
- [x] Shuffle permutation, mid-cycle enable, explicit selection, reshuffle, and cross-cycle boundary tests pass.
- [x] Repeat Once/Forever/manual-navigation policy and player reducer tests pass.
- [x] Room exact-source duplicate, ordering, transactional copy/move/remove/reorder, Undo, queue separation, session recreation, and corrupt-transient-state isolation tests pass.
- [x] Backup validation and transactional export/restore tests pass.
- [x] Search latest-request-wins and finite failure/retry tests pass.
- [x] `lintDebug`: 0 errors; nine pinned-dependency/tool update notices.
- [x] `compileDebugAndroidTestKotlin`: Room schema and Compose launch/navigation tests compile.
- [x] `assembleDebug`: debug APK produced.
- [x] `assembleRelease`: unsigned release APK produced when signing environment is absent.
- [x] Git commit is exposed in BuildConfig, Settings, and diagnostic export.
- [x] `AI_MAINTENANCE.md` matches implementation and known gates.

## Public-source gate

- [x] Guarded canary command exists and is separate from deterministic tests.
- [x] Public search metadata succeeded during the 2026-08-19 canary.
- [ ] Normal intended public content resolves to a transient playable audio stream. Current failure: NewPipe Extractor v0.26.2 returns no audio-only URL streams.
- [ ] Real target-device search, watch/share URL, pagination, Play, Play Next, Queue, Add to Playlist, and public playlist import pass.
- [x] DRM/authentication/bot/access-control/age/region/private/paid/anti-copying hard-stop behavior is explicit; no bypass is implemented.

## Samsung Galaxy A36 acceptance

Record before testing:

- Model: pending
- Android version / API: pending
- Build fingerprint: pending
- ADB authorization: pending

Required tests:

- [ ] Clean install signed release APK and launch independently.
- [ ] Notification permission accept and decline paths behave safely.
- [ ] Playlist CRUD/filter/manual reorder works.
- [ ] Multi-select/select-visible/copy/move/remove/Undo preserves order.
- [ ] Editing a playlist while its queue is active does not mutate that queue.
- [ ] Queue play-next/add/remove/reorder/clear-upcoming works.
- [ ] Large-playlist shuffle includes every entry exactly once with no duplicate and correct boundary behavior.
- [ ] Enable shuffle halfway through a queue; consumed items do not reappear in the current cycle.
- [ ] Repeat Once replays exactly once; Repeat Forever loops only current; manual Next exits repeat.
- [ ] Audio playback, seeking, pause/resume, and metadata/artwork work.
- [ ] Background playback survives closing UI and locking phone.
- [ ] Notification, lock-screen, wired/Bluetooth headset media controls work.
- [ ] Audio focus loss/ducking and becoming-noisy unplug/disconnect behavior work without speaker blast.
- [ ] Force-stop/process-death scenario restores coherent queue/current index/position/shuffle/repeat as Android permits.
- [ ] Clearing playback cache does not damage library/session and playback re-resolves safely.
- [ ] Source/network failure remains finite and library stays available offline.
- [ ] Manual backup, library mutation, previewed validated restore, and exact order recovery work.
- [ ] Automatic backup location permission survives restart and rolling retention remains about ten files.
- [ ] Diagnostic last-error copy and JSON export work without secrets or transient media URLs.

## Upgrade and recovery

- [ ] Install an older same-ID/same-key build with representative library/queue data.
- [ ] Install the candidate over it with a higher version code; confirm database, relationships, order, and session survive.
- [ ] Corrupt/clear transient playback state where the harness permits; confirm permanent library still opens.
- [ ] Clean app data, reinstall candidate, restore portable backup, and confirm library/order.
- [ ] Confirm forward-rollback procedure with last-known-good source and higher version code is documented and viable.

## Signing and final release

- [ ] Create one permanent private release JKS outside Git.
- [ ] Store a separate offline backup and recovery instructions.
- [ ] Set `MY_MUSIC_PLAYER_KEYSTORE_PATH`, `MY_MUSIC_PLAYER_KEYSTORE_PASSWORD`, `MY_MUSIC_PLAYER_KEY_ALIAS`, and `MY_MUSIC_PLAYER_KEY_PASSWORD` only in the trusted local release environment.
- [ ] Run `assembleRelease`; confirm output is signed with the permanent certificate.
- [ ] Verify APK package ID, version code/name, signing certificate, and embedded final Git commit.
- [ ] Install signed release APK on the Samsung Galaxy A36.
- [ ] Re-run all required device acceptance tests on the exact signed artifact.
- [ ] Create annotated Git tag `v0.1.0` only after every required item above passes.
- [ ] Record `v0.1.0` as the first last-known-good release.

## Exact local commands

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease

.\gradlew.bat testDebugUnitTest `
  --tests 'com.admin.mymusicplayer.source.newpipe.NewPipeLiveIntegrationTest' `
  -DliveSourceTests=true
```
