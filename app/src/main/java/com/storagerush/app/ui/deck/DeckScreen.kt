package com.storagerush.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.data.model.MediaItem
import com.storagerush.app.ui.components.HelpButton
import com.storagerush.app.ui.menu.AppMenu
import com.storagerush.app.viewmodel.DeckViewModel
import com.storagerush.app.viewmodel.PlayerViewModel
import com.storagerush.app.viewmodel.TrashBinViewModel
import androidx.activity.compose.BackHandler

@Composable
fun DeckScreen(
    viewModel: DeckViewModel,
    trashViewModel: TrashBinViewModel? = null,
    playerViewModel: PlayerViewModel? = null,
    deckType: DeckType = DeckType.Screenshots,
    onNavigateBack: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToImages: () -> Unit = {},
    onNavigateToVideos: () -> Unit = {},
    onOpenTutorial: () -> Unit = {}
) {
    val deckState = viewModel.deckState.collectAsState().value
    val playerState = playerViewModel?.playerState?.collectAsState()?.value
    val snackbarHostState = remember { SnackbarHostState() }

    // Which video (if any) is currently shown in the full-screen overlay
    // (Phase B). Null means the overlay is closed.
    var overlayVideoItem by remember { mutableStateOf<MediaItem?>(null) }

    // Level-up celebration (Phase 2 gamification) — null means no
    // celebration pending. Populated below, in the same LaunchedEffect
    // that already watches for deletion stats, right before those stats
    // get reset. Purely transient: never written to disk, so a killed app
    // process naturally clears this — reopening never replays a stale
    // celebration.
    var levelUpInfo by remember { mutableStateOf<LevelUpInfo?>(null) }

    // Load the appropriate deck on first composition
    LaunchedEffect(deckType) {
        viewModel.loadDeck(deckType)
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

                if (trashViewModel.wasLastDeletionLevelUp() && playerState != null) {
                    // Level-up takes priority over the regular deletion
                    // snackbar for this event — the celebration screen
                    // already communicates how much was freed, so showing
                    // both would be redundant.
                    levelUpInfo = LevelUpInfo(
                        level = playerState.level,
                        freedBytesReadable = readableSize
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        message = "✅ Freed $readableSize | Deleted $itemCount item(s)"
                    )
                }

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
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar with menu
            TopBar(
                deckName = deckState.deckType,
                onTrashBinClick = onNavigateToTrash,
                onStatsClick = onNavigateToStats,
                onImagesClick = onNavigateToImages,
                onVideosClick = onNavigateToVideos,
                onHelpClick = onOpenTutorial
            )

            // Gamification progress card (Level + streak + XP) — sits
            // between the top bar and the deck content, matching the
            // approved mockup. Only renders once a PlayerViewModel is
            // wired up from MainActivity; existing call sites that don't
            // pass one yet keep compiling and simply skip the card.
            if (playerState != null) {
                ProgressCard(
                    playerState = playerState,
                    onClick = onNavigateToStats,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                )
            }

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
                                .weight(0.6f)
                                .padding(16.dp),
                            onDragLeft = {
                                viewModel.swipeLeft()
                            },
                            onDragRight = {
                                viewModel.swipeRight()
                            },
                            onTapVideo = {
                                overlayVideoItem = currentCard
                            }
                        )

                        // Bottom section: Info panel + action buttons (40%)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.4f)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            // Media info panel — takes remaining space above the
                            // buttons and scrolls internally if content (long
                            // filenames, larger system font size, etc.) doesn't fit,
                            // instead of pushing the buttons off-screen.
                            MediaInfoPanel(
                                mediaItem = currentCard,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 8.dp)
                            )

                            // Action buttons — natural height, always fully visible,
                            // padded for gesture-nav devices so it never sits under
                            // the system navigation bar.
                            ActionButtons(
                                onKeepClick = {
                                    viewModel.clickKeep()
                                },
                                onTrashClick = {
                                    viewModel.clickTrash()
                                },
                                modifier = Modifier.navigationBarsPadding()
                            )
                        }
                    }
                }
            }
        }

        // Floating undo button — positioned to hover just above the Keep/Trash
        // row, over the info panel's space. This is a floating overlay (lives
        // in the outer Box, not inside the info panel's Column), so it never
        // takes layout space from the info panel and never affects its
        // weight()/verticalScroll() behavior — purely a visual position change.
        if (!deckState.isLoading && !deckState.isEmpty() && !deckState.isComplete()) {
            UndoButton(
                enabled = deckState.canUndo(),
                onClick = {
                    viewModel.undo()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 16.dp)
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

        // Full-screen video overlay (Phase B) — only composed while a video
        // is actually selected, so its ExoPlayer is created/released exactly
        // in sync with the overlay being open/closed (see VideoOverlay.kt's
        // DisposableEffect for the actual release call).
        val videoItem = overlayVideoItem
        if (videoItem != null) {
            VideoOverlay(
                mediaItem = videoItem,
                onDismiss = {
                    overlayVideoItem = null
                }
            )
        }

        // Level-up celebration (Phase 2 gamification) — drawn last so it
        // sits on top of everything, including the video overlay/snackbar.
        // Only closes via its own Continue button.
        val pendingLevelUp = levelUpInfo
        if (pendingLevelUp != null) {
            LevelUpCelebrationOverlay(
                newLevel = pendingLevelUp.level,
                freedBytesReadable = pendingLevelUp.freedBytesReadable,
                onContinue = { levelUpInfo = null }
            )
        }
    }
}

/**
 * Transient data for the pending level-up celebration. Never persisted —
 * see the levelUpInfo state declaration above for why that's deliberate.
 */
private data class LevelUpInfo(
    val level: Int,
    val freedBytesReadable: String
)

@Composable
private fun TopBar(
    deckName: String,
    onTrashBinClick: () -> Unit,
    onStatsClick: () -> Unit,
    onImagesClick: () -> Unit,
    onVideosClick: () -> Unit,
    onHelpClick: () -> Unit,
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
            onImagesClick = onImagesClick,
            onVideosClick = onVideosClick,
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

        // Help ("?") on the right — mirrors the menu on the left
        HelpButton(
            onClick = onHelpClick,
            modifier = Modifier.align(Alignment.CenterEnd)
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