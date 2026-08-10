package com.storagerush.app.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagerush.app.R

/**
 * One "section" (heading + description) within a tutorial card. Simple
 * cards have one section; merged cards (e.g. Swipe Left + Swipe Right)
 * have two, stacked vertically within the same page.
 */
private data class TutorialCardSection(
    val title: String,
    val description: String
)

private data class TutorialCard(
    val sections: List<TutorialCardSection>
)

/**
 * 4 cards (reduced from 6) — related concepts merged into the same page,
 * stacked vertically with a gap between them, rather than separate pages.
 * Card 0 is the logo/welcome card.
 */
private val tutorialCards = listOf(
    TutorialCard(
        sections = listOf(
            TutorialCardSection(
                title = "Welcome to Storage Rush",
                description = "The fast way to declutter your photos and videos — swipe to clean, one item at a time."
            )
        )
    ),
    TutorialCard(
        sections = listOf(
            TutorialCardSection(
                title = "Swipe Left to Trash",
                description = "Don't want it? Swipe the card left. It moves to your Trash Bin, not deleted right away — you can still review or restore it before it's gone for good."
            ),
            TutorialCardSection(
                title = "Swipe Right to Keep",
                description = "Want to keep it? Swipe the card right. It stays exactly where it is on your device — no changes made."
            )
        )
    ),
    TutorialCard(
        sections = listOf(
            TutorialCardSection(
                title = "Or Use the Buttons",
                description = "Not a fan of swiping? The Trash and Keep buttons below the card do exactly the same thing."
            ),
            TutorialCardSection(
                title = "Made a Mistake?",
                description = "Tap the Undo button to bring back your last swiped item — works for both Trash and Keep."
            )
        )
    ),
    TutorialCard(
        sections = listOf(
            TutorialCardSection(
                title = "Explore More Sections",
                description = "Tap the ☰ menu to browse Images by folder (Camera, Screenshots, WhatsApp, and more) or Videos by size and length — plus your Trash Bin and Cleanup Stats."
            )
        )
    )
)

/**
 * Swipeable-card tutorial (onboarding).
 *
 * @param isMandatory If true, a "Skip" text button (top-right) is shown
 *   instead of a close X, and both it and the final card's confirm button
 *   call [onComplete] — used for the forced first-launch flow. Skip and
 *   completing the flow are treated identically: both mark the tutorial as
 *   seen, so it won't show again automatically either way.
 *   If false, an X is shown on every card (calls [onDismiss]) and the final
 *   card's button just closes the screen — used when a user reopens this
 *   later via the "?" button, since they already know the app.
 * @param onComplete Called when the mandatory flow's Skip button OR its
 *   final confirm button is tapped. Callers should mark the tutorial as
 *   seen here.
 * @param onDismiss Called when the X is tapped, or the final button in
 *   dismissible mode.
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
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!isMandatory) {
                // Dismissible mode (reopened via "?"): close X.
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
                // Mandatory mode (first launch): Skip button, same end
                // state as completing — both call onComplete.
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Skip",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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
                        .padding(bottom = 24.dp)
                        .navigationBarsPadding(),
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
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Storage Rush logo",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        card.sections.forEachIndexed { index, section ->
            if (index > 0) {
                // Small gap between merged sections within the same card —
                // increases the card's vertical span slightly, not a full
                // screen expansion.
                Spacer(modifier = Modifier.height(28.dp))
            }

            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = section.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
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