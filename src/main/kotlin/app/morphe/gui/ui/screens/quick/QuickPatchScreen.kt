/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick

import app.morphe.gui.ui.screens.quick.components.BrandingLogo
import app.morphe.gui.ui.screens.quick.components.PatchesVersionBadge
import app.morphe.gui.ui.screens.quick.components.IdleContent
import app.morphe.gui.ui.screens.quick.components.ReadyContent
import app.morphe.gui.ui.screens.quick.components.PatchingContent
import app.morphe.gui.ui.screens.quick.components.CompletedContent
import app.morphe.gui.ui.screens.quick.components.SupportedAppsRow
import app.morphe.gui.ui.icons.MorpheIcons
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.SourceSheetMode
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceVersionMap
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun QuickPatchContent(viewModel: QuickPatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Source picker state. Quick Patch is single-source by design, so the picker
    // uses the same SourceManagementSheet as Expert mode but in SINGLE_SELECT
    // mode (radio behavior). Users can also add/edit/remove sources from here,
    // matching morphe-manager which doesn't gate source management on expert mode.
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()
    val pickerScope = rememberCoroutineScope()
    var showSourcePicker by remember { mutableStateOf(false) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.patchSourceName, allSources) {
        // Resolve the current active source's id by name for radio selection.
        activeSourceId = allSources.firstOrNull { it.name == uiState.patchSourceName }?.id
            ?: patchSourceManager.getActiveSource().id
    }

    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    if (showSourcePicker) {
        // Same per-source version, channel, and failure data Expert mode feeds the
        // sheet, so a source that broke reads as FAILED here too instead of looking
        // healthy but silently contributing no patches.
        val snapshot = viewModel.getResolvedSourcesSnapshot()
        SourceManagementSheet(
            sources = allSources,
            sourceVersions = snapshot.sourceVersionMap(),
            sourceChannels = snapshot.sourceChannelMap(),
            sourceErrors = snapshot.sourceErrorMap(),
            isLoading = uiState.isLoadingPatches,
            mode = SourceSheetMode.SINGLE_SELECT,
            activeSourceId = activeSourceId,
            onSelectSingle = { id ->
                showSourcePicker = false
                pickerScope.launch { patchSourceManager.switchSource(id) }
            },
            onToggleEnabled = { _, _ -> /* no-op in SINGLE_SELECT mode */ },
            onAdd = { src -> pickerScope.launch { patchSourceManager.addSource(src) } },
            onEdit = { src -> pickerScope.launch { patchSourceManager.updateSource(src) } },
            onRemove = { id -> pickerScope.launch { patchSourceManager.removeSource(id) } },
            onReorder = { orderedIds -> pickerScope.launch { patchSourceManager.reorderSources(orderedIds) } },
            onOpenPatches = { /* unused in SINGLE_SELECT mode */ },
            onDismiss = { showSourcePicker = false },
            onRefresh = { viewModel.retryLoadPatches() },
            enabled = uiState.phase != QuickPatchPhase.DOWNLOADING &&
                      uiState.phase != QuickPatchPhase.PATCHING,
        )
    }

    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { files ->
            files.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) ||
                it.name.endsWith(".apkm", ignoreCase = true) ||
                it.name.endsWith(".xapk", ignoreCase = true) ||
                it.name.endsWith(".apks", ignoreCase = true)
            }?.let { viewModel.onFileSelected(it) }
        },
        enabled = uiState.phase != QuickPatchPhase.ANALYZING
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Header row, matches expert mode ──
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
                    // Logo, left-aligned
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .onSizeChanged { leadingWidthPx = it.width }
                    ) {
                        BrandingLogo()
                    }

                    // Patches version badge, centered. Click opens the source-management
                    // sheet in SINGLE_SELECT mode so the user can pick which source Quick
                    // Patch uses (and add/edit/remove sources too).
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(start = centerSidePadding, end = centerSidePadding)
                    ) {
                        PatchesVersionBadge(
                            patchesVersion = uiState.patchesVersion,
                            isLoading = uiState.isLoadingPatches,
                            patchSourceName = uiState.patchSourceName,
                            latestLabel = if (uiState.patchesVersion != null &&
                                              uiState.patchesVersion == uiState.latestPatchesVersion) {
                                "LATEST STABLE"
                            } else null,
                            onClick = { showSourcePicker = true },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .onSizeChanged { trailingWidthPx = it.width }
                    ) {
                        TopBarRow(
                            allowCacheClear = false,
                            isPatching = uiState.phase == QuickPatchPhase.DOWNLOADING || uiState.phase == QuickPatchPhase.PATCHING,
                            onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        )
                    }
                }

                // ── Content ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Offline banner
                    if (uiState.isOffline && uiState.phase == QuickPatchPhase.IDLE) {
                        OfflineBanner(
                            onRetry = { viewModel.retryLoadPatches() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // CLI update banner
                    if (uiState.showUpdateBanner) {
                        app.morphe.gui.ui.components.UpdateBanner(
                            info = uiState.updateInfo!!,
                            onDismissForSession = { viewModel.dismissUpdateForSession() },
                            onDismissForVersion = { viewModel.dismissUpdateForVersion() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // ── Main content ──
                    val lastApkInfo = remember(uiState.apkInfo) { uiState.apkInfo }
                    val lastOutputPath = remember(uiState.outputPath) { uiState.outputPath }

                    AnimatedContent(
                        targetState = uiState.phase,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                    ) { phase ->
                        when (phase) {
                            QuickPatchPhase.IDLE, QuickPatchPhase.ANALYZING -> {
                                IdleContent(
                                    isAnalyzing = phase == QuickPatchPhase.ANALYZING,
                                    isDragHovering = uiState.isDragHovering,
                                    onBrowse = { openFilePicker()?.let { viewModel.onFileSelected(it) } }
                                )
                            }
                            QuickPatchPhase.READY -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                if (info != null) {
                                    ReadyContent(
                                        apkInfo = info,
                                        compatiblePatches = uiState.compatiblePatches,
                                        onPatch = { viewModel.startPatching() },
                                        onClear = { viewModel.reset() }
                                    )
                                }
                            }
                            QuickPatchPhase.DOWNLOADING, QuickPatchPhase.PATCHING -> {
                                PatchingContent(
                                    phase = phase,
                                    statusMessage = uiState.statusMessage,
                                    onCancel = { viewModel.cancelPatching() }
                                )
                            }
                            QuickPatchPhase.COMPLETED -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                val output = uiState.outputPath ?: lastOutputPath
                                if (info != null && output != null) {
                                    CompletedContent(
                                        outputPath = output,
                                        apkInfo = info,
                                        onPatchAnother = { viewModel.reset() }
                                    )
                                }
                            }
                        }
                    }

                    // ── Supported apps (idle only) ──
                    if (uiState.phase == QuickPatchPhase.IDLE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SupportedAppsRow(
                            supportedApps = uiState.supportedApps,
                            isLoading = uiState.isLoadingPatches,
                            loadError = uiState.patchLoadError,
                            isDefaultSource = uiState.isDefaultSource,
                            onRetry = { viewModel.retryLoadPatches() }
                        )
                    }
                }
            }

            // Drag overlay
            if (uiState.isDragHovering) {
                DragOverlay()
            }

            // Error/warning bar
            uiState.error?.let { error ->
                val isUnsupportedWarning = error.contains("not supported in Quick Patch")
                MorpheErrorBar(
                    message = error,
                    onDismiss = { viewModel.clearError() },
                    isWarning = isUnsupportedWarning,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(
                width = 2.dp,
                color = accents.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(0.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = MorpheIcons.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = accents.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Drop APK here",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = accents.primary
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  UTILITIES
// ════════════════════════════════════════════════════════════════════

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") } }
        isVisible = true
    }
    val directory = fileDialog.directory
    val file = fileDialog.file
    return if (directory != null && file != null) File(directory, file) else null
}

internal fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
