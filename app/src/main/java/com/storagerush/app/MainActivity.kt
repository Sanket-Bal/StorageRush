package com.storagerush.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.storagerush.app.ui.deck.DeckScreen
import com.storagerush.app.ui.deck.DeckType
import com.storagerush.app.ui.permission.PermissionDialog
import com.storagerush.app.ui.sections.ImageSectionsScreen
import com.storagerush.app.ui.sections.VideoSectionsScreen
import com.storagerush.app.ui.stats.StatsScreen
import com.storagerush.app.ui.theme.StorageRushTheme
import com.storagerush.app.ui.trash.TrashBinScreen
import com.storagerush.app.ui.tutorial.OnboardingGateOverlay
import com.storagerush.app.ui.tutorial.TutorialGuideScreen
import com.storagerush.app.ui.onboarding.NicknameSetupDialog
import com.storagerush.app.ui.onboarding.AccountLinkDialog
import com.storagerush.app.data.repository.CloudSyncRepository
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.StatsRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import com.storagerush.app.viewmodel.DeckViewModel
import com.storagerush.app.viewmodel.PlayerViewModel
import com.storagerush.app.viewmodel.StatsViewModel
import com.storagerush.app.viewmodel.TrashBinViewModel
import androidx.activity.result.IntentSenderRequest
import android.os.Build

enum class AppScreen {
    DECK,
    TRASH_BIN,
    STATS,
    IMAGE_SECTIONS,
    VIDEO_SECTIONS,
    TUTORIAL
}

/**
 * Media permission state, version-aware:
 * - FULL: all-photos access granted (any API level)
 * - PARTIAL: Android 14+ "select photos" partial access granted
 * - DENIED: nothing usable granted; show the permission request UI
 */
enum class MediaPermissionState {
    FULL,
    PARTIAL,
    DENIED
}

class MainActivity : ComponentActivity() {

    private var trashViewModelInstance: TrashBinViewModel? = null
    private var pendingMediaIds: List<Long> = emptyList()

