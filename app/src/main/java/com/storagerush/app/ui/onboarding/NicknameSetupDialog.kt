package com.storagerush.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.data.repository.CloudSyncRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

/**
 * One-time nickname setup dialog shown after tutorial completion, before
 * the main Deck screen becomes fully interactive. Lets the player choose a
 * public-facing name for leaderboards, checks availability, and triggers
 * the full cloud sync setup (anonymous auth → player record creation →
 * mark setup complete).
 *
 * Once setup completes, this never shows again — gated by
 * UserPreferencesRepository.hasCompletedCloudSetup().
 */
@Composable
fun NicknameSetupDialog(
    cloudSyncRepository: CloudSyncRepository,
    userPreferencesRepository: UserPreferencesRepository,
    onSetupComplete: () -> Unit
) {
    var nicknameInput by remember { mutableStateOf("") }
    var isCheckingAvailability by remember { mutableStateOf(false) }
    var availabilityError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var nicknameConfirmed by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Consume every touch on the scrim itself so taps never fall
            // through to whatever is rendered behind this dialog (e.g.
            // MediaCard's tap-for-video gesture on DeckScreen). Without
            // this, only actual Compose-clickable children (buttons,
            // etc.) intercept touches — empty space, padding, and even
            // the title Text are "dead" and pass taps straight through
            // in a plain overlay Box.
            .pointerInput(Unit) {
                detectTapGestures { /* no-op: just eat the tap */ }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Same reasoning as the outer scrim — keeps the card's
                // own tap area self-contained.
                .pointerInput(Unit) {
                    detectTapGestures { /* no-op: just eat the tap */ }
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Choose your nickname",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "This is how you'll appear on the leaderboard. You can change it later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = nicknameInput,
                onValueChange = {
                    nicknameInput = it
                    // Clear confirmation when user edits
                    nicknameConfirmed = false
                    availabilityError = null
                    submitError = null
                },
                label = { Text("Nickname") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingAvailability && !isSubmitting,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() }
                ),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Check availability button
            if (!nicknameConfirmed && nicknameInput.isNotEmpty()) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        scope.launch {
                            isCheckingAvailability = true
                            availabilityError = null
                            try {
                                val available = cloudSyncRepository.isNicknameAvailable(nicknameInput)
                                if (available) {
                                    nicknameConfirmed = true
                                } else {
                                    availabilityError = "Nickname already taken"
                                }
                            } catch (e: Exception) {
                                availabilityError = e.message ?: "Check failed"
                            } finally {
                                isCheckingAvailability = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCheckingAvailability && !isSubmitting && nicknameInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    if (isCheckingAvailability) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        Text("Check Availability")
                    }
                }
            }

            // Error message
            if (availabilityError != null) {
                Text(
                    text = availabilityError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (submitError != null) {
                Text(
                    text = submitError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Confirm button (only active after availability check passes)
            Button(
                onClick = {
                    keyboardController?.hide()
                    scope.launch {
                        isSubmitting = true
                        submitError = null
                        try {
                            // Step 1: anonymous sign-in — returns the Supabase
                            // auth user ID, which is what players.anonymous_id
                            // gets set to.
                            val anonymousId = cloudSyncRepository.ensureSignedIn()

                            // Step 2: create the player record. Returns a
                            // Result<PlayerRecord> rather than throwing, so
                            // both branches need explicit handling.
                            val result = cloudSyncRepository.createPlayerRecord(
                                anonymousId = anonymousId,
                                nickname = nicknameInput
                            )

                            result.fold(
                                onSuccess = {
                                    // Step 3: mark cloud setup complete — this
                                    // lives on UserPreferencesRepository (local
                                    // DataStore), not CloudSyncRepository.
                                    userPreferencesRepository.markCloudSetupComplete(nicknameInput)
                                    onSetupComplete()
                                },
                                onFailure = { error ->
                                    submitError = error.message ?: "Setup failed"
                                    isSubmitting = false
                                }
                            )
                        } catch (e: Exception) {
                            submitError = e.message ?: "Setup failed"
                            isSubmitting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = nicknameConfirmed && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create profile")
                }
            }
        }
    }
}