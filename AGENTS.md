# Repository Instructions

Read `PROJECT_CONTEXT_AND_ROADMAP.md` completely before changing any file. Read `AI_MAINTENANCE.md` before changing architecture, dependencies, persistence, playback, source adapters, build, or release behavior.

The roadmap's frozen decisions and all 36 permanent invariants are binding. Preserve the permanent application ID `com.admin.mymusicplayer`.

- Make the smallest scoped change that satisfies the request. Do not refactor unrelated working code or add P1 features before v0.1.
- Keep NewPipe/extractor types behind `SearchProvider` and `AudioResolver`; never persist temporary stream URLs.
- Treat playlists and the persistent playback queue as separate data. Preserve exact shuffle, repeat, transactional bulk-ordering, startup-isolation, and stale-work invariants.
- Never use destructive production Room migrations. Do not change the database schema, backup format, application ID, signing identity, or version/update behavior casually or during autonomous source repair.
- Never attempt DRM, authentication, bot/access-control, age/region, private/members-only, or copying-restriction circumvention. Record the restriction and stop that source path.
- Inspect the worktree first and preserve user changes. Avoid unrelated dependency upgrades.
- Run the narrow relevant tests while iterating, then the applicable regression/build tasks. Do not claim completion from code inspection alone.
- Update `AI_MAINTENANCE.md` whenever implementation reality, paths, versions, schema, commands, source boundaries, release state, canary behavior, or known issues change.
- Make small intentional commits only after a milestone is verified. Do not publish or push remotely unless the user explicitly asks.

