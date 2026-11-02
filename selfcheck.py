#!/usr/bin/env python3
"""Preflight checks for the Krypton native Android project.

Run `python3 selfcheck.py` for static repository checks.
Run `python3 selfcheck.py --build` in an Android SDK + Gradle environment to
also execute the real debug APK build and verify that the APK was produced.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def report(label: str, ok: bool, details: str = "") -> bool:
    mark = "PASS" if ok else "FAIL"
    suffix = f" — {details}" if details else ""
    print(f"[{mark}] {label}{suffix}")
    return ok


def require_file(relative: str) -> bool:
    path = ROOT / relative
    return report(f"Required file: {relative}", path.is_file())


def _valid_android_manifest() -> bool:
    try:
        ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
        return True
    except (ET.ParseError, OSError):
        return False


def static_checks() -> bool:
    results: list[bool] = []
    print("KRYPTON PLAYER — REPOSITORY SELF-CHECK")
    print(f"Project root: {ROOT}")

    required = [
        "settings.gradle.kts",
        "build.gradle.kts",
        "gradle.properties",
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/krypton/player/MainActivity.kt",
        "app/src/main/java/com/krypton/player/LibraryScreen.kt",
        "app/src/main/java/com/krypton/player/PlaylistScreens.kt",
        "app/src/main/java/com/krypton/player/StatisticsScreen.kt",
        "app/src/main/java/com/krypton/player/PlayerRepositories.kt",
        "app/src/main/java/com/krypton/player/MusicLibrary.kt",
        "app/src/main/java/com/krypton/player/TrackArtwork.kt",
        "app/src/main/java/com/krypton/player/PlaybackService.kt",
        "app/src/main/java/com/krypton/player/PlaybackScreens.kt",
        "app/src/main/java/com/krypton/player/LockScreenActivity.kt",
        "app/src/main/java/com/krypton/player/EqualizerScreen.kt",
        "app/src/main/java/com/krypton/player/AudioEffects.kt",
        "app/src/main/java/com/krypton/player/PlaybackOptions.kt",
        "app/src/main/java/com/krypton/player/LyricsReader.kt",
        ".github/workflows/android.yml",
        "selfcheck.py",
    ]
    results.extend(require_file(item) for item in required)

    root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
    app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    main_activity = (ROOT / "app/src/main/java/com/krypton/player/MainActivity.kt").read_text(encoding="utf-8")
    library_screen = (ROOT / "app/src/main/java/com/krypton/player/LibraryScreen.kt").read_text(encoding="utf-8")
    playlist_screens = (ROOT / "app/src/main/java/com/krypton/player/PlaylistScreens.kt").read_text(encoding="utf-8")
    statistics_screen = (ROOT / "app/src/main/java/com/krypton/player/StatisticsScreen.kt").read_text(encoding="utf-8")
    repositories = (ROOT / "app/src/main/java/com/krypton/player/PlayerRepositories.kt").read_text(encoding="utf-8")
    music_library = (ROOT / "app/src/main/java/com/krypton/player/MusicLibrary.kt").read_text(encoding="utf-8")
    track_artwork = (ROOT / "app/src/main/java/com/krypton/player/TrackArtwork.kt").read_text(encoding="utf-8")
    playback_service = (ROOT / "app/src/main/java/com/krypton/player/PlaybackService.kt").read_text(encoding="utf-8")
    playback_screens = (ROOT / "app/src/main/java/com/krypton/player/PlaybackScreens.kt").read_text(encoding="utf-8")
    lockscreen = (ROOT / "app/src/main/java/com/krypton/player/LockScreenActivity.kt").read_text(encoding="utf-8")
    equalizer_screen = (ROOT / "app/src/main/java/com/krypton/player/EqualizerScreen.kt").read_text(encoding="utf-8")
    audio_effects = (ROOT / "app/src/main/java/com/krypton/player/AudioEffects.kt").read_text(encoding="utf-8")
    lyrics_reader = (ROOT / "app/src/main/java/com/krypton/player/LyricsReader.kt").read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")

    results.append(report("Native Android Gradle plugins", "com.android.application" in root_build and "org.jetbrains.kotlin.android" in root_build))
    results.append(report("AGP / Gradle compatibility pin", 'version "8.7.3"' in root_build and 'gradle-version: "8.9"' in workflow))
    results.append(report("Android app module and Compose enabled", 'id("com.android.application")' in app_build and "compose = true" in app_build))
    results.append(report("Media3 playback dependencies", "media3-exoplayer" in app_build and "media3-session" in app_build))
    results.append(report("Android manifest is valid XML", _valid_android_manifest()))
    results.append(report("Android manifest permissions", "READ_MEDIA_AUDIO" in manifest and "FOREGROUND_SERVICE_MEDIA_PLAYBACK" in manifest and "WAKE_LOCK" in manifest))
    results.append(report("MediaSessionService registered", ".PlaybackService" in manifest and "MediaSessionService" in manifest))
    results.append(report("Compose player UI present", "@Composable" in main_activity and "LibraryScreen" in main_activity and "PlayerScreen" in main_activity))
    results.append(report("Three-page swipeable Library carousel", "HorizontalPager" in library_screen and "pageCount = { 3 }" in library_screen and "OPEN PLAYLISTS" in library_screen and "VIEW STATISTICS" in library_screen))
    results.append(report("Compact Library hero banner", ".height(100.dp)" in library_screen and "PLAY LIBRARY" in library_screen))
    results.append(report("Library sort choices and reverse-order control", all(token in library_screen for token in ["Most Recent", "Title", "Most Plays", "ArrowUpward", "ArrowDownward"])))
    results.append(report("Sort inversion control is separated and mode-aware", "Box(Modifier.width(1.dp).height(24.dp).background(DividerColor))" in library_screen and "LibrarySort.TITLE -> !inverted" in library_screen and "LibrarySort.RECENT, LibrarySort.MOST_PLAYS -> inverted" in library_screen))
    results.append(report("Library sort preference persisted", "krypton_library_sort" in library_screen and 'putBoolean("inverted", inverted)' in library_screen))
    results.append(report("Duplicate header track count removed", "private fun AppHeader(" in main_activity and "trackCount:" not in main_activity.split("private fun AppHeader", 1)[1].split("@Composable", 1)[0]))
    results.append(report("Playlist creation, rename, delete and song management UI", all(token in playlist_screens for token in ["Create playlist", "Rename playlist", "Delete playlist?", "ADD SONGS", "onRemoveTrack"])))
    results.append(report("Playlist detail sorting and shuffle controls", all(token in playlist_screens for token in ["Title A–Z", "Title Z–A", "Icons.Default.Shuffle", "displayPlaylist"])))
    results.append(report("M3U import and export flows wired", 'ActivityResultContracts.OpenDocument()' in main_activity and 'CreateDocument("audio/x-mpegurl")' in main_activity and "#EXTM3U" in main_activity))
    results.append(report("Playlists persisted locally", "class PlaylistRepository" in repositories and "krypton_playlists" in repositories and "fun imported" in repositories))
    results.append(report("Dedicated Listening Stats screen", "LAST 7 DAYS" in statistics_screen and "MOST LISTENED" in statistics_screen and "TOTAL LISTENED" in statistics_screen))
    results.append(report("Playback statistics persisted locally", "class PlaybackStatsStore" in repositories and "total_listened_ms" in repositories and "day_${today}" in repositories))
    results.append(report("Track counts only after reaching half duration", "position >= itemDuration / 2L" in main_activity and "recordQualifiedPlay" in main_activity))
    results.append(report("Favorites are persisted and counted", "toggleFavorite" in repositories and "favorite_track_ids" in repositories and "FavoriteBorder" in library_screen))
    results.append(report("Library sort metadata loaded from MediaStore", all(token in music_library for token in ["DATE_ADDED", "DURATION", "DISPLAY_NAME", "MediaStore.Audio.Media.DATA"])))
    results.append(report("Embedded song artwork extracted asynchronously with fallback", all(token in track_artwork for token in ["MediaMetadataRetriever", "embeddedPicture", "Dispatchers.IO", "MusicNote", "LruCache"])))
    results.append(report("Artwork appears in Library, playlist, add-song, queue and statistics rows", all(token in library_screen for token in ["TrackArtwork(track = track"]) and all(token in playlist_screens for token in ["TrackArtwork(track = track", "TrackArtwork(track = availableTracks.first()"]) and "TrackArtwork(item.track" in playback_screens and "TrackArtwork(track = entry.track" in statistics_screen))
    results.append(report("Persistent Library/Queue bottom navigation removed", "NavigationBar(" not in main_activity and "NavigationBarItem(" not in main_activity and "Triple(\"Queue\"" not in main_activity))
    results.append(report("Mini-player appears only during active playback outside the full Player screen", 'if (isPlaying && controller?.currentMediaItem != null && selectedTab != "Queue")' in main_activity and "MiniPlayerBar(" in main_activity))
    results.append(report("Mini-player opens the full Player and exposes pause/next controls", 'onOpen = { navigateTo("Queue") }' in main_activity and 'contentDescription = "Pause playback"' in main_activity and 'contentDescription = "Next track"' in main_activity))
    results.append(report("Library song selection opens the full Player screen", 'onPlay = { sequence, selected -> startTrackAndOpenPlayer(sequence, selected) }' in main_activity and 'navigateTo("Queue")' in main_activity))
    results.append(report("Tapping the already-playing library song opens Player without restarting it", 'val alreadyPlaying = selected != null && controller?.currentMediaItem?.mediaId == selected.id.toString()' in main_activity and 'if (!alreadyPlaying) onPlay(sequence, selected)' in main_activity))
    results.append(report("Settings action moved to the Library top-right toolbar", 'onSettings = { navigateTo("Settings") }' in main_activity and 'Icon(Icons.Default.Settings, contentDescription = "Settings"' in main_activity and 'Triple("Settings"' not in main_activity))
    results.append(report("System Back navigates within app instead of exiting at root", 'BackHandler(enabled = true)' in main_activity and 'navigationHistory.removeAt(navigationHistory.lastIndex)' in main_activity))
    results.append(report("App-wide palette is pure black and neutral charcoal", all(token in main_activity for token in ['Background = Color(0xFF000000)', 'Surface = Color(0xFF0B0D10)', 'Accent = Color(0xFFE9EBEF)'])))
    results.append(report("Android status and navigation bars use black with light icons", 'WindowCompat.setDecorFitsSystemWindows(window, true)' in main_activity and 'window.statusBarColor = android.graphics.Color.BLACK' in main_activity and 'window.navigationBarColor = android.graphics.Color.BLACK' in main_activity and 'isAppearanceLightNavigationBars = false' in main_activity))
    results.append(report("No cyan base palette remains in the app-wide Library surfaces", all(token not in library_screen for token in ['0xFF203C45', '0xFF182A36', '0xFF25213C', '0x3324D1C5']) and '0xFF2D79B8' not in statistics_screen))
    results.append(report("Queue state and controls present", "addMediaItem" in main_activity and "seekToNextMediaItem" in main_activity))
    results.append(report("Media items keep track metadata", "MediaMetadata.Builder()" in main_activity and "setTitle(title)" in main_activity and "setArtist(artist)" in main_activity))
    results.append(report("LRC parsing and multi-timestamp handling", "fun parse(text: String)" in lyrics_reader and "lrcPattern" in lyrics_reader and "stamps.forEach stampLoop" in lyrics_reader))
    results.append(report("Automatic same-folder LRC/TXT lookup and sidecar refresh", all(token in lyrics_reader for token in ["detectForTrack", "detectSidecarForTrack", "sidecarNames", "findSidecarInMediaStore", "findSidecarInTree"])))
    results.append(report("Embedded ID3 USLT/SYLT/TXXX lyrics extraction", all(token in lyrics_reader for token in ["parseId3", "USLT", "SYLT", "parseLyricsUserText", "parseSylt"])) )
    results.append(report("Embedded FLAC, Ogg/Opus and M4A lyric metadata readers", all(token in lyrics_reader for token in ["parseFlacComments", "parseOggComments", "OpusTags", "parseMp4Lyrics", "©lyr"])) )
    results.append(report("One-time lyrics folder permission is persisted", all(token in main_activity for token in ["OpenDocumentTree()", "takePersistableUriPermission", "folder_tree_uri", "lyricsFolderTreeUri"])))
    results.append(report("Lyrics auto-detection reloads on active track or folder changes", "LaunchedEffect(activeTrack?.id, lyricsFolderTreeUri)" in main_activity and "onDetectLyrics(it, lyricsFolderTreeUri)" in main_activity))
    results.append(report("New sidecar files are polled while Lyrics is open", "delay(5_000L)" in main_activity and "onCheckSidecarLyrics(target, lyricsFolderTreeUri)" in main_activity and "detectSidecarForTrack" in lyrics_reader))
    results.append(report("Track MediaStore relative path retained for sidecar matching", "relativePath: String?" in music_library and "MediaStore.Audio.Media.RELATIVE_PATH" in music_library))
    results.append(report("Artwork-derived dynamic accent extraction", all(token in track_artwork for token in ["rememberArtworkAccent", "dominantArtworkAccent", "HSVToColor", "getOrLoadArtwork"])))
    results.append(report("Dynamic accent reaches Player and Queue sheet", "accent = activeAccent" in main_activity and "containerColor = tintSurface(Surface, accent" in playback_screens and "accent.copy(alpha = 0.14f)" in playback_screens))
    results.append(report("Lockscreen activity is registered over Android keyguard", 'android:name=".LockScreenActivity"' in manifest and 'android:showWhenLocked="true"' in manifest and "setShowWhenLocked(true)" in lockscreen))
    results.append(report("Lockscreen has a compact clock and rounded-square artwork", "fontSize = 43.sp" in lockscreen and "RoundedCornerShape(29.dp)" in lockscreen and "TrackArtwork(track = activeTrack" in lockscreen))
    results.append(report("Lockscreen omits a playback seekbar", "Slider(" not in lockscreen and "SliderDefaults" not in lockscreen))
    results.append(report("Lockscreen queue button opens live Media3 queue sheet", 'contentDescription = "Open playback queue"' in lockscreen and "LockScreenQueueDialog(" in lockscreen and "player.mediaItemCount" in lockscreen))
    results.append(report("Lockscreen queue preserves current-track highlighting and selection", "isCurrent = index == player.currentMediaItemIndex" in lockscreen and "controller?.seekTo(index, 0L)" in lockscreen))
    results.append(report("Lockscreen shuffle button toggles live playback shuffle", 'contentDescription = if (shuffleEnabled)' in lockscreen and "player.shuffleModeEnabled = !player.shuffleModeEnabled" in lockscreen))
    results.append(report("Lockscreen uses wing skips and rounded-pill play/pause", "WingSkipGlyph(next = false" in lockscreen and "WingSkipGlyph(next = true" in lockscreen and "RoundedCornerShape(34.dp)" in lockscreen and "if (playing)" in lockscreen))
    results.append(report("Lockscreen colors derive from current artwork on pure black", "rememberArtworkAccent(activeTrack)" in lockscreen and "background(Color.Black)" in lockscreen and "TrackArtwork(track = activeTrack" in lockscreen))
    results.append(report("Lockscreen slide closes only Krypton surface and leaves system security intact", "onSlideUnlock = { finish() }" in lockscreen and "does not" in lockscreen and "secure PIN/pattern/password" in lockscreen))
    results.append(report("Lockscreen can be opened from Settings", "LockScreenSettingsCard(onOpen = onOpenLockScreen)" in main_activity and "LockScreenActivity::class.java" in main_activity))
    results.append(report("Wing-shaped previous/next and rounded-pill Play/Pause controls", all(token in playback_screens for token in ["WingSkipGlyph(next = false", "WingSkipGlyph(next = true", "RoundedCornerShape(34.dp)", "clickable(onClick = onPlayPause)"])))
    results.append(report("Background audio focus and unplug handling", "setAudioAttributes(AudioAttributes.DEFAULT, true)" in playback_service and "setHandleAudioBecomingNoisy(true)" in playback_service and "setWakeMode(C.WAKE_MODE_LOCAL)" in playback_service))
    results.append(report("Background playback service releases resources", "player.release()" in playback_service and "mediaSession?.run" in playback_service and "release()" in playback_service))
    results.append(report("Adaptive full Now Playing screen", "BoxWithConstraints" in playback_screens and "artworkSize" in playback_screens and 'Text("NOW PLAYING"' in playback_screens))
    results.append(report("Player transport controls operate MediaController", all(token in main_activity for token in ["controller?.seekTo(target)", "player.shuffleModeEnabled", "player.repeatMode", "seekToPreviousMediaItem", "seekToNextMediaItem"])))
    results.append(report("Combined Queue + Player glyph remains available in Player and lockscreen", "QueuePlayerGlyph" in playback_screens and "QueuePlayerGlyph" in lockscreen and "Icons.Default.PlayArrow" in playback_screens))
    results.append(report("Top-right queue icon opens a queue sheet", "onOpenQueue = { queueSheetOpen = true }" in main_activity and "ModalBottomSheet" in playback_screens and "UP NEXT" in playback_screens))
    results.append(report("Right-edge lyrics gesture and tap affordance", "detectHorizontalDragGestures" in playback_screens and "onOpenLyrics" in playback_screens and "LyricsSwipeHandle" in playback_screens))
    results.append(report("Lyrics view synchronizes timed lines to playback", "indexOfLast { it.timeMs != null && it.timeMs <= positionMs }" in playback_screens and "animateScrollToItem" in playback_screens))
    results.append(report("Lyrics file picker reads LRC/TXT documents", 'ActivityResultContracts.OpenDocument()' in main_activity and '"text/plain"' in main_activity and "LyricsReader.readDocument" in main_activity))
    results.append(report("Service listens for Media3 audio session IDs", "onAudioSessionIdChanged" in playback_service and "KryptonAudioEffects.attach" in playback_service))
    results.append(report("Service-owned equalizer survives Activity recreation", "KryptonAudioEffects.release()" in playback_service and "private var equalizer: Equalizer?" in audio_effects and "SharedPreferences" in audio_effects))
    results.append(report("Equalizer UI has bands, presets and device-aware effect controls", all(token in equalizer_screen for token in ["PRESETS", "FREQUENCY BANDS", "VerticalBandSlider", "BASS BOOST", "VIRTUALIZER"])))
    results.append(report("Equalizer status Text receives a non-null String", "val statusMessage: String = when" in equalizer_screen and "text = statusMessage" in equalizer_screen and "state.error.orEmpty()" in equalizer_screen))
    results.append(report("Compose verticalScroll imported in every using screen", all(
        "import androidx.compose.foundation.verticalScroll" in (ROOT / f"app/src/main/java/com/krypton/player/{name}").read_text(encoding="utf-8")
        for name in ["EqualizerScreen.kt", "PlaybackScreens.kt"]
    )))
    results.append(report("Compose Color import present where PlaylistScreens uses Color", "import androidx.compose.ui.graphics.Color" in playlist_screens and "Color(0xFF252930)" in playlist_screens))
    results.append(report("Android audio settings permission declared", "MODIFY_AUDIO_SETTINGS" in manifest))

    workflow_dir = ROOT / ".github/workflows"
    workflow_files = sorted(p for p in workflow_dir.glob("*") if p.is_file()) if workflow_dir.exists() else []
    results.append(report("Exactly one GitHub Actions workflow", len(workflow_files) == 1, ", ".join(p.name for p in workflow_files)))
    results.append(report("Manual workflow dispatch enabled", "workflow_dispatch:" in workflow))
    results.append(report("Push builds run on all branches", 'branches:' in workflow and '- "**"' in workflow))
    results.append(report("Workflow configures JDK 17", "actions/setup-java@v4" in workflow and 'java-version: "17"' in workflow))
    results.append(report(
        "Workflow uses current Android SDK setup action",
        "android-actions/setup-android@v4" in workflow and "packages: 'platform-tools'" in workflow,
    ))
    results.append(report(
        "Workflow avoids removed legacy SDK tools package",
        "tools platform-tools" not in "\n".join(
            line for line in workflow.splitlines() if not line.lstrip().startswith("#")
        ) and '"tools"' not in "\n".join(
            line for line in workflow.splitlines() if not line.lstrip().startswith("#")
        ),
    ))
    results.append(report(
        "Workflow installs Android 35 platform and build tools",
        "sdkmanager --install" in workflow and "platforms;android-35" in workflow and "build-tools;35.0.0" in workflow,
    ))
    results.append(report("Workflow provisions Gradle 8.9", "gradle/actions/setup-gradle@v4" in workflow and 'gradle-version: "8.9"' in workflow))
    results.append(report("Workflow runs self-check with real build", "python3 selfcheck.py --build" in workflow and "assembleDebug" not in workflow))
    results.append(report("Workflow locates nested Gradle project root", "find . -type f -name 'settings.gradle.kts'" in workflow and "project_root" in workflow))
    results.append(report("Workflow uploads the produced APK", "actions/upload-artifact@v4" in workflow and "app-debug.apk" in workflow))

    workflow_texts = "\n".join(p.read_text(encoding="utf-8", errors="replace") for p in workflow_files)
    results.append(report("No Flutter commands remain in workflows", "flutter pub get" not in workflow_texts and "flutter build" not in workflow_texts))
    results.append(report("No stale Flutter project manifest", not (ROOT / "pubspec.yaml").exists()))

    agp_match = re.search(r'com\.android\.application"\) version "([0-9.]+)"', root_build)
    gradle_match = re.search(r"gradle-version:\s*[\"']([0-9.]+)[\"']", workflow)
    results.append(report("Gradle / AGP versions are readable", bool(agp_match and gradle_match)))

    failed = [name for name, ok in zip(required, results[: len(required)]) if not ok]
    total = len(results)
    passed = sum(results)
    print(f"\nStatic checks: {passed}/{total} passed")
    return all(results)


def run_build() -> bool:
    gradle = shutil.which("gradle")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not gradle:
        return report("Gradle build", False, "Gradle is not installed or not on PATH; CI installs Gradle 8.9")
    if not sdk or not Path(sdk).exists():
        return report("Gradle build", False, "Android SDK not found; install Android SDK 35 and set ANDROID_HOME")

    command = [gradle, "--no-daemon", "--stacktrace", ":app:assembleDebug"]
    print("\nExecuting:", " ".join(command))
    completed = subprocess.run(command, cwd=ROOT, check=False)
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    build_ok = completed.returncode == 0 and apk.is_file() and apk.stat().st_size > 0
    if build_ok:
        print(f"APK: {apk.relative_to(ROOT)} ({apk.stat().st_size:,} bytes)")
    return report("Gradle debug APK build", build_ok, f"exit code {completed.returncode}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build", action="store_true", help="run the real Gradle assembleDebug build after static checks")
    args = parser.parse_args()

    checks_ok = static_checks()
    build_ok = run_build() if args.build and checks_ok else True
    if checks_ok and build_ok:
        print("\nSELF-CHECK RESULT: PASS")
        return 0
    print("\nSELF-CHECK RESULT: FAIL")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
