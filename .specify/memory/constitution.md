# Podcasts Constitution

Non-negotiable principles. Specs, plans and code must comply; if one cannot, amend this file first.
Shares its spirit (and §1, §4, §6–§9) with the Athena Launcher constitution.

## 1. Calm by default
A quiet, monochrome app: no badges, no recommendations, no ads, no autoplay of "next suggested", no animation that exists only to decorate. Anything that adds visual noise must be opt-in.

## 2. Essentials first
Playback, subscriptions, downloads. Everything else (speed, sleep timer, search/discovery, chapters, queues, stats) is out of v1 unless a spec says otherwise. Album art is the first extra (step two) and must not delay the essentials.

## 2b. Buttons, not gestures
v1 has no gestures (no swipe, pull-to-refresh, long-press menus or drag). Every action is a visible button or option on the screen, row or item it applies to. Gestures can be added later, on top of the buttons, by a spec.

## 3. Offline-first, network only for feeds and files
- Network is used for exactly two things: refreshing the user's subscribed feeds and downloading their episode files. Nothing else talks to the network. No streaming: playback uses downloaded files only.
- No analytics, no crash-reporting SDKs, no ads SDKs, no directory/search service in v1.
- All data stays on device. Backup is user-initiated export only (OPML + settings JSON).

## 4. Secrets stay secret
Paid/private feed URLs (they contain the access token) and any future credentials are secrets. Store them app-private (encrypted at rest where the platform allows), never log them, never put them in crash output, exports (unless the user explicitly chooses "include private feeds"), or UI beyond an explicit reveal.

## 5. Quiet background work
Background activity is limited to: periodic feed refresh and episode downloads (WorkManager, respecting battery/network constraints), plus the foreground media service while audio plays. No wake locks beyond what Media3 needs, no polling loops, no always-on service.

## 6. Latest two Android versions only
`minSdk` = API 36 (Android 16), `targetSdk`/`compileSdk` = API 37 (Android 17). Bump when a new Android ships; drop the oldest. No compatibility shims.

## 7. Modern, boring Android
Kotlin, Jetpack Compose, single activity, unidirectional data flow, AndroidX/Jetpack (Media3, WorkManager, Room/DataStore) over third-party libraries. Every new dependency needs a one-line justification in `plan.md`.

## 8. Spec first
No feature code without a spec section and tasks. Behaviour changes update `spec.md` in the same commit.

## 9. Own the code, respect licences
Fresh implementation, MIT-licensed. GPL projects (AntennaPod, GPL-3.0) are design reference only: do not copy their source, assets or strings. Libraries must be licence-compatible (Apache-2.0, MIT, BSD, OFL fonts). Every bundled font/library is listed on the Licences screen before it ships.

## 10. One look across Athena apps
Same monochrome style, same typefaces, same text-scale and light/dark behaviour as Athena Launcher. Appearance is configured once, in the launcher, and followed here (see spec FR-060..FR-064); the app must still look right on its own when the launcher is absent.

## 11. Tested where it hurts
Unit tests for domain logic (feed parsing, episode de-duplication, download policy, position resume, skip maths). Instrumented tests for playback service, downloads and the launcher-settings bridge. Manual checklist for lock-screen/headset/Bluetooth behaviour.
