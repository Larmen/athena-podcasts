# Athena Podcasts

A minimalist Android podcast player: subscribe to RSS feeds (public or paid/private), download new episodes automatically in the background, and play them with play/pause, back 10 s and forward 30 s. No streaming. Monochrome look shared with [Athena Launcher](../athena-launcher). Android 16 and 17 only. MIT licensed.

Development is spec-driven: start with [CLAUDE.md](CLAUDE.md) and `specs/001-podcasts-core/`.

## Name
The app is called **Athena Podcasts**. Its Android package id stays `se.anders.athenapodcaster` (and the settings-file tag `athena-podcaster`), so existing installs keep their feeds, downloads and settings; only the visible name changed.

## Status
Phases 1–4 built: shared theme and settings, feeds (private feed addresses are stored encrypted), episode lists, OPML, automatic and manual downloads, periodic refresh, and playback with play/pause, back 10 s and forward 30 s. Cover art for shows and episodes (grayscale by default), and theme, typeface and text size that follow Athena Launcher. (see `specs/001-podcasts-core/tasks.md`).

## Installing and signing
Debug builds made on this Mac are all signed with the same key (`~/.android/debug.keystore`), which is what lets Athena Launcher and Athena Podcasts share theme settings (the launcher's provider only answers apps with the same signature). Install both from this Mac:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Install the launcher first (it defines the permission), then this app. For a release build, create one keystore and sign **both** apps with it, otherwise they stop recognising each other and this app falls back to its own appearance settings:

```bash
keytool -genkeypair -v -keystore athena-release.jks -alias athena -keyalg RSA -keysize 4096 -validity 10000
```

Keep the keystore and its passwords out of the repository (`*.jks` is already ignored). A manual test list is in `docs/manual-qa.md`.

## Android Auto
Athena Podcasts shows up in Android Auto as a media app with one flat list: every downloaded episode of every podcast, newest first (at most 50). Tap one to play; play/pause, back 10 s and forward 30 s are on the player screen, and steering-wheel or handlebar media keys do the same. Nothing is streamed, so it works without mobile data.

Because the app is sideloaded, Android Auto hides it until you allow unknown sources: on the phone open Android Auto settings, tap **Version** ten times to enable developer mode, open the three-dot menu → **Developer settings**, and turn on **Unknown sources**. Reconnect to the car or mount afterwards.

## Building

Requirements: a JDK 17+ (Android Studio's bundled JDK works) and the Android 17 (API 37) SDK platform plus build-tools.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # gitignored
./gradlew assembleDebug testDebugUnitTest lintDebug
```

## Sharing appearance with Athena Launcher
Theme, typeface and text size can follow the launcher's settings. Both apps must be signed with the same key (see plan §3.7).
