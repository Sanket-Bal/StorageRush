package com.example.swipeclean

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.example.swipeclean.ui.deck.DeckScreen
import com.example.swipeclean.ui.deck.DeckType
import com.example.swipeclean.ui.permission.PermissionDialog
import com.example.swipeclean.ui.stats.StatsScreen
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import com.example.swipeclean.ui.trash.TrashBinScreen
import com.example.swipeclean.viewmodel.DeckViewModel
import com.example.swipeclean.viewmodel.StatsViewModel
import com.example.swipeclean.viewmodel.TrashBinViewModel
import androidx.activity.result.IntentSenderRequest
import android.os.Build

enum class AppScreen {
    DECK,
    TRASH_BIN,
    STATS
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
        super.onCreate(savedInstanceState)
        setContent {
            SwipeCleanTheme {
                val permissionsGranted = remember {
                    mutableStateOf(checkPermissions())
                }

                val currentScreen = remember {
                    mutableStateOf(AppScreen.DECK)
                }

                // Keep ViewModels alive across screen changes
                val deckViewModel = remember { DeckViewModel(context = this@MainActivity) }
                val trashViewModel = remember { TrashBinViewModel(context = this@MainActivity) }
                val statsViewModel = remember { StatsViewModel(context = this@MainActivity) }

                // Store reference for deletion handler
                trashViewModelInstance = trashViewModel

                if (!permissionsGranted.value) {
                    // Show permission dialog on startup
                    PermissionDialog(
                        onPermissionsGranted = {
                            permissionsGranted.value = true
                        }
                    )
                } else {
                    // Main app navigation
                    when (currentScreen.value) {
                        AppScreen.DECK -> {
                            DeckScreen(
                                viewModel = deckViewModel,
                                trashViewModel = trashViewModel,
                                deckType = DeckType.SCREENSHOTS,
                                onNavigateToTrash = {
                                    currentScreen.value = AppScreen.TRASH_BIN
                                },
                                onNavigateToStats = {
                                    currentScreen.value = AppScreen.STATS
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
            // Fallback if anything goes wrong
            trashViewModelInstance?.deleteSelectedItemsFallback()
        }
    }

    private fun checkPermissions(): Boolean {
        val imagePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        val videoPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED

        return imagePermission && videoPermission
    }
}