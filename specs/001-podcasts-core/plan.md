# Plan 001 – Podcasts Core

Implements `spec.md`. Constrained by `.specify/memory/constitution.md`.

## 1. Stack
| Concern | Choice | Why |
|---|---|---|
| Language/UI | Kotlin, Jetpack Compose (Material 3) | Constitution §7; same as launcher so theme code is shared |
| Build | Gradle Kotlin DSL, version catalog, AGP current stable | Copy versions from `athena-launcher/gradle/libs.versions.toml` as the starting point |
| SDK | minSdk 36, target/compile 37 | Constitution §6 |
| Playback | AndroidX Media3 (ExoPlayer, `MediaSessionService`, session UI notification) | Official, handles audio focus, noisy-audio, notification, lock screen, Bluetooth |
| Background | WorkManager (periodic refresh + download workers) | Doze-safe, survives reboot |
| HTTP | OkHttp | Range requests, conditional GET, redirects |
| Persistence | Room (feeds, episodes), DataStore Preferences (settings) | Relational episode data; flat settings as in launcher |
| Secrets | Android Keystore + encrypted file/DataStore wrapper (own thin wrapper around `Cipher` AES-GCM) | Jetpack Security-crypto is deprecated; avoid it |
| RSS | Own `XmlPullParser` parser (T2.1 may swap for a library) | Small, testable, no dependency |
| Images (step 2) | Coil 3 (Apache-2.0) with a grayscale transformation | Compose-native cache/downscale; justify again at T6.1 |
| State / DI | ViewModel + StateFlow, manual `AppContainer` | As launcher |
| Serialization | kotlinx-serialization-json (settings export) | As launcher |
| Tests | JUnit 4 + Turbine (unit), AndroidX Test (instrumented), MockWebServer (feeds/downloads) | |

## 2. Module layout
Single module `app`; packages enforce boundaries.

```
se.anders.athenapodcaster
├─ MainActivity            (single activity, edge-to-edge)
├─ core/        AppContainer, dispatchers, Result types
├─ data/
│   ├─ feeds/     FeedRepository, RssParser, OpmlReader/Writer, FeedFetcher (OkHttp)
│   ├─ episodes/  EpisodeRepository, EpisodeDao
│   ├─ db/        Room database, entities
│   ├─ download/  DownloadWorker, DownloadPolicy, StorageGuard
│   ├─ refresh/   RefreshWorker, scheduling
│   ├─ secrets/   SecretStore (Keystore AES-GCM)
│   ├─ settings/  SettingsRepository, SettingsBackup
│   └─ launcher/  LauncherAppearanceSource (bridge client, plan §3.7)
├─ playback/    PlaybackService (MediaSessionService; MediaLibraryService from Phase 8), AutoLibrary, PlayerController, SkipMath
├─ domain/      EpisodeIdentity (guid/dedupe), DownloadSelection (latest N), Resume
├─ ui/
│   ├─ feeds/    FeedListScreen, AddFeedScreen
│   ├─ episodes/ EpisodeListScreen, EpisodeRow
│   ├─ player/   PlayerScreen
│   ├─ settings/ SettingsScreen, LicencesScreen
│   └─ theme/    Theme.kt (shared tokens), fonts
```

## 3. Key technical decisions

### 3.1 Playback (FR-020..025)
- `PlaybackService : MediaSessionService` owns an `ExoPlayer` configured with `setSeekBackIncrementMs(10_000)` / `setSeekForwardIncrementMs(30_000)`, `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)` with `handleAudioFocus = true`, and `setHandleAudioBecomingNoisy(true)`.
- Media3's default notification/lock-screen controls expose seek back/forward using those increments; verify they render as 10/30 (T3.3), else add custom `CommandButton`s.
- UI uses a `MediaController` bound to the session; `PlayerController` maps to `StateFlow<PlayerState>`.
- Media items only come from local `file://` URIs (FR-024); no network data source is configured on the player, so streaming is impossible by construction.
- Position: a coroutine ticks every 5 s while playing, plus `onIsPlayingChanged(false)` and `onPlaybackStateChanged(ENDED)` write to Room. Resume seeks to the stored position (minus 2 s rewind, optional).
- `SkipMath` (pure) clamps seeks; unit-tested.

