package com.example.swipeclean.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Divider
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

            Divider()

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

            Divider()

            // Future menu items placeholder
            Text(
                text = "More coming soon...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(enabled = false) {}
            )
        }
    }
}