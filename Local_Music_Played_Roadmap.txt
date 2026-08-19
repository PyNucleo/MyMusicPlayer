# PROJECT_CONTEXT_AND_ROADMAP.md
## Single-User Android Music Player — Authoritative Project Handoff

**Document purpose:** This file is the authoritative context, specification, architecture decision record, implementation roadmap, AI-agent operating manual, and acceptance-test definition for this project.

**Primary reader:** ChatGPT/Codex or another capable coding AI taking over in a fresh conversation with little or no prior context.

**Current planning date:** Wednesday, August 19, 2026  
**User timezone:** Asia/Beirut  
**Target device:** Samsung Galaxy A36  
**Project status:** Architecture/roadmap complete; implementation has not yet meaningfully begun.  
**Immediate next phase:** Day 1 implementation.

---

# 0. READ THIS FIRST — INSTRUCTIONS TO THE NEXT CHATGPT

Do **not** immediately redesign the project.

This architecture has already undergone repeated adversarial review from the perspectives of:

- daily usability;
- single-user optimization;
- Android lifecycle behavior;
- playback reliability;
- YouTube failure modes;
- search reliability;
- shuffle correctness;
- persistent queue semantics;
- data-loss prevention;
- database migrations;
- backup/restore;
- Android sideloading;
- Samsung-specific behavior;
- media playback APIs;
- automation;
- Codex autonomous maintenance;
- rollback;
- Git/CI;
- battery/storage overhead;
- unnecessary overengineering;
- transferable learning value for a Bioinformatics undergraduate;
- external platform constraints.

Treat the decisions marked **FROZEN** as source-of-truth unless:

1. implementation reveals they are technically impossible;
2. a current official platform/API change invalidates them;
3. a clearly superior solution can be demonstrated rather than merely proposed.

If considering changing a frozen decision:

1. identify the exact problem;
2. verify current documentation where relevant;
3. compare the existing approach against the alternative;
4. quantify the benefit;
5. account for added complexity and maintenance;
6. change the roadmap only if the new option is materially superior.

Do **not** introduce enterprise architecture, generic best-practice ceremony, publication infrastructure, cloud systems, accounts, security infrastructure, analytics, telemetry, backend services, or abstraction layers unless they directly improve this user's experience.

The user is the **only intended user**.

Optimize for:

1. reliability;
2. exact requested behavior;
3. minimum friction;
4. repairability by future AI;
5. preservation of the user's playlists/data;
6. minimal personal learning burden;
7. useful transferable engineering learning;
8. simplicity.

Everything else is secondary.

---

# 1. USER CONTEXT

The user is a Bioinformatics undergraduate.

They already have programming experience, including:

- Java;
- objects/classes;
- queues;
- stacks;
- general programming fundamentals.

They learn quickly.

They do **not** want to become an Android developer merely to obtain a music player.

Personal learning-time constraint:

- approximately 2 focused hours/day;
- core project learning should fit within 3–4 days;
- total core personal learning target: approximately 8 hours;
- up to 7 days is acceptable only for skills that have meaningful Bioinformatics/CV value.

AI/Codex should implement anything whose main value is Android/framework-specific expertise rather than transferable knowledge.

The user is comfortable with AI doing a large amount of implementation.

---

# 2. ORIGINAL MOTIVATION

The user currently uses Freefy.

Problems experienced:

- search sometimes does not work;
- shuffle appears to choose/repeat only a subset of a playlist;
- some tracks are effectively ignored;
- application sometimes fails to open.

The user tested SimpMusic.

Observed:

- playback failed;
- then an advertisement played.

This led to a hard requirement:

> **No advertisements.**

The user does not want to continue repeatedly testing third-party clients with varying bugs.

The chosen direction is therefore a personal Android application.

---

# 3. PRODUCT PHILOSOPHY

This is **personal software**.

It is not:

- a startup;
- a commercial application;
- a public app;
- a Play Store product;
- a SaaS;
- a multi-user product;
- a social service;
- a cloud platform.

Do not design for hypothetical users.

Do not implement features merely because production applications traditionally contain them.

Ask of every feature:

> Does this substantially improve the sole user's listening experience, reliability, recoverability, or ability to maintain the application?

If no, omit it.

---

# 4. PRIMARY PRODUCT GOAL

Create a reliable Android music player for the user's Samsung Galaxy A36 that can:

1. find normal public YouTube videos/music through search;
2. resolve usable audio playback where technically available;
3. play audio in the background;
4. maintain personal playlists locally;
5. provide mathematically correct shuffle behavior;
6. provide exact requested repeat behavior;
7. support efficient bulk playlist management;
8. preserve data reliably;
9. remain easy for future Codex/ChatGPT agents to repair.

No advertisements are to be deliberately implemented into the application.

---

# 5. IMPORTANT EXTERNAL PLATFORM CONSTRAINT

There is no fully official YouTube-supported path that simultaneously provides the desired:

- arbitrary public-video access;
- custom audio-only player;
- background playback;
- removal/absence of YouTube advertising;
- independent custom UI.

YouTube's current Terms restrict automated scraping/access and interference with service restrictions. citeturn364426view6

YouTube's official developer-policy guidance also explicitly restricts API clients from separating audio, altering/blocking advertisements, and providing background playback in the relevant ways. citeturn364426view7

Therefore:

## FROZEN DECISION

Do **not** build the product around the official YouTube Data API.

Use an isolated unofficial extraction layer for search/resolution, initially NewPipe Extractor, with the understanding that:

- YouTube can break it;
- YouTube does not officially guarantee this usage;
- compatibility cannot be promised permanently;
- future blocking may occur.

This risk is accepted as an external constraint of the desired product.

---

# 6. HARD AUTOMATION STOP CONDITION

Future automated Codex repair may address ordinary compatibility changes such as:

- changed response structures;
- changed player/client parameters;
- parsing changes;
- stream selection bugs;
- upstream extractor changes;
- extractor dependency updates;
- ordinary request/response compatibility issues.

However:

## AUTOMATION MUST STOP if successful playback would require defeating or circumventing:

- DRM;
- security mechanisms;
- authentication restrictions;
- bot/access-control challenges;
- age restrictions;
- region restrictions;
- private/members-only access;
- mechanisms explicitly intended to prevent access or copying.

In such a case:

1. do not attempt circumvention;
2. do not automatically patch;
3. produce a diagnostic report;
4. explain the external restriction.

This boundary is both architectural and operational.

---

# 7. TARGET EXPERIENCE

The eventual user experience should feel approximately like:

```text
Open app

→ Search
→ type song / artist / video name
→ results appear
→ tap result
→ audio begins

OR

YouTube
→ Share
→ Personal Music Player
→ Play now / Play next / Add to playlist
```

Main logical screens:

```text
SEARCH
PLAYLISTS
QUEUE
NOW PLAYING
```

Settings should remain tiny.

---

# 8. P0 — MUST-HAVE FEATURES

These define the MVP.

The project is **not complete** until these work.

---

## 8.1 Search

Required:

- search public YouTube content;
- search by song;
- search by artist;
- search by arbitrary video title;
- support imperfect/general text queries;
- display:
  - title;
  - uploader/channel;
  - thumbnail;
  - duration where available;
