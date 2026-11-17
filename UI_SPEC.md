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
- A compact now-playing bar appears at the bottom of non-Player screens whenever a track is loaded (playing or paused). It shows the current artwork, title and artist, a play/pause and a next action, and a thin progress line. Tapping its body opens Now Playing; it is hidden only while the full Player screen is open or when nothing is loaded.
- Minimal layout: no title bar and no glow. Large cover, left-aligned title and artist, thin progress line (seek on release), previous / round play-pause / next, then one quiet action row: shuffle, repeat, lyrics, equalizer (opens the Equalizer screen) and queue (opens the queue sheet without leaving the player).
- The main player adapts its artwork size to available width and height rather than using screenshot pixel dimensions. It scrolls on compact-height devices.
- Player controls include seek/progress, wing-shaped Previous/Next icons, rounded-pill Play/Pause, shuffle and repeat. They operate the existing Media3 MediaController.
- The active embedded cover supplies the dominant accent color for the Player and Queue sheet, including controls, progress, active queue row and lyrics affordance. Missing artwork falls back to a neutral white/grey accent in the app theme.
- Swiping the cover left, or tapping the lyrics icon in the action row, opens Lyrics. Timed LRC lines highlight and scroll with playback. On track changes, the app checks same-folder `.lrc`, then embedded lyrics (ID3 USLT/SYLT/TXXX, FLAC Vorbis comments, Ogg/Opus comments, and iTunes-style M4A `©lyr`), then same-folder `.txt`.
- Android may not expose non-media `.lrc` files through MediaStore. The user can grant persistent read access once to the music folder (or parent folder), after which the app searches the matching folder for sidecars. A manual `.lrc`/`.txt` picker remains available.
- Queue sheet displays the existing queue in order and highlights the active track; selecting a row seeks to and plays it.

## Equalizer + audio effects

- The Equalizer screen is opened from the equalizer icon in the player's action row.
- The background PlaybackService listens for Media3 audio-session ID changes and owns Android Equalizer, BassBoost and Virtualizer effects for that session.
- EQ band values, enable state, and supported effect toggles/strengths are persisted in SharedPreferences and reattached to a new session.
- The Equalizer UI discovers the device's actual number of frequency bands, center frequencies, level range and available system presets. Unsupported effects remain disabled and are labelled accordingly.
- Equalizer effects are released with the playback service, not when MainActivity is destroyed, so they remain attached during background playback.

## Global navigation and appearance

- The default theme (Mono) is pure black with charcoal surfaces and neutral white/grey controls. Other themes are selectable in Settings > Appearance (see Themes below). Artwork-derived color remains enabled on the Player/Queue/Lyrics unless switched off in Settings, and on the lockscreen.
- Settings is opened from the upper-right gear in the Library header, not from a bottom navigation item.
- The Android system Back action returns through the in-app navigation history. At the Library root Back is handled by the system (the screen closes, playback keeps running in the service).
- The persistent Library / Queue bottom navigation strip is removed completely. The compact now-playing bar stays visible while a track is loaded (also when paused, so the Player is always one tap away) and disappears on the full Player screen.


## Themes

- Six selectable palettes live in `Themes.kt`: Mono (default, the original black/charcoal look), Midnight, Ember, Emerald, Violet and Rose. Each defines background, three surface levels, accent, deep accent, three text levels and a divider colour.
- Settings > Appearance shows a 2-column grid of tiles; each tile previews its own palette (background, raised card, accent dot and progress bar). Tapping a tile applies it immediately and saves it in SharedPreferences (`krypton_theme`).
- All screens read colours through the tokens in `MainActivity.kt` (`Background`, `Surface`, `SurfaceRaised`, `SurfaceHigh`, `Accent`, `AccentDeep`, `TextPrimary`, `TextSecondary`, `TextMuted`, `DividerColor`), which forward to the selected theme. Do not add hard-coded hex colours to screens; add a token instead.
- Visible theme surfaces: a soft accent glow behind every screen, the Library hero banner and its icon tile, page dots, the header logo tile, the mini-player play button and progress line, the statistics chart bars, settings icons and the player background.
- Settings > "Artwork colours on Player" (on by default) lets the Player, Queue sheet and Lyrics take their accent from the cover art. Turn it off to always use the theme accent.
- Status bar, navigation bar and window background follow the theme background.

## Player behaviour notes

- The seek bar follows the finger while dragging and seeks once on release; the position label shows the dragged time.
- Previous restarts the current track when it has played for more than a few seconds, otherwise it goes to the previous track.
- Repeat shows a dedicated Repeat-One icon; the status line under the controls shows shuffle and repeat together.
- Playback position is refreshed every 250 ms while playing; the queue list is rebuilt only on real player events.
- Playback uses music audio attributes (focus, ducking, headphone-unplug pause). On Android 13+ the notification permission is requested after the storage permission so the media notification and lock-screen controls can appear.


## Search focus and cover quality

- Focusing the Library search field scrolls the app header and the hero banner up and out of view (300 ms animated collapse), leaving only the search field, the collection header and a larger song list. A **Cancel** button appears beside the field. The zone stays collapsed while the field is focused or a query is present; Cancel, clearing the query and unfocusing, or system Back restores it.
- Search filters the list on every keystroke. Matching is case- and accent-insensitive ("detail" finds "Détail") and every typed word must appear in the title or artist.
- The Now Playing screen and the custom lockscreen decode the embedded cover at up to 1280 px and draw it with high-quality filtering; list rows keep the small 256 px thumbnails. The current media id is Compose state, so the cover changes immediately on every next/previous/automatic track change.

## Now Playing backdrop and swipe-down

- When "Artwork colours on Player" is on, the Now Playing background is a vertical gradient built from the cover's dominant hue: a readable mid-tone on top (never near-black, capped in luminance so white text stays legible) fading to a deeper shade of the same hue. Grey covers use a neutral slate. Colors cross-fade over 600 ms on every track change and the status/navigation bars follow them while the screen is open. With the toggle off the screen keeps the plain theme background.
- Dragging down anywhere on the screen moves it with the finger and fades it slightly; releasing past about 22% of the height (or with a downward fling) slides it away and returns to the previous screen, otherwise it springs back.

## Reordering the queue

- Every row in the Up Next sheet ends with a visible three-dot handle. Press and drag it up or down: the row lifts (slight scale, shadow, haptic tick), follows the finger, the other rows glide out of the way, and the list auto-scrolls when the row is held near the top or bottom edge. Releasing settles the row into place.
- The new order is applied in the sheet immediately and sent to the player once on release (`moveMediaItem`), so the playing track keeps playing and keeps its highlight wherever it is moved. Each queue entry has a stable key (media id + occurrence) so duplicates of the same song are safe.

## Smart Shuffle sort

- "Sort by" has a fourth option, **Smart Shuffle**. It orders the library randomly but weighted by play count (weight = 1 + plays), so most-played songs tend to come first while unplayed ones still appear. Tapping the option again, or the shuffle button that replaces the direction arrow while this mode is active, draws a fresh order and scrolls to the top.
- The order stays stable until the next tap: favoriting a song or a play being counted does not reshuffle it. "Play library" and tapping a song play in the displayed shuffled order.
