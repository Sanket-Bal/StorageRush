package com.example.swipeclean.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One concept per card, in order. Card 0 is the logo/welcome card.
 * TODO: once the real app icon/logo is generated, replace the "SC" text
 * placeholder in the logo card below with the actual logo asset
 * (e.g. painterResource(R.drawable.app_logo)).
 */
private data class TutorialCard(
    val title: String,
    val description: String
)

private val tutorialCards = listOf(
    TutorialCard(
        title = "Welcome to SwipeClean",
        description = "The fast way to declutter your photos and videos — swipe to clean, one item at a time."
    ),
    TutorialCard(
        title = "Swipe Left to Trash",
        description = "Don't want it? Swipe the card left. It moves to your Trash Bin, not deleted right away — you can still review or restore it before it's gone for good."
    ),
    TutorialCard(
        title = "Swipe Right to Keep",
        description = "Want to keep it? Swipe the card right. It stays exactly where it is on your device — no changes made."
    ),
    TutorialCard(
        title = "Or Use the Buttons",
        description = "Not a fan of swiping? The Trash and Keep buttons below the card do exactly the same thing."
    ),
    TutorialCard(
        title = "Made a Mistake?",
        description = "Tap the Undo button to bring back your last swiped item — works for both Trash and Keep."
    ),
    TutorialCard(
        title = "Explore More Sections",
        description = "Tap the ☰ menu to browse Images by folder (Camera, Screenshots, WhatsApp, and more) or Videos by size and length — plus your Trash Bin and Cleanup Stats."
    )
)

/**
 * Swipeable-card tutorial (onboarding).
 *
 * @param isMandatory If true, no close (X) is shown and the final card ends
 *   in a confirm button that calls [onComplete] — used for the forced
 *   first-launch flow. If false, an X is shown on every card (calls
 *   [onDismiss]) and the final card's button just closes the screen —
 *   used when a user reopens this later via the "?" button, since they
 *   already know the app and just want a refresher.
 * @param onComplete Called when the mandatory flow's final confirm button
 *   is tapped. Callers should mark the tutorial as seen here.
 * @param onDismiss Called when the X is tapped (dismissible mode only), or
 *   when the final button is tapped in dismissible mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialGuideScreen(
    isMandatory: Boolean,
    onComplete: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { tutorialCards.size })
    val isLastPage by remember {
        derivedStateOf { pagerState.currentPage == tutorialCards.size - 1 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Close button (dismissible mode only) — mandatory mode has no
            // way out except completing the flow.
            if (!isMandatory) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                TutorialCardContent(card = tutorialCards[page], isLogoCard = page == 0)
            }

            PageIndicator(
                pagerState = pagerState,
                pageCount = tutorialCards.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )

            if (isLastPage) {
                Button(
                    onClick = { if (isMandatory) onComplete() else onDismiss() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(bottom = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isMandatory) "Got it, let's go!" else "Done",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                // Reserve the same vertical space as the button on other
                // pages, so the pager/dots don't visually jump as the user
                // swipes toward the last card.
                Spacer(modifier = Modifier.height(24.dp + 48.dp + 24.dp))
            }
        }
    }
}

@Composable
private fun TutorialCardContent(
    card: TutorialCard,
    isLogoCard: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLogoCard) {
            // TODO: swap for the real app logo once generated — this is a
            // plain-text placeholder (not an icon or emoji) to avoid both
            // the missing material-icons-extended dependency and any
            // cross-device emoji rendering inconsistency in the meantime.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SC",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = card.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = card.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val isSelected = pagerState.currentPage == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (isSelected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}