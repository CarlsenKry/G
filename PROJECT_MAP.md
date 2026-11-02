# Project map

- `app/src/main/java/com/krypton/player/MainActivity.kt` — navigation, MediaController, M3U document-picking, playback and listening-stat collection
- `LibraryScreen.kt` — compact 3-page swipe carousel, search, collection sort/reverse control, favorites and track list
- `PlaylistScreens.kt` — playlist collection/detail screens, add/remove songs, playlist sorting and shuffle actions
- `StatisticsScreen.kt` — listening metric cards, seven-day chart and most-listened ranking
- `PlayerRepositories.kt` — persistent playlist metadata and playback statistics
- `MusicLibrary.kt` — MediaStore audio scan, duration, date-added and file-path metadata
- `TrackArtwork.kt` — asynchronous embedded-cover extraction, small in-memory bitmap cache and fallback artwork used by track rows
- `PlaybackService.kt` — Media3 background playback and MediaSession
- `LyricsReader.kt` — LRC/TXT parsing and embedded metadata lyric helper
- `PlaybackOptions.kt` — shuffle, repeat and playback-speed helpers
- `.github/workflows/android.yml` — single branch-aware Android APK build
- `selfcheck.py` — structural preflight checks and optional real Gradle build
- `UI_SPEC.md` — agreed screen behavior and design direction
