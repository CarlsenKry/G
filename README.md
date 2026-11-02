# Krypton Player

Krypton Player is a native Android offline music-player starter built with Kotlin, Jetpack Compose and AndroidX Media3. It is **not a Flutter project**; build it with Gradle and the Android SDK.

## Current features

- Native Android app built with Kotlin, Jetpack Compose and AndroidX Media3.
- Krypton full-dark black / charcoal / neutral-white design, compact Library hero banner and a three-page swipe carousel for Library, Playlists and Listening Statistics.
- Scrollable local music collection with search, persistent sort modes (Most Recent, Title, Most Plays), reverse-order control, queue insertion and favorites.
- Dedicated Playlists screen with create, rename, delete, add/remove songs, playlist playback, and M3U import/export. Imported M3U entries are matched to tracks already indexed on the device; unavailable files are skipped.
- Dedicated Listening Stats screen with total listening time, qualified track plays, favorites, library size, last-seven-day listening chart and most-listened tracks. Listening time and statistics are stored locally. A track counts as played when playback reaches at least 50% of its duration.
- Device audio discovery through Android MediaStore, including duration and date-added metadata for sorting.
- Background playback through Media3 ExoPlayer and MediaSessionService, with Android media notification, headset/media-button handling and system lock-screen transport controls through the media session.
- Full adaptive Now Playing screen with embedded album artwork, progress seeking, wing-shaped Previous/Next controls and rounded-pill Play/Pause control, plus shuffle and repeat. The persistent bottom Library / Queue tabs are removed; a compact now-playing bar appears only during active playback on non-Player screens. Selecting a song from the Library opens Now Playing directly.
- Queue sheet opened from the player toolbar; it preserves queue order and highlights the active track.
- A right-edge Lyrics affordance opens the synchronized lyrics view. On song changes, Krypton automatically checks same-folder `.lrc` sidecars, embedded ID3 USLT/SYLT/TXXX lyrics, FLAC Vorbis comments, Ogg/Opus comments and iTunes-style M4A lyric atoms. Timed lines follow playback; a one-time folder grant enables reliable sidecar discovery on Android versions with scoped storage.
- Custom AMOLED lockscreen player available from Settings: compact clock, rounded-square cover, pure black (unblurred) background, artwork-reactive high-contrast accents, queue sheet at upper left, live shuffle toggle at upper right, wing-shaped Previous/Next, rounded-pill Play/Pause, no playback seekbar, and a bottom slide handle.
- Service-owned Android Equalizer, BassBoost and Virtualizer effects connected through Media3 audio-session callbacks. Device-supported presets and bands are discovered at runtime; supported settings persist locally and remain active while playback continues in the background.

## Build locally

Requirements:

- JDK 17
- Gradle 8.9
- Android SDK Platform 35 and Build Tools 35.0.0

Open this folder in Android Studio and sync the Gradle project, or run:

