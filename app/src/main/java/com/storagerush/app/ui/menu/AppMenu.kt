package com.storagerush.app.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box

/**
 * Hamburger menu for app navigation
 * Options: Trash Bin, Stats, etc.
 */
@Composable
fun AppMenu(
    onTrashBinClick: () -> Unit,
    onStatsClick: () -> Unit = {},
    onImagesClick: () -> Unit = {},
    onVideosClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    isAccountLinked: Boolean = false,
    linkedEmail: String? = null,
    onAccountLinkClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Hamburger menu button (three dashes)
        IconButton(
            onClick = { expanded.value = true }
        ) {
            Text(
                text = "☰",
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            // Images option (Phase 4.5)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "🖼️ Images",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded.value = false
                    onImagesClick()
                }
            )

            // Videos option (Phase 4.5)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "🎬 Videos",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded.value = false
                    onVideosClick()
                }
            )

            HorizontalDivider()

            // Trash Bin option
            DropdownMenuItem(
                text = {
                    Text(
                        text = "🗑️ Trash Bin",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded.value = false
                    onTrashBinClick()
                }
            )

            HorizontalDivider()

            // Stats option
            DropdownMenuItem(
                text = {
                    Text(
                        text = "📊 Cleanup Stats",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded.value = false
                    onStatsClick()
                }
            )

            HorizontalDivider()

            // Profile option — opens the Stats screen on its Profile tab
            // (formerly "Progress"). Placed just above the account-link
            // section since editing your nickname there is closely tied
            // to having a cloud profile.
            DropdownMenuItem(
                text = {
                    Text(
                        text = "👤 Profile",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded.value = false
                    onProfileClick()
                }
            )

            HorizontalDivider()

            // Account link status — flips live between Sign Up/Log In and
            // Log Out based on isAccountLinked (see UserPreferencesRepository
            // .isAccountLinkedFlow). Available even after a user declined
            // linking during first-launch onboarding.
            if (isAccountLinked) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (linkedEmail != null) {
                                "🔓 Log Out ($linkedEmail)"
                            } else {
                                "🔓 Log Out"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        expanded.value = false
                        onLogoutClick()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "🔐 Sign Up / Log In",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        expanded.value = false
                        onAccountLinkClick()
                    }
                )
            }
        }
    }
}