- pagination or Load More;
- pasted YouTube watch URL;
- stale-search protection;
- clear loading state;
- clear failure state;
- retry;
- no infinite spinner;
- local playlist search;
- Android Share → application integration.

### Search architecture

```text
SearchScreen
     ↓
SearchViewModel
     ↓
SearchProvider
     ↓
YouTubeSearchProvider
     ↓
Extractor adapter
     ↓
YouTube
```

`SearchProvider` must be an interface.

Example conceptual API:

```kotlin
interface SearchProvider {
    suspend fun search(
        query: String,
        page: SearchPage? = null
    ): SearchResultPage
}
```

No extractor-specific classes should leak into UI/domain layers.

---

# 8.2 Search race-condition invariant

Example:

```text
request A: "Radio"
request B: "Radiohead"
```

If B returns first and A returns later:

**A must not overwrite B.**

Implement either:

- cancellation of the previous search coroutine;
- monotonically increasing request IDs;
- another equivalent latest-request-wins mechanism.

Permanent invariant:

> A stale search response may never replace the result of a newer search.

---

# 8.3 Audio resolution

Architecture:

```text
Track
  ↓
AudioResolver
  ↓
YouTubeAudioResolver
  ↓
Extractor
  ↓
PlayableAudio
  ↓
PlayerController
  ↓
Media3/ExoPlayer
```

Interface concept:

```kotlin
interface AudioResolver {
    suspend fun resolve(track: Track): PlayableAudio
}
```

Store permanent source identity:

```text
sourceType
sourceMediaId
```

For YouTube:

```text
sourceType = YOUTUBE
sourceMediaId = videoId
```

Do **not** persist extracted temporary stream URLs as permanent library data.

They may expire.

Permanent data is the media identity and metadata.

---

# 8.4 Audio-resolution race protection

Example:

```text
tap Song A
resolver A begins

immediately tap Song B
resolver B begins
```

If resolver A returns after B:

Song A must **not** unexpectedly start.

Permanent invariant:

> A stale resolution request may never replace a newer playback intent.

Cancel or invalidate obsolete resolution work.

---

# 8.5 Core playback controls

Required:

- Play.
- Pause.
- Previous.
- Next.
- Seek.
- Progress display.
- Normal Android volume behavior.
- Background playback.
- Lock-screen controls.
- Notification controls.
- Bluetooth media controls.
- Headphone/media-device behavior.
- Android audio-focus behavior.

Android currently recommends keeping the player and media session inside a `MediaSessionService` for background playback. citeturn364426view2

Therefore:

## FROZEN DECISION

Use:

```text
AndroidX Media3
ExoPlayer
MediaSession
MediaSessionService
```

Do not invent custom background-media infrastructure.

---

# 8.6 Audio device behavior

When headphones/Bluetooth audio disconnect unexpectedly while playing:

```text
PLAYING
→ PAUSE
```

Do not allow sudden phone-speaker playback.

Handle audio focus through normal Media3/Android mechanisms.

---

# 8.7 Playback recovery

Model playback internally with meaningful states such as:

```text
IDLE
RESOLVING
BUFFERING
PLAYING
PAUSED
FAILED
```

For transient playback failure:

```text
stream failure
    ↓
retry
    ↓
still failure?
    ↓
re-resolve same Track
    ↓
new current stream
    ↓
resume approximately previous position
```

Do not immediately delete a track because playback failed.

If a track is genuinely unavailable:

```text
mark/display unavailable
skip to next when appropriate
keep database record
```

Potential future action:

```text
Find replacement
```

---

# 8.8 Pre-resolve next track

Do not resolve an entire large queue.

Instead:

```text
CURRENT
→ resolved and playing

NEXT
→ prepare/resolution ahead of time

others
→ unresolved
```

Goal:

- lower gap between tracks;
- avoid stale URLs;
- avoid unnecessary work.

---

# 8.9 Bounded playback cache

Use Media3-compatible caching.

Initial target:

```text
approximately 512 MB
```

Use automatic least-recently-used eviction or equivalent bounded strategy.

Purpose:

- seeking;
- replaying recently heard material;
- temporary network resilience;
- reducing redundant fetching.

Not a permanent download library.

P0 settings only need:

```text
Clear cache
```

Do not build a full cache-management UI.

---

# 9. PLAYLISTS

Required:

- create playlist;
- rename playlist;
- delete playlist;
- manual order;
- add track;
- remove track;
- search within playlist;
- multi-select;
- select all;
- clear selection;
- move selected tracks;
- copy selected tracks;
- remove selected tracks;
- undo bulk operations;
- public YouTube playlist import.

---

# 9.1 Multi-select semantics

Long press an item:

```text
selection mode ON
```

Selected state should conceptually be represented by track/entry IDs:

```kotlin
Set<Id>
```

Then allow:

```text
MOVE
COPY
REMOVE
SELECT ALL
```

---

# 9.2 Bulk operation invariants

All bulk operations must be transactional.

Example:

```text
move 57 entries
```

Result must be:

```text
all 57 moved
```

or:

```text
none moved
```

Never partially move because an operation failed halfway through.

Relative source order must be preserved.

Example:

```text
source positions

B
D
E
```

Move to target:

```text
B
D
E
```

Not whatever ordering the database happened to return.

---

# 9.3 Undo

After destructive/bulk operations:

```text
3 tracks moved to Study
[UNDO]
```

Support cheap reversal of recent user actions where practical.

This provides much higher single-user value than elaborate confirmation dialogs.

---

# 9.4 Duplicate policy

Default playlist invariant:

> The exact same source track should not appear twice in the same playlist.

Use source identity, not title text.

Different YouTube uploads of the same song have different source IDs and may coexist.

If adding an exact existing source:

```text
Already in playlist
```

rather than silently duplicating it.

---

# 10. QUEUE MODEL

Queue and playlist are separate concepts.

The active queue is a **playback snapshot**.

A playlist is persistent library organization.

Required queue actions:

- Play Now.
- Play Next.
- Add to Queue.
- remove upcoming item;
- reorder upcoming items;
- clear upcoming queue;
- inspect Queue screen.

Changing the queue must not automatically reorder the originating playlist.

Changing the playlist must not unpredictably alter the active queue.

---

# 11. PLAYLIST SNAPSHOT SEMANTICS

Example playlist:

```text
A B C D E F
```

Active queue:

```text
D A F B C E
```

User adds `G` to the playlist.

Persistent playlist becomes:

```text
A B C D E F G
```

Existing playback queue remains:

```text
D A F B C E
```

`G` becomes relevant on the next newly-created queue/cycle unless the user explicitly adds it to the queue.

This avoids the playback list mutating underneath the listener.

---

# 12. SHUFFLE — CRITICAL BEHAVIOR

The user's Freefy shuffle experience is a primary reason for this project.

Shuffle correctness is non-negotiable.

---

## 12.1 Never implement random next-track sampling

BAD:

```text
next = random.choice(playlist)
```

This allows:

- repeated tracks;
- ignored tracks;
- poor coverage.

