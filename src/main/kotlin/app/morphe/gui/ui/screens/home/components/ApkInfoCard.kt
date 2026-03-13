package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.VersionStatus
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.ChecksumStatus

@Composable
fun ApkInfoCard(
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accentColor = statusAccentColor(apkInfo)
    val cardShape = RoundedCornerShape(corners.medium)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, borderColor, cardShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
                .align(Alignment.CenterStart)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp)
        ) {
            // ── Header: app identity + dismiss ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App initial — monospace, bold, in accent
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                        .background(accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = apkInfo.appName.first().uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accentColor
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.appName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = apkInfo.packageName,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.3.sp
                    )
                }

                // Dismiss button
                val closeHover = remember { MutableInteractionSource() }
                val isCloseHovered by closeHover.collectIsHoveredAsState()
                val closeBg by animateColorAsState(
                    if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )
                val closeBorder by animateColorAsState(
                    if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    animationSpec = tween(150)
                )

                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier
                        .size(30.dp)
                        .hoverable(closeHover)
                        .background(closeBg, RoundedCornerShape(corners.small))
                        .border(1.dp, closeBorder, RoundedCornerShape(corners.small))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove APK",
                        tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // ── Technical data grid ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = borderColor,
                            start = Offset(20.dp.toPx(), 0f),
                            end = Offset(size.width - 20.dp.toPx(), 0f),
                            strokeWidth = 1f
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TechDataCell(
                    label = "VERSION",
                    value = apkInfo.versionName,
                    mono = mono,
                    modifier = Modifier.weight(1f)
                )
                TechDataCell(
                    label = "SIZE",
                    value = apkInfo.formattedSize,
                    mono = mono,
                    modifier = Modifier.weight(1f)
                )
                if (apkInfo.minSdk != null) {
                    TechDataCell(
                        label = "MIN SDK",
                        value = "API ${apkInfo.minSdk}",
                        mono = mono,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Architectures — shown as individual tags, never truncated ──
            if (apkInfo.architectures.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(20.dp.toPx(), 0f),
                                end = Offset(size.width - 20.dp.toPx(), 0f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ARCH",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    apkInfo.architectures.forEach { arch ->
                        Box(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                    RoundedCornerShape(corners.small)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = arch,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ── Status bar ──
            val statusInfo = resolveStatus(apkInfo)
            if (statusInfo != null) {
                StatusBar(
                    statusInfo = statusInfo,
                    mono = mono,
                    borderColor = borderColor
                )
            }
        }
    }
}

@Composable
private fun TechDataCell(
    label: String,
    value: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Status ──

private data class StatusInfo(
    val color: Color,
    val label: String,
    val detail: String? = null
)

@Composable
private fun resolveStatus(apkInfo: ApkInfo): StatusInfo? {
    if (apkInfo.suggestedVersion != null && apkInfo.versionStatus != VersionStatus.EXACT_MATCH) {
        return when (apkInfo.versionStatus) {
            VersionStatus.OLDER_VERSION -> StatusInfo(
                color = Color(0xFFFF9800),
                label = "OUTDATED",
                detail = "Patches target v${apkInfo.suggestedVersion}"
            )
            VersionStatus.NEWER_VERSION -> StatusInfo(
                color = MaterialTheme.colorScheme.error,
                label = "VERSION MISMATCH",
                detail = "Expected v${apkInfo.suggestedVersion} — patching may fail"
            )
            else -> StatusInfo(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "UNVERIFIED",
                detail = "Suggested: v${apkInfo.suggestedVersion}"
            )
        }
    }

    if (apkInfo.versionStatus == VersionStatus.EXACT_MATCH) {
        return when (apkInfo.checksumStatus) {
            is ChecksumStatus.Verified -> StatusInfo(
                color = MorpheColors.Teal,
                label = "VERIFIED",
                detail = "Checksum matches APKMirror"
            )
            is ChecksumStatus.Mismatch -> StatusInfo(
                color = MaterialTheme.colorScheme.error,
                label = "CHECKSUM MISMATCH",
                detail = "File may be corrupted — re-download from APKMirror"
            )
            is ChecksumStatus.Error -> StatusInfo(
                color = Color(0xFFFF9800),
                label = "RECOMMENDED VERSION",
                detail = "Checksum verification failed"
            )
            is ChecksumStatus.NotConfigured -> StatusInfo(
                color = MorpheColors.Teal,
                label = "RECOMMENDED VERSION"
            )
            is ChecksumStatus.NonRecommendedVersion -> null
        }
    }

    return null
}

@Composable
private fun StatusBar(
    statusInfo: StatusInfo,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(20.dp.toPx(), 0f),
                    end = Offset(size.width - 20.dp.toPx(), 0f),
                    strokeWidth = 1f
                )
            }
            .background(statusInfo.color.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusInfo.color, RoundedCornerShape(1.dp))
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = statusInfo.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = statusInfo.color,
            letterSpacing = 1.sp
        )

        if (statusInfo.detail != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = statusInfo.detail,
                fontSize = 11.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun statusAccentColor(apkInfo: ApkInfo): Color {
    if (apkInfo.suggestedVersion != null && apkInfo.versionStatus != VersionStatus.EXACT_MATCH) {
        return when (apkInfo.versionStatus) {
            VersionStatus.NEWER_VERSION -> MaterialTheme.colorScheme.error
            VersionStatus.OLDER_VERSION -> Color(0xFFFF9800)
            else -> MorpheColors.Blue
        }
    }
    if (apkInfo.checksumStatus is ChecksumStatus.Mismatch) {
        return MaterialTheme.colorScheme.error
    }
    if (apkInfo.versionStatus == VersionStatus.EXACT_MATCH) {
        return MorpheColors.Teal
    }
    return MorpheColors.Blue
}
