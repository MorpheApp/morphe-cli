/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import app.morphe.gui.ui.screens.quick.QuickPatchPhase

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.*

// ============================================================================
// PATCHING CONTENT (progress)
// ============================================================================

@Composable
internal fun PatchingContent(
    phase: QuickPatchPhase,
    statusMessage: String,
    onCancel: () -> Unit
) {
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = accents.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when (phase) {
                QuickPatchPhase.DOWNLOADING -> "PREPARING"
                QuickPatchPhase.PATCHING -> "PATCHING"
                else -> ""
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = accents.secondary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        val cancelHover = remember { MutableInteractionSource() }
        val isCancelHovered by cancelHover.collectIsHoveredAsState()
        val cancelBg by animateColorAsState(
            if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent,
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .hoverable(cancelHover)
                .clip(RoundedCornerShape(corners.small))
                .background(cancelBg)
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "CANCEL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.error,
                letterSpacing = 0.5.sp
            )
        }
    }
}

