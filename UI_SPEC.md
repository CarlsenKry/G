# Krypton Player — agreed UI specification

This document captures the Library / Playlists / Listening Stats changes based on the user-provided sketches. The images are layout references; the application uses a near-black / charcoal / white monochrome palette, rounded cards and restrained text treatment. The Player and lockscreen may use artwork-derived accents.

## Library

- Keep the Krypton logo, Library title and refresh/rescan action. Remove the duplicate track-count badge from the header.
- Replace the tall hero with a compact banner (about half the original visual height) and a three-page horizontal carousel.
- Page 1: “Your Music. Your Moment.”, track count/device subtitle, and **Play Library**.
- Page 2: playlist introduction and **Open Playlists**, which opens the dedicated Playlist screen.
- Page 3: listening-statistics preview and **View Statistics**, which opens the dedicated Listening Stats screen.
- Show three page indicators; the active indicator is highlighted. Swipe horizontally or tap a dot to change page.
- The sort and reverse controls sit side-by-side with a small separator. The direction arrow reflects the real ordering for the selected mode (newest-first / oldest-first, A–Z / Z–A, most-played / least-played).
- Keep search and the song collection vertically scrollable. Do not show a persistent bottom Library / Queue navigation bar. Place Settings as a gear button at the upper-right edge of the Library header.
- Remove the duplicate collection count text and put the sort controls beside “Your Collection”. Modes: Most Recent, Title, Most Plays. A separate adjacent arrow control reverses the active order. Mode and reverse state persist.
- Song rows support play, add-to-queue and favorite toggling. Tapping a song opens the full Player / Queue screen immediately. Tapping the currently playing song opens that screen without restarting the track.

## Playlists

- Dedicated screen using a pure-black / charcoal / neutral-white theme.
- Header row shows playlist count, **Import M3U**, and **New**.
- Each playlist card shows the first available track’s embedded cover (or Krypton fallback), name, available song count, total duration, play action and overflow menu.
- Overflow actions: open, rename, export M3U and delete.
- Playlist detail screen shows its tracks, allows adding/removing songs, playing the whole playlist or a selected track, shuffling, sorting by saved order or title A–Z / Z–A, and exporting M3U.
- Playlist metadata and track order persist locally. M3U imports match entries to audio already indexed on the device; missing/unavailable entries are skipped. Exports write paths where available, otherwise MediaStore content URIs.

## Listening Statistics

- Dedicated screen with back navigation and no bottom tab bar.
- 2 × 2 metric grid: Total Listened, Tracks Played, Favorites and Library Size.
- Last 7 Days chart with daily listening durations.
- Most Listened list with title, artist, accumulated listening duration and play count.
- Statistics are stored locally and begin accumulating after this build is installed. A track is counted as played only after reaching at least 50% of its duration.

## Scope note

This update includes the agreed player controls, artwork-derived theme accents, embedded lyrics parsing and automatic sidecar lookup. Separate work such as visualizer/background RAM optimization and advanced crossfade remains outside this update.


## Song artwork in lists

- Library rows, playlist detail rows, add-songs selection rows, queue rows and most-listened rows show the track's embedded album cover.
- Embedded artwork is decoded off the UI thread at a reduced resolution and kept in a small bounded memory cache.
- Tracks without readable artwork retain a monochrome dark music-note placeholder.
- Artwork extraction failure must never prevent a track row or playback from appearing.


## Main Player + Queue

- There is no persistent bottom Library / Queue tab bar. Selecting any Library song opens the dedicated Now Playing / Queue screen directly; selecting the currently playing song opens it without restarting the track.
- A compact now-playing bar appears at the bottom of non-Player screens only while a track is actively playing. It shows the current artwork, title and artist, plus pause and next actions. Tapping its body opens Now Playing; it is hidden when playback is paused/stopped and while the full Player screen is open.
- Top-left equalizer icon opens the Equalizer screen; top-right queue icon opens a queue sheet without leaving the player.
- The main player adapts its artwork size to available width and height rather than using screenshot pixel dimensions. It scrolls on compact-height devices.
- Player controls include seek/progress, wing-shaped Previous/Next icons, rounded-pill Play/Pause, shuffle and repeat. They operate the existing Media3 MediaController.
- The active embedded cover supplies the dominant accent color for the Player and Queue sheet, including controls, progress, active queue row and lyrics affordance. Missing artwork falls back to a neutral white/grey accent in the app theme.
- The right-edge Lyrics handle can be tapped or swiped inward (left) to open Lyrics. Timed LRC lines highlight and scroll with playback. On track changes, the app checks same-folder `.lrc`, then embedded lyrics (ID3 USLT/SYLT/TXXX, FLAC Vorbis comments, Ogg/Opus comments, and iTunes-style M4A `©lyr`), then same-folder `.txt`.
- Android may not expose non-media `.lrc` files through MediaStore. The user can grant persistent read access once to the music folder (or parent folder), after which the app searches the matching folder for sidecars. A manual `.lrc`/`.txt` picker remains available.
- Queue sheet displays the existing queue in order and highlights the active track; selecting a row seeks to and plays it.

## Equalizer + audio effects

- The Equalizer screen is opened from the top-left player icon.
- The background PlaybackService listens for Media3 audio-session ID changes and owns Android Equalizer, BassBoost and Virtualizer effects for that session.
- EQ band values, enable state, and supported effect toggles/strengths are persisted in SharedPreferences and reattached to a new session.
- The Equalizer UI discovers the device's actual number of frequency bands, center frequencies, level range and available system presets. Unsupported effects remain disabled and are labelled accordingly.
- Equalizer effects are released with the playback service, not when MainActivity is destroyed, so they remain attached during background playback.

## Global navigation and appearance

- The main application theme is pure black with charcoal surfaces and neutral white/grey controls; cyan is no longer the app-wide base accent. Artwork-derived color remains enabled on the Player/Queue and lockscreen.
- Settings is opened from the upper-right gear in the Library header, not from a bottom navigation item.
- The Android system Back action returns through the in-app navigation history. At the Library root it does not close the app unexpectedly.
- The persistent Library / Queue bottom navigation strip is removed completely. A compact now-playing bar appears only while audio is actively playing on non-Player screens; it disappears when paused/stopped and on the full Player screen.