Do not do this.

---

## 12.2 Correct shuffle algorithm

For a queue of N unique queue entries:

```text
generate a random permutation of all N entries
```

Example:

```text
stored playlist:
A B C D E F

shuffled cycle:
D A F C B E
```

Properties:

```text
all entries included
each entry exactly once
no duplicates
playlist order unchanged
```

After completing the entire cycle:

```text
generate another random permutation
```

---

# 12.3 Cross-cycle boundary rule

If playlist/queue size > 1:

```text
last(previousCycle)
!=
first(newCycle)
```

Preferred simple algorithm:

```text
shuffle

while first == previousLast:
    reshuffle
```

This constraint should not otherwise weight or "smartly" influence the ordering.

---

# 12.4 No smart shuffle

Explicitly reject:

- history weighting;
- artist spacing;
- popularity weighting;
- Spotify-like smart ordering;
- machine-learning shuffle;
- mood balancing.

The requested behavior is random permutation with exact coverage.

---

# 12.5 Turning shuffle on halfway through playback

Example:

```text
already consumed:
A B C

remaining:
D E F G
```

When Shuffle is enabled:

```text
A B C | shuffled(D E F G)
```

Previously consumed entries do not return during that cycle.

---

# 12.6 Explicit track tap while shuffle is enabled

If user explicitly taps `C`:

```text
C
```

becomes first/current.

Remaining eligible entries are shuffled after it.

Example:

```text
C E A D B
```

The explicit user request overrides random choice for the first entry.

---

# 12.7 Reshuffle button

An explicit Reshuffle action may intentionally abandon the remaining current cycle and generate another queue.

Do not silently reshuffle behind the user's back.

---

# 13. REPEAT — EXACT REQUIRED SEMANTICS

Three-state button:

```text
① PLAY ONCE
② REPEAT ONCE
∞ REPEAT FOREVER
```

The mode applies to the **current queue item**, not globally to every subsequent track.

---

## 13.1 Play once

```text
A → B
```

---

## 13.2 Repeat once

Exactly one additional playback:

```text
A → A → B
```

Not:

```text
A → A → A
```

Maintain explicit state such as:

```text
repeatOnceConsumed
```

---

## 13.3 Repeat forever

```text
A → A → A → ...
```

until:

- Next;
- Previous;
- another track is selected;
- mode is changed.

Manual Next leaves A and resets the new current item to normal Play Once unless UX implementation deliberately specifies otherwise.

Preferred behavior:

```text
A ∞
press Next
→ B, mode PLAY_ONCE
```

---

# 13.4 Repeat and shuffle interaction

Example shuffled queue:

```text
D B F C A
```

B set to Repeat Once:

```text
D → B → B → F → C → A
```

The underlying queue still contains B only once.

Repeat is playback behavior, not queue duplication.

---

# 14. PERSISTENT PLAYBACK SESSION — CRITICAL CORRECTION

The active queue must survive ordinary Android process death.

Without persistence, shuffle correctness would be violated across restarts.

Persist enough state to reconstruct the current session.

Conceptual model:

```text
PlaybackSession
────────────────────────────
sessionId
originPlaylistId?
originPlaylistRevision?
shuffleEnabled
currentQueueIndex
currentPositionMs
currentRepeatMode
repeatOnceConsumed
createdAt
updatedAt
```

Plus:

```text
QueueEntry
────────────────────────────
sessionId
position
trackId
```

---

# 14.1 Why this matters

Shuffle:

```text
F C A H D B G E
```

User hears:

```text
F C A
```

Android kills app.

On restart, DO NOT regenerate:

```text
new random order
```

Resume remaining snapshot:

```text
H D B G E
```

---

# 14.2 Position checkpoint strategy

Do not write playback position constantly.

Persist structural changes immediately:

- queue change;
- current item change;
- shuffle state;
- repeat state.

Checkpoint position approximately every few seconds and on events such as:

- pause;
- app/service transition;
- track change;
- shutdown where available.

Exact interval is implementation detail; ~5 seconds is a reasonable initial target.

---

# 15. SAFE STARTUP — P0

One original Freefy complaint is that sometimes the application does not open.

This project must isolate permanent library state from transient playback state.

Startup:

```text
open database
    ↓
load permanent library
    ↓
attempt transient PlaybackSession restoration
```

If session restoration fails:

```text
discard/reset ONLY transient playback session
```

Then app opens normally.

Permanent invariant:

> A corrupted queue/session/cache must never prevent access to the library.

Likewise:

> Network/YouTube failure must never prevent application startup.

The local library UI must be able to open offline.

---

# 16. DATABASE

## FROZEN DECISION

Use:

```text
Room 2.x stable line
```

At planning time, Room 2.8.4 is a released stable Room version. citeturn364426view1

Room 3 is undergoing a major breaking redesign and should not be adopted merely because it is newer.

Before implementation, verify the current stable Room situation.

If Room 2.x remains the mature low-risk Android-only choice, use it.

Do not automatically upgrade to Room 3 because of version-number novelty.

Use KSP rather than unnecessary legacy annotation-processing approaches where compatible.

Room remains an abstraction over SQLite and is appropriate for structured local data. citeturn808662search32

---

# 17. DATABASE SCHEMA — CONCEPTUAL

## Track

```text
Track
────────────────────────────
id                   PK
sourceType
sourceMediaId
title
uploader
durationMs
thumbnailUrl
lastKnownAvailability?
createdAt?
```

Unique source identity:

```text
(sourceType, sourceMediaId)
```

Do not make title the identity.

---

## Playlist

```text
Playlist
────────────────────────────
id                   PK
name
createdAt
modifiedAt
revision
```

---

## PlaylistEntry

```text
PlaylistEntry
────────────────────────────
playlistId
trackId
position
addedAt
```

Represents many-to-many relationship between playlists and tracks.

---

## PlaybackSession / QueueEntry

As specified earlier.

---

# 17.1 Track garbage collection policy

Do not aggressively delete Track rows simply because no playlist references them.

Track metadata is cheap.

Keeping old rows simplifies:

- active queues;
- Undo;
- diagnostics;
- history;
- future replacement matching.

Database cleanup is not important enough to complicate the MVP.

---

# 18. DATABASE MIGRATION POLICY

Absolute invariant:

> Released user data must never be destroyed merely because an AI changed the schema.

Do not use destructive migration on a production/personal library containing real user data.

Every schema change after first real usage must include:

1. previous schema inspection;
2. migration implementation;
3. migration test;
4. entity-count validation;
5. playlist relationship validation;
6. queue/session migration validation if relevant.

If migration cannot be demonstrated safe:

**do not release it.**

---

# 19. BACKUP/RESTORE — P0

The playlists/library are more valuable than the executable.

APK loss:

```text
rebuild APK
```

Library loss:

```text
potentially reconstruct hundreds/thousands of tracks
```

Therefore backup is core functionality.

---

# 19.1 Live storage versus portable backup

Use:

```text
Room/SQLite
= live application storage
```

Use:

```text
versioned JSON
= portable backup
```

Do not make raw SQLite copies the primary portable backup format.

---

# 19.2 Backup format

