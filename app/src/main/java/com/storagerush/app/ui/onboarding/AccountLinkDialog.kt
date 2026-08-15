package com.storagerush.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.data.model.remote.PlayerRecord
import com.storagerush.app.data.repository.CloudSyncRepository
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.StatsRepository
import com.storagerush.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.launch

private enum class AccountLinkMode {
    SIGN_UP, LOG_IN
}

/**
 * Derives a best-effort nickname from an email's local part, used only for
 * the Log In edge case below where a verified identity has no player
 * record yet. Not availability-checked up front — see the retry-with-
 * suffix handling at the call site for the collision case.
 */
private fun fallbackNicknameFromEmail(email: String): String {
    val local = email.substringBefore("@").filter { it.isLetterOrDigit() }.take(16)
    return local.ifBlank { "player" }
}

/**
 * Supabase's RestException.message bundles the full request/response dump
 * (URL, headers, HTTP method) — useful in Logcat (see CloudSyncRepository's
 * Log.e calls), unusable as UI text. Every send-code error shown in this
 * dialog routes through this instead of ever displaying e.message directly.
 * The two branches below map the errors that actually happen in practice:
 * re-signing-up with an already-registered email, and logging in with an
 * email that's never signed up.
 */
private fun friendlySendCodeError(e: Throwable, mode: AccountLinkMode): String {
    val raw = e.message.orEmpty()
    return when {
        raw.contains("email_exists", ignoreCase = true) ||
            raw.contains("already been registered", ignoreCase = true) ->
            "That email is already linked to an account. Try Log In instead."

        raw.contains("otp_disabled", ignoreCase = true) ||
            raw.contains("Signups not allowed", ignoreCase = true) ->
            "No account found with that email. Try Sign Up instead."

        mode == AccountLinkMode.SIGN_UP ->
            "Couldn't send the sign-up code. Please check your connection and try again."

        else ->
            "Couldn't send the login code. Please check your connection and try again."
    }
}

/**
 * Shared restore step used by both the immediate (onboarding) and
 * confirmed (menu, after the user accepts the overwrite warning) Log In
 * paths, so the actual DataStore-writing logic only exists once.
 */
private suspend fun performRestore(
    playerRepository: PlayerRepository,
    statsRepository: StatsRepository,
    userPreferencesRepository: UserPreferencesRepository,
    playerRecord: PlayerRecord
) {
    playerRepository.restoreFromCloud(
        level = playerRecord.level,
        currentXp = playerRecord.currentXp,
        totalCareerXp = playerRecord.totalCareerXp,
        weeklyStreak = playerRecord.weeklyStreak,
        bestStreak = playerRecord.bestStreak,
        lastCleanupTimestamp = playerRecord.lastCleanupTimestamp ?: 0L
    )
    statsRepository.restoreFromCloud(
        totalStorageFreedBytes = playerRecord.storageFreedBytes,
        totalMediaCleaned = playerRecord.totalMediaCleaned,
        largestSingleCleanupBytes = playerRecord.largestSingleCleanupBytes
    )
    userPreferencesRepository.markCloudSetupComplete(playerRecord.nickname)
}

/**
 * Optional account-linking dialog: Sign Up (link email to the current
 * anonymous session, preserving its data) or Log In (reconnect to a
 * previously-linked account on a new install). Always dismissible via
 * the X — this is opt-in, not mandatory like NicknameSetupDialog.
 *
 * Three possible outcomes on completion, each routed differently by the
 * caller (see MainActivity):
 * - [onDismissed]: user tapped X, skip entirely, fall through to the
 *   existing anonymous + nickname flow.
 * - [onNeedsNickname]: email successfully linked/verified, but there's
 *   no existing player record under this identity yet (first-ever Sign
 *   Up, or a Log In to an account that never finished nickname setup) —
 *   fall through to NicknameSetupDialog same as the skip path.
 * - [onFullyRestored]: Log In succeeded AND an existing player record
 *   was found and restored into local DataStore — go straight to Deck,
 *   no nickname dialog needed.
 *
 * [confirmBeforeRestore]: false during first-launch onboarding (default)
 * — there's no local progress worth protecting yet, so a successful Log
 * In restores immediately. Pass true when opening this dialog from the
 * hamburger menu mid-use: the user may have real local progress, and
 * logging into a different pre-existing cloud account would otherwise
 * silently overwrite it. When true, a successful Log In pauses on an
 * inline "replace your current progress?" confirmation instead of
 * restoring right away.
 */
