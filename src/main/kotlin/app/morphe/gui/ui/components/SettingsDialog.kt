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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
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
    onCacheCleared: () -> Unit = {}
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
                    ThemePreference.entries.forEach { theme ->
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
        SectionLabel("PATCH SOURCES", mono)
        Spacer(Modifier.height(2.dp))
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