Conceptual:

```json
{
  "formatVersion": 1,
  "createdAt": "...",
  "tracks": [],
  "playlists": [],
  "playlistEntries": [],
  "settings": {}
}
```

Do not necessarily include transient caches.

PlaybackSession backup is optional; permanent library is the priority.

---

# 19.3 Backup features

Required:

```text
Backup Now
Restore Backup
Automatic backup location
```

Use Android's document/storage framework so the user can select an appropriate location.

---

# 19.4 Rolling backups

Maintain multiple recent backups rather than overwriting the only good copy.

Initial policy may be:

```text
keep last ~10 successful backups
```

Exact count can be tuned later.

---

# 19.5 Restore must validate before mutation

Before restore:

```text
validate file
validate formatVersion
parse completely
validate basic relationships
```

Then display useful summary such as:

```text
Backup contains:
17 playlists
1,283 tracks
```

Only after validation should live data be replaced/merged.

Restore should itself be transactional where practical.

---

# 20. UPDATE SURVIVAL TEST

Before calling first release complete:

```text
install v0.1
create playlists/data
build v0.2
install v0.2 over v0.1
verify data survives
```

Also test disaster restoration:

```text
export backup
uninstall app
reinstall clean app
restore backup
verify data
```

Both paths must work.

---

# 21. APPLICATION ID

Use a unique permanent package/application ID.

Do not choose an overly generic name if avoidable.

Example format only:

```text
io.<unique-personal-namespace>.musicplayer
```

Once real data exists:

> Do not change applicationId casually.

Android's developer-verification ecosystem now includes package-name registration considerations, making stable unique identities increasingly useful. citeturn364426view4

---

# 22. SIGNING

Create one permanent release signing key.

Conceptual filename:

```text
personal-music-release.jks
```

Keep at least:

- one primary copy;
- one separate backup.

Reason is practical, not "security theater":

The same app identity/signing relationship is needed for straightforward future update installation.

Do not expose signing secrets to autonomous Codex repair jobs.

Signing should occur only after deterministic validation.

---

# 23. TARGET DEVICE OPTIMIZATION

The user has one target phone:

```text
Samsung Galaxy A36
```

Do not optimize for:

- tablets;
- foldables;
- Android TV;
- ChromeOS;
- ancient Android devices;
- unusual manufacturers.

During Day 1:

Use ADB to query the actual phone OS/API level.

Then choose:

```text
minSdk
```

based primarily on this real environment rather than hypothetical compatibility.

Still use appropriate current:

```text
compileSdk
targetSdk
```

after verifying Android requirements.

---

# 24. SAMSUNG INSTALLATION CONSTRAINT

Samsung Auto Blocker can block:

- installation from unknown sources;
- USB commands.

Samsung documents these behaviors. citeturn364426view5

Therefore Day 1 setup should check:

```text
Settings
→ Security and privacy
→ Auto Blocker
```

If it blocks APK installation or ADB development:

temporarily disable/configure it as necessary.

Do not waste time debugging ADB while Auto Blocker is the real cause.

---

# 25. ANDROID SIDELOADING / DEVELOPER VERIFICATION

Android's developer-verification rules are evolving.

As of the planning date, Android documents limited-distribution options aimed at students/hobbyists, alongside its developing verification framework. citeturn364426view4

Do not build Play Store infrastructure unless actually required.

Before any future distribution/install decision:

verify current Android rules.

The product remains:

```text
private personal APK
```

unless the user explicitly changes that requirement.

---

# 26. SHARE-TO-APP INTEGRATION

High-value feature:

From YouTube or another application:

```text
Share
→ Personal Music Player
```

The app extracts the incoming URL and offers:

```text
Play Now
Play Next
Add to Queue
Add to Playlist
```

This eliminates unnecessary duplicate searching.

---

# 27. PUBLIC YOUTUBE PLAYLIST IMPORT

Required because manually rebuilding large playlists would be poor UX.

Input:

```text
public YouTube playlist URL
```

Then:

```text
resolve playlist
preview/import items
create target playlist
```

Use extractor capabilities rather than official YouTube Data API.

Handle unavailable entries gracefully.

Do not fail the entire import because one item is inaccessible unless unavoidable.

---

# 28. LOCAL DIAGNOSTICS FOR FUTURE AI

No analytics.

No telemetry service.

No remote logging infrastructure.

Instead keep a bounded rolling local diagnostic event log.

Example events:

```text
SEARCH_STARTED
SEARCH_SUCCESS
SEARCH_FAILED

AUDIO_RESOLUTION_STARTED
AUDIO_RESOLUTION_SUCCESS
AUDIO_RESOLUTION_FAILED

PLAYBACK_STARTED
PLAYBACK_ERROR

DB_MIGRATION
BACKUP_CREATED
RESTORE_FAILED
```

Do not log huge data blobs.

---

# 28.1 Diagnostic bundle

Settings:

```text
Diagnostics
├── Copy last error
└── Export diagnostic bundle
```

Bundle should include where useful:

```text
app version
Git commit
device model
Android API
extractor version
Media3 version
database schema version
recent relevant events/errors
last compatibility-canary result
```

Purpose:

The user can give the bundle directly to Codex/ChatGPT.

This makes future AI maintenance dramatically easier.

---

# 29. SETTINGS — KEEP MINIMAL

Recommended initial settings:

```text
Playback
└── maybe Resume queue on launch

Data
├── Backup now
├── Restore
└── Automatic backup location

Storage
└── Clear playback cache

Diagnostics
├── Copy last error
├── Export diagnostic bundle
└── Run compatibility test

About
├── App version
└── Git commit
```

Do not create a massive settings subsystem.

---

# 30. FEATURES DELIBERATELY OUT OF MVP

Do not delay v0.1 for:

- local-file playback;
- permanent offline download library;
- Recently Played;
- Favorites shortcut;
- sleep timer;
- lyrics;
- visualizer;
- equalizer;
- recommendation engine;
- AI recommendations;
- social features;
- accounts;
- cloud synchronization;
- play counts;
- complex sorting;
- artist database;
- album database;
- theme editor.

Some may be useful later.

They are not P0.

---

# 31. FEATURES ACTIVELY REJECTED

Do not introduce without explicit future user request:

```text
backend server
microservices
Docker
cloud database
Firebase
authentication system
user profiles
admin interface
web frontend
React Native
Flutter
Electron
embedded Python runtime
yt-dlp server
self-hosted extraction server
public Piped/Invidious dependency as primary backend
complex dependency injection
multiple Gradle modules
enterprise clean-architecture ceremony
```

---

# 32. WHY NATIVE ANDROID

## FROZEN DECISION

Language/UI/platform:

```text
Kotlin
Jetpack Compose
Android ViewModel / StateFlow
coroutines
```

Playback:

```text
AndroidX Media3
ExoPlayer
MediaSession
MediaSessionService
```

Persistence:

```text
Room 2.x stable
SQLite
```

Extraction:

```text
NewPipe Extractor initially
```

NewPipe Extractor is specifically usable independently of the NewPipe application. citeturn364426view0

Its repository currently carries the GPL-3.0 license. citeturn364426view0

