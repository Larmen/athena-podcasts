# Manual QA checklist – Athena Podcasts

Run on the real phone (Pixel, Android 17) with the real paid feeds. Tick as you go, note anything odd.
Automated tests cover parsing, downloads, playback logic and the launcher link; this list is for what only a
person with a phone can check.

## First run
- [ ] App opens to an empty "Podcasts" list with the Add / Refresh / Settings buttons.
- [ ] Add a public feed: it appears with its cover (grayscale), and its latest episode starts downloading.
- [ ] Add each paid feed with "Private (paid) feed" ticked: episodes appear, latest downloads, cover shows.
- [ ] An http (not https) public feed asks for confirmation; a private feed with an http address is refused.
- [ ] The notification permission is asked for once (first Add, Download or Play), never at first launch.

## Episodes
- [ ] Download, Cancel download, Delete download behave and the state text changes ("Downloading 42 %", "Downloaded").
- [ ] Download while on mobile data (Wi-Fi off) asks first.
- [ ] Mark played asks for confirmation, mentions the file being deleted, and deletes it.
- [ ] Mark unplayed works without a dialog. The "Showing:" button steps through all, downloaded only and unplayed only.
- [ ] Mark all as played: dialog shows the count and the delete warning; afterwards nothing new downloads automatically.
- [ ] Unsubscribe removes the podcast, its files and its cover; a private feed's address is not shown anywhere but behind "Show address".

## Playback
- [ ] The bottom now-playing bar shows on the podcast and episode screens while an episode is loaded; its three icon buttons work and tapping the title opens the player.
- [ ] Play starts from the saved position; Pause, Back 10 s and Forward 30 s do what they say.
- [ ] Screen off: audio continues. Lock screen and notification show title, cover and the 10 s / 30 s buttons.
- [ ] Tap the Now Playing widget on the lock screen (and in the notification shade): after unlocking, the app opens on the player.
- [ ] Headset or Bluetooth: play/pause works; next skips 30 s forward and previous 10 s back (the keys were tested with simulated events, not a real device). An incoming call pauses playback; it resumes after.
- [ ] Unplugging headphones pauses.
- [ ] Playing to the end marks the episode played.

## Background
- [ ] Leave the phone overnight on Wi-Fi: a new episode of a followed feed is downloaded by morning.
- [ ] Reboot with a download queued: it completes afterwards.
- [ ] A paid feed whose token stops working shows "Last refresh failed: Access was refused" and one notification.
- [ ] Airplane mode: downloaded episodes still list, show covers and play.

## Appearance
- [ ] Settings → Appearance → "Follow Athena Launcher" is on; "Now:" matches the launcher.
- [ ] Change theme, typeface and text size in the launcher: this app follows when reopened (live if it is open).
- [ ] Switch following off: the local controls appear; switching it back on returns to the launcher's values.
- [ ] Cover art Original / Grayscale switch works (lists, player, lock screen on next play).
- [ ] Settings → About → Open-source licences opens, each entry expands.
- [ ] System font size at 200 %: every screen readable, nothing cut off (checked on the emulator, repeat on the phone).

## Backup
- [ ] Export settings, change one, import the file: confirm dialog, setting restored.
- [ ] Export OPML (private feeds excluded unless ticked); import the OPML on a clean install adds the public feeds.
- [ ] "Import from a launcher settings file" reads a launcher export.

## Android Auto (on the bike / in a car)
- [ ] Unknown sources is on in Android Auto's developer settings; Athena Podcasts appears in the app list.
- [ ] The list shows downloaded episodes of all podcasts, newest first; nothing that is not downloaded.
- [ ] With gloves on: every row and the play/pause, back 10 s and forward 30 s controls can be hit reliably on the mount screen. Note what is too small (FR-090).
- [ ] Tap an episode: it starts where it stopped last time; pausing in the car shows the same position in the phone app.
- [ ] Handlebar / helmet intercom buttons: play/pause, next (forward 30 s), previous (back 10 s).
- [ ] An incoming call pauses playback and it resumes afterwards.
- [ ] Airplane mode: browsing and playing still work.
- [ ] Finish an episode with "Delete download after played" on: it leaves the car list.
- [ ] With nothing downloaded, the list shows "Nothing downloaded yet".

## Queue
- [ ] Add two episodes of different podcasts to the queue; the first one finishes and the second starts by itself, also with the screen off. The lock screen shows the second one's title and cover.
- [ ] Move up / Move down / Remove / Clear queue (with confirmation) behave; buttons are large enough at 200 % font.
- [ ] Play now on any episode keeps the queue.
- [ ] Restart the app / reboot: the queue is still there.
- [ ] Car: the Queue button lists what is next and jumping works; there is no way to edit the queue from the car.
- [ ] Bike with gloves: the Queue button is reachable and readable.

