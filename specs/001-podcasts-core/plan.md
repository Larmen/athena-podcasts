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
├─ playback/    PlaybackService (MediaSessionService), PlayerController, SkipMath
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