### 3.2 Feeds & parsing (FR-001, 010, 011, NFR-007)
- `FeedFetcher` uses OkHttp with gzip, redirects (max 5), `If-None-Match` / `If-Modified-Since` (FR-035), 30 s timeouts, size cap (e.g. 20 MB).
- `RssParser` (streaming `XmlPullParser`): channel title/description/image; item guid, title, pubDate (RFC 822 variants tolerated), `itunes:duration` (secs, mm:ss, hh:mm:ss), `<enclosure url type length>` with audio/* only. Tolerates missing guid (fallback to enclosure URL), BOMs, wrong encodings, HTML in titles.
- `EpisodeIdentity` decides insert/update/skip; refresh updates metadata but never touches position/download/played fields.

### 3.2b Learned in Phase 2
- Android's `Xml.newPullParser()` enables namespace processing; the parser forces it off so `itunes:*` names match. A JVM test runs with it on (it failed without the fix).
- Cleartext http is allowed by `network_security_config.xml` and gated in the app: an http feed needs a confirmation and a private feed must be https (NFR-004). TLS errors are never bypassed.
- Android 17 blocks private-network addresses (e.g. the emulator host `10.0.2.2`) without `ACCESS_LOCAL_NETWORK`. Only the debug manifest declares it (grant with `adb shell pm grant … android.permission.ACCESS_LOCAL_NETWORK`); release builds do not.
- Private feed URLs live only in `SecretStore` (Keystore AES-GCM); the table keeps `https://host/…`, a SHA-256 `urlHash` for duplicate detection and a `secretRef` (DB v2).

### 3.3 Paid/private feeds (FR-002, US4, Constitution §4)
- v1 mode: private URL with the token inside (confirmed for both feeds). HTTP Basic is deferred (spec §8); the fetcher keeps a seam for per-feed headers.
- The feed URL is a secret: stored in `SecretStore`, never sent anywhere except as the request to itself. Enclosure URLs are fetched as given (often signed or same-host); they may also carry tokens, so they get the same redaction in logs.
- Redirects: follow, but a test (`MockWebServer`) checks the token URL is not leaked in `Referer` or logs.
- Logging: a redacting `HttpUrl` formatter (`https://host/…`) is the only way URLs are logged; tokens never appear in logs, errors or crash text.
- A 401/403/410 on a paid feed sets `lastError = AuthFailed` (token revoked or expired) and posts the single notification (FR-036).
- **Spike T4.1:** fetch the user's two real feeds before deepening the UI.

### 3.4 Downloads & refresh (FR-030..036)
- `RefreshWorker` (periodic, `NetworkType.CONNECTED`/`UNMETERED` per setting, `setRequiresCharging` per setting): fetch each feed, upsert episodes, then compute `DownloadSelection` (latest N not yet downloaded and not played) and enqueue unique `DownloadWorker`s (`enqueueUniqueWork("dl-<episodeId>", KEEP)`).
- `DownloadWorker` (`CoroutineWorker`, foreground info for progress notification, `setExpedited` when manual): streams to `<file>.part`, resumes with `Range: bytes=<size>-` if `.part` exists and the server answers 206, verifies length when known, renames atomically, updates DB state/progress (throttled), retries with exponential back-off (`Result.retry()`), gives up after N attempts with a visible error state.
- `StorageGuard`: checks free space (≥ file size + safety margin) before starting; one-time user notice otherwise.
- Cleanup: played-and-expired episodes removed by a small `CleanupWorker` after refresh (FR-013, FR-034).
- Cost control: ETag/Last-Modified per feed; refresh interval default 6 h with flex window.

### 3.5 Data (spec §6)
- Room entities as in spec; `Episode(feedId, guid)` unique index. Download state enum: `None, Queued, Downloading, Done, Failed`. Migrations tested from day one (`exportSchema = true`).
- Credentials/tokens in `SecretStore`: AES-GCM key in Android Keystore, ciphertext in a private file; `Feed.secretRef` is only an id.

### 3.6 Theme & fonts (FR-060, 061)
- Copy (not link) `Theme.kt` colour schemes, `TypefaceChoice`, `ThemeMode` and the six bundled fonts + OFL licence files from Athena Launcher into this project. Duplication is deliberate for v1 (two small apps); extract an `athena-design` Gradle module later if a third app appears.
- Same `Density(fontScale * textScalePercent/100)` approach as the launcher for text size.

### 3.7 Following the launcher's settings (FR-062, FR-063) — cross-project
Cross-app options considered:
1. **Read-only ContentProvider in the launcher** (chosen). Authority `se.anders.athenalauncher.appearance`, a single URI `content://…/appearance` returning one row with `themeMode`, `typeface`, `textScalePercent` and a `version` column; `notifyChange` when those settings change so Podcasts can observe via `ContentObserver`. Protected by a **signature-level custom permission** (`se.anders.athenalauncher.permission.READ_APPEARANCE`) so only apps signed with the same key can read it; Podcasts declares `<uses-permission>` and a `<queries>` entry for the provider.
2. Shared file or `Settings.System`: needs storage/WRITE_SETTINGS permissions; rejected.
3. Manual JSON import (FR-063): always available as fallback; works with no launcher changes.

Consequences:
- Requires a small **launcher spec/code change** (new requirement in the launcher's spec, e.g. FR-070 "Appearance bridge", plus tasks there). Do this in the launcher repo following its own spec-first rule; this project's tasks only list the client side and a tracking task (T5.0).
- Launcher adds no network and no new data beyond three non-sensitive values (Constitution §3 intact).
- Signing: no extra setup for testing. Gradle debug builds are signed automatically with `~/.android/debug.keystore`, shared by every project on this Mac, so both sideloaded debug APKs match. A mismatch would only happen if one app were built on another machine/CI or one is a release build and the other debug. For release/sideloaded production builds, create one keystore and sign both with it (T7.5). The bridge fails soft: a signature mismatch just means Podcasts uses its local settings.
- Failure modes (provider absent, permission denied, old launcher version, null columns) all resolve to local settings; unit-tested via a fake `AppearanceSource`.
- Live updates: `ContentObserver` → `Flow<Appearance>`; the theme recomposes without restart.
- Precedence: `followLauncher && source available → launcher values`, else local values. Effective values are never written back over local ones, so toggling "Follow launcher" off restores the last local choice.

### 3.8 Artwork (step two, FR-040..043)
**Built (2026-11-05):** no Coil. `ArtworkStore` downloads each picture once into `filesDir/art/<hash>` (so it works offline, which a cache-header-driven image library does not guarantee), decodes it at the drawn size with a power-of-two sample factor, and caches decoded bitmaps in a small LruCache. Grayscale is one shared colour matrix (`GRAYSCALE_MATRIX`) used by Compose and for the lock-screen bytes. The notes below are the original plan.
- Parse `itunes:image href` (channel + item) and `<image><url>`. Coil with disk cache (cap ~100 MB, setting later) and a `GrayscaleTransformation` (ColorMatrix saturation 0, contrast 0.75, same as launcher's Grayscale icons) behind an `ArtStyle` seam.
- MediaSession metadata artwork: pass the downscaled bitmap/URI to `MediaMetadata` so notification and lock screen show it.
- Fetched with the feed refresh (same network policy), so art is available offline.

### 3.9 Edge cases to handle
- Feed moves/permanent redirect (update stored URL on 301); enclosure URL changes with same guid; episodes removed from feed (keep local copy).
- Huge episodes (> 500 MB) and servers without Range support (restart from zero, with a clear state).
- Process death during download/playback; Bluetooth disconnect; phone calls; sample rate/VBR duration oddities.
- Locale/time zone, font scale 200 %, light/dark switch while playing.
- Airplane mode: everything listed still plays (offline-first).

### 3.10 Android Auto (US7, FR-080..089)
- **Service type.** `PlaybackService` becomes a `MediaLibraryService` (same Media3 artifact `media3-session`, no new dependency). The manifest filter changes to `androidx.media3.session.MediaLibraryService` (keep the `MediaSessionService` action too, so the phone's `SessionToken` keeps binding). `onGetSession` returns a `MediaLibrarySession`; the existing `SkipPlayer`, session activity, media-button preferences and `ProgressSaver` stay as they are.
- **Declaration.** `res/xml/automotive_app_desc.xml` with `<uses name="media"/>` and `<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>` in the application element.
- **Library tree.** A small pure `AutoLibrary` class (in `playback/`) answers `getLibraryRoot`, `getChildren(parentId, page, pageSize)` and `getItem(id)` from the existing DAOs. The tree is flat: root (`root`, browsable) → up to 50 playable episode items from one query (downloaded, newest `publishedAt` first, joined with the podcast title); an empty result returns one non-playable "Nothing downloaded yet" item. Episode media ids stay the plain numeric id the phone uses. Root is marked list-style via `MediaConstants` content-style extras. Art uses `artworkData` from the local `ArtworkStore` (512 px JPEG, chosen style), never a URL.
- **Playing from the tree.** `onAddMediaItems` resolves a requested `episode:<id>`/numeric id to the full local `MediaItem` (file URI, metadata, saved position) through the same builder as `PlayerController`; a missing file returns an error result (`SessionError.ERROR_BAD_VALUE`) with the message "Episode not downloaded". No other URI is ever accepted, so the file-only guarantee (FR-024) holds for browsing too.
- **Trust.** `onConnect`/`MediaLibrarySession.Callback` checks `controllerInfo`: the app itself, `isTrusted` system controllers, and Android Auto's package (`com.google.android.projection.gearhead`, plus `com.google.android.googlequicksearchbox` for the Assistant) may browse; other controllers get playback commands but `getLibraryRoot` returns `ERROR_PERMISSION_DENIED` (FR-088).
- **Transport.** Reuse the existing back-10/forward-30 `CommandButton`s as custom layout and the next/previous mapping in `SkipPlayer`; Auto shows them on the player screen. Nothing else is added there (FR-090). Touch-target size belongs to the head unit/mount; it cannot be changed from the app.
- **Updates.** When a download finishes or a file is deleted, call `notifyChildrenChanged` for the root so an open car list refreshes (observe the same Room flows as the phone UI, cheap and local).
- **Limits.** `getChildren` pages by `pageSize` (episodes capped at 50 per podcast); queries are indexed lookups on `downloadedPath`/`feedId`. A 10 k-episode feed is covered by an emulator test (FR-086).
- **Queue in the car (Phase 9, FR-107): view only.** The car's Queue button reads the player's timeline (current + upcoming), so nothing extra is needed; there is no add/remove path from the car, and a media id that is not an episode is refused.
- **Testing.** JVM tests for `AutoLibrary` tree shapes, empty states, the cap of 50 and newest-first ordering across podcasts. Emulator tests: a `MediaBrowser` connected to the service lists the tree, plays an item, is refused for a missing file, and an untrusted caller is denied browse. Manual: Desktop Head Unit (Android SDK "Android Auto Desktop Head Unit Emulator", started with `adb forward tcp:5277 tcp:5277` and Android Auto's developer-mode "Start head unit server"), then a real car. Documented in `docs/manual-qa.md`.
- **Risks.** Android Auto may cache the tree (mitigated by `notifyChildrenChanged`); sideloaded apps need the Unknown-sources switch (FR-089); behaviour on Android 17 with the current Android Auto build is only known by trying it on the DHU. Voice search and Automotive OS are separate specs.

### 3.11 Queue (US8, FR-100..108)
- **Data.** Room table `queue_items(episodeId PK → episodes.id ON DELETE CASCADE, position INTEGER)`, DB version 4 with `MIGRATION_3_4` (schema exported, migration test). A `QueueRepository` offers `observe()`, `add`, `remove`, `moveUp/moveDown`, `clear`, `purgeUnplayable()`; reading joins `episodes` and keeps only `downloadState = 'Done' AND played = 0`, so a stale row can never reach the player. Reordering renumbers positions in one transaction.
- **Player mirrors the queue.** The service player's playlist is `[current] + upcoming`. `QueueSync` in the service watches the queue flow and the current item; whenever the items after the current one differ from the queue it calls `replaceMediaItems(current + 1, end, ...)` (a no-op when equal, which also stops loops). Upcoming items are built with `episodeMediaItem` and small (160 px) cover bytes so the timeline stays well under the Binder limit; the current item keeps the 512 px cover. ExoPlayer then advances gaplessly by itself, and Auto's Queue button reads the timeline natively (`COMMAND_GET_TIMELINE` is forwarded by `SkipPlayer`).
- **Transitions.** `ProgressSaver` gains `onPositionDiscontinuity`: for `DISCONTINUITY_REASON_AUTO_TRANSITION` the old item is marked played (`progressFor(..., ended = true)`); for other reasons (skip to another entry) its position is saved. The new current item is removed from the DB queue. `STATE_ENDED` of the last item works as today.
- **Play now.** `PlayerController.play` and the car's `onSetMediaItems` still replace the playlist with one item; `QueueSync` then re-appends the queue (FR-104). Nothing clears the queue except the Clear button.
- **Purging.** `Downloads.markPlayedAndDelete`, `markAllPlayedAndDelete`, delete-download, cleanup and unsubscribe call `purgeUnplayable()`; playback of a missing file already repairs state (FR-024) and drops it.
- **UI.** `Screen.Queue`; `QueueScreen` with a `FlowRow` of text buttons per row (48 dp targets, same pattern as the episode rows); `Queue (n)` on the podcast list; `Add to queue` / `Remove from queue` in `EpisodeListScreen` and `PlayerScreen`. Strings and TalkBack labels like "Move up, <title>".
- **Testing.** JVM: queue ordering and move rules in a pure `QueueOrder` helper (add, remove, move up/down at ends, purge). Emulator: migration 3→4; queue repository against Room (cascade, purge, join filter); a service test queues a second episode, lets the first reach its end (a short sample file, seek near the end) and sees the second start with the first marked played; play-now keeps the queue; the Auto browser's timeline shows the upcoming items. Manual: lock screen cover after advance, Auto Queue button on the DHU, bike.
- **Risks.** Playlist edits while the player is preparing; cover bytes in a long timeline (mitigated by the 160 px size and a cap of the first 10 upcoming items mirrored in the player; the rest wait in the DB); a transition and a user skip arriving together (the discontinuity reason decides).

### 3.12 Home-screen widget (FR-120)
- `PlayerWidget : AppWidgetProvider` with a plain `RemoteViews` layout (`widget_player.xml`: title `TextView` + `ImageButton`); no Glance, so no new dependency. `res/xml/player_widget_info.xml`: `targetCellWidth=4`, `targetCellHeight=1`, `resizeMode=none`, `updatePeriodMillis=0`.
- State is pushed, not polled: `PlaybackService` has a small player listener that stores title + playing in the in-memory `WidgetState` and calls `PlayerWidget.refresh`; `onDestroy` resets it to idle. `onUpdate` renders from `WidgetState`.
- Play/Pause: a broadcast PendingIntent to `PlayerWidget`; the receiver connects a `MediaController` (`goAsync`), toggles `playWhenReady`, releases it. When nothing is loaded the button and the title open `MainActivity` instead (an activity cannot be started from a broadcast receiver in the background).
- Colours: `widget_bg`/`widget_fg` in `values` and `values-night`.
- Testing: JVM test for the pure `WidgetState` → text/label mapping; emulator test that a toggle intent starts/pauses playback. Looks checked by adding the widget on the emulator.

## 4. Dependencies (justify each)
androidx.core, activity-compose, lifecycle-viewmodel-compose, compose BOM + material3, **media3-exoplayer / media3-session / media3-ui** (playback), **work-runtime-ktx** (background), **room-runtime/room-ktx + KSP room-compiler** (episode DB), **okhttp** (+ `mockwebserver` test) (HTTP), datastore-preferences, kotlinx-serialization-json, junit, turbine; step two: **coil-compose + coil-network-okhttp**. Nothing else without a plan amendment.

## 5. Testing strategy
- Unit: `RssParser` (fixture feeds incl. malformed, no guid, odd durations, 10k items), OPML, `EpisodeIdentity`, `DownloadSelection`, `SkipMath`, `Resume`, settings backup, `Appearance` precedence, redaction.
- Network (MockWebServer): conditional GET, 301, gzip, 401 → AuthFailed, token URL never logged, Range resume.
- Instrumented (API 37 emulator): playback service + controller (play/pause/seek 10/30 on a test MP3 asset), WorkManager test driver for refresh → download chain, Room migrations, launcher bridge against a fake provider.
- Manual checklist `docs/manual-qa.md`: lock screen, headset buttons, Bluetooth, call interruption, reboot with queued downloads, airplane mode, real paid feeds, 200 % font.

## 6. Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Paid feed behaves unlike a plain RSS feed (expiring tokens, odd enclosure hosts) | Med | Spike T4.1 with the real feeds before building more UI |
| Background downloads killed by OEM battery policies | Med | WorkManager + expedited work; manual QA on the real phone; settings hint |
| Media3 default controls not showing 10/30 s | Low | Custom `CommandButton` fallback (T3.3) |
| Launcher bridge needs launcher changes and same-key signing | Med | Manual JSON import fallback (FR-063); cross-repo task T5.0 |
| Untested on Android 16 | Med | Accepted (as in launcher) |
| Large feeds / memory | Low | Streaming parser, size caps, paging in the episode list |
