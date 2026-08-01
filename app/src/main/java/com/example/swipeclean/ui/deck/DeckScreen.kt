package com.example.swipeclean.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.swipeclean.ui.menu.AppMenu
import com.example.swipeclean.viewmodel.DeckViewModel
import com.example.swipeclean.viewmodel.TrashBinViewModel
import androidx.activity.compose.BackHandler

@Composable
fun DeckScreen(
    viewModel: DeckViewModel,
    trashViewModel: TrashBinViewModel? = null,
    deckType: DeckType = DeckType.SCREENSHOTS,
    onNavigateBack: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToStats: () -> Unit = {}
) {
    val deckState = viewModel.deckState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }

    // Load the appropriate deck on first composition
    LaunchedEffect(deckType) {
        when (deckType) {
            DeckType.SCREENSHOTS -> viewModel.loadScreenshotsDeck()
            DeckType.LARGE_VIDEOS -> viewModel.loadLargeVideosDeck()
            DeckType.MONTHLY_PHOTOS -> viewModel.loadMonthlyPhotosDeck()
            DeckType.CUSTOM -> {}
        }
    }

    // Show snackbar if returning from trash with deletion stats (Phase 3.7)
    LaunchedEffect(trashViewModel) {
        if (trashViewModel != null) {
            val (freedBytes, itemCount) = trashViewModel.getLastDeletionStats()
            if (freedBytes > 0L && itemCount > 0) {
                val readableSize = when {
                    freedBytes < 1024 -> "$freedBytes B"
                    freedBytes < 1024 * 1024 -> "${freedBytes / 1024} KB"
                    freedBytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", freedBytes / (1024.0 * 1024.0))
                    else -> String.format("%.1f GB", freedBytes / (1024.0 * 1024.0 * 1024.0))
                }
                
                snackbarHostState.showSnackbar(
                    message = "✅ Freed $readableSize | Deleted $itemCount item(s)"
                )
                
                // Reset stats after showing
                trashViewModel.resetLastSessionStats()
            }
        }
    }

    // Handle device back button
    BackHandler {
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with menu
            TopBar(
                deckName = deckState.deckType,
                onTrashBinClick = onNavigateToTrash,
                onStatsClick = onNavigateToStats
            )

            // Main content
            if (deckState.isLoading && deckState.mediaItems.isEmpty()) {
                LoadingState()
            } else if (deckState.isEmpty()) {
                EmptyDeckState(
                    deckName = deckState.deckType,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (deckState.isComplete()) {
                EmptyDeckState(
                    deckName = "${deckState.deckType} - All Reviewed!",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val currentCard = deckState.getCurrentCard()
                if (currentCard != null) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top section: Large swipeable media card (60%)
                        MediaCard(
                            mediaItem = currentCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.6f)
                                .padding(16.dp),
                            onDragLeft = {
                                viewModel.swipeLeft()
                            },
                            onDragRight = {
                                viewModel.swipeRight()
                            }
                        )

                        // Bottom section: Info panel + action buttons (40%)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            // Media info panel
                            MediaInfoPanel(
                                mediaItem = currentCard,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            // Action buttons
                            ActionButtons(
                                onKeepClick = {
                                    viewModel.clickKeep()
                                },
                                onTrashClick = {
                                    viewModel.clickTrash()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating undo button (bottom-right)
        if (!deckState.isLoading && !deckState.isEmpty() && !deckState.isComplete()) {
            UndoButton(
                enabled = deckState.canUndo(),
                onClick = {
                    viewModel.undo()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 16.dp)
            )
        }

        // Snackbar host for showing deletion stats (Phase 3.7)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        )
    }
}

@Composable
private fun TopBar(
    deckName: String,
    onTrashBinClick: () -> Unit,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Menu on the left
        AppMenu(
            onTrashBinClick = onTrashBinClick,
            onStatsClick = onStatsClick,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Title in the center
        Text(
            text = deckName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}