@Composable
fun AccountLinkDialog(
    cloudSyncRepository: CloudSyncRepository,
    userPreferencesRepository: UserPreferencesRepository,
    playerRepository: PlayerRepository,
    statsRepository: StatsRepository,
    onDismissed: () -> Unit,
    onNeedsNickname: () -> Unit,
    onFullyRestored: () -> Unit,
    confirmBeforeRestore: Boolean = false
) {
    var mode by remember { mutableStateOf(AccountLinkMode.SIGN_UP) }
    var emailInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Populated only when confirmBeforeRestore is true and a Log In just
    // verified successfully against an account that has existing cloud
    // progress. Non-null means: show the confirmation step instead of
    // the normal email/code UI, and hold off on touching local DataStore
    // until the user explicitly confirms.
    var pendingRestore by remember { mutableStateOf<PlayerRecord?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    fun resetForModeSwitch(newMode: AccountLinkMode) {
        mode = newMode
        codeSent = false
        codeInput = ""
        errorMessage = null
    }

    fun sendCode() {
        keyboardController?.hide()
        errorMessage = null
        scope.launch {
            isSending = true
            val result = if (mode == AccountLinkMode.SIGN_UP) {
                cloudSyncRepository.sendSignUpOtp(emailInput)
            } else {
                cloudSyncRepository.sendLoginOtp(emailInput)
            }
            result.fold(
                onSuccess = { codeSent = true },
                onFailure = { e -> errorMessage = friendlySendCodeError(e, mode) }
            )
            isSending = false
        }
    }

    fun verifyCode() {
        keyboardController?.hide()
        errorMessage = null
        scope.launch {
            isVerifying = true
            if (mode == AccountLinkMode.SIGN_UP) {
                val result = cloudSyncRepository.verifySignUpOtp(emailInput, codeInput)
                result.fold(
                    onSuccess = {
                        userPreferencesRepository.markAccountLinked(emailInput)
                        isVerifying = false
                        onNeedsNickname()
                    },
                    onFailure = { e ->
                        errorMessage = "Invalid or expired code. Please try again."
                        isVerifying = false
                    }
                )
            } else {
                val result = cloudSyncRepository.verifyLoginOtp(emailInput, codeInput)
                result.fold(
                    onSuccess = { newUserId ->
                        val playerRecord = cloudSyncRepository.getPlayerRecord(newUserId)
                        isVerifying = false
                        when {
                            playerRecord == null -> {
                                // Verified identity, but no player record yet under it
                                // (e.g. they signed up once, verified the email, but the
                                // app closed before nickname setup finished). Log In must
                                // never show a nickname popup, so auto-generate a fallback
                                // nickname from the email and create the profile silently
                                // in the background instead of falling through to
                                // NicknameSetupDialog.
                                isVerifying = true
                                val fallback = fallbackNicknameFromEmail(emailInput)
                                var createResult = cloudSyncRepository.createPlayerRecord(
                                    anonymousId = newUserId,
                                    nickname = fallback
                                )
                                if (createResult.isFailure) {
                                    // Most likely a nickname collision — retry once with
                                    // a randomized suffix rather than surfacing an error
                                    // for something the user never typed.
                                    createResult = cloudSyncRepository.createPlayerRecord(
                                        anonymousId = newUserId,
                                        nickname = "$fallback${(1000..9999).random()}"
                                    )
                                }
                                createResult.fold(
                                    onSuccess = { record ->
                                        userPreferencesRepository.markAccountLinked(emailInput)
                                        userPreferencesRepository.markCloudSetupComplete(record.nickname)
                                        isVerifying = false
                                        onFullyRestored()
                                    },
                                    onFailure = { e ->
                                        errorMessage = "Something went wrong finishing setup. Please try again."
                                        isVerifying = false
                                    }
                                )
                            }
                            confirmBeforeRestore -> {
                                // Hold off on touching local DataStore — the account
                                // link itself (markAccountLinked) also waits, since
                                // cancelling should leave everything exactly as it
                                // was before this Log In attempt.
                                pendingRestore = playerRecord
                            }
                            else -> {
                                userPreferencesRepository.markAccountLinked(emailInput)
                                performRestore(
                                    playerRepository = playerRepository,
                                    statsRepository = statsRepository,
                                    userPreferencesRepository = userPreferencesRepository,
                                    playerRecord = playerRecord
                                )
                                onFullyRestored()
                            }
                        }
                    },
                    onFailure = { e ->
                        errorMessage = "Invalid or expired code. Please try again."
                        isVerifying = false
                    }
                )
            }
        }
    }

    fun confirmRestore() {
        val record = pendingRestore ?: return
        scope.launch {
            isRestoring = true
            userPreferencesRepository.markAccountLinked(emailInput)
            performRestore(
                playerRepository = playerRepository,
                statsRepository = statsRepository,
                userPreferencesRepository = userPreferencesRepository,
                playerRecord = record
            )
            isRestoring = false
            pendingRestore = null
            onFullyRestored()
        }
    }

    fun cancelRestore() {
        // The Supabase session was already swapped to the other account
        // by verifyLoginOtp() above — undo that so the app doesn't stay
        // silently signed in as an identity the user just declined to
        // switch to. Local progress was never touched, so there's
        // nothing to roll back there.
        scope.launch {
            isRestoring = true
            cloudSyncRepository.signOut()
            isRestoring = false
            pendingRestore = null
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Consume every touch on the scrim itself so taps never fall
            // through to whatever is rendered behind this dialog (e.g.
            // MediaCard's tap-for-video gesture on DeckScreen). Without
            // this, only actual Compose-clickable children (buttons, the
            // X icon, etc.) intercept touches — empty space, padding, and
            // even the title Text are "dead" and pass taps straight
            // through in a plain overlay Box.
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
                // Card content also needs its own tap-consumer — otherwise
                // a tap that lands on the card (but not on a specific
                // clickable child) would still be caught by the parent
                // Box's pointerInput above, which is fine, but we add this
                // too so future children added without explicit clickable
                // modifiers stay contained rather than relying solely on
                // the outer scrim's catch-all.
                .pointerInput(Unit) {
                    detectTapGestures { /* no-op: just eat the tap */ }
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pendingRestore != null) {
                // Confirmation step: a Log In just verified against an
                // account with existing cloud progress, and
                // confirmBeforeRestore is true — pause here rather than
                // silently overwriting whatever's currently on-device.
                Text(
                    text = "Replace current progress?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Logging in will replace your current progress with this account's saved data. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { confirmRestore() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Log in and replace progress")
                    }
                }
                Button(
                    onClick = { cancelRestore() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRestoring,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
                return@Column
            }

            // Top row: title + X dismiss
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Save your progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.8f)
                )
                IconButton(
                    onClick = onDismissed,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Skip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Link an email so you can recover your level, streak, and friends if you ever reinstall.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Sign Up / Log In toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AccountLinkMode.entries.forEach { entry ->
                    val isSelected = entry == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable(enabled = !isSending && !isVerifying) {
                                resetForModeSwitch(entry)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (entry == AccountLinkMode.SIGN_UP) "Sign Up" else "Log In",
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

            TextField(
                value = emailInput,
                onValueChange = {
                    emailInput = it
                    errorMessage = null
                },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !codeSent && !isSending && !isVerifying,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
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

            if (!codeSent) {
                Button(
                    onClick = { sendCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = emailInput.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (mode == AccountLinkMode.SIGN_UP) "Send code" else "Send login code")
                    }
                }
            } else {
                TextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        errorMessage = null
                    },
                    label = { Text("6-digit code") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isVerifying,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
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

                Text(
                    text = "Change email",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !isVerifying) {
                        codeSent = false
                        codeInput = ""
                        errorMessage = null
                    }
                )

                Button(
                    onClick = { verifyCode() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeInput.isNotBlank() && !isVerifying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Verify")
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}