```bash
gradle --version
gradle --no-daemon :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Run the repository preflight checks with:

```bash
python3 selfcheck.py
```

When Gradle and the Android SDK are available, include an actual debug build:

```bash
python3 selfcheck.py --build
```

## GitHub Actions

There is exactly one workflow: `.github/workflows/android.yml`. It runs on pushes to any branch, pull requests, and manual `workflow_dispatch` runs. It discovers the Gradle project root automatically, so the app can be stored at the repository root or inside a folder such as `KryptonPlayer/`.

The workflow installs JDK 17, Android command-line tools using `android-actions/setup-android@v4` (requesting only `platform-tools`), Android SDK Platform 35 / Build Tools 35.0.0, and Gradle 8.9. It deliberately avoids the retired SDK package named `tools`, which Google no longer publishes. It then runs `selfcheck.py --build` and uploads the debug APK as the `krypton-player-debug-apk` artifact. No Flutter setup, `pubspec.yaml`, or Flutter dependency step is used.

For manual dispatch, GitHub requires the workflow file to exist on the repository's **default branch**. Commit `.github/workflows/android.yml` there first, then also push it to the feature branch you want to build. Open **Actions → Krypton Android Build → Run workflow** and select that branch from the branch dropdown.

## Project map

- `app/src/main/java/com/krypton/player/MainActivity.kt` — app navigation, MediaController connection, M3U document-picker flows and playback-stat collection
- `LibraryScreen.kt` — compact three-page carousel, search, song list, sort/reverse controls and favorites
- `PlaylistScreens.kt` — playlist collection, detail, editing and M3U-related UI
- `StatisticsScreen.kt` — listening metrics, seven-day chart and most-listened list
- `PlayerRepositories.kt` — persistent playlists and listening statistics
- `MusicLibrary.kt` — MediaStore-based offline library scan and sort metadata
- `PlaybackService.kt` — Media3 background playback, MediaSession and audio-session ID listener
- `PlaybackScreens.kt` — adaptive Now Playing UI, live queue sheet and synchronized lyrics view
- `LockScreenActivity.kt` — custom keyguard-visible AMOLED player surface, live queue and shuffle controls, artwork-derived colors, and slide-to-close gesture
- `EqualizerScreen.kt` — device-aware graphic equalizer, presets, bass boost and virtualizer UI
- `AudioEffects.kt` — service-owned Android audio effects with persisted settings
- `LyricsReader.kt` — LRC/TXT parsing, ID3/FLAC/Ogg/Opus/M4A embedded lyric extraction and automatic MediaStore/SAF sidecar discovery
- `PlaybackOptions.kt` — shuffle, repeat and playback-speed helpers
- `.github/workflows/android.yml` — single branch-aware APK build workflow
- `selfcheck.py` — repository preflight and optional real Gradle APK build

## Known implementation boundaries

- Imported M3U paths are resolved against the tracks indexed in the device library. Entries whose audio files are absent or inaccessible are skipped; this app does not copy audio files into its own storage.
- Listening statistics begin accumulating after this version is installed and playback is observed by the app. They do not reconstruct historical listening activity.
- Android scoped storage may hide non-media `.lrc` files from MediaStore. For reliable automatic sidecar lookup, grant Krypton read access once to the music folder (or a parent folder containing the song folders) from the Lyrics screen. A manual lyrics-file picker remains available as a fallback.
- Android audio-effect availability varies by device/ROM. The Equalizer screen detects unsupported native effects and disables the corresponding controls rather than pretending they are active.
- Android's secure keyguard cannot be replaced or unlocked by an ordinary media app. Krypton's custom lockscreen Activity can be opened from Settings and displayed above the keyguard using Android's `showWhenLocked` support while it remains the top Activity. The slide gesture closes Krypton's surface only; Android still requires the user's configured PIN, pattern, password or biometric authentication. The standard MediaSession controls remain available through Android's native media controls as well.
- Configure a production signing key through GitHub Secrets before distributing a signed release APK. Never commit keystores or passwords.

## Current global navigation and appearance

The app now uses pure black system bars and a monochrome charcoal / white visual theme. The persistent Library / Queue bottom navigation bar has been removed. A compact now-playing bar appears only while audio is actively playing, and tapping it opens the full Player / Queue screen; it is hidden when playback is paused/stopped and on the full Player screen. Settings is accessed from the upper-right gear in the Library header. Android Back returns through the app's own navigation history and does not close the app while the user is at the Library root.

## Album artwork in song lists

Song rows now use the embedded cover from each local audio file. Artwork decoding runs off the UI thread, is sampled for thumbnail use, and is kept in a small in-memory cache. Tracks without a readable embedded cover keep a monochrome dark music-note fallback. This is applied to Library, playlist, add-song, queue and most-listened rows.

## Main Player + Equalizer update

The full Now Playing screen is reached immediately after selecting a Library song, or by tapping the compact now-playing bar while audio is active. Tapping the currently playing song in the Library opens Now Playing without restarting playback. The player uses the existing MediaController and Media3 service for every transport action. The upper-left equalizer icon opens the sound controls; the upper-right combined queue/player icon opens the live queue sheet. Swipe or tap the right-edge Lyrics handle to open lyrics for the active track.

Artwork dimensions are calculated from the available Compose constraints, with a scroll fallback for short displays. The player samples a dominant color from the current embedded cover and smoothly applies it to the player accent, progress track, transport controls, lyrics handle, queue sheet; a neutral white/grey fallback is retained when no cover is available. Previous/Next use the chosen Futuristic Wing silhouettes, while Play/Pause uses the rounded-pill form. The UI is not laid out using the reference screenshot's pixel dimensions.