For this private single-user project:

- keep the repository private;
- retain appropriate third-party licensing information;
- do not spend implementation time on publication/legal infrastructure;
- if distribution to other people is ever contemplated, review licensing obligations at that time.

---

# 33. NO YT-DLP-IN-ANDROID BY DEFAULT

Rejected architecture:

```text
Android app
+ Python runtime
+ yt-dlp
```

Reason:

- heavier packaging;
- more build complexity;
- more runtime complexity;
- more failure points.

If NewPipe proves unusable in real implementation, alternatives may be re-evaluated.

Do not preemptively add multiple extraction engines.

---

# 34. HIGH-LEVEL PACKAGE STRUCTURE

Preferred conceptual layout:

```text
app/
└── src/main/java/<package>/
    │
    ├── domain/
    │   ├── Track.kt
    │   ├── Playlist.kt
    │   ├── RepeatMode.kt
    │   ├── ShufflePlanner.kt
    │   └── RepeatPolicy.kt
    │
    ├── search/
    │   ├── SearchProvider.kt
    │   └── YouTubeSearchProvider.kt
    │
    ├── resolver/
    │   ├── AudioResolver.kt
    │   └── YouTubeAudioResolver.kt
    │
    ├── data/
    │   ├── database/
    │   ├── dao/
    │   ├── repository/
    │   ├── backup/
    │   └── diagnostics/
    │
    ├── playback/
    │   ├── PlayerController.kt
    │   ├── PlaybackService.kt
    │   ├── QueueManager.kt
    │   └── SessionRepository.kt
    │
    └── ui/
        ├── search/
        ├── playlists/
        ├── queue/
        ├── player/
        └── settings/
```

Exact filenames may vary.

Do not let architecture naming become more important than behavior.

---

# 35. SOURCE-OF-TRUTH FILES

Repository root must contain:

```text
README.md
AGENTS.md
AI_MAINTENANCE.md
PROJECT_CONTEXT_AND_ROADMAP.md
```

This document should become:

```text
PROJECT_CONTEXT_AND_ROADMAP.md
```

---

# 35.1 AGENTS.md purpose

Short operational instructions to all coding agents.

Must say approximately:

```text
Read PROJECT_CONTEXT_AND_ROADMAP.md first.
Read AI_MAINTENANCE.md before changing architecture.

Preserve all invariants.

Prefer minimal scoped changes.

Do not refactor unrelated working code.

Do not change database schema casually.

Do not change applicationId.

Do not change signing configuration.

Run relevant tests.

Update AI_MAINTENANCE.md when architecture/dependencies change.
```

---

# 35.2 AI_MAINTENANCE.md purpose

Maintain current implementation reality:

- current architecture;
- actual package paths;
- exact dependency versions;
- database schema/version;
- how SearchProvider works;
- how AudioResolver works;
- extractor version;
- playback architecture;
- persistent-session mechanism;
- backup format;
- build commands;
- test commands;
- release commands;
- canary commands;
- known issues;
- last compatibility repair;
- current applicationId;
- Git tag conventions.

This is **not prose documentation for humans**.

Optimize it for future coding AI.

---

# 36. PERMANENT INVARIANTS

These constitute the project's "constitution."

Future AI must not violate them.

1. Playlist/library data must never be silently lost.
2. Released database migrations must never be destructively substituted for proper migration.
3. Restore must validate before altering live data.
4. Every normal shuffled cycle contains every eligible queue entry exactly once.
5. No queue entry repeats inside a normal shuffled cycle.
6. Consecutive shuffle cycles may not share the same boundary entry when size > 1.
7. Playlist order is not changed by shuffle.
8. Repeat Once means exactly one additional playback.
9. Repeat Forever affects only the current item.
10. Manual Next exits the current item's repeat-forever behavior.
11. Repeat behavior does not duplicate queue entries.
12. Stale searches cannot replace newer searches.
13. Stale audio resolutions cannot start playback.
14. Queue and playlist are distinct.
15. Playlist edits cannot unexpectedly mutate the current queue snapshot.
16. Active shuffle/queue state survives ordinary process death.
17. Permanent library data is isolated from transient playback state.
18. Corrupted transient playback state must not prevent startup.
19. Network failure must not prevent local-library startup.
20. YouTube failure must not prevent local-library startup.
21. Temporary stream URLs are not permanent library state.
22. Unavailable media is not silently deleted from playlists.
23. Bulk playlist operations are transactional.
24. Bulk move/copy preserves relative source order.
25. APK updates preserve application identity and user data.
26. Dependencies are pinned intentionally.
27. Released APKs identify their source Git commit.
28. AI_MAINTENANCE.md must match released implementation.
29. Signing identity is not changed casually.
30. Autonomous YouTube repair is forbidden from altering unrelated core components.
31. Autonomous repair must stop on DRM/access-control/circumvention requirements.
32. Automatic fixes must run regression tests before release.
33. Database schema changes cannot occur as an unattended YouTube repair.
34. A last-known-good recovery build must remain possible.
35. Search should fail clearly rather than spin forever.
36. Playback failure should attempt sensible transient recovery but never loop infinitely.

---

# 37. PERSONAL LEARNING DIVISION

The user should personally understand:

```text
Kotlin fundamentals
Git fundamentals
Track/Playlist domain model
queue versus playlist
ShufflePlanner
RepeatPolicy
basic unit testing
SQL fundamentals
relational modeling
transactions
Room concept
ViewModel/state flow concept
basic coroutine concept
HTTP/JSON/API concepts
Media3 high-level playback model
SearchProvider boundary
AudioResolver boundary
how to inspect a Git diff
how to build/install APK
how to revert/recover
```

---

# 38. CODEX SHOULD OWN

Unless the user explicitly wants to learn them:

```text
most Compose implementation
Gradle details
Room DAO boilerplate
Room implementation details
MediaSessionService plumbing
notification plumbing
audio-focus implementation details
extractor internals
YouTube parsing/protocol internals
network retries
cache integration
database migration boilerplate
backup implementation
GitHub Actions YAML
Codex automation wiring
build/release automation
diagnostic infrastructure
Android lifecycle edge cases
```

---

# 39. CORE FOUR-DAY PLAN

The purpose is not merely "finish code."

Each day gives the user the most transferable useful understanding while AI implements the rest.

---

# DAY 1 — WEDNESDAY, AUGUST 19, 2026

## Objective

End Day 1 with:

```text
tooling works
phone connected
app runs
Git repository exists
core models exist
shuffle logic exists
repeat logic exists
unit tests pass
architecture skeleton exists
```

Personal target: approximately 2 focused hours.

---

## Day 1 Step 1 — Tooling

Install/verify:

```text
Android Studio
Git
Codex
ADB / Android platform tools
```

Do not install unnecessary frameworks.

---

## Day 1 Step 2 — Samsung setup

On Samsung A36:

Enable Developer Options.

Enable USB debugging.

Check Samsung Auto Blocker.

Samsung documents that Auto Blocker can block USB commands and unknown-source installations. citeturn364426view5

Temporarily configure/disable if required.

Run:

```bash
adb devices
```

