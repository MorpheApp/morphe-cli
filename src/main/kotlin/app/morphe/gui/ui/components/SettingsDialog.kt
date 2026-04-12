/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.patcher.apk.ApkSigner
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.UUID

@Composable
fun SettingsDialog(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    autoCleanupTempFiles: Boolean,
    onAutoCleanupChange: (Boolean) -> Unit,
    useExpertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    allowCacheClear: Boolean = true,
    isPatching: Boolean = false,
    patchSources: List<PatchSource> = emptyList(),
    activePatchSourceId: String = "",
    onActivePatchSourceChange: (String) -> Unit = {},
    onAddPatchSource: (PatchSource) -> Unit = {},
    onEditPatchSource: (PatchSource) -> Unit = {},
    onRemovePatchSource: (String) -> Unit = {},
    onCacheCleared: () -> Unit = {},
    keystorePath: String? = null,
    keystorePassword: String? = null,
    keystoreAlias: String = "Morphe",
    keystoreEntryPassword: String = "Morphe",
    onKeystorePathChange: (String?) -> Unit = {},
    onKeystoreCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit = { _, _, _ -> }
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PatchSource?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "SETTINGS",
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(min = 340.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Theme ──
                SectionLabel("THEME", mono)
                Spacer(Modifier.height(8.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemePreference.entries.filter { it != ThemePreference.MATCHA }.forEach { theme ->
                        val isSelected = currentTheme == theme
                        val themeAccent = theme.accentColor()
                        val hoverInteraction = remember { MutableInteractionSource() }
                        val isHovered by hoverInteraction.collectIsHoveredAsState()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(corners.small))
                                .border(
                                    1.dp,
                                    when {
                                        isSelected -> themeAccent.copy(alpha = 0.5f)
                                        isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        else -> borderColor
                                    },
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    if (isSelected) themeAccent.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .hoverable(hoverInteraction)
                                .clickable { onThemeChange(theme) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Themed icon
                            Text(
                                text = theme.iconSymbol(),
                                fontSize = 11.sp,
                                color = themeAccent
                            )
                            Text(
                                text = theme.toDisplayName().uppercase(),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = if (isSelected) themeAccent
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsDivider(borderColor)

                // ── Expert Mode ──
                SettingToggleRow(
                    label = "Expert mode",
                    description = "Full control over patch selection and configuration",
                    checked = useExpertMode,
                    onCheckedChange = onExpertModeChange,
                    accentColor = accents.primary,
                    mono = mono,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Auto Cleanup ──
                SettingToggleRow(
                    label = "Auto-cleanup temp files",
                    description = "Delete temporary files after patching",
                    checked = autoCleanupTempFiles,
                    onCheckedChange = onAutoCleanupChange,
                    accentColor = accents.primary,
                    mono = mono,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Signing / Keystore ──
                SigningSection(
                    keystorePath = keystorePath,
                    keystorePassword = keystorePassword,
                    keystoreAlias = keystoreAlias,
                    keystoreEntryPassword = keystoreEntryPassword,
                    onKeystorePathChange = onKeystorePathChange,
                    onCredentialsChange = onKeystoreCredentialsChange,
                    mono = mono,
                    accentColor = accents.primary,
                    borderColor = borderColor,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Patch Sources ──
                PatchSourcesSection(
                    sources = patchSources,
                    activeSourceId = activePatchSourceId,
                    onActiveChange = { id ->
                        onActivePatchSourceChange(id)
                        onDismiss()
                    },
                    onRemove = onRemovePatchSource,
                    onEdit = { source -> editingSource = source },
                    onAddClick = { showAddSourceDialog = true },
                    mono = mono,
                    accentColor = accents.primary,
                    borderColor = borderColor,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Actions ──
                SectionLabel("ACTIONS", mono)
                Spacer(Modifier.height(8.dp))

                ActionButton(
                    label = "OPEN LOGS",
                    icon = Icons.Default.BugReport,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val logsDir = FileUtils.getLogsDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                ActionButton(
                    label = "OPEN APP DATA",
                    icon = Icons.Default.FolderOpen,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val appDataDir = FileUtils.getAppDataDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(appDataDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                // Clear cache
                val cacheColor = when {
                    cacheCleared -> MorpheColors.Teal
                    cacheClearFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.error
                }
                ActionButton(
                    label = when {
                        !allowCacheClear -> "CLEAR CACHE (DISABLED)"
                        cacheCleared -> "CACHE CLEARED"
                        cacheClearFailed -> "CLEAR FAILED"
                        else -> "CLEAR CACHE"
                    },
                    icon = Icons.Default.Delete,
                    mono = mono,
                    borderColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.3f)
                                  else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = cacheColor,
                    enabled = allowCacheClear && !cacheCleared,
                    onClick = { showClearCacheConfirm = true }
                )

                Spacer(Modifier.height(4.dp))

                val cacheSize = calculateCacheSize()
                Text(
                    text = "Cache: $cacheSize (patches + logs)",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )

                SettingsDivider(borderColor)

                // ── About ──
                Text(
                    text = "${AppConstants.APP_NAME} ${AppConstants.APP_VERSION}",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    "CLOSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    // Clear cache confirmation
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            shape = RoundedCornerShape(corners.medium),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "CLEAR CACHE?",
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    "This will delete downloaded patches and log files. Patches will be re-downloaded when needed.",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clearAllCache()
                        cacheCleared = success
                        cacheClearFailed = !success
                        showClearCacheConfirm = false
                        if (success) onCacheCleared()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        "CLEAR",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(
                        "CANCEL",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        )
    }

    if (showAddSourceDialog) {
        AddPatchSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onAdd = { source ->
                onAddPatchSource(source)
                showAddSourceDialog = false
            }
        )
    }

    editingSource?.let { source ->
        EditPatchSourceDialog(
            source = source,
            onDismiss = { editingSource = null },
            onSave = { updated ->
                onEditPatchSource(updated)
                editingSource = null
            }
        )
    }
}

// ── Shared building blocks ──

@Composable
private fun SectionLabel(
    text: String,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun CollapsibleSection(
    title: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    val corners = LocalMorpheCorners.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) -90f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
    )
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                else Color.Transparent
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.6f else 0.4f),
            letterSpacing = 1.5.sp
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.5f else 0.3f)
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = expanded,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = androidx.compose.animation.core.tween(200)
        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = androidx.compose.animation.core.tween(200)
        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDivider(borderColor: Color) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = borderColor)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (!enabled) "Disabled while patching" else description,
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * alpha)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accentColor,
                checkedTrackColor = accentColor.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().hoverable(hoverInteraction),
        shape = RoundedCornerShape(corners.small),
        border = BorderStroke(
            1.dp,
            if (isHovered && enabled) contentColor.copy(alpha = 0.3f)
            else borderColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── Patch Sources Section ──

@Composable
private fun PatchSourcesSection(
    sources: List<PatchSource>,
    activeSourceId: String,
    onActiveChange: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (PatchSource) -> Unit,
    onAddClick: () -> Unit,
    mono: androidx.compose.ui.text.font.FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true
) {
    val corners = LocalMorpheCorners.current
    val alpha = if (enabled) 1f else 0.4f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection("PATCH SOURCES", mono) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) "Disabled while patching" else "Select where patches are loaded from",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        sources.forEach { source ->
            val isActive = source.id == activeSourceId
            val hoverInteraction = remember(source.id) { MutableInteractionSource() }
            val isHovered by hoverInteraction.collectIsHoveredAsState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.medium))
                    .border(
                        1.dp,
                        when {
                            isActive -> accentColor.copy(alpha = 0.4f)
                            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else -> borderColor
                        },
                        RoundedCornerShape(corners.medium)
                    )
                    .background(
                        if (isActive) accentColor.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .hoverable(hoverInteraction)
                    .then(if (enabled) Modifier.clickable { onActiveChange(source.id) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Active indicator dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isActive) accentColor
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (source.type) {
                                PatchSourceType.DEFAULT -> "Default"
                                PatchSourceType.GITHUB -> source.url?.removePrefix("https://github.com/") ?: "GitHub"
                                PatchSourceType.LOCAL -> source.filePath?.let { File(it).name } ?: "Local file"
                            },
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (source.deletable && enabled) {
                        IconButton(
                            onClick = { onEdit(source) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = { onRemove(source.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Add source
        OutlinedButton(
            onClick = onAddClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(corners.small),
            border = BorderStroke(1.dp, borderColor),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "ADD SOURCE",
                fontFamily = mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
        } // inner Column
        } // CollapsibleSection
    }
}

// ── Add / Edit Source Dialogs ──

@Composable
private fun AddPatchSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(PatchSourceType.GITHUB) }
    var url by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "ADD SOURCE",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                // Type toggle
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PatchSourceType.GITHUB, PatchSourceType.LOCAL).forEach { type ->
                        val isSelected = sourceType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(corners.small))
                                .border(
                                    1.dp,
                                    if (isSelected) accents.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    if (isSelected) accents.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable { sourceType = type }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = when (type) {
                                    PatchSourceType.GITHUB -> "GITHUB"
                                    PatchSourceType.LOCAL -> "LOCAL FILE"
                                    else -> ""
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = if (isSelected) accents.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name", fontFamily = mono, fontSize = 11.sp) },
                    placeholder = { Text("My Custom Patches", fontFamily = mono, fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(corners.small)
                )

                when (sourceType) {
                    PatchSourceType.GITHUB -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; error = null },
                            label = { Text("Repository URL", fontFamily = mono, fontSize = 11.sp) },
                            placeholder = { Text("github.com/owner/repo", fontFamily = mono, fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(corners.small)
                        )
                        Text(
                            "Accepts GitHub URL or morphe.software/add-source link",
                            fontFamily = mono,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 0.3.sp
                        )
                    }
                    PatchSourceType.LOCAL -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = filePath,
                                onValueChange = { filePath = it; error = null },
                                label = { Text(".mpp file", fontFamily = mono, fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(corners.small),
                                readOnly = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val dialog = FileDialog(null as Frame?, "Select .mpp file", FileDialog.LOAD).apply {
                                        setFilenameFilter { _, n -> n.endsWith(".mpp", ignoreCase = true) }
                                        isVisible = true
                                    }
                                    if (dialog.directory != null && dialog.file != null) {
                                        filePath = File(dialog.directory, dialog.file).absolutePath
                                        if (name.isBlank()) name = dialog.file.removeSuffix(".mpp")
                                        error = null
                                    }
                                },
                                shape = RoundedCornerShape(corners.small)
                            ) {
                                Text(
                                    "BROWSE",
                                    fontFamily = mono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    else -> {}
                }

                error?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name is required"; return@Button }
                    when (sourceType) {
                        PatchSourceType.GITHUB -> {
                            val trimmedUrl = url.trim()
                            val resolvedUrl = resolveGitHubUrl(trimmedUrl)
                            if (resolvedUrl == null) {
                                error = "Enter a valid GitHub URL or Morphe source link"; return@Button
                            }
                            onAdd(PatchSource(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                type = sourceType,
                                url = resolvedUrl,
                                deletable = true
                            ))
                            return@Button
                        }
                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = "Select a valid .mpp file"; return@Button
                            }
                        }
                        else -> {}
                    }
                    onAdd(PatchSource(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        type = sourceType,
                        url = null,
                        filePath = if (sourceType == PatchSourceType.LOCAL) filePath.trim() else null,
                        deletable = true
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "ADD",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "CANCEL",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

@Composable
private fun EditPatchSourceDialog(
    source: PatchSource,
    onDismiss: () -> Unit,
    onSave: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url ?: "") }
    var filePath by remember { mutableStateOf(source.filePath ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "EDIT SOURCE",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                // Type indicator
                Text(
                    text = when (source.type) {
                        PatchSourceType.GITHUB -> "GITHUB REPOSITORY"
                        PatchSourceType.LOCAL -> "LOCAL FILE"
                        else -> ""
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name", fontFamily = mono, fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(corners.small)
                )

                when (source.type) {
                    PatchSourceType.GITHUB -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; error = null },
                            label = { Text("Repository URL", fontFamily = mono, fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(corners.small)
                        )
                    }
                    PatchSourceType.LOCAL -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = filePath,
                                onValueChange = { filePath = it; error = null },
                                label = { Text(".mpp file", fontFamily = mono, fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(corners.small),
                                readOnly = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val dialog = FileDialog(null as Frame?, "Select .mpp file", FileDialog.LOAD).apply {
                                        setFilenameFilter { _, n -> n.endsWith(".mpp", ignoreCase = true) }
                                        isVisible = true
                                    }
                                    if (dialog.directory != null && dialog.file != null) {
                                        filePath = File(dialog.directory, dialog.file).absolutePath
                                        error = null
                                    }
                                },
                                shape = RoundedCornerShape(corners.small)
                            ) {
                                Text(
                                    "BROWSE",
                                    fontFamily = mono,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    else -> {}
                }

                error?.let {
                    Text(text = it, fontSize = 11.sp, fontFamily = mono, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name is required"; return@Button }
                    when (source.type) {
                        PatchSourceType.GITHUB -> {
                            val resolvedUrl = resolveGitHubUrl(url.trim())
                            if (resolvedUrl == null) {
                                error = "Enter a valid GitHub URL or Morphe source link"; return@Button
                            }
                            onSave(source.copy(
                                name = name.trim(),
                                url = resolvedUrl
                            ))
                            return@Button
                        }
                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = "Select a valid .mpp file"; return@Button
                            }
                        }
                        else -> {}
                    }
                    onSave(source.copy(
                        name = name.trim(),
                        filePath = if (source.type == PatchSourceType.LOCAL) filePath.trim() else source.filePath
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "SAVE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "CANCEL",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

// ── Signing / Keystore Section ──

@Composable
private fun SigningSection(
    keystorePath: String?,
    keystorePassword: String?,
    keystoreAlias: String,
    keystoreEntryPassword: String,
    onKeystorePathChange: (String?) -> Unit,
    onCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit,
    mono: androidx.compose.ui.text.font.FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true
) {
    val corners = LocalMorpheCorners.current
    val alpha = if (enabled) 1f else 0.4f

    var localPassword by remember(keystorePassword) { mutableStateOf(keystorePassword ?: "") }
    var localAlias by remember(keystoreAlias) { mutableStateOf(keystoreAlias) }
    var localEntryPassword by remember(keystoreEntryPassword) { mutableStateOf(keystoreEntryPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var showEntryPassword by remember { mutableStateOf(false) }
    var showKeystoreInfo by remember { mutableStateOf(false) }
    var keystoreError by remember { mutableStateOf<String?>(null) }

    val keystoreFile = keystorePath?.let { File(it) }
    val keystoreExists = keystoreFile?.exists() == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection("SIGNING", mono) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Keystore used to sign patched APKs",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(8.dp))

        // Keystore path row
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (keystorePath != null) {
                        keystoreFile?.name ?: keystorePath
                    } else "Default (auto-generated)",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val dialog = FileDialog(null as Frame?, "Select Keystore", FileDialog.LOAD).apply {
                        setFilenameFilter { _, n ->
                            n.lowercase().let {
                                it.endsWith(".keystore") || it.endsWith(".jks") ||
                                it.endsWith(".bks") || it.endsWith(".p12") || it.endsWith(".pfx")
                            }
                        }
                        isVisible = true
                    }
                    if (dialog.directory != null && dialog.file != null) {
                        val selected = File(dialog.directory, dialog.file)
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            keystoreError = null
                            onKeystorePathChange(selected.absolutePath)
                        } else {
                            keystoreError = "Invalid file type. Expected: ${validExtensions.joinToString(", ")}"
                        }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    "BROWSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            if (keystorePath != null) {
                OutlinedButton(
                    onClick = { onKeystorePathChange(null) },
                    enabled = enabled,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        "RESET",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Warning if keystore path set but file doesn't exist
        if (keystorePath != null && !keystoreExists) {
            Text(
                text = "Keystore not found — will be created on next patch",
                fontSize = 10.sp,
                fontFamily = mono,
                color = Color(0xFFE0A030)
            )
        }

        // Error for invalid file type selection
        keystoreError?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Full path tooltip
        if (keystorePath != null) {
            Text(
                text = keystorePath,
                fontSize = 9.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(4.dp))

        // Keystore password
        OutlinedTextField(
            value = localPassword,
            onValueChange = {
                localPassword = it
                onCredentialsChange(it.ifEmpty { null }, localAlias, localEntryPassword)
            },
            label = { Text("Keystore password", fontFamily = mono, fontSize = 10.sp) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                                   else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide" else "Show",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            },
            textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(corners.small)
        )

        Spacer(Modifier.height(4.dp))

        // Key alias
        OutlinedTextField(
            value = localAlias,
            onValueChange = {
                localAlias = it
                onCredentialsChange(localPassword.ifEmpty { null }, it, localEntryPassword)
            },
            label = { Text("Key alias", fontFamily = mono, fontSize = 10.sp) },
            singleLine = true,
            enabled = enabled,
            textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(corners.small)
        )

        Spacer(Modifier.height(4.dp))

        // Key entry password
        OutlinedTextField(
            value = localEntryPassword,
            onValueChange = {
                localEntryPassword = it
                onCredentialsChange(localPassword.ifEmpty { null }, localAlias, it)
            },
            label = { Text("Key password", fontFamily = mono, fontSize = 10.sp) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (showEntryPassword) androidx.compose.ui.text.input.VisualTransformation.None
                                   else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showEntryPassword = !showEntryPassword },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (showEntryPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showEntryPassword) "Hide" else "Show",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            },
            textStyle = LocalTextStyle.current.copy(fontFamily = mono, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(corners.small)
        )

        // Verify credentials button
        var verifyResult by remember { mutableStateOf<String?>(null) }
        var verifySuccess by remember { mutableStateOf(false) }

        if (keystoreExists) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    verifyResult = null
                    verifySuccess = false
                    val path = keystorePath ?: return@OutlinedButton
                    val result = readKeystoreInfo(
                        path,
                        localPassword.ifEmpty { null },
                        localAlias.ifEmpty { "Morphe" },
                        localEntryPassword.ifEmpty { "Morphe" }
                    )
                    if (result == null) {
                        verifyResult = "Could not open keystore — check keystore password"
                        verifySuccess = false
                    } else if (result.warnings.isNotEmpty()) {
                        verifyResult = result.warnings.first()
                        verifySuccess = false
                    } else {
                        verifyResult = "Credentials valid"
                        verifySuccess = true
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp,
                    when {
                        verifySuccess -> MorpheColors.Teal.copy(alpha = 0.4f)
                        verifyResult != null -> Color(0xFFE0A030).copy(alpha = 0.4f)
                        else -> borderColor
                    }
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "VERIFY CREDENTIALS",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            verifyResult?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = if (verifySuccess) MorpheColors.Teal else Color(0xFFE0A030),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Generate button (only when no keystore exists yet)
        var generateError by remember { mutableStateOf<String?>(null) }
        var generateSuccess by remember { mutableStateOf(false) }

        if (!keystoreExists) {
            OutlinedButton(
                onClick = {
                    generateError = null
                    generateSuccess = false

                    // If no path set, ask the user where to save
                    val path = keystorePath ?: run {
                        val dialog = FileDialog(null as Frame?, "Save Keystore", FileDialog.SAVE).apply {
                            file = "morphe.keystore"
                            isVisible = true
                        }
                        if (dialog.directory != null && dialog.file != null) {
                            val chosen = File(dialog.directory, dialog.file).absolutePath
                            onKeystorePathChange(chosen)
                            chosen
                        } else {
                            return@OutlinedButton // user cancelled
                        }
                    }

                    try {
                        val file = File(path)
                        file.parentFile?.mkdirs()
                        val keyPair = ApkSigner.newPrivateKeyCertificatePair("Morphe", java.util.Date(System.currentTimeMillis() + 8L * 365 * 24 * 60 * 60 * 1000))
                        val ks = ApkSigner.newKeyStore(setOf(
                            ApkSigner.KeyStoreEntry(
                                localAlias.ifEmpty { "Morphe" },
                                localEntryPassword.ifEmpty { "Morphe" },
                                keyPair
                            )
                        ))
                        file.outputStream().use {
                            ks.store(it, localPassword.ifEmpty { null }?.toCharArray())
                        }
                        // Save credentials to config
                        onCredentialsChange(
                            localPassword.ifEmpty { null },
                            localAlias.ifEmpty { "Morphe" },
                            localEntryPassword.ifEmpty { "Morphe" }
                        )
                        generateSuccess = true
                    } catch (e: Exception) {
                        generateError = "Failed to generate: ${e.message}"
                        Logger.error("Failed to generate keystore", e)
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, if (generateSuccess) MorpheColors.Teal.copy(alpha = 0.4f) else accentColor.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (generateSuccess) MorpheColors.Teal else accentColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (generateSuccess) "KEYSTORE GENERATED" else "GENERATE KEYSTORE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = if (generateSuccess) MorpheColors.Teal else accentColor
                )
            }

            generateError?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = "Uses the credentials entered above",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Certificate info
            OutlinedButton(
                onClick = { showKeystoreInfo = true },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "CERTIFICATE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Export
            OutlinedButton(
                onClick = {
                    val sourceFile = keystoreFile ?: return@OutlinedButton
                    if (!sourceFile.exists()) return@OutlinedButton
                    val dialog = FileDialog(null as Frame?, "Export Keystore", FileDialog.SAVE).apply {
                        file = sourceFile.name
                        isVisible = true
                    }
                    if (dialog.directory != null && dialog.file != null) {
                        try {
                            sourceFile.copyTo(File(dialog.directory, dialog.file), overwrite = true)
                        } catch (e: Exception) {
                            Logger.error("Failed to export keystore", e)
                        }
                    }
                },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "EXPORT",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        } // inner Column
        } // CollapsibleSection
    }

    // Certificate info dialog
    if (showKeystoreInfo && keystorePath != null) {
        KeystoreInfoDialog(
            keystorePath = keystorePath,
            password = keystorePassword,
            alias = keystoreAlias,
            entryPassword = keystoreEntryPassword,
            onDismiss = { showKeystoreInfo = false }
        )
    }
}

@Composable
private fun KeystoreInfoDialog(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String,
    onDismiss: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    val info = remember(keystorePath, password, alias, entryPassword) {
        readKeystoreInfo(keystorePath, password, alias, entryPassword)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "CERTIFICATE INFO",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            if (info != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(min = 300.dp)
                ) {
                    // Show warnings first if there are any
                    if (info.warnings.isNotEmpty()) {
                        info.warnings.forEach { warning ->
                            Text(
                                text = warning,
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = Color(0xFFE0A030),
                                lineHeight = 14.sp
                            )
                        }
                        // If no cert data (alias not found), stop here
                        if (info.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow("Alias", info.alias, mono)
                    CertInfoRow("Issuer", info.issuer, mono)
                    CertInfoRow("Valid from", info.validFrom, mono)
                    CertInfoRow("Valid until", info.validTo, mono)

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-256 FINGERPRINT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = info.sha256Fingerprint,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-1 FINGERPRINT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = info.sha1Fingerprint,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Could not read keystore. Check the password and alias.",
                    fontSize = 12.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    "CLOSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun CertInfoRow(
    label: String,
    value: String,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    Column {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

private data class KeystoreInfoResult(
    val alias: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<String> = emptyList()
)

private fun readKeystoreInfo(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String? = null
): KeystoreInfoResult? {
    val file = File(keystorePath)
    if (!file.exists()) return null

    val passwordChars = password?.toCharArray() ?: charArrayOf()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    // Ensure BouncyCastle provider is registered (needed for BKS keystores)
    try {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(
                Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as java.security.Provider
            )
        }
    } catch (_: Exception) {
        // BC not on classpath — BKS keystores won't be readable, but JKS/PKCS12 still work
    }

    // Try multiple keystore types: BKS (what Morphe generates), then JKS, then PKCS12
    // BKS requires BouncyCastle provider — try with provider name, fall back without
    val types = listOf("BKS" to "BC", "BKS" to null, "JKS" to null, "PKCS12" to null)
    for ((type, provider) in types) {
        try {
            val ks = if (provider != null) {
                KeyStore.getInstance(type, provider)
            } else {
                KeyStore.getInstance(type)
            }

            file.inputStream().use { ks.load(it, passwordChars) }

            val warnings = mutableListOf<String>()

            // Alias must match exactly
            if (!ks.containsAlias(alias)) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf("Alias \"$alias\" not found in keystore")
                )
            }

            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue

            // Verify the entry password actually works
            try {
                ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
            } catch (_: Exception) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf("Key password is incorrect for alias \"$alias\"")
                )
            }

            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            return KeystoreInfoResult(
                alias = alias,
                issuer = cert.issuerDN.name,
                validFrom = dateFormat.format(cert.notBefore),
                validTo = dateFormat.format(cert.notAfter),
                sha256Fingerprint = sha256,
                sha1Fingerprint = sha1,
                warnings = warnings
            )
        } catch (_: Exception) {
            continue
        }
    }
    return null
}

private fun ThemePreference.toDisplayName(): String {
    return when (this) {
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
        ThemePreference.AMOLED -> "AMOLED"
        ThemePreference.NORD -> "Nord"
        ThemePreference.CATPPUCCIN -> "Catppuccin"
        ThemePreference.SAKURA -> "Sakura"
        ThemePreference.MATCHA -> "Matcha"
        ThemePreference.SYSTEM -> "System"
    }
}

private fun ThemePreference.iconSymbol(): String {
    return when (this) {
        ThemePreference.LIGHT -> "☀"
        ThemePreference.DARK -> "☾"
        ThemePreference.AMOLED -> "◆"
        ThemePreference.NORD -> "❄"
        ThemePreference.CATPPUCCIN -> "🐱"
        ThemePreference.SAKURA -> "🌸"
        ThemePreference.MATCHA -> "🍵"
        ThemePreference.SYSTEM -> "⚙"
    }
}

private fun ThemePreference.accentColor(): Color {
    return when (this) {
        ThemePreference.LIGHT -> MorpheColors.Blue
        ThemePreference.DARK -> MorpheColors.Blue
        ThemePreference.AMOLED -> MorpheColors.Cyan
        ThemePreference.NORD -> Color(0xFF88C0D0)
        ThemePreference.CATPPUCCIN -> Color(0xFFCBA6F7)
        ThemePreference.SAKURA -> Color(0xFFB43A67)
        ThemePreference.MATCHA -> Color(0xFF4C7A35)
        ThemePreference.SYSTEM -> MorpheColors.Blue
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
        FileUtils.getPatchesDir().listFiles()?.forEach { file ->
            try { if (!file.deleteRecursively()) throw Exception("Could not delete") }
            catch (e: Exception) { failedCount++; Logger.error("Failed to delete ${file.name}: ${e.message}") }
        }
        FileUtils.getLogsDir().listFiles()?.forEach { file ->
            try { if (!file.deleteRecursively()) throw Exception("Could not delete") }
            catch (e: Exception) { failedCount++; Logger.error("Failed to delete log ${file.name}: ${e.message}") }
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

/**
 * Resolves a URL to a GitHub repository URL.
 * Supports:
 * - Direct GitHub URLs: https://github.com/owner/repo
 * - Morphe source links: https://morphe.software/add-source?github=owner/repo
 * - Short form: owner/repo (assumed GitHub)
 * Returns a normalized https://github.com/owner/repo URL, or null if invalid.
 */
private fun resolveGitHubUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    // Morphe source link: morphe.software/add-source?github=owner/repo
    if (trimmed.contains("morphe.software/add-source")) {
        val match = Regex("[?&]github=([^&]+)").find(trimmed)
        val repoPath = match?.groupValues?.get(1) ?: return null
        val clean = repoPath.trimEnd('/')
        return if (clean.contains('/') && clean.split('/').size == 2) {
            "https://github.com/$clean"
        } else null
    }

    // Direct GitHub URL: https://github.com/owner/repo
    if (trimmed.contains("github.com/")) {
        // Extract owner/repo from full URL
        val match = Regex("github\\.com/([^/]+/[^/]+)").find(trimmed)
        return if (match != null) {
            "https://github.com/${match.groupValues[1].trimEnd('/')}"
        } else null
    }

    // Short form: owner/repo
    if (trimmed.matches(Regex("[\\w.-]+/[\\w.-]+"))) {
        return "https://github.com/$trimmed"
    }

    return null
}
