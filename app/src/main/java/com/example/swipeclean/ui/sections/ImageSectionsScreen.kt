package com.example.swipeclean.ui.sections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.swipeclean.data.model.BucketInfo
import com.example.swipeclean.data.repository.MediaStoreRepository
import com.example.swipeclean.ui.deck.DeckType

/**
 * Phase 4.5: Section picker for Images.
 * Shows every real image folder on the device (MediaStoreRepository.getAllImageBuckets()),
 * sorted by item count, with sparse folders (<3 items) collapsed behind
 * "Show more folders" so heavy WhatsApp/Telegram users don't get a wall of
 * near-empty app-cache-style folders drowning out the ones that matter.
 *
 * This is a simple one-shot fetch — load once, no ongoing writes or complex
 * state transitions — so it reads directly from the repository via
 * LaunchedEffect rather than needing a dedicated ViewModel.
 */
@Composable
fun ImageSectionsScreen(
    onBucketSelected: (DeckType.Bucket) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaStoreRepository(context) }

    var buckets by remember { mutableStateOf<List<BucketInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showSparseFolders by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        buckets = mediaRepository.getAllImageBuckets()
        isLoading = false
    }

    BackHandler { onNavigateBack() }

    val mainFolders = remember(buckets) { buckets.filter { it.itemCount >= 3 } }
    val sparseFolders = remember(buckets) { buckets.filter { it.itemCount < 3 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SectionHeader(title = "Images", onNavigateBack = onNavigateBack)

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            buckets.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No photo folders found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mainFolders, key = { it.bucketId }) { bucket ->
                        BucketRow(
                            bucket = bucket,
                            onClick = {
                                onBucketSelected(
                                    DeckType.Bucket(bucketId = bucket.bucketId, bucketName = bucket.bucketName)
                                )
                            }
                        )
                    }

                    if (sparseFolders.isNotEmpty()) {
                        item(key = "show_more_toggle") {
                            Text(
                                text = if (showSparseFolders) {
                                    "Hide sparse folders"
                                } else {
                                    "Show more folders (${sparseFolders.size})"
                                },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSparseFolders = !showSparseFolders }
                                    .padding(vertical = 8.dp)
                            )
                        }

                        if (showSparseFolders) {
                            items(sparseFolders, key = { it.bucketId }) { bucket ->
                                BucketRow(
                                    bucket = bucket,
                                    onClick = {
                                        onBucketSelected(
                                            DeckType.Bucket(bucketId = bucket.bucketId, bucketName = bucket.bucketName)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = "← Back",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onNavigateBack() }
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun BucketRow(
    bucket: BucketInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = bucket.thumbnailUri,
            contentDescription = bucket.bucketName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bucket.bucketName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "${bucket.itemCount} item(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}