Then query actual Android/API version.

Document it in:

```text
AI_MAINTENANCE.md
```

---

## Day 1 Step 3 — Create Android project

Create native Android application using:

```text
Kotlin
Jetpack Compose
```

Choose permanent unique applicationId.

Run default project on actual Samsung.

Do not continue until this works.

---

## Day 1 Step 4 — Initialize Git

Conceptually:

```bash
git init
git status
git add .
git commit -m "Initial Android project"
```

Create a private GitHub repository.

Push baseline.

---

## Day 1 Step 5 — Kotlin crash course

Only learn:

```text
val
var
functions
classes
data class
enum class
interface
nullable types
?.
?:
when
List
MutableList
Set
Map
map
filter
find
any
all
lambdas
basic exceptions
suspend concept
launch/viewModelScope concept
```

Stop.

Do not take an entire Kotlin course.

---

## Day 1 Step 6 — User personally implements/understands

```text
Track
Playlist
RepeatMode
ShufflePlanner
RepeatPolicy
```

---

## Day 1 Step 7 — Shuffle tests

Tests must cover:

```text
0 items
1 item
2 items
20 items
100 items
1000 items
```

Assertions:

```text
output size == input size

output identities == input identities

no duplicates

input order/data not mutated

cross-cycle boundary rule

mid-cycle behavior where applicable
```

Do not attempt statistical "looks random" testing.

Test invariants.

---

## Day 1 Step 8 — Repeat tests

Verify:

```text
PLAY_ONCE
→ advance

REPEAT_ONCE first completion
→ replay current

REPEAT_ONCE second completion
→ advance

REPEAT_FOREVER
→ replay

manual Next
→ advance and appropriate reset
```

---

## Day 1 Step 9 — Codex architecture task

Codex should:

- inspect user-written domain code first;
- not rewrite working user-owned algorithms;
- create minimal single-module application architecture;
- create interfaces:
  - `SearchProvider`;
  - `AudioResolver`;
- create placeholder screens:
  - Search;
  - Playlists;
  - Queue;
  - Now Playing;
- create `AGENTS.md`;
- create `AI_MAINTENANCE.md`;
- place this roadmap into repository;
- run tests.

Do not integrate YouTube on Day 1.

---

# DAY 2 — THURSDAY, AUGUST 20, 2026

## Objective

Functional UI against fake data.

Target:

```text
navigation
fake search
playlist interactions
queue interactions
multi-select
user understands state architecture
```

Personal target: ~2 hours.

---

## Day 2 Learning

Spend limited focused time understanding:

```text
@Composable
state
ViewModel
StateFlow
event
state hoisting
unidirectional data flow
suspend function
coroutine
HTTP request/response
status code
JSON
pagination
timeout
retry
```

Do not study Compose broadly.

---

## Day 2 architecture mental model

```text
STATE
 ↓
UI
 ↓
EVENT
 ↓
VIEWMODEL
 ↓
STATE CHANGE
 ↓
UI
```

---

## Day 2 — Fake providers

Implement:

```text
FakeSearchProvider
FakeAudioResolver
```

UI must be tested without YouTube involvement.

This separates:

```text
UI bugs
```

from:

```text
extractor/network bugs
```

---

## Day 2 Codex implementation

Build:

### Search screen

- text input;
- submit;
- fake results;
- loading;
- failure;
- retry;
- add to playlist.

### Playlist screen

- list playlists;
- create;
- rename;
- delete;
- open;
- local filter/search;
- long press selection;
- Select All;
- Move;
- Copy;
- Remove;
- Undo.

### Queue screen

- current;
- upcoming;
- reorder;
- remove;
- clear upcoming.

### Now Playing

- metadata;
- controls;
- shuffle control;
- 3-state repeat control;
- seek UI placeholder.

---

## Day 2 user review

For each ViewModel, user should be able to answer:

```text
What state does it own?
What events can occur?
What state changes after each event?
```

That is enough architectural understanding.

---

# DAY 3 — FRIDAY, AUGUST 21, 2026

## Objective

Real local persistence + real native playback.

Target:

```text
Room database
persistent playlists
persistent session
Media3 playback
background playback
Bluetooth/notification controls
backup groundwork
```

---

## Day 3 personal learning

Approximately:

```text
SQL basics
SELECT
INSERT
UPDATE
DELETE
JOIN

primary key
foreign key
many-to-many
transaction
migration

Room architecture concept

MediaItem
Player
ExoPlayer
MediaSession
MediaSessionService
```

Do not memorize APIs.

---

## Day 3 database implementation

Codex implements:

```text
Room 2.x stable
Track table
Playlist table
PlaylistEntry table
PlaybackSession
QueueEntry
repositories
DAOs
transactions
schema export
migration-test foundation
```

At planning time, Room 2.8.4 is a stable Room release. citeturn364426view1

Re-verify current stable versions before committing dependencies.

---

## Day 3 persistence acceptance

Test:

```text
create playlists
add items
move items
copy items
remove items
close app
reopen
force stop
reopen
```

Data remains.

---

## Day 3 playback implementation

Use Media3.

Android's documented background-playback design places Player/MediaSession in a `MediaSessionService`. citeturn364426view2

Implement:

```text
play
pause
seek
previous
next
queue
background
MediaSession
notification
lock screen
Bluetooth
audio focus
becoming noisy/headphone disconnect
bounded cache
session persistence
```

Initially test with a known legitimate test/local media source before introducing YouTube extraction.

---

# DAY 4 — SATURDAY, AUGUST 22, 2026

## Objective

Integrate real source + abuse-test entire application + create first release.

Target:

```text
real search
real audio resolution
public playlist import
share intent
backup/restore
diagnostics
full regression tests
signed APK
known-good Git tag
```

---

## Day 4 personal learning

Understand only:

```text
SearchProvider → extractor
AudioResolver → extractor
Track → resolved audio → Media3

failure/retry state machine
persistent queue concept
backup concept
release/update concept
```

Do not study NewPipe internals.

---

## Day 4 extractor integration

NewPipe Extractor is designed as a standalone extraction library. citeturn364426view0

Codex must keep it isolated.

Do not allow:

```text
NewPipe classes
```

inside:

```text
domain
database
UI contracts
queue logic
shuffle logic
repeat logic
```

---

# 40. DAY 4 ADVERSARIAL ACCEPTANCE SESSION

User should deliberately abuse the app.

Test:

```text
search exact title
search vague title
search obscure video
search long video
paste URL
search again before previous returns

tap Song A
immediately tap Song B

spam Next
spam Previous

lose Wi-Fi
switch Wi-Fi to mobile data
regain network

lock phone
unlock
switch apps

disconnect headphones while playing

Bluetooth play/pause/next if available

shuffle large playlist
kill process during shuffle
restart

turn shuffle on halfway through queue

Repeat Once
Repeat Forever
Next during Repeat Forever

move many selected tracks
copy many
remove many
Undo

edit playlist while its queue is active

force-stop app
restart

corrupt/clear transient playback state if test harness permits
confirm library opens

backup
modify library
restore backup

install newer APK over old APK
confirm data survives
```

---

