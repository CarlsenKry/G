package com.krypton.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

@Composable
internal fun StatisticsScreen(modifier: Modifier = Modifier, stats: StatsSnapshot) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.weight(1f), Icons.Default.Headphones, formatDurationShort(stats.totalListenedMs), "TOTAL LISTENED")
                    MetricCard(Modifier.weight(1f), Icons.Default.MusicNote, stats.tracksPlayed.toString(), "TRACKS PLAYED")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(Modifier.weight(1f), Icons.Default.Favorite, stats.favorites.toString(), "FAVORITES")
                    MetricCard(Modifier.weight(1f), Icons.Default.LibraryMusic, stats.librarySize.toString(), "LIBRARY SIZE")
                }
            }
        }
        item { WeeklyListeningCard(stats.lastSevenDays) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp)) {
                Text("MOST LISTENED", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(12.dp))
                if (stats.mostListened.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BarChart, null, tint = Accent, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your listening history will appear here", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    stats.mostListened.forEachIndexed { index, entry ->
                        MostListenedRow(entry = entry)
                        if (index != stats.mostListened.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(19.dp)).background(Surface).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(21.dp))
        Text(value, color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeeklyListeningCard(days: List<DailyListening>) {
    val maximum = (days.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(1L)
    val dateFormat = DateTimeFormatter.ofPattern("MM/dd")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp)) {
        Text("LAST 7 DAYS", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(7.dp))
        Text("Listening time recorded by Krypton Player", color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val hasTime = day.durationMs > 0L
                val height = if (hasTime) (day.durationMs.toFloat() / maximum.toFloat() * 96f).coerceAtLeast(5f) else 2f
                Column(Modifier.weight(1f).height(142.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hasTime) formatChartDuration(day.durationMs) else "—", color = TextMuted, fontSize = 9.sp, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.width(27.dp).height(height.dp).clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(if (hasTime) Brush.verticalGradient(listOf(Color(0xFFB9BEC6), Color(0xFF59616C))) else Brush.verticalGradient(listOf(Color(0xFF343A43), Color(0xFF343A43))))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(day.date.format(dateFormat), color = TextMuted, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MostListenedRow(entry: TrackListening) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork(track = entry.track, modifier = Modifier.size(50.dp), cornerRadius = 12.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.track.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.track.artist, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDurationShort(entry.durationMs), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${entry.playCount} plays", color = TextMuted, fontSize = 9.sp)
        }
    }
}

private fun formatChartDuration(durationMs: Long): String {
    val minutes = (durationMs / 60_000L).coerceAtLeast(1L)
    val hours = minutes / 60L
    return if (hours > 0L) "${hours}h" else "${minutes}m"
}
