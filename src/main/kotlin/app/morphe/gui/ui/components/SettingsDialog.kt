/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop
import java.io.File

@Composable
fun SettingsDialog(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    autoCleanupTempFiles: Boolean,
    onAutoCleanupChange: (Boolean) -> Unit,
    useExpertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    allowCacheClear: Boolean = true
) {
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Settings",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(min = 300.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Theme selection
                Text(
                    text = "Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemePreference.entries.forEach { theme ->
                        val isSelected = currentTheme == theme
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MorpheColors.Blue.copy(alpha = 0.15f)
                                    else Color.Transparent,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) MorpheColors.Blue.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onThemeChange(theme) }
                        ) {
                            Text(
                                text = theme.toDisplayName(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MorpheColors.Blue
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Expert mode setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Expert mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Full control over patch selection and configuration",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useExpertMode,
                        onCheckedChange = onExpertModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MorpheColors.Blue,
                            checkedTrackColor = MorpheColors.Blue.copy(alpha = 0.5f)
                        )
                    )
                }

                HorizontalDivider()

                // Auto-cleanup setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-cleanup temp files",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically delete temporary files after patching",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCleanupTempFiles,
                        onCheckedChange = onAutoCleanupChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MorpheColors.Teal,
                            checkedTrackColor = MorpheColors.Teal.copy(alpha = 0.5f)
                        )
                    )
                }

                HorizontalDivider()

                // Actions
                Text(
                    text = "Actions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Export logs button
                OutlinedButton(
                    onClick = {
                        try {
                            val logsDir = FileUtils.getLogsDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Logs Folder")
                }

                // Open app data folder
                OutlinedButton(
                    onClick = {
                        try {
                            val appDataDir = FileUtils.getAppDataDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(appDataDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open App Data Folder")
                }

                // Clear cache button
                OutlinedButton(
                    onClick = { showClearCacheConfirm = true },
                    enabled = allowCacheClear && !cacheCleared,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = when {
                            cacheCleared -> MorpheColors.Teal
                            cacheClearFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.error
                        },
                        disabledContentColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            !allowCacheClear -> "Clear Cache (disabled during patching)"
                            cacheCleared -> "Cache Cleared"
                            cacheClearFailed -> "Clear Cache Failed (files in use)"
                            else -> "Clear Cache"
                        }
                    )
                }

                // Cache info
                val cacheSize = calculateCacheSize()
                Text(
                    text = "Cache: $cacheSize (Patches + Logs)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // About
                Text(
                    text = "${AppConstants.APP_NAME} ${AppConstants.APP_VERSION}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Close",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )

    // Clear cache confirmation dialog
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Clear Cache?") },
            text = {
                Text("This will delete downloaded patch files and log files. Patches will be re-downloaded when needed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clearAllCache()
                        cacheCleared = success
                        cacheClearFailed = !success
                        showClearCacheConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun ThemePreference.toDisplayName(): String {
    return when (this) {
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
        ThemePreference.AMOLED -> "AMOLED"
        ThemePreference.SYSTEM -> "System"
    }
}

private fun calculateCacheSize(): String {
    val patchesSize = FileUtils.getPatchesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val logsSize = FileUtils.getLogsDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val totalSize = patchesSize + logsSize

    return when {
        totalSize < 1024 -> "$totalSize B"
        totalSize < 1024 * 1024 -> "%.1f KB".format(totalSize / 1024.0)
        else -> "%.1f MB".format(totalSize / (1024.0 * 1024.0))
    }
}

private fun clearAllCache(): Boolean {
    return try {
        var failedCount = 0

        // Delete patch files
        FileUtils.getPatchesDir().listFiles()?.forEach { file ->
            try {
                java.nio.file.Files.delete(file.toPath())
            } catch (e: Exception) {
                failedCount++
                Logger.error("Failed to delete ${file.name}: ${e.message}")
            }
        }

        // Delete log files
        FileUtils.getLogsDir().listFiles()?.forEach { file ->
            try {
                java.nio.file.Files.delete(file.toPath())
            } catch (e: Exception) {
                failedCount++
                Logger.error("Failed to delete log ${file.name}: ${e.message}")
            }
        }

        FileUtils.cleanupAllTempDirs()
        if (failedCount > 0) {
            Logger.error("Cache clear incomplete: $failedCount file(s) could not be deleted (may be locked)")
            false
        } else {
            Logger.info("Cache cleared successfully")
            true
        }
    } catch (e: Exception) {
        Logger.error("Failed to clear cache", e)
        false
    }
}