    // Handle MediaStore deletion result
    private val deleteFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Files were deleted successfully
            trashViewModelInstance?.onDeletionSuccess()
        } else {
            // User cancelled deletion
            trashViewModelInstance?.hideDeleteConfirmation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent status/navigation bars instead of the default grey
        // scrim — content draws edge-to-edge behind them. Must be called
        // before super.onCreate() per the official androidx.activity
        // guidance. Screens are responsible for their own inset padding
        // (see DeckScreen's .statusBarsPadding() / .navigationBarsPadding())
        // so content doesn't render underneath the system bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            StorageRushTheme {
                val permissionState = remember {
                    mutableStateOf(checkPermissions())
                }
                val permissionsGranted = remember {
                    derivedStateOf { permissionState.value != MediaPermissionState.DENIED }
                }

                val currentScreen = remember {
                    mutableStateOf(AppScreen.DECK)
                }

                // Keep ViewModels alive across screen changes
                val deckViewModel = remember { DeckViewModel(context = this@MainActivity) }
                val trashViewModel = remember { TrashBinViewModel(context = this@MainActivity) }
                val statsViewModel = remember { StatsViewModel(context = this@MainActivity) }
                val playerViewModel = remember { PlayerViewModel(context = this@MainActivity) }

                // Phase A (Friends leaderboard) — cloud infrastructure
                val userPreferencesRepository = remember { UserPreferencesRepository(this@MainActivity) }
                val cloudSyncRepository = remember { CloudSyncRepository(this@MainActivity) }
                val playerRepository = remember { PlayerRepository(this@MainActivity) }
                val statsRepository = remember { StatsRepository(this@MainActivity) }
                var showNicknameDialog by remember { mutableStateOf(false) }
                var showAccountLinkDialog by remember { mutableStateOf(false) }
                var hasCompletedCloudSetup by remember { mutableStateOf(false) }

                // Menu-triggered account linking (separate from the onboarding
                // showAccountLinkDialog above) — reachable any time via the
                // hamburger menu, not just once after the first-launch tutorial.
                var showMenuAccountLinkDialog by remember { mutableStateOf(false) }
                var showLogoutConfirmation by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // Reactive link state for the menu's Sign Up/Log In ↔ Log Out
                // item — flips live the moment markAccountLinked()/
                // clearAccountLink() writes to DataStore, no manual refresh needed.
                val isAccountLinked by userPreferencesRepository.isAccountLinkedFlow
                    .collectAsState(initial = false)
                val linkedEmail by userPreferencesRepository.linkedEmailFlow
                    .collectAsState(initial = null)

                // Resolved once per app session: last-viewed deck section
                // read from storage, or the temporary fallback below if
                // nothing's been saved yet (first launch).
                var resolvedDeckType by remember { mutableStateOf<DeckType?>(null) }

                // Resolved once per app session: has the mandatory
                // first-launch tutorial already been completed? Null while
                // still reading from storage.
                var hasSeenTutorial by remember { mutableStateOf<Boolean?>(null) }

                // True only during the brief window after the user taps the
                // help button on the onboarding gate, while the mandatory
                // tutorial cards are showing.
                var showMandatoryTutorialCards by remember { mutableStateOf(false) }

                // Store reference for deletion handler
                trashViewModelInstance = trashViewModel

                if (!permissionsGranted.value) {
                    // Show permission dialog on startup
                    PermissionDialog(
                        onPermissionsGranted = {
                            permissionState.value = checkPermissions()
                        }
                    )
                } else {
                    // Read/resolve the initial deck once permission is granted
                    // (Phase 4.4 + 4.6). Runs once per session.
                    LaunchedEffect(Unit) {
                        if (resolvedDeckType == null) {
                            resolvedDeckType = deckViewModel.resolveInitialDeckType()
                        }
                        if (hasSeenTutorial == null) {
                            hasSeenTutorial = deckViewModel.hasSeenTutorial()
                        }
                        // Load cloud setup state from UserPreferences
                        hasCompletedCloudSetup = userPreferencesRepository.hasCompletedCloudSetup()
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main app navigation
                        // NOTE: permissionState.value == MediaPermissionState.PARTIAL means the
                        // user granted access to only some photos/videos (Android 14+). Phase 4.5
                        // will surface a banner here prompting them to select more if they want.
                        when (currentScreen.value) {
                            AppScreen.DECK -> {
                                val deckType = resolvedDeckType
                                if (deckType == null) {
                                    // Still resolving last-viewed section from storage
                                    // (typically instantaneous; this rarely renders visibly).
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else {
                                    DeckScreen(
                                        viewModel = deckViewModel,
                                        trashViewModel = trashViewModel,
                                        playerViewModel = playerViewModel,
                                        deckType = deckType,
                                        onNavigateBack = {
                                            // Deck is the app's root screen — system
                                            // back here should exit the app, not
                                            // silently do nothing (the previous
                                            // default) or navigate anywhere else.
                                            finish()
                                        },
                                        onNavigateToTrash = {
                                            currentScreen.value = AppScreen.TRASH_BIN
                                        },
                                        onNavigateToStats = {
                                            currentScreen.value = AppScreen.STATS
                                        },
                                        onNavigateToProfile = {
                                            // Profile is the (renamed) first tab of
                                            // StatsScreen, which defaults to it on
                                            // fresh composition — no extra state needed.
                                            currentScreen.value = AppScreen.STATS
                                        },
                                        onNavigateToImages = {
                                            currentScreen.value = AppScreen.IMAGE_SECTIONS
                                        },
                                        onNavigateToVideos = {
                                            currentScreen.value = AppScreen.VIDEO_SECTIONS
                                        },
                                        onOpenTutorial = {
                                            // Voluntary reopen (dismissible mode) —
                                            // separate from the mandatory first-launch flow.
                                            currentScreen.value = AppScreen.TUTORIAL
                                        },
                                        isAccountLinked = isAccountLinked,
                                        linkedEmail = linkedEmail,
                                        onAccountLinkClick = {
                                            showMenuAccountLinkDialog = true
                                        },
                                        onLogoutClick = {
                                            showLogoutConfirmation = true
                                        }
                                    )
                                }
                            }

                            AppScreen.IMAGE_SECTIONS -> {
                                ImageSectionsScreen(
                                    onBucketSelected = { bucket ->
                                        // Reassigning resolvedDeckType changes DeckScreen's
                                        // deckType param, which its own LaunchedEffect(deckType)
                                        // reacts to by calling viewModel.loadDeck() automatically —
                                        // no separate load call needed here.
                                        resolvedDeckType = bucket
                                        currentScreen.value = AppScreen.DECK
                                    },
                                    onNavigateBack = {
                                        currentScreen.value = AppScreen.DECK
                                    }
                                )
                            }

                            AppScreen.VIDEO_SECTIONS -> {
                                VideoSectionsScreen(
                                    onFilterSelected = { filter ->
                                        resolvedDeckType = filter
                                        currentScreen.value = AppScreen.DECK
                                    },
                                    onNavigateBack = {
                                        currentScreen.value = AppScreen.DECK
                                    }
                                )
                            }

                            AppScreen.TRASH_BIN -> {
                                TrashBinScreen(
                                    viewModel = trashViewModel,
                                    onNavigateBack = {
                                        currentScreen.value = AppScreen.DECK
                                    },
                                    onDeleteTriggered = { uris ->
                                        // Launch MediaStore deletion
                                        if (uris.isNotEmpty()) {
                                            launchFileDeletion(uris)
                                        }
                                    }
                                )
                            }

                            AppScreen.STATS -> {
                                StatsScreen(
                                    viewModel = statsViewModel,
                                    onNavigateBack = {
                                        currentScreen.value = AppScreen.DECK
                                    }
                                )
                            }

                            AppScreen.TUTORIAL -> {
                                // Voluntary reopen, via the "?" button on Deck —
                                // dismissible, since the user already knows the app.
                                TutorialGuideScreen(
                                    isMandatory = false,
                                    onDismiss = {
                                        currentScreen.value = AppScreen.DECK
                                    }
                                )
                            }
                        }

                        // Mandatory first-launch onboarding — drawn last so it
                        // sits on top of literally everything above, regardless
                        // of currentScreen (in practice this only ever shows
                        // while on Deck, since every other screen is only
                        // reachable through Deck's menu/buttons, which the
                        // gate blocks).
                        if (hasSeenTutorial == false) {
                            if (!showMandatoryTutorialCards) {
                                OnboardingGateOverlay(
                                    onHelpTapped = {
                                        showMandatoryTutorialCards = true
                                    }
                                )
                            } else {
                                TutorialGuideScreen(
                                    isMandatory = true,
                                    onComplete = {
                                        deckViewModel.markTutorialSeen()
                                        hasSeenTutorial = true
                                        showMandatoryTutorialCards = false
                                        // After tutorial, offer account linking first (dismissible),
                                        // falling through to nickname setup either way it resolves.
                                        if (!hasCompletedCloudSetup) {
                                            showAccountLinkDialog = true
                                        }
                                    }
                                )
                            }
                        }

                        // Account linking (Sign Up / Log In) — offered once after tutorial,
                        // dismissible. Shown before NicknameSetupDialog so a returning
                        // user can restore their existing profile instead of getting a
                        // fresh nickname prompt.
                        if (showAccountLinkDialog) {
                            AccountLinkDialog(
                                cloudSyncRepository = cloudSyncRepository,
                                userPreferencesRepository = userPreferencesRepository,
                                playerRepository = playerRepository,
                                statsRepository = statsRepository,
                                onDismissed = {
                                    // Skip/X: stay anonymous, no nickname popup. The user
                                    // can still play locally and link an account later via
                                    // the hamburger menu.
                                    showAccountLinkDialog = false
                                },
                                onNeedsNickname = {
                                    // Reached only via a real Sign Up (verifySignUpOtp) —
                                    // Log In never routes here, see AccountLinkDialog.
                                    showAccountLinkDialog = false
                                    showNicknameDialog = true
                                },
                                onFullyRestored = {
                                    showAccountLinkDialog = false
                                    hasCompletedCloudSetup = true
                                    // Progress was just restored into local DataStore by
                                    // AccountLinkDialog — no nickname dialog needed, this
                                    // is a returning user with an existing profile.
                                }
                            )
                        }

                        // Nickname setup dialog (Phase A finalizer) — shown after tutorial,
                        // blocks main app interaction until player profile is created on cloud.
                        // Gated by hasCompletedCloudSetup so it never shows twice.
                        if (showNicknameDialog && !hasCompletedCloudSetup) {
                            NicknameSetupDialog(
                                cloudSyncRepository = cloudSyncRepository,
                                userPreferencesRepository = userPreferencesRepository,
                                onSetupComplete = {
                                    showNicknameDialog = false
                                    hasCompletedCloudSetup = true
                                    // Cloud sync is now active; syncing will happen automatically
                                    // after every cleanup event (see TrashBinViewModel.syncPlayerProgress())
                                }
                            )
                        }

                        // Menu-triggered account linking — reachable any time via
                        // AppMenu's "Sign Up / Log In" item, not just once after
                        // onboarding. confirmBeforeRestore = true here: unlike the
                        // onboarding path above, there may be real local progress
                        // worth protecting by now, so a successful Log In pauses
                        // on an inline confirmation before overwriting it.
                        if (showMenuAccountLinkDialog) {
                            AccountLinkDialog(
                                cloudSyncRepository = cloudSyncRepository,
                                userPreferencesRepository = userPreferencesRepository,
                                playerRepository = playerRepository,
                                statsRepository = statsRepository,
                                confirmBeforeRestore = true,
                                onDismissed = {
                                    showMenuAccountLinkDialog = false
                                },
                                onNeedsNickname = {
                                    showMenuAccountLinkDialog = false
                                    showNicknameDialog = true
                                },
                                onFullyRestored = {
                                    showMenuAccountLinkDialog = false
                                    hasCompletedCloudSetup = true
                                }
                            )
                        }

                        // Log Out confirmation — resets local progress to a fresh
                        // new-player state (see PlayerRepository.resetToNewPlayer() /
                        // StatsRepository.clearAllStats()). The cloud record itself is
                        // untouched, so logging back in restores it — this warning is
                        // about local device state, not data loss.
                        if (showLogoutConfirmation) {
                            AlertDialog(
                                onDismissRequest = { showLogoutConfirmation = false },
                                title = { Text("Log out?") },
                                text = {
                                    Text("This will reset your local level, streak, and stats back to a fresh start. Your cloud-saved progress isn't deleted — logging back in will restore it.")
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showLogoutConfirmation = false
                                        scope.launch {
                                            cloudSyncRepository.signOut()
                                            userPreferencesRepository.clearAccountLink()
                                            // The level/streak/stats on screen belonged to
                                            // the account just logged out of — reset local
                                            // progress back to a fresh new-player state
                                            // rather than continuing to show it. Achievement
                                            // badges are derived live from these same values
                                            // (see Achievement.kt), so they reset for free.
                                            playerRepository.resetToNewPlayer()
                                            statsRepository.clearAllStats()
                                        }
                                    }) {
                                        Text("Log Out")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutConfirmation = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
    * Launch MediaStore deletion (Phase 3.5)
    */
    private fun launchFileDeletion(uris: List<android.net.Uri>) {
        try {
            // MediaStore.createDeleteRequest requires API 30+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteFilesLauncher.launch(
                    IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                )
            } else {
                // Fallback for devices below API 30
                trashViewModelInstance?.deleteSelectedItemsFallback()
            }
        } catch (e: Exception) {
            // TEMP DEBUG: show the real reason before falling back
            android.widget.Toast.makeText(
                this,
                "Delete request failed: ${e.javaClass.simpleName}: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            android.util.Log.e("StorageRush", "MediaStore.createDeleteRequest failed", e)
            // Fallback if anything goes wrong
            trashViewModelInstance?.deleteSelectedItemsFallback()
        }
    }

    /**
     * Version-aware permission check.
     * - API 34+ : full (IMAGES+VIDEO) > partial (VISUAL_USER_SELECTED) > denied
     * - API 33  : IMAGES+VIDEO
     * - API 24-32 : READ_EXTERNAL_STORAGE
     */
    private fun checkPermissions(): MediaPermissionState {
        fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                val fullAccess = granted(Manifest.permission.READ_MEDIA_IMAGES) &&
                    granted(Manifest.permission.READ_MEDIA_VIDEO)
                val partialAccess = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    fullAccess -> MediaPermissionState.FULL
                    partialAccess -> MediaPermissionState.PARTIAL
                    else -> MediaPermissionState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= 33 -> {
                val fullAccess = granted(Manifest.permission.READ_MEDIA_IMAGES) &&
                    granted(Manifest.permission.READ_MEDIA_VIDEO)
                if (fullAccess) MediaPermissionState.FULL else MediaPermissionState.DENIED
            }
            else -> {
                // API 24-32: single READ_EXTERNAL_STORAGE permission
                if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    MediaPermissionState.FULL
                } else {
                    MediaPermissionState.DENIED
                }
            }
        }
    }
}