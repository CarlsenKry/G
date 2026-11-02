# Krypton Player — this update

## Player controls and artwork theme

- Previous and Next now use custom mirrored Futuristic Wing silhouettes.
- Play/Pause now uses a rounded-pill control with custom play and pause glyphs.
- The active track's embedded cover is sampled off the UI thread to derive a high-contrast accent color.
- That accent is applied to the Now Playing surface glow, transport controls, progress slider, Lyrics handle, Queue sheet, active queue row.
- Missing or unreadable cover art falls back to the app's neutral white/grey accent. Artwork-reactive colors remain enabled for the Player and lockscreen.

## Lyrics detection

Automatic lookup runs when the active media item changes. The detection priority is:

1. A same-folder `.lrc` file.
2. Embedded lyric metadata: ID3v2 USLT/SYLT/TXXX (including common MP3 tags), FLAC Vorbis comments, Ogg/Opus comments and the iTunes-style M4A/MP4 `©lyr` atom.
3. A same-folder `.txt` fallback.

Android scoped storage can prevent an app from reading non-media `.lrc` files through MediaStore. The Lyrics screen therefore offers a one-time **Choose Music Folder** action. Grant read access to the folder containing the music (or its parent folder); Krypton saves that SAF permission and matches the audio file's relative folder and basename. A manual LRC/TXT document picker remains available.

While the Lyrics screen is open, Krypton rechecks the sidecar folder every five seconds so a newly added or updated `.lrc` can be picked up without restarting playback.

## Validation status

- `python3 selfcheck.py`: latest repository-wide structural checks pass; see `BUILD_VALIDATION.md` for the current count.
- ZIP integrity is checked before delivery.
- A real Gradle APK build was not run in this environment because Gradle and the Android SDK / `android.jar` are not installed here. The repository's GitHub Actions workflow remains configured to run `selfcheck.py --build` with JDK 17, Gradle 8.9 and Android SDK 35.


## Custom lockscreen player

- Added a dedicated `LockScreenActivity`, launched from Settings. It is configured with Android's `showWhenLocked` / `turnScreenOn` behavior so it can remain visible above the secure keyguard when it is the top activity. It does not replace Android's credential screen.
- The screen uses a compact clock/date, centered rounded-square cover, track and artist, Previous / Play-Pause / Next controls, and a bottom slide-to-close interaction. There is intentionally no playback seekbar.
- Upper-left Queue + Player control opens a live queue sheet with current-track highlighting and tap-to-play selection. Upper-right Shuffle control toggles Media3 shuffle mode without leaving the lockscreen.
- The active cover is sampled via the shared artwork cache. Controls and outlines use its bright accent; the base background remains pure black and text stays white/light-grey for readability.
- The slide label remains “Slide to unlock” to match the chosen visual language, but technically it dismisses only Krypton's custom surface. Android's own lock security remains active and may still require PIN, pattern, password or biometrics.

## Validation status for this update

- Static self-check includes dedicated coverage for lockscreen registration, queue and shuffle callbacks, lack of seekbar, artwork-derived colors and the secure-keyguard boundary.
- A device/Gradle build has not been run in this environment; verify the lockscreen-on-wake behavior on the target Android device after the GitHub Actions APK build.


## Library navigation and full-dark theme update

- Removed the entire persistent Library / Queue bottom navigation strip.
- Selecting a song from Library opens the full Player / Queue screen; selecting the current track opens it without restarting playback.
- A compact now-playing bar appears at the bottom of non-Player screens only while playback is actively running; tapping it opens the Player, with pause and next controls. It is hidden while paused/stopped and on the full Player screen.
- Settings is launched through a gear icon at the top-right of the Library header.
- System Back is handled by an in-app navigation history and does not exit at the Library root.
- Main APK palette is pure black / charcoal / neutral white, including system bars, artwork placeholders and Statistics chart bars. Player and lockscreen accents still derive from album artwork where supported.
