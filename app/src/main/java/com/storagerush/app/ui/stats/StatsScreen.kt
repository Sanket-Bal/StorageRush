package com.storagerush.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.data.gamification.Achievement
import com.storagerush.app.data.model.remote.FriendLeaderboardEntry
import com.storagerush.app.data.repository.AppStats
import com.storagerush.app.data.repository.PlayerState
import com.storagerush.app.viewmodel.FriendsUiState
import com.storagerush.app.viewmodel.GlobalUiState
import com.storagerush.app.viewmodel.NicknameEditState
import com.storagerush.app.viewmodel.StatsViewModel

// Sampled from the app logo — same brand purple used in the Deck screen's
// XP bar (ProgressCard.kt), kept consistent here for the ring/fills.
private val BrandPurple = Color(0xFF2E2480)

private enum class StatsTab(val label: String) {
    PROGRESS("Profile"),
    ACHIEVEMENTS("Achievements"),
    LEADERBOARD("Leaderboard")
}

private enum class LeaderboardScope(val label: String) {
    LOCAL("Local"),
    FRIENDS("Friends"),
    GLOBAL("Global")
}

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state = viewModel.uiState.collectAsState().value
    val friendsState = viewModel.friendsUiState.collectAsState().value
    val globalState = viewModel.globalUiState.collectAsState().value
    val isAccountLinked = viewModel.isAccountLinked.collectAsState().value
    val nickname = viewModel.nickname.collectAsState().value
    val hasCloudProfile = viewModel.hasCloudProfile.collectAsState().value
    val nicknameEditState = viewModel.nicknameEditState.collectAsState().value
    var selectedTab by remember { mutableStateOf(StatsTab.PROGRESS) }

    BackHandler { onNavigateBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (state.isLoading) {
            LoadingState()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                StatsHeader(onBack = onNavigateBack)

                TabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        StatsTab.PROGRESS -> ProgressTab(
                            playerState = state.playerState,
                            appStats = state.appStats,
                            nickname = nickname,
                            hasCloudProfile = hasCloudProfile,
                            nicknameEditState = nicknameEditState,
                            onSaveNickname = { viewModel.updateNickname(it) },
                            onClearNicknameError = { viewModel.clearNicknameEditError() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        StatsTab.ACHIEVEMENTS -> AchievementsTab(
                            achievements = state.achievements,
                            playerState = state.playerState,
                            appStats = state.appStats,
                            unlockedCount = state.unlockedCount,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        StatsTab.LEADERBOARD -> LeaderboardTab(
                            playerState = state.playerState,
                            appStats = state.appStats,
                            friendsState = friendsState,
                            globalState = globalState,
                            isAccountLinked = isAccountLinked,
                            onLoadFriends = { viewModel.loadFriendsTab() },
                            onRedeemCode = { code -> viewModel.redeemFriendCode(code) },
                            onLoadGlobal = { viewModel.loadGlobalTab() },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (selectedTab == StatsTab.PROGRESS) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        Button(
                            onClick = { viewModel.clearAllStats() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Reset All Stats")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onBack() }
        )

        Text(
            text = "Cleanup Stats",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Empty spacer matching the Back text's width class so the title
        // stays visually centered, mirroring the three-slot header pattern
        // used on DeckScreen's TopBar.
        Text(text = "", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TabBar(
    selectedTab: StatsTab,
    onTabSelected: (StatsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Inline nickname display/editor shown at the top of the Profile tab.
 * View mode shows the nickname with an edit pencil; tapping it switches
 * to an editable text field with save (✓) / cancel (✗). Availability
 * check + cloud update happen in StatsViewModel.updateNickname() — this
 * composable only owns the transient "am I currently editing" UI state.
 */
@Composable
private fun NicknameEditor(
    nickname: String,
    editState: NicknameEditState,
    onSave: (String) -> Unit,
    onClearError: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var draftNickname by remember { mutableStateOf(nickname) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Once a save succeeds, the nickname prop updates and editState.errorMessage
    // clears — drop out of edit mode automatically rather than requiring a
    // second tap.
    LaunchedEffect(nickname, editState.errorMessage, editState.isSaving) {
        if (isEditing && !editState.isSaving && editState.errorMessage == null && draftNickname.trim() == nickname) {
            isEditing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(
            text = "Nickname",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        draftNickname = nickname
                        onClearError()
                        isEditing = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit nickname",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = draftNickname,
                    onValueChange = {
                        draftNickname = it
                        onClearError()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !editState.isSaving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            onSave(draftNickname)
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                if (editState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            onSave(draftNickname)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save nickname",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            draftNickname = nickname
                            onClearError()
                            isEditing = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (editState.errorMessage != null) {
                Text(
                    text = editState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
// Progress tab (displayed to the user as "Profile")
// ---------------------------------------------------------------------

@Composable
private fun ProgressTab(
    playerState: PlayerState,
    appStats: AppStats,
    nickname: String?,
    hasCloudProfile: Boolean,
    nicknameEditState: NicknameEditState,
    onSaveNickname: (String) -> Unit,
    onClearNicknameError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (hasCloudProfile) {
            NicknameEditor(
                nickname = nickname ?: "",
                editState = nicknameEditState,
                onSave = onSaveNickname,
                onClearError = onClearNicknameError
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        LevelRing(playerState = playerState)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Weekly streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\uD83D\uDD25 ${playerState.weeklyStreak} week${if (playerState.weeklyStreak == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Best streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${playerState.bestStreak} weeks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Text(
                text = "Lifetime stats",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            StatRow("Items cleaned", "${appStats.totalMediaCleaned} items")
            StatRow("Storage freed", appStats.getTotalStorageFreedReadable())
            StatRow("Career XP", "${playerState.totalCareerXp} XP")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LevelRing(
    playerState: PlayerState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 10.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = BrandPurple,
                    startAngle = -90f,
                    sweepAngle = 360f * playerState.xpProgressFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${playerState.level}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "${playerState.currentXp} / ${playerState.xpRequiredForNextLevel} XP to level ${playerState.level + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------------------
// Achievements tab
// ---------------------------------------------------------------------

@Composable
private fun AchievementsTab(
    achievements: List<Achievement>,
    playerState: PlayerState,
    appStats: AppStats,
    unlockedCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$unlockedCount of ${achievements.size} badges earned",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(achievements, key = { it.id }) { achievement ->
                val isUnlocked = achievement.isUnlocked(playerState, appStats)
                AchievementBadge(achievement = achievement, isUnlocked = isUnlocked)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    isUnlocked: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isUnlocked) 0.5f else 0.25f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isUnlocked) achievement.emoji else "\uD83D\uDD12", // 🔒
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = achievement.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (isUnlocked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------
// Leaderboard tab
// ---------------------------------------------------------------------

@Composable
private fun LeaderboardTab(
    playerState: PlayerState,
    appStats: AppStats,
    friendsState: FriendsUiState,
    globalState: GlobalUiState,
    isAccountLinked: Boolean,
    onLoadFriends: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onLoadGlobal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scope by remember { mutableStateOf(LeaderboardScope.LOCAL) }

    // Load friends/global data on-demand the first time the user switches
    // to that scope, rather than eagerly whenever Stats opens — both hit
    // the network, so no reason to pay that cost unless the tab is
    // actually used. Skipped entirely when not account-linked: those tabs
    // show a locked prompt instead (see below), so there's nothing to load.
    LaunchedEffect(scope, isAccountLinked) {
        if (!isAccountLinked) return@LaunchedEffect
        when (scope) {
            LeaderboardScope.FRIENDS -> onLoadFriends()
            LeaderboardScope.GLOBAL -> onLoadGlobal()
            LeaderboardScope.LOCAL -> Unit
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LeaderboardScope.entries.forEach { entry ->
                val isSelected = entry == scope
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent
                        )
                        .clickable { scope = entry }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        when (scope) {
            LeaderboardScope.LOCAL -> LocalLeaderboardContent(playerState, appStats)
            LeaderboardScope.FRIENDS -> {
                if (isAccountLinked) {
                    FriendsLeaderboardContent(
                        state = friendsState,
                        onRedeemCode = onRedeemCode,
                        onRetry = onLoadFriends
                    )
                } else {
                    LinkAccountToUnlockPrompt()
                }
            }
            LeaderboardScope.GLOBAL -> {
                if (isAccountLinked) {
                    GlobalLeaderboardContent(state = globalState, onRetry = onLoadGlobal)
                } else {
                    LinkAccountToUnlockPrompt()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LocalLeaderboardContent(
    playerState: PlayerState,
    appStats: AppStats
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${playerState.level}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "You",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Level ${playerState.level} · \uD83D\uDD25 ${playerState.weeklyStreak} weeks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = appStats.getTotalStorageFreedReadable(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Personal best",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        StatRow("Longest streak", "${playerState.bestStreak} weeks")
        StatRow("Biggest single cleanup", appStats.getLargestSingleCleanupReadable())
        StatRow("Highest level", "${playerState.level}")
    }
}

/**
 * Shown in place of the Friends/Global leaderboard content when the
 * player hasn't linked an email account yet. Local XP/streak tracking
 * (and the Local leaderboard scope) stays available to everyone —
 * Friends/Global are the social/cross-device features that come with
 * linking, so they're the only things gated here.
 */
@Composable
private fun LinkAccountToUnlockPrompt() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Sign up or log in to unlock this",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Link an email from the menu to see friends and the global leaderboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Phase B: real friends leaderboard content — friend code (with copy),
 * a redeem-code input, and the friends list sorted by lifetime XP.
 * Replaces the old ComingSoonPlaceholder for this scope.
 */
@Composable
private fun FriendsLeaderboardContent(
    state: FriendsUiState,
    onRedeemCode: (String) -> Unit,
    onRetry: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var redeemInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Your friend code
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp)
        ) {
            Text(
                text = "Your friend code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.myFriendCode ?: "···",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (state.myFriendCode != null) {
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(state.myFriendCode))
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Redeem a friend's code
        Text(
            text = "Add a friend",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = redeemInput,
                onValueChange = { redeemInput = it.uppercase() },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Enter code") },
                enabled = !state.isRedeeming,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Button(
                onClick = {
                    onRedeemCode(redeemInput)
                    redeemInput = ""
                },
                enabled = redeemInput.isNotBlank() && !state.isRedeeming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isRedeeming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Add")
                }
            }
        }

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Retry")
            }
        }

        if (state.redeemSuccessMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.redeemSuccessMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Friends (${state.friends.size})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.friends.isEmpty()) {
            Text(
                text = "No friends yet — share your code above or redeem one to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                state.friends.forEachIndexed { index, friend ->
                    FriendRow(rank = index + 1, friend = friend)
                }
            }
        }
    }
}

@Composable
private fun FriendRow(rank: Int, friend: FriendLeaderboardEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.nickname,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Level ${friend.level} · \uD83D\uDD25 ${friend.weeklyStreak} weeks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${friend.totalCareerXp} XP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Global leaderboard content — top 50 players worldwide, ranked by
 * lifetime XP. Read-only: no friend code / redeem UI here, unlike
 * FriendsLeaderboardContent. The current player's own row (matched by
 * cached local nickname, see GlobalUiState.myNickname) is visually
 * highlighted with a "You" tag if they happen to be in the top 50.
 */
@Composable
private fun GlobalLeaderboardContent(state: GlobalUiState, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Top 50 worldwide",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Retry")
                }
            }
        } else if (state.entries.isEmpty()) {
            Text(
                text = "No players on the leaderboard yet — be the first!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                state.entries.forEachIndexed { index, entry ->
                    GlobalRow(
                        rank = index + 1,
                        entry = entry,
                        isYou = state.myNickname != null && entry.nickname == state.myNickname
                    )
                }
            }

            // If the player has a saved nickname but it wasn't found among
            // the fetched top 50, let them know rather than staying silent
            // about why "You" never shows up in the list above.
            if (state.myNickname != null && state.entries.none { it.nickname == state.myNickname }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You're not in the top 50 yet — keep cleaning up to climb the ranks!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GlobalRow(rank: Int, entry: FriendLeaderboardEntry, isYou: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isYou) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isYou) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "You",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Text(
                text = "Level ${entry.level} · \uD83D\uDD25 ${entry.weeklyStreak} weeks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${entry.totalCareerXp} XP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ComingSoonPlaceholder(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Loading stats...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}