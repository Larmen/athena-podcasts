# Spec 001 – Podcasts Core

Status: Draft · Owner: Anders · Date: 2026-11-05

## 1. Summary
A minimalist Android podcast player that does the essentials: subscribe to RSS feeds (including two paid/private feeds), **automatically download** new episodes in the background, and play the downloaded files with play/pause, **back 10 s** and **forward 30 s**. No streaming. Album art is step two. The look (monochrome, typeface, text size, light/dark) matches Athena Launcher and follows its settings where possible.

## 2. Goals / Non-goals
**Goals**
- Reliable offline listening: new episodes are on the phone before I need them.
- Support the user's paid feeds (private feed URLs with a token).
- Look and feel identical in spirit to Athena Launcher; appearance configured in one place.

**Non-goals (v1)**
- Streaming of episodes that are not downloaded.
- Podcast discovery/search directories, recommendations, social features.
- Playback speed, sleep timer, chapters, equaliser, Chromecast/Wear (kept in mind, §8); Android Auto is added by US7 (Phase 8) and a play queue by US8 (Phase 9).
- Cloud sync, accounts, ads, analytics.
- Android 15 or older; Play Store distribution (sideload first).
- Paywalled content that needs an interactive web login (cookie/OAuth), and HTTP Basic-auth feeds (deferred, §8). v1 supports private-URL feeds only.

## 3. User stories

### US1 – Subscribe to feeds (P1)
As a user I add a podcast by pasting an RSS URL (or importing OPML) and see its episodes.
- Acceptance: valid feed → appears in the list with title and episodes; invalid/unreachable URL → clear error, nothing saved.

### US2 – Play downloaded episodes (P1)
As a user I open an episode and play/pause, jump back 10 s or forward 30 s, and it resumes where I left off.
- Acceptance: controls work from the app, lock screen, notification, headset and Bluetooth; audio continues with the screen off; position is remembered per episode.

### US3 – Automatic downloads (P1)
As a user, new episodes of my subscriptions are downloaded in the background without opening the app.
- Acceptance: with defaults (Wi-Fi only, charging not required), a new episode published while the app is closed is downloaded before I next open it; I can also download or delete an episode by hand.

### US4 – Paid/private feeds (P1)
As a user I can add my two paid feeds (private URLs with a token inside) so they behave exactly like public ones.
- Acceptance: a private feed URL can be pasted and added; refresh and downloads work; the URL is never shown in full or logged (Constitution §4).

### US5 – Appearance like the launcher (P2)
As a user the app looks like Athena Launcher and picks up its theme, typeface and text size, so I configure them once.
- Acceptance: change typeface in the launcher → the app shows it next time it is opened (or live); with the launcher absent, local settings apply.

### US6 – Album art (P2, step two)
As a user I see the show's and episode's artwork in the list, the player and the notification/lock screen.

### US7 – Listen in the car with Android Auto (P2)
As a motorcyclist riding in thick gloves I want to open Athena Podcasts in Android Auto and see one flat list of my downloaded episodes, newest first, so a single large tap starts the one I want, with play/pause and the skips on the car or mount screen and on my handlebar/helmet controls. No mobile data is needed because only downloaded episodes are offered. *Independent test:* with the Desktop Head Unit (and later on the bike), open the app, tap an episode in the list, play, pause, skip 10 s back and 30 s forward, and see the phone app show the same position afterwards.

### US8 – Queue episodes and listen in a row (P2)
As a listener I want to line up episodes, e.g. one of show A and then one of show B, so playback moves on by itself without me stopping to fiddle with the phone (on the bike, with gloves). The car's Queue button shows the same lineup. *Independent test:* queue two downloaded episodes from different podcasts on the phone, play the first to its end and hear the second start; the car's Queue button lists the second one.

## 4. Functional requirements

