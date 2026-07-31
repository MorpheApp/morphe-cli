/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.HomeUiState

import app.morphe.gui.ui.icons.MorpheIcons
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_dark
import app.morphe.morphe_desktop.generated.resources.morphe_light
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import org.jetbrains.compose.resources.painterResource
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourcesCountPill
import app.morphe.gui.ui.components.sourceLedState
import app.morphe.gui.ui.components.TopBarRow

// ============================================================================
// HEADER BAR AND STATUS INDICATORS
// ============================================================================

@Composable
internal fun HeaderBar(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onUpdateChannelChanged: () -> Unit = {},
    onManageSourcesClick: () -> Unit = {},
    sourceStates: List<SourceLedState> = emptyList(),
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(vertical = 8.dp)
    ) {
        // Logo, left-aligned, compact
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .onSizeChanged { leadingWidthPx = it.width }
        ) {
            BrandingSection(isCompact = true)
        }

        // Patches version inline, centered
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = centerSidePadding, end = centerSidePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isLoadingPatches) {
                    PatchesLoadingIndicator()
                } else {
                    SourcesCountPill(
                        sourceStates = sourceStates,
                        onClick = onManageSourcesClick,
                    )
                }

                if (uiState.isOffline && !uiState.isLoadingPatches) {
                    Spacer(modifier = Modifier.width(12.dp))
                    OfflineBadge(onRetry = onRetry)
                }
            }
        }


        // Device indicator plus settings, inline in the header
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .onSizeChanged { trailingWidthPx = it.width }
        ) {
            TopBarRow(
                allowCacheClear = true,
                onUpdateChannelChanged = onUpdateChannelChanged,
            )
        }
    }
}

/** One-time intro banner shown when the user first sees multi-source mode.
 *  Persists dismissal in ConfigRepository so it never reappears once dismissed. */
@Composable
internal fun MultiSourceHintBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, accents.primary.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
            .background(accents.primary.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "MULTIPLE SOURCES ACTIVE: Patches from every enabled source are unioned. Manage from the SOURCES button above.",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = MorpheIcons.Clear,
                contentDescription = "Dismiss",
                tint = accents.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// SourcesCountPill, SourceLed, SourceLedState, sourceLedState moved to
// gui/ui/components/SourcesPill.kt for reuse across modes (Quick Patch uses
// a non-clickable variant).

@Composable
internal fun PatchesLoadingIndicator() {
    val mono = LocalMorpheFont.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Loading patches…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = homeMutedTextColor(0.5f)
        )
    }
}

@Composable
internal fun OfflineBadge(onRetry: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .clickable(onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "OFFLINE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
    }
}

@Composable
internal fun BrandingSection(isCompact: Boolean = false) {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }
    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(if (isCompact) 36.dp else 60.dp)
    )
}

