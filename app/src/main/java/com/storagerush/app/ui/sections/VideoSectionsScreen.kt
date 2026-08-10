package com.storagerush.app.ui.sections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storagerush.app.ui.deck.DeckType
import com.storagerush.app.ui.deck.VideoFilterType

private data class VideoFilterOption(
    val filter: VideoFilterType,
    val title: String,
    val description: String,
    val emoji: String
)

private val videoFilterOptions = listOf(
    VideoFilterOption(
        filter = VideoFilterType.LARGE_VIDEOS,
        title = "Large Videos",
        description = "Videos over 50 MB — the biggest space users",
        emoji = "📦"
    ),
    VideoFilterOption(
        filter = VideoFilterType.SHORT_VIDEOS,
        title = "Short Clips",
        description = "Under 30 seconds — often accidental recordings",
        emoji = "🎬"
    ),
    VideoFilterOption(
        filter = VideoFilterType.ALL_VIDEOS,
        title = "All Videos",
        description = "Every video on your device, newest first",
        emoji = "🎞️"
    )
)

/**
 * Phase 4.5: Section picker for Videos.
 * Attribute-based filters (agreed in planning), not folder-based like Images —
 * video folders tend to be far less differentiated than photo folders, so
 * size/duration filters give more useful cleanup targeting.
 */
@Composable
fun VideoSectionsScreen(
    onFilterSelected: (DeckType.VideoFilter) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        SectionHeader(title = "Videos", onNavigateBack = onNavigateBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videoFilterOptions, key = { it.filter.name }) { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .clickable { onFilterSelected(DeckType.VideoFilter(option.filter)) }
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "${option.emoji} ${option.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}