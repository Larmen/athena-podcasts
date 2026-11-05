# Tasks 001 – Podcasts Core

Order matters. Each task leaves the app building. `[P]` = can run in parallel with siblings. Tick when done; note date and what was verified.

## Phase 0 – Decisions & scaffold
- [x] T0.1 Spec framework in place: constitution, spec, plan, tasks, CLAUDE.md, README, LICENSE (2026-11-05)
- [x] T0.2 Open questions in spec §10 all resolved (2026-11-05); autoplay deferred to a future spec
- [x] T0.3 (2026-11-05: AGP/Kotlin/Compose versions copied from the launcher; `assembleDebug testDebugUnitTest lintDebug` pass; not yet run on a device) Gradle project: Kotlin DSL, version catalog (start from the launcher's versions), minSdk 36 / target 37, Compose, empty `MainActivity`, package `se.anders.athenapodcaster`; `assembleDebug testDebugUnitTest lintDebug` pass
- [x] T0.4 (workflow runs once `gradlew` exists, which it now does; untested on GitHub) CI: copy `.github/workflows/ci.yml` pattern from the launcher (already in this repo; enable once T0.3 exists)
- [x] T0.5 `AppContainer`, edge-to-edge scaffold and placeholder home; installed and run on the API 37 emulator (2026-11-05)

## Phase 1 – Theme & settings base (US5 base) · FR-060, 061, 064, 065
- [x] T1.1 (2026-11-05) Copied `Theme.kt` tokens, `ThemeMode`, `TypefaceChoice`, the six fonts (1.7 MB) and OFL/Apache/MIT licence texts from the launcher; `Licences` list + test that each text file exists
- [x] T1.2 (2026-11-05; verified on the API 37 emulator: dark theme, live font switch, text size 100→120 %; 7 JVM tests incl. DataStore) `SettingsRepository` (DataStore Preferences) with theme, typeface, text size; live theme + `Density` text scaling; system bars and window background match (no flash)
- [x] T1.3 (2026-11-05) Settings screen skeleton with Appearance and Backup sections; dark theme and typeface checked on the API 37 emulator, light theme and system-bar icons not viewed
- [~] T1.4 (2026-11-05) Export/import JSON (`app` = "athena-podcaster") with confirmation dialog, 7 unit tests (round trip, rejects the launcher's file, clamping). The file-picker flow on a device is not exercised yet

## Phase 2 – Feeds (US1) · FR-001, 003, 004, 005, 011, NFR-007
- [x] T2.1 (2026-11-05: own parser kept, no library needed; 11 tests incl. 10k items in <0.1 s) `RssParser` on `XmlPullParser` + fixture-based unit tests (malformed, no guid, odd durations, huge feed). Decide: own parser vs library
- [x] T2.2 (2026-11-05; schema v1 exported to `app/schemas`, migration test harness not yet needed) Room database (Feed, Episode), DAOs, schema export, migration test harness
- [x] T2.3 (2026-11-05; 10 tests) `FeedFetcher` (OkHttp: gzip, redirects incl. 301 URL update, ETag/Last-Modified, size cap) + MockWebServer tests
- [x] T2.4 (2026-11-05; 6 JVM merge tests + 8 instrumented repository tests on the API 37 emulator) `FeedRepository.add/refresh/remove`, `EpisodeIdentity` de-duplication (never loses position/download state) + tests
- [x] T2.5 (2026-11-05; verified on the API 37 emulator: http confirmation, add, error text; refresh is a button, changed from pull-to-refresh on 2026-11-05) Add-feed screen (URL → validate → save) and feed list screen (FR-005). Private-feed checkbox rejects http
- [x] T2.6 (2026-11-05, verified on emulator: date, duration, state) Episode list screen with state text (FR-010). No played toggle or All/Unplayed filter yet (T3.4b)
- [~] T2.7 (2026-11-05) OPML import/export in Settings; private feeds excluded unless ticked. Reader/writer unit-tested; the file-picker flows are not exercised on a device

## Phase 3 – Playback (US2) · FR-020..025
- [x] T3.1 (2026-11-05; Media3 1.11.1; verified on the API 37 emulator) `PlaybackService` (Media3 `MediaSessionService`), manifest + foreground-service types, ExoPlayer with 10 s / 30 s increments, speech audio attributes, audio focus, becoming-noisy
- [x] T3.2 (2026-11-05) `PlayerController` (MediaController → StateFlow). No `SkipMath`: ExoPlayer clamps seeks to 0 and the end itself, and the emulator test checks +30 s / -10 s
- [x] T3.3 (2026-11-05) Player screen: play/pause, back 10, forward 30, elapsed/remaining and a non-draggable progress bar (no gestures). The media session exposes "Back 10 s" and "Forward 30 s" actions and the notification has three actions; the lock screen and headset buttons were not tried by hand
- [x] T3.4 (2026-11-05; 6 JVM tests + emulator tests; verified: playing to the end marks played) Position persistence every 5 s + on pause/stop; resume; auto-complete at end / last 15 s → played, position reset (FR-014, FR-022) + tests
- [x] T3.4b (2026-11-05) Mark played/unplayed buttons in the player and on each episode row, "Played" state text, a Showing: all / unplayed button. Played rows are not yet drawn muted in the list apart from the title colour
- [x] T3.5 (2026-11-05) Only downloaded local files play (the player has a file-only data source, tested); a missing file resets the episode to "not downloaded". Debug-only "Add sample audio" button writes a 5-minute test WAV, to be removed with T4.3
- [~] T3.6 (2026-11-05) Emulator tests: play/pause, +30 s / -10 s, saved position, resume, http source rejected (3 tests). Media keys (pause, play, play/pause, headset hook, next, previous, fast forward, rewind) tested with system key events (4 tests; they fail without the skip wrapper). Not tested: a real Bluetooth device, and call interruption

## Phase 4 – Downloads, refresh, paid feeds (US3, US4) · FR-002, 030..036
- [ ] T4.1 **Spike — waiting for you**: add your two paid feeds yourself in the app (tick "Private (paid) feed"), then check that refresh works and the latest episode downloads. Everything it needs is built and tested against local servers (2026-11-05); no real paid feed has been tried. **Spike**: add the user's two paid feeds (private URLs), fetch and download one episode each; record results in plan §3.3
- [x] T4.2 (2026-11-05; 4 emulator tests on the real Keystore, DB v1→v2 migration test, redaction tests) `SecretStore` (Keystore AES-GCM) for private feed URLs (until then they sit in the app-private Room database; migrate existing private feeds when this lands), masked display with reveal, redaction helper for logs/errors + tests (FR-002, Constitution §4)
- [x] T4.3 (2026-11-05; 10 JVM downloader tests incl. a resume bug they caught, 6 emulator worker tests; verified on emulator) `DownloadWorker`: `.part` file, Range resume, atomic rename, progress, back-off retry, notification only while running (FR-032)
- [x] T4.4 (2026-11-05; 7 JVM tests, emulator chain test through the real WorkManager; verified: a new feed downloads only its latest episode) `DownloadSelection` (latest N per feed, first-subscribe limit) + `RefreshWorker` periodic scheduling and settings (FR-030, 031) + tests
- [~] T4.5 (2026-11-05) Wi-Fi-only (default) and charging-only settings drive the WorkManager constraints; a manual download on mobile data asks first. The dialog and the charging rule were not exercised on a device. Network/charging policy and manual download over mobile data confirmation (FR-033)
- [~] T4.6 (2026-11-05) Storage guard (needs file size + 100 MB free, else the episode shows "Download failed") and delete-after-played (done at the next refresh or app start, never for the episode now playing; emulator-tested) are in. **Deferred:** keep-at-most-X and the one-time low-storage notice. Storage guard, delete-after-played (FR-013, 034)
- [~] T4.7 (2026-11-05) Per-feed "last refresh failed" line and the one-time notification for a private feed that refuses us are in; `POST_NOTIFICATIONS` is asked for the first time you Add, Play or Download. The notification itself was not triggered on a device. Failure surfacing: per-feed last-error line; single auth-failed notification (FR-036); `POST_NOTIFICATIONS` just in time
- [~] T4.8 (2026-11-05) Chain tested with the real WorkManager on the emulator (WorkManager's synchronous test driver deadlocked). Reboot and process-death behaviour not checked by hand. WorkManager tests for refresh → download chain

## Phase 5 – Follow Athena Launcher (US5) · FR-062, 063
- [x] T5.0 (2026-11-05, launcher FR-070 / T9.1–T9.4) **Cross-repo**: add to the launcher's spec/plan/tasks a requirement "Appearance bridge" (read-only ContentProvider, signature permission, observer notifications); implement there following its own workflow. Track status here
- [x] T5.1 (2026-11-05; 8 JVM tests) `AppearanceSource` interface + fake; precedence logic (`followLauncher`) + unit tests
- [x] T5.2 (2026-11-05; 6 emulator tests against a stand-in provider: live values, change notification, refresh, permission denied, no provider, odd data) `LauncherAppearanceSource` (ContentResolver + ContentObserver → Flow); manifest `<uses-permission>` and `<queries>`; instrumented test against a fake provider
- [x] T5.3 (2026-11-05; verified on the emulator) "Follow launcher" switch in Settings (default on when launcher present); local values shown disabled while following
- [~] T5.4 (2026-11-05; parsing unit-tested, the file picker was not exercised on a device) One-off import of the launcher's export JSON (theme, typeface, text size only) (FR-063)
- [x] T5.5 (2026-11-05, API 37 emulator: the launcher's typeface changed to JetBrains Mono and the podcasts app followed within a refresh) Verify end to end on the emulator with both apps installed (both debug-signed on this Mac, so keys match); change typeface in the launcher → Podcasts updates

## Phase 6 – Album art (US6, step two) · FR-040..043
- [x] T6.1 (2026-11-05; DB v3 with migration test) Parse artwork URLs (channel + item); Room column + migration. No Coil: an own small `ArtworkStore` does the job (plan §3.8)
- [x] T6.2 (2026-11-05; 5 emulator tests) Show art is fetched with each refresh, episode art with its download; stored in app-private storage (offline afterwards), 5 MB cap, only real images kept; removed on unsubscribe
- [x] T6.3 (2026-11-05; verified on the API 37 emulator) Art in the podcast list, episode rows and player; Original / Grayscale setting, default Grayscale; no space is reserved when there is no art
- [~] T6.4 (2026-11-05) Artwork bytes (512 px JPEG, in the chosen style) are put in the MediaSession metadata, which gained its art entries on the emulator. The notification and lock screen were not looked at

## Phase 7 – Polish & release readiness
- [x] T7.1 (2026-11-05; verified on the emulator, at 200 % font too) Licences screen under Settings → About with every font and library; JVM tests check that every listed licence has its text file, that no licence file ships unlisted, and that every bundled font has an entry
- [~] T7.2 (2026-11-05) Done: unambiguous screen-reader labels on episode buttons ("Play, <title>"), headings, stepper and progress descriptions; button rows wrap instead of overflowing at 200 % font (found and fixed on the emulator: episodes page, podcast list, player); Material buttons keep 48 dp targets. Not done: a run with TalkBack itself
- [~] T7.3 (2026-11-05) 10,000-episode feed: parse <0.1 s, merge <1.5 s (JVM test), list and summary queries <2 s (emulator test). Not measured: cold start on the phone, and scroll smoothness of a long list
- [~] T7.4 (2026-11-05) Checklist written (`docs/manual-qa.md`); not yet run on the phone
- [~] T7.5 (2026-11-05) Sideload and shared-signing notes are in the README, with the `keytool` command. No release keystore was created (debug builds already share a key)
- [x] T7.6 (2026-11-05; verified on the API 37 emulator) Icon transport buttons (play, pause, skip 10 back, skip 30 forward), now-playing bar on the podcast list and episode screens, headset/Bluetooth next and previous mapped to the skips (FR-020, FR-021b, FR-021c)
- [x] T7.7 (2026-11-05; 4 JVM tests, stepped through on the emulator) Episode filter button now cycles All → Downloaded only → Unplayed (FR-014). "Mark all as played" counts over the whole podcast, not just what the filter shows
- [x] T7.8 (2026-11-05; found and fixed: the widget did nothing, the session had no activity to open) Tapping the media notification / Now Playing widget opens the player (FR-021a). Checked on the emulator by tapping the widget in the notification shade; one emulator test checks the session carries an activity. The lock-screen variant uses the same intent but was not tapped on a lock screen
- [x] T7.9 (2026-11-05; 3 new emulator tests, 2 new JVM tests) Fix: "Last refresh failed: Could not reach the server" stayed on every podcast after a failed automatic refresh although refreshing by hand worked. Connection failures are retried once, the scheduled refresh no longer records them, refreshes of one feed are serialized, and the message now names the real reason. Which automatic trigger failed on the phone was not established (a cold start at 13:23 refreshed all three feeds fine)

## Phase 8 – Android Auto (US7) · FR-080..089
- [x] T8.0 (2026-11-05) Decided: one flat list of downloaded episodes, newest first, every downloaded episode; glove use drives the design (FR-081, 090)
- [x] T8.1 (2026-11-05; 10 JVM tests incl. cap, order, paging, empty state, browse trust) `AutoLibrary` (flat root of downloaded episodes across podcasts, newest first; cap 50; empty state) + JVM tests (FR-081, 082, 086)
- [x] T8.2 (2026-11-05; the whole existing suite stayed green, the phone binds as before) `PlaybackService` → `MediaLibraryService` with `MediaLibrarySession` callback (`getLibraryRoot/getChildren/getItem/onAddMediaItems`); phone `PlayerController` still binds; existing PlaybackService/MediaKeys tests stay green (FR-080, 083)
- [x] T8.3 (2026-11-05; cover art in the chosen style, 160 px, local files only; lint's voice-search check is switched off on purpose) Manifest + `automotive_app_desc.xml`; art from the local store in the library items (FR-080, 085)
- [~] T8.4 (2026-11-05; a browser plays by id and starts at the saved position; a missing file or unknown id is refused and the episode is repaired. The refusal of other apps is a JVM test of the rule; no foreign app was connected) Trusted-controller check and refusal of missing files (FR-083, 088) + emulator tests with a `MediaBrowser` (list, play, missing file, untrusted caller)
- [x] T8.5 (2026-11-05; emulator test: a deleted download leaves the list) `notifyChildrenChanged` when downloads finish or files are deleted (FR-081)
- [x] T8.6 (2026-11-05; emulator, 10,000 downloaded episodes: a browse took under the 500 ms limit) Performance test: 10 k-episode feed browse < 500 ms (FR-086)
- [~] T8.7 (2026-11-05) Checked by the user on the Desktop Head Unit connected to the phone (browse, play, controls worked as intended). Not yet tried on the bike with gloves and handlebar keys; see docs/manual-qa.md. Run on the Desktop Head Unit and then the bike (US7, FR-090); decide the "ride mode" question (spec §10) after the bike test
- [x] T8.8 (2026-11-05) README (sideloading with Unknown sources, FR-089) and `docs/manual-qa.md` (real car, steering-wheel keys, phone call during playback, no network)

## Phase 9 – Play queue (US8) · FR-100..108
- [ ] T9.1 `QueueItem` entity + DAO, DB v4, `MIGRATION_3_4`, exported schema, migration test (FR-100)
- [ ] T9.2 Pure `QueueOrder` (add, remove, move up/down, purge) + JVM tests; `QueueRepository` with the joined, filtered read (FR-100, 101, 105)
- [ ] T9.3 Service: `QueueSync` mirrors the queue into the player's upcoming items (first 10, small covers); advancing marks the old episode played and removes the new one from the queue; `ProgressSaver` handles transitions and skips (FR-103, 104, 106, 108)
- [ ] T9.4 Purge hooks: mark played, mark all played, delete download, cleanup, unsubscribe, missing file (FR-105)
- [ ] T9.5 UI: Add/Remove from queue on episode rows and the player; `Queue (n)` button; `QueueScreen` with Play now / Remove / Move up / Move down / Clear (confirm); strings, labels, 200 % font check (FR-101, 102)
- [ ] T9.6 Android Auto: the Queue button shows the upcoming items and jumps to a chosen one; the browse list and play-now keep the queue (FR-104, 107)
- [ ] T9.7 Emulator tests (repository, advance-by-itself, play-now keeps queue, Auto timeline) and full suites, lint
- [ ] T9.8 Manual: lock screen after an advance, DHU Queue button, the bike; README and `docs/manual-qa.md`

