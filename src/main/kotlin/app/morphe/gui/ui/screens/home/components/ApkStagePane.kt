/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.screens.home.ApkInfo

import app.morphe.gui.ui.icons.MorpheIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.toColor

// ============================================================================
// APK STAGE (drop zone, APK info, analyzing)
// ============================================================================

@Composable
internal fun MiddleContent(
    uiState: HomeUiState,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    when {
        uiState.isAnalyzing -> {
            AnalyzingSection()
        }
        uiState.apkInfo != null -> {
            ApkSelectedSection(
                patchesLoaded = patchesLoaded,
                apkInfo = uiState.apkInfo,
                onClearClick = onClearClick,
                onChangeClick = onChangeClick,
                onContinueClick = onContinueClick,
                patchSourceNames = patchSourceNames,
            )
        }
        else -> {
            DropPromptSection(
                isDragHovering = uiState.isDragHovering,
                onBrowseClick = onChangeClick
            )
        }
    }
}

// ============================================================================
// DROP ZONE (corner brackets, scanner/targeting aesthetic)
// ============================================================================

@Composable
internal fun DropPromptSection(
    isDragHovering: Boolean,
    onBrowseClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val bracketLen = 32f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        // Drop zone with corner brackets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .drawBehind {
                    val strokeWidth = 2f
                    val len = bracketLen.dp.toPx()
                    val inset = 0f

                    // Top-left corner
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                    // Top-right corner
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                    // Bottom-left corner
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                    // Bottom-right corner
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isDragHovering) "RELEASE TO DROP" else "DROP APK HERE",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = if (isDragHovering) accents.primary
                            else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "or",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.3f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.primary)
                ) {
                    Text(
                        text = "BROWSE FILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.25f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ============================================================================
// APK SELECTED (info card and action buttons)
// ============================================================================

@Composable
internal fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val accents = LocalMorpheAccents.current
    val warningColor = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus).toColor()
    val primaryColor = if (showWarning) warningColor else accents.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
            patchSourceNames = patchSourceNames,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onChangeClick,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    "CHANGE APK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    letterSpacing = 1.sp
                )
            }
            Button(
                onClick = onContinueClick,
                enabled = patchesLoaded,
                modifier = Modifier.widthIn(min = 160.dp).height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(corners.small)
            ) {
                ActionButtonContent(patchesLoaded, showWarning, mono)
            }
        }
    }
}

@Composable
internal fun ActionButtonContent(
    patchesLoaded: Boolean,
    showWarning: Boolean,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    if (!patchesLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "LOADING…",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    } else {
        if (showWarning) {
            Icon(
                imageVector = MorpheIcons.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            "CONTINUE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  ANALYZING STATE
// ════════════════════════════════════════════════════════════════════

@Composable
internal fun AnalyzingSection() {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = accents.primary,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ANALYZING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reading app metadata…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ============================================================================
// DRAG OVERLAY
// ============================================================================

@Composable
internal fun DragOverlay() {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = accents.primary.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .drawBehind {
                val strokeWidth = 3f
                val len = 48.dp.toPx()
                val inset = 24.dp.toPx()

                // Top-left
                drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                // Top-right
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                // Bottom-left
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                // Bottom-right
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DROP APK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = accents.primary,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                fontSize = 11.sp,
                fontFamily = mono,
                color = accents.primary.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    }
}