# 41. RELEASE REQUIREMENTS FOR v0.1

Do not tag v0.1 until:

```text
✓ app launches independently
✓ search works
✓ normal intended public content can resolve where extractor supports it
✓ audio playback works
✓ background playback works
✓ lock-screen/media controls work
✓ playlist CRUD works
✓ multi-select works
✓ move/copy/remove works
✓ bulk operations preserve order
✓ Undo works
✓ queue works
✓ shuffle invariants pass
✓ queue survives process death
✓ repeat states pass
✓ backup works
✓ restore works
✓ database survives upgrade
✓ diagnostic export works
✓ release APK installs
✓ release APK is signed
✓ source Git commit identifiable
✓ AI_MAINTENANCE.md accurate
```

Then:

```bash
git tag v0.1.0
```

This becomes the first:

```text
LAST KNOWN GOOD
```

---

# 42. OPTIONAL DAYS 5–7 — ONLY FOR CV/TRANSFERABLE VALUE

Do not spend these days learning advanced Android.

---

## DAY 5 — Git/GitHub

Learn properly:

```text
clone
status
diff
add
commit
log
branch
switch
merge
revert
tag
push
pull
pull request concept
```

Reason:

Extremely transferable to computational biology, research software, pipelines, collaboration, reproducibility.

---

## DAY 6 — SQL + HTTP/JSON/APIs

Deepen:

```text
SELECT
WHERE
ORDER BY
GROUP BY
JOIN
indexes
transactions
normalization basics

HTTP methods
status codes
headers
JSON
pagination
timeouts
retry
API boundaries
```

Highly transferable to Bioinformatics.

---

## DAY 7 — Testing + CI/CD

Learn:

```text
unit test
integration test
end-to-end test
regression test
fake/test double
continuous integration
build pipeline
release pipeline
```

Then inspect the GitHub Actions workflows.

Do not memorize YAML.

Understand pipeline semantics.

---

# 43. AUTOMATED YOUTUBE COMPATIBILITY MONITOR

Do not ask AI every few hours:

> Did YouTube update?

Instead test:

> Does the behavior our app requires still work?

Run a tiny deterministic canary.

---

# 43.1 Canary frequency

Initial target:

```text
approximately every 2–3 hours
```

Use an off-round minute such as:

```text
:17
```

rather than exactly `:00`.

Exact scheduling is implementation detail and should respect current CI capabilities/costs.

---

# 43.2 Canary scope

Do **not** run entire Android regression suite on healthy schedule runs.

Cheap canary:

```text
1. Search known query.
2. Verify plausible results.
3. Resolve several known public canary videos.
4. Obtain audio stream information.
5. Read a very small amount of stream data.
```

Use multiple known videos.

Example classification:

```text
A fails
B passes
C passes
→ likely content-specific

A/B/C all fail
→ likely systemic
```

And distinguish:

```text
search failure
```

from:

```text
resolution failure
```

---

# 43.3 Healthy path

```text
CANARY PASS
→ stop
```

No Codex.

No Android build.

No full tests.

---

# 43.4 Failure path

```text
canary fails
    ↓
retry
    ↓
still fails
    ↓
classify failure
    ↓
Codex investigation
```

---

# 44. CODEX AUTONOMOUS REPAIR

Current Codex tooling supports repeatable GitHub/CI tasks. citeturn364426view3

However autonomous repair must be tightly scoped.

---

# 44.1 AUTOMATIC-REPAIR ALLOWLIST

Codex automation may modify only relevant compatibility areas such as:

```text
search/
resolver/
extractor adapter
extractor dependency version
related networking compatibility
related tests
AI_MAINTENANCE.md
dependency catalog when required
```

---

# 44.2 AUTOMATIC-REPAIR DENYLIST

Automatic repair may **not** modify:

```text
database schema
backup format
shuffle logic
repeat logic
playlist semantics
queue semantics
domain invariants
signing
applicationId
major UI architecture
unrelated application architecture
```

If Codex determines one of these must change:

```text
STOP
→ report diagnosis
→ create proposed human-reviewed change
```

No unattended merge.

---

# 44.3 Patch-first workflow

Preferred:

```text
canary fails
    ↓
Codex analyzes
    ↓
Codex produces patch
    ↓
deterministic changed-path checker
    ↓
allowed?
  /      \
yes      no
 ↓        ↓
apply    STOP
 ↓
tests
 ↓
full regression suite
 ↓
build
 ↓
sign
 ↓
release
```

Codex should not need the signing key.

---

# 45. TEST LEVELS

## Fast unit tests

Always run frequently.

Cover:

```text
ShufflePlanner
RepeatPolicy
queue transformations
playlist bulk-operation ordering
selection logic
backup serialization
state reducers
```

---

## Repository/database tests

Cover:

```text
insert
move
copy
delete
transactions
migrations
restore
playlist ordering
```

---

## Source-boundary tests

Using fakes:

```text
SearchProvider
AudioResolver
```

Test UI/domain behavior independently of YouTube.

---

## Integration canary

Small real network check.

---

## Full regression

Run:

- before releases;
- after compatibility patches;
- after dependency changes;
- after schema migrations.

---

# 46. ROLLBACK / FORWARD-RECOVERY STRATEGY

Maintain Git tags for known-good releases.

Suppose:

```text
v1.7 good
v1.8 bad
```

Because Android versioning can make installing an older APK awkward, support a **forward rollback**:

```text
checkout v1.7 source
assign versionCode > v1.8
build v1.9-rollback
```

Result:

```text
newer Android package version
+
last-known-good code
```

This provides a recovery path while keeping:

```text
applicationId
signing identity
data compatibility
```

Never delete known-good Git tags.

---

# 47. BUILD/RELEASE AUTOMATION

Long-term workflow:

```text
private GitHub repo
      ↓
GitHub Actions
      ↓
tests
      ↓
build
      ↓
sign
      ↓
private release artifact/APK
```

Do not automatically sign arbitrary unvalidated Codex output.

Signing is downstream of tests.

---

# 48. DEPENDENCY POLICY

Pin intentional versions.

Avoid:

```text
latest
+
SNAPSHOT
wildcard ranges
```

in a released build unless temporarily required for a known compatibility fix.

Before implementation, verify current stable versions of:

```text
Android Gradle Plugin
Kotlin
Compose
Lifecycle/ViewModel
Media3
Room 2.x
NewPipe Extractor
KSP
```

Do not upgrade everything merely because newer versions exist.

Criteria:

```text
Does upgrade solve a real problem?
Is it required for compatibility?
Is current version unsupported?
```

If no:

leave working dependency alone.

---

# 49. NEWPIPE LICENSING NOTE

NewPipe Extractor's repository currently states GPL-3.0 licensing and independent library use. citeturn364426view0

For the current use case:

```text
private repository
single user
no public distribution
```

Do not waste time building publication/legal compliance infrastructure.

Still retain:

```text
LICENSE / third-party notice information
```

so future context is not lost.

If distribution to others ever becomes a goal:

review licensing before distribution.

---

# 50. THINGS FUTURE CHATGPT MUST NOT DO

Do not:

