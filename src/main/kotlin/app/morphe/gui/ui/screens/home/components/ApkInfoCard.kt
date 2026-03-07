package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.VersionStatus
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.ChecksumStatus

@Composable
fun ApkInfoCard(
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with app icon and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = apkInfo.appName.first().toString(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MorpheColors.Blue
                        )
                    }

                    Column {
                        // App name
                        Text(
                            text = apkInfo.appName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Version
                        Text(
                            text = "v${apkInfo.versionName}",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Close button
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Size
                InfoColumn(
                    label = "Size",
                    value = apkInfo.formattedSize,
                    modifier = Modifier.weight(1f)
                )

                // Architecture
                InfoColumn(
                    label = "Architecture",
                    value = if (apkInfo.architectures.isEmpty()) "Unknown" else apkInfo.architectures.joinToString(", "),
                    modifier = Modifier.weight(1f)
                )

                // Min SDK
                if (apkInfo.minSdk != null) {
                    InfoColumn(
                        label = "Min SDK",
                        value = "API ${apkInfo.minSdk}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Version and checksum status section
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Version status
            if (apkInfo.suggestedVersion != null && apkInfo.versionStatus != VersionStatus.EXACT_MATCH) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    VersionStatusBanner(
                        versionStatus = apkInfo.versionStatus,
                        currentVersion = apkInfo.versionName,
                        suggestedVersion = apkInfo.suggestedVersion
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Checksum warning for non-recommended versions
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Checksum verification unavailable for non-recommended versions",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (apkInfo.versionStatus == VersionStatus.EXACT_MATCH) {
                // Show checksum status for recommended version
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ChecksumStatusBanner(checksumStatus = apkInfo.checksumStatus)
                }
            }
        }
    }
}

@Composable
private fun ChecksumStatusBanner(checksumStatus: ChecksumStatus) {
    when (checksumStatus) {
        is ChecksumStatus.Verified -> {
            Surface(
                color = MorpheColors.Teal.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recommended version - Verified",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MorpheColors.Teal
                    )
                    Text(
                        text = "Checksum matches APKMirror",
                        fontSize = 10.sp,
                        color = MorpheColors.Teal.copy(alpha = 0.8f)
                    )
                }
            }
        }

        is ChecksumStatus.Mismatch -> {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Checksum Mismatch",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "File may be corrupted or modified. Re-download from APKMirror.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is ChecksumStatus.NotConfigured -> {
            Surface(
                color = MorpheColors.Teal.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Using recommended version",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MorpheColors.Teal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        is ChecksumStatus.Error -> {
            Surface(
                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Using recommended version",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        text = "Could not verify checksum",
                        fontSize = 10.sp,
                        color = Color(0xFFFF9800).copy(alpha = 0.8f)
                    )
                }
            }
        }

        is ChecksumStatus.NonRecommendedVersion -> {
            // This shouldn't happen in this branch, but handle it gracefully
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Using non-recommended version",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VersionStatusBanner(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String
) {
    val (backgroundColor, textColor, message) = when (versionStatus) {
        VersionStatus.OLDER_VERSION -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.15f),
            Color(0xFFFF9800),
            "Newer patches available for v$suggestedVersion"
        )
        VersionStatus.NEWER_VERSION -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.error,
            "Version too new. Recommended: v$suggestedVersion"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Suggested version: v$suggestedVersion"
        )
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center
            )
            if (versionStatus == VersionStatus.NEWER_VERSION) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Patching may not work correctly with newer versions",
                    fontSize = 11.sp,
                    color = textColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

//private fun formatArchitectures(archs: List<String>): String {
//    if (archs.isEmpty()) return "Unknown"
//
//    // Show full architecture names for clarity
//    val formatted = archs.map { arch ->
//        when (arch) {
//            "arm64-v8a" -> "arm64-v8a"
//            "armeabi-v7a" -> "armeabi-v7a"
//            "x86_64" -> "x86_64"
//            "x86" -> "x86"
//            else -> arch
//        }
//    }
//
//    return formatted.joinToString(", ")
//}