**Subscriptions**
- FR-001 Add a feed by URL. The app fetches and validates it (RSS 2.0 with `<enclosure>`; iTunes and Podcasting 2.0 namespaces read where useful, not required). Nothing is saved if parsing fails.
- FR-002 Private feeds: the feed URL itself carries the secret token. Feed URLs are stored in the encrypted secret store and shown masked (host only) with an explicit reveal. No credentials are sent as headers in v1, and the feed URL is never passed to any host other than itself (enclosure URLs from the feed are fetched as given).
- FR-003 Unsubscribe: removes the feed; asks whether to also delete its downloaded files.
- FR-004 Import and export subscriptions as OPML via the system file picker. Private feeds (URLs with tokens) are excluded from export unless the user explicitly includes them.
- FR-005 Feed list screen: title, unplayed/undownloaded counts as plain text, tap opens the feed. Calm: no badges.

**Episodes**
- FR-010 Episode list per feed, newest first: title, date, duration (if known), state (not downloaded / downloading n % / downloaded / played).
- FR-011 Episodes are identified by `<guid>` (fallback: enclosure URL). Refresh never creates duplicates and never loses position or download state.
- FR-012 Per episode: Download, Delete download, Mark played/unplayed. **Delete download** asks for confirmation first (a mis-tap, e.g. with gloves, must not lose a file); the dialog says the episode stays in the list and can be downloaded again, and that playback stops if it is the episode playing. Tapping an episode that is not downloaded offers Download; it never streams.
- FR-014 Played tracking. Every episode is either unplayed or played. **Auto-complete:** an episode becomes played when playback reaches its end (or within the last 15 s, so outros do not matter), and its position resets. **Manual:** a Played/Unplayed toggle button in the player and in each episode row (plain buttons, no long-press or swipe) sets the state either way. **Mark played** asks for confirmation first: the dialog says the downloaded file will be deleted, and confirming marks the episode played and deletes the file (a queued or running download is cancelled instead); the episode stays in the list and can be downloaded again. **Mark unplayed** needs no confirmation and does not bring the file back. Marking played also clears the saved position. **Mark all as played** (a button on each podcast's episode page, with a confirmation dialog that states the count and that downloaded files are deleted and downloads in progress cancelled) does the same for every unplayed episode of that podcast; episodes already played are left as they are. New episodes that appear later are unplayed and download as usual. Played episodes are shown muted with the word "Played"; unplayed episodes show "New" until opened and then their remaining time. The episode list has a filter button that steps through **All → Downloaded only → Unplayed** and back to All; a podcast's list **starts on "unplayed only"** (default, changed 2026-11-06 from "all episodes"), so what is left to hear is what you see first ("Showing: all episodes", "Showing: downloaded only", "Showing: unplayed only"); "downloaded" means the file is on the phone. The state survives refresh, re-download and app updates, and is never changed by a refresh. Marking an episode played also stops it from being auto-downloaded.
- FR-016 On a podcast's episode page, the episode that is playing shows **Pause** instead of Play (Play again when paused). Opening the page while one of its episodes is loaded scrolls the list to that episode, once per visit; the user's own scrolling is never overridden.
- FR-013 Played episodes: setting "Delete download after played" (default off). When on, deletion happens after the episode ends and after a short grace period.

**Playback**
- FR-020 Player screen: title, show, elapsed/remaining, a progress bar (not draggable in v1), **Play/Pause**, **Back 10 s**, **Forward 30 s**, all plain icon buttons (a circular arrow showing 10 or 30, play and pause symbols, each with a screen-reader label; no gestures). Skip amounts are fixed in v1 (constants, easy to change later). Skips clamp to 0 and to the episode end.
- FR-021 Playback runs in a Media3 `MediaSessionService` (foreground, media notification) so it continues with the screen off and is controllable from the notification, lock screen, headset buttons and Bluetooth (play/pause, ±skip mapped to 10 s back / 30 s forward).
- FR-021a Tapping the media notification or the lock-screen Now Playing widget opens the app on the player screen (on the lock screen the system asks to unlock first).
- FR-021b Headset, Bluetooth and car media keys: play, pause, play/pause and the headset hook control the player; **next** and **fast forward** skip 30 s forward, **previous** and **rewind** skip 10 s back (there is only one item, so next/previous would otherwise do nothing).
- FR-021c Now-playing bar: while an episode is loaded and not finished, a strip at the bottom of the podcast list and episode screens shows its title and show with the back 10 s, play/pause and forward 30 s icon buttons, and a time bar (elapsed, a plain progress bar, time left) so you can tell where you are before pressing Play. Tapping the title opens the player. It is hidden on the player screen and when nothing is loaded.
- FR-022 Position is saved at least every 5 s while playing and on pause/stop; reopening resumes there. Finishing an episode marks it played and resets position.
- FR-023 Audio focus: pause on interruptions (calls), resume per platform rules; pause on headphones unplugged ("becoming noisy").
- FR-024 Only downloaded files are playable; a missing/deleted file shows the Download state again.
- FR-025 No automatic choice of what to play next. When an episode ends, playback continues only with the next episode in the queue (FR-100..); with an empty queue it stops, as before.

**Downloads & refresh**
- FR-030 Periodic feed refresh via WorkManager, default every 6 hours (setting: 1 / 3 / 6 / 12 / 24 h / manual only); also a **Refresh** button on the feed list and on each feed's page (no pull-to-refresh gesture) and on app open.
- FR-031 New episodes are enqueued for download automatically, as long as they are among the latest **N** per feed (setting, default 1 = only the latest episode, range 0–10; 0 = manual only). On first subscribe only the latest N are fetched, never the whole back catalogue.
- FR-032 Downloads: resumable (HTTP Range), run as WorkManager expedited/long-running work with a progress notification only while a download is running; retry with back-off; survive process death and reboot.
- FR-033 Network policy (setting): Wi-Fi only (default) or any network. Optional "only while charging" (default off). A manual download ignores the charging rule and, after a confirmation, may use mobile data.
- FR-034 Storage: files in app-specific external storage (removed on uninstall, no storage permission). Setting "Keep at most X episodes per feed" (default: keep all downloaded and unplayed; delete played per FR-013). Low-storage guard: do not start downloads below a free-space threshold, and tell the user once.
- FR-035 Feed refresh uses conditional requests (ETag / Last-Modified) to save data.
- FR-036 Failures are visible but quiet. A refresh that fails on the connection itself (offline, name lookup, timeout) is tried once more after a moment; if it still fails, the scheduled background refresh leaves no message (the system may simply have blocked background network access, and the job retries later), while a refresh the user asked for, or the one at app open, shows the specific reason ("could not find the server", "took too long", "could not make a secure connection", or the general "could not reach the server"). Two refreshes of the same feed never run at once, so an old failure cannot overwrite a newer success. The rest of FR-036: a per-feed "last refresh failed: reason" line; no notification for failures except a single notification when a paid feed returns 401/403 ("Check credentials for X").

**Album art (step two, P2)**
- FR-040 Show feed artwork in the feed list and player; episode artwork where the feed provides it, else the feed's. Artwork is fetched and cached with the episode/feed and works offline afterwards.
- FR-041 Media notification and lock screen show the artwork (MediaSession metadata).
- FR-042 Art style (setting, default Grayscale): **Original** or **Grayscale** (colour removed, contrast reduced), mirroring the launcher's icon style. Follows light/dark live.
- FR-043 Artwork is downscaled and disk-cached with a size cap; no art = no placeholder noise (a plain text row).

**Appearance & settings (shared with Athena Launcher)**
- FR-060 Style: monochrome. Light = black on white; dark = white on pure black; custom colour schemes (no dynamic colour); system-bar icons and window background match so nothing flashes on start. Same tokens as the launcher's `Theme.kt`.
- FR-061 Appearance settings: theme (follow system / light / dark), typeface (System, Condensed, Serif, Monospace, Cursive, Casual, plus bundled VT323, IBM Plex Mono, Space Mono, JetBrains Mono, Inter, Atkinson Hyperlegible), text size 80–140 % in 10 % steps. Same names, ranges and defaults as the launcher so values map one-to-one.
- FR-062 **Follow launcher** (setting, default on when the launcher is installed): theme, typeface and text size are read from Athena Launcher. The app reads them through a read-only bridge exposed by the launcher (plan §3.7), at start-up and while running (observer); if the launcher is missing, not updated, or denies access, the app silently uses its own local values. With "Follow launcher" off, local values apply and are editable here.
- FR-063 One-off import of the launcher's settings export JSON (`app` = "athena-launcher") as a fallback/alternative to the live bridge: only `themeMode`, `typeface`, `textScalePercent` are read; everything else is ignored.
- FR-064 Local settings screen with sections: Appearance (follow launcher, theme, typeface, text size, art style), Downloads (episodes per feed, network, charging, delete after played, keep-at-most), Refresh (interval), About (Open-source licences).
- FR-065 Export/import of the app's own settings JSON (`app` = "athena-podcaster", `version`), same rules as the launcher's FR-061 (unknown keys ignored, bad values clamped, other apps' files rejected). Credentials are never in this file.
- FR-066 Licences screen as in the launcher (FR-062): Athena Podcasts (MIT), bundled fonts (OFL), AndroidX/Media3/OkHttp etc. (Apache-2.0), each expandable to the full text, shipped in the app.

**Android Auto (US7)**
- FR-080 The app is offered as a media app in Android Auto (phone projection). Android Automotive OS is out of scope. The app declares itself as an Auto media app and exposes a browsable media library from the playback service.
- FR-081 Browse tree: **one flat list**. The root shows every downloaded episode of every subscribed podcast, newest release date first, capped at 50 items, each playable with one tap. There are no podcast folders, no sub-levels and no extra entries, so playing an episode takes one tap (large gloved hands, FR-090). Title, podcast name and duration are shown. Every downloaded episode is listed, played or not; the "Delete download after played" setting (FR-013) is what keeps finished episodes off the phone and so out of the list, exactly as on the phone, with no separate rule for the car. A half-heard episode resumes where it stopped. Only downloaded episodes ever appear; nothing in the tree can start a download or a stream.
- FR-082 Empty state: when nothing is downloaded, the list holds a single plain-text item, "Nothing downloaded yet", not a blank screen.
- FR-083 Playing from the car uses exactly the phone's rules: the same player, the saved position is resumed (always: a car asking to start "at 0" is ignored, it must never overwrite the progress), auto-complete and played tracking (FR-014) and position saving (FR-022) behave the same, and the phone's Now Playing bar and the car show the same state. Selecting an item whose file has since disappeared is refused with a short message, never a network fetch (FR-024).
- FR-084 Transport in the car: play/pause, skip back 10 s and skip forward 30 s as actions on the car's player screen; the steering-wheel next/previous keys map to the same skips as headset keys (FR-021b).
- FR-085 Art on each item is the locally stored episode or podcast art in the chosen style (FR-043); no art is fetched while connected.
- FR-086 The library reads only the local database and files, works without network and with the app's UI not running, and answers a browse request in under 500 ms for a feed with 10,000 episodes.
- FR-087 No settings, text input or destructive actions (delete, mark played, unsubscribe) are offered in the car.
- FR-088 The browse tree is served only to trusted controllers: Android Auto, the system, and the app itself. Other apps that connect to the session can control playback as before but cannot list the library (it reveals the user's subscriptions).
- FR-090 Glove use (motorcycle). Android Auto draws its own screens, so touch-target size is set by the head unit or mount, not by the app. The app therefore does what it controls: a single-level list with the fewest possible taps to playback; only the three transport actions play/pause, back 10 s, forward 30 s on the player screen (no extra custom actions); every control is also reachable by media keys, so handlebar and helmet-intercom buttons (play/pause, next = forward 30 s, previous = back 10 s) work without touching the screen (FR-021b); and no dialogs, confirmations or text prompts appear while connected. Whether the touch targets are big enough on the actual bike screen is checked by the user in T8.7.
- FR-089 Sideloading: Android Auto lists apps that did not come from Google Play only after the user enables developer mode in Android Auto's settings (tap "Version" 10 times) and turns on "Unknown sources". The README documents this; the app does not need to detect it.

**Queue (US8)**
- FR-100 The queue is an ordered list of downloaded episodes that are to play after the current one. It is stored in the database, so it survives app restarts and reboots. The episode being played is not in the queue.
- FR-101 Adding: every downloaded, unplayed episode has an **Add to queue** button (episode lists on the phone (the queue is managed on the phone only, FR-107); the episode now playing is not in the queue and has no such button), which appends it at the end; while queued the button reads **Remove from queue**. Episodes that are not downloaded, or are played, cannot be queued.
- FR-102 Queue screen: reached with a **Queue (n)** button on the podcast list and on the player screen (n = how many are queued); Back returns to the screen it was opened from. Each row shows title and podcast and has plain buttons **Play now**, **Remove**, **Move up**, **Move down**; a **Clear queue** button asks for confirmation. No gestures. An empty queue says "Nothing queued".
- FR-103 Advancing: when the current episode ends (and is marked played per FR-014) the next queued episode starts by itself from its saved position and leaves the queue. This works with the screen off, and in the car. With an empty queue playback stops and the finished episode is unloaded: it disappears from the Now Playing bar, the player screen and the lock screen / notification (FR-110).
- FR-104 Tapping Play on any episode (phone or car list) plays it now and keeps the queue as it is; if that episode was queued it leaves the queue. Starting an episode never clears the queue.
- FR-105 The queue never holds an episode that cannot play: marking it played, deleting its download, the file disappearing, or unsubscribing from its podcast removes it. Episodes marked played this way are not re-queued by a refresh.
- FR-106 Position and played tracking are unchanged (FR-014, FR-022): leaving an episode by skipping to another queue entry saves its position; reaching its end marks it played.
- FR-107 Queue in the car: **view only**. The car's own Queue button shows the current episode and the upcoming queue, and choosing an entry there jumps to it. Adding, removing, reordering and clearing are done on the phone only; the car list has no folder or action for it (decided 2026-11-05: other podcast apps do not offer it either, and a second list with the same content in the car is confusing). The steering-wheel and headset next/previous keys stay forward 30 s / back 10 s (FR-021b).
- FR-108 The lock screen and notification show the cover and title of the episode now playing, also after an automatic advance.

## 5. Non-functional requirements
- NFR-001 Platforms: minSdk 36, target/compile 37. Testing on the Android 17 (API 37) emulator and the user's phone; Android 16 is supported by `minSdk` but not tested.
- NFR-002 Cold start to interactive list < 500 ms (list comes from the local DB; no network on the critical path). Opening the player and starting playback < 300 ms.
- NFR-003 Permissions: `INTERNET`, `FOREGROUND_SERVICE` (+ `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `FOREGROUND_SERVICE_DATA_SYNC` if downloads need it), `POST_NOTIFICATIONS` (requested just in time, when the first download or playback notification is needed). No storage, contacts, location or microphone permissions. No analytics/crash SDKs.
- NFR-004 Network security: HTTPS preferred; cleartext HTTP allowed per feed only after an explicit confirmation (many small feeds still use it); a private (token) feed must use HTTPS; TLS errors are never silently ignored.
- NFR-005 Accessibility: TalkBack labels on every control, touch targets ≥ 48 dp (the three transport buttons larger), font scaling to 200 % without clipping, sufficient contrast.
- NFR-006 Battery: no wake locks outside Media3 playback/WorkManager; no polling; periodic work respects Doze and battery-saver.
- NFR-007 Robustness: malformed feeds, huge feeds (10 k episodes), missing durations, redirects, gzip, odd encodings and non-audio enclosures must not crash; tests cover them.
- NFR-008 Predictive back and edge-to-edge handled correctly.

## 6. Data & state
- Room database: `Feed` (id, url, title, auth reference, last refresh, ETag/Last-Modified, last error), `QueueItem` (episode id, position; cascade-deleted with the episode), `Episode` (feed id, guid, title, published, duration, enclosure URL/type/size, download state, file path, position, played).
- Credentials: Android Keystore-backed encrypted storage, referenced from `Feed`; feed URLs that contain tokens are treated as secrets (same store; the DB holds a reference).
- Settings: DataStore Preferences (flat keys, typed `Settings` class, as in the launcher).
- Files: `getExternalFilesDir("episodes")/<feedId>/<episodeId>.<ext>`; artwork cache under `cacheDir`.

## 7. Open-source basis – recommendation
Evaluated approach: **do not fork a full app**. AntennaPod (GPL-3.0, Java/Kotlin, XML Views) is the mature reference but its licence conflicts with Constitution §9 (MIT, own code) and its UI stack differs from the Compose launcher. Instead, build thin on proven building blocks (all Apache-2.0 or similar, see plan §4): **Media3** (playback, MediaSession, notification), **WorkManager** (refresh/downloads), **Room**, **OkHttp**, and a small purpose-written RSS parser on `XmlPullParser`. Use AntennaPod only as a behavioural reference (feed edge cases, download policies). A permissively-licensed feed-parsing library may replace our parser if it saves real effort (decide in task T2.1).

## 8. Out of scope but kept in mind
HTTP Basic-auth feeds (username + password; host-bound `Authorization` header), autoplay of an episode you did not queue, playback speed and skip-silence, sleep timer, queue, chapters/transcripts (Podcasting 2.0), podcast search (iTunes/Podcast Index), voice search in the car ("play X"), Android Automotive OS (cars with Android built in), widgets, per-feed overrides, cloud sync, an Athena Launcher home-screen "now playing" row.

## 9. Decisions (2026-11-05)
- Name: **Athena Podcasts**. Application id: `se.anders.athenapodcaster`. Licence: MIT. Sideload first.
- Platform: native Android (Kotlin + Compose), same toolchain and targets as Athena Launcher, so theme, fonts and settings can be shared. (Flutter was considered because of the other projects on this machine, but it would make sharing launcher settings and fonts harder.)
- Fresh implementation on Media3 + WorkManager; no fork (§7).
- Streaming: not supported, by design.
- Skip amounts: 10 s back, 30 s forward, fixed.
- Art style default: Grayscale (fits the calm aesthetic); Original is one tap away.

- FR-120 Home-screen widget, 4 × 1 cells, fixed size, deliberately minimal: the title of the episode now playing (one line) and one large **Play/Pause** button (at least 48 dp, in practice about 56 dp). Tapping the title opens the app on the player. Nothing is loaded → it reads "Nothing playing" and the whole widget opens the app. Black on white / white on black following the system light/dark, same type-less monochrome look; no cover art, no skip buttons, no settings. Playing uses the one player (FR-020, FR-022): progress is saved exactly as from the app. No network, no new permissions. [Decided 2026-11-10; skip buttons are a possible later addition.]

## 10. Open questions
- Resolved 2026-11-05: both paid feeds use private URLs with a token. HTTP Basic is deferred.
- Resolved 2026-11-05: launcher bridge uses a signature-level permission. Android signs every APK anyway; debug builds made on this Mac share `~/.android/debug.keystore`, so sideloaded debug builds of both apps already match with no extra work. For release APKs, sign both with one keystore (plan §3.7).
- Resolved 2026-11-05: autoplay of the next episode was deferred; replaced by the user-built play queue (US8, FR-100..), still no automatic pick of what to play.
- Resolved 2026-11-05 (Android Auto): only downloaded episodes, one flat list of all podcasts by release date, newest first, every downloaded episode; the delete-after-played setting alone decides whether finished ones remain (FR-081, FR-013). Use is on a motorcycle in thick gloves (FR-090).
- Open (Android Auto): an in-app large-button "ride mode" screen for the phone itself, for when the phone is the mount screen and Android Auto is not used. Not in this spec; decide after trying the DHU and the real bike setup. [NEEDS CLARIFICATION]
- Open (Android Auto): voice search ("play <podcast>") deferred; add via a later spec if wanted.
- Resolved 2026-11-05: auto-download default is 1 (latest episode only).
- Resolved 2026-11-05: "Follow launcher" includes the text-size percentage, applied on top of the system font scale (they multiply), exactly as in the launcher.