- convert project to Flutter;
- convert project to React Native;
- build a backend;
- propose Firebase;
- propose Spotify authentication;
- propose Play Store publishing;
- add accounts;
- introduce Dagger/Hilt solely for architecture purity;
- split into many Gradle modules;
- rewrite correct shuffle using Media3 default behavior without preserving exact semantics;
- replace queue snapshot semantics casually;
- use destructive Room migration;
- change applicationId;
- change signing key;
- store expiring audio URLs permanently;
- allow YouTube extraction types into domain/UI contracts;
- invoke AI on every healthy compatibility check;
- auto-refactor unrelated components during YouTube repair;
- spend user's limited learning time on Gradle/extractor internals.

---

# 51. USER LEARNING DECISION RULE

Whenever implementation hits unfamiliar technology, classify it.

Ask:

> Will understanding this materially improve:
> 1. reasoning about the program,
> 2. reviewing AI changes,
> 3. debugging,
> 4. Bioinformatics/software skill?

If **yes**:

spend approximately 10–30 minutes learning it.

If **no**:

assign it to Codex.

Examples:

```text
Why many-to-many needs PlaylistEntry?
→ USER LEARNS

Room annotation trivia?
→ CODEX

Why random choice is bad shuffle?
→ USER LEARNS

YouTube player deciphering?
→ CODEX

What does suspend mean?
→ USER LEARNS

MediaSessionService lifecycle boilerplate?
→ CODEX

How Git revert works?
→ USER LEARNS

Gradle plugin internals?
→ CODEX
```

---

# 52. FUTURE P1 FEATURES

After v0.1 is stable, highest-value candidates:

## Local audio-file support

Would provide content independent of YouTube.

Potential abstraction:

```text
sourceType = LOCAL
```

This is probably the strongest future enhancement.

## Find Replacement

For unavailable YouTube track:

```text
search title + uploader
→ user selects replacement source
```

## Recently Played

Cheap virtual history playlist.

## Sleep timer

Useful but not core.

## Basic sorting

```text
manual
title
date added
```

Do not implement automatically before v0.1.

---

# 53. DEFINITION OF "RELIABLE"

Do not interpret "reliable 24/7" as an impossible promise that YouTube will never change.

For this project, reliability means:

```text
local app launches reliably

library remains accessible even if YouTube fails

playlists are not lost

shuffle/repeat behavior is deterministic according to spec

temporary errors retry sensibly

failure is observable rather than silent

upstream compatibility breakage is detected quickly

repairs are isolated

known-good rollback exists
```

We cannot guarantee a third-party service will never become incompatible.

We can guarantee the architecture fails gracefully and is repairable.

---

# 54. NEXT CHATGPT — FIRST RESPONSE/WORKFLOW

When this document is given to a fresh ChatGPT, the next ChatGPT should:

1. Confirm it has read the entire document.
2. Treat Day 1 as the immediate objective unless user says otherwise.
3. Do **not** re-litigate the architecture.
4. Verify only time-sensitive implementation facts:
   - Android Studio/current SDK;
   - current Samsung A36 API level via ADB;
   - current stable Room 2.x;
   - current stable Media3;
   - current usable NewPipe Extractor release;
   - Kotlin/Compose/AGP compatibility.
5. Guide the user through Day 1 sequentially.
6. Keep the user's hands-on learning limited to the planned high-value concepts.
7. Give Codex explicit scoped prompts for implementation-heavy steps.
8. Never ask the user to learn unnecessary Android internals.
9. Commit known-good states frequently.
10. Update `AI_MAINTENANCE.md` as the real implementation diverges from this conceptual plan.

---

# 55. DAY 1 IMMEDIATE EXECUTION ORDER FOR THE NEXT CHAT

The first real session should proceed in this order:

```text
1. Verify development computer OS/environment.

2. Verify/install:
   Android Studio
   Git
   Codex

3. Connect Samsung A36.

4. Check Auto Blocker if ADB fails.

5. Enable Developer Options / USB debugging.

6. Run:
   adb devices

7. Query phone Android/API version.

8. Create native Kotlin/Compose project.

9. Choose unique permanent applicationId.

10. Run default app on physical Samsung.

11. Initialize Git.

12. Push private baseline repository.

13. Teach user only required Kotlin differences.

14. User creates/understands:
    Track
    Playlist
    RepeatMode
    ShufflePlanner
    RepeatPolicy

15. User creates core tests.

16. Run tests.

17. Commit.

18. Give Codex architecture-skeleton prompt.

19. Inspect Codex diff.

20. Run tests/build.

21. Commit known-good Day 1 state.
```

Do not skip ahead to YouTube extraction before this foundation works.

---

# 56. PROJECT SUCCESS CONDITION

The project succeeds when the user can treat the application as an appliance:

```text
tap app
find music
play music
manage playlists
shuffle correctly
repeat exactly as desired
close/lock phone
music continues
return later
state remains coherent
```

and when future breakage approximately becomes:

```text
compatibility canary fails
        ↓
diagnostic evidence exists
        ↓
Codex patches isolated source layer
        ↓
regression tests pass
        ↓
new APK
```

rather than:

```text
application broke
→ rebuild everything
```

---

# 57. FINAL FROZEN DECISIONS SUMMARY

Unless verified circumstances force reconsideration:

```text
PLATFORM:
Native Android

DEVICE:
Samsung Galaxy A36 only

LANGUAGE:
Kotlin

UI:
Jetpack Compose

ASYNC/STATE:
Coroutines + StateFlow/ViewModel

PLAYBACK:
AndroidX Media3 / ExoPlayer

BACKGROUND PLAYBACK:
MediaSessionService

DATABASE:
Stable Room 2.x / SQLite

BACKUP:
Versioned JSON

EXTRACTION:
NewPipe Extractor initially

SEARCH:
Extractor-backed SearchProvider

AUDIO:
Extractor-backed AudioResolver

BACKEND:
None

CLOUD:
None

ACCOUNTS:
None

ANALYTICS:
None

ADS:
None intentionally implemented

SHUFFLE:
Uniform permutation, exactly once per cycle

REPEAT:
Play Once / Repeat Once / Repeat Forever

QUEUE:
Persistent snapshot separate from playlists

BACKUPS:
Automatic + manual + restore

DIAGNOSTICS:
Local only

MAINTENANCE:
Deterministic canary + scoped Codex repair

AUTOMATED CORE REFACTOR:
Forbidden

ROLLBACK:
Known-good Git tags + forward rollback APK

PERSONAL LEARNING:
3–4 days core

OPTIONAL CV LEARNING:
Git / SQL+APIs / testing+CI through Day 7
```

---

# 58. FINAL NOTE TO FUTURE CHATGPT

The user does **not** need another roadmap brainstorming session.

This project has already spent substantial effort on architecture review.

The next highest-value action is implementation.

Prefer:

```text
working tested vertical slice
```

over:

```text
more hypothetical architecture
```

Proceed incrementally.

Protect user data.

Preserve the invariants.

Keep extractor-specific instability isolated.

Teach only what is worth the user's time.

Use Codex aggressively for Android-specific implementation work.

**Begin with Day 1.**