/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_dark
import app.morphe.morphe_desktop.generated.resources.morphe_light
import androidx.compose.ui.input.pointer.pointerHoverIcon
import app.morphe.gui.ui.theme.*
import org.jetbrains.compose.resources.painterResource

// ============================================================================
// QUICK BRANDING (logo + patches version badge)
// ============================================================================

@Composable
internal fun BrandingLogo() {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }

    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(28.dp)
    )
}

@Composable
internal fun PatchesVersionBadge(
    patchesVersion: String?,
    isLoading: Boolean,
    patchSourceName: String? = null,
    latestLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val interactive = onClick != null

    if (isLoading) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = accents.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LOADING…",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    } else if (patchesVersion != null) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (interactive) Modifier
                        .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
                        .clickable(onClick = onClick)
                    else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = patchSourceName?.uppercase() ?: "PATCHES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = " · ",
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )
            Text(
                text = patchesVersion,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = accents.primary
            )
            if (latestLabel != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(accents.secondary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                        .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = latestLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.secondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

