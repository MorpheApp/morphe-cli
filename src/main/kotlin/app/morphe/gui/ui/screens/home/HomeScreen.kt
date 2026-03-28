package app.morphe.gui.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalUriHandler
import app.morphe.morphe_cli.generated.resources.Res
import app.morphe.morphe_cli.generated.resources.morphe_dark
import app.morphe.morphe_cli.generated.resources.morphe_light
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import org.jetbrains.compose.resources.painterResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.DraggableHeaderArea
import app.morphe.gui.ui.components.LocalTitleBarInsets
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.screens.home.components.ApkInfoCard
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.util.VersionStatus
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<HomeViewModel>()
        HomeScreenContent(viewModel = viewModel)
    }
}

@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel
) {
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()

    val navStackSize = navigator.items.size
    LaunchedEffect(navStackSize) {
        viewModel.refreshPatchesIfNeeded()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // Full screen drop zone wrapper
    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { viewModel.onFilesDropped(it) },
        enabled = !uiState.isAnalyzing
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val useSplitLayout = maxWidth >= 720.dp
            val isCompact = maxWidth < 500.dp
            val isSmall = maxHeight < 600.dp
            val padding = if (isCompact) 16.dp else 24.dp

            // Version warning dialog state
            var showVersionWarningDialog by remember { mutableStateOf(false) }

            if (showVersionWarningDialog && uiState.apkInfo != null) {
                VersionWarningDialog(
                    versionStatus = uiState.apkInfo!!.versionStatus,
                    currentVersion = uiState.apkInfo!!.versionName,
                    suggestedVersion = uiState.apkInfo!!.suggestedVersion ?: "",
                    onConfirm = {
                        showVersionWarningDialog = false
                        val patchesFile = viewModel.getCachedPatchesFile()
                        if (patchesFile != null && uiState.apkInfo != null) {
                            navigator.push(PatchSelectionScreen(
                                apkPath = uiState.apkInfo!!.filePath,
                                apkName = uiState.apkInfo!!.appName,
                                patchesFilePath = patchesFile.absolutePath,
                                packageName = uiState.apkInfo!!.packageName,
                                apkArchitectures = uiState.apkInfo!!.architectures
                            ))
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val useHorizontalHeader = maxWidth >= 600.dp
            val patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null
            val onChangePatchesClick: () -> Unit = {
                navigator.push(PatchesScreen(
                    apkPath = uiState.apkInfo?.filePath ?: "",
                    apkName = uiState.apkInfo?.appName ?: "Select APK first"
                ))
            }
            val onRetry: () -> Unit = { viewModel.retryLoadPatches() }
            val onClearClick: () -> Unit = { viewModel.clearSelection() }
            val onChangeClick: () -> Unit = {
                openFilePicker()?.let { file ->
                    viewModel.onFileSelected(file)
                }
            }
            val onContinueClick: () -> Unit = {
                handleContinue(uiState, viewModel, navigator) {
                    showVersionWarningDialog = true
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Header ──
                    if (useHorizontalHeader) {
                        HeaderBar(
                            uiState = uiState,
                            isSmall = isSmall,
                            onChangePatchesClick = onChangePatchesClick,
                            onRetry = onRetry
                        )
                    } else {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 16.dp))
                        BrandingSection(isCompact = isCompact)

                        if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            PatchesVersionCard(
                                patchesVersion = uiState.patchesVersion!!,
                                isLatest = uiState.isUsingLatestPatches,
                                onChangePatchesClick = onChangePatchesClick,
                                isCompact = isCompact,
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            )
                        } else if (uiState.isLoadingPatches) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            PatchesLoadingIndicator()
                        } else if (uiState.patchLoadError != null) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            PatchesVersionCard(
                                patchesVersion = "NOT LOADED",
                                isLatest = false,
                                onChangePatchesClick = onChangePatchesClick,
                                isCompact = isCompact,
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            )
                        }

                        if (uiState.isOffline && !uiState.isLoadingPatches) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            OfflineBanner(
                                onRetry = onRetry,
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            )
                        }
                    }

                    // ── Main workspace area ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        MiddleContent(
                            uiState = uiState,
                            isCompact = isCompact,
                            patchesLoaded = patchesLoaded,
                            onClearClick = onClearClick,
                            onChangeClick = onChangeClick,
                            onContinueClick = onContinueClick
                        )
                    }

                    // ── Supported apps ──
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(
                            start = padding,
                            end = padding,
                            bottom = if (isSmall) 8.dp else 16.dp
                        )
                    ) {
                        SupportedAppsSection(
                            isCompact = isCompact,
                            maxWidth = this@BoxWithConstraints.maxWidth,
                            isLoading = uiState.isLoadingPatches,
                            isDefaultSource = uiState.isDefaultSource,
                            supportedApps = uiState.supportedApps,
                            loadError = uiState.patchLoadError,
                            onRetry = onRetry
                        )
                    }
                }

                // Top bar — only floated when not using horizontal header
                if (!useHorizontalHeader) {
                    val titleInsets = LocalTitleBarInsets.current
                    TopBarRow(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = padding + titleInsets.top,
                                end = padding
                            ),
                        allowCacheClear = true
                    )
                }

                // Snackbar host
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Drag overlay
                if (uiState.isDragHovering) {
                    DragOverlay()
                }
            }
        }
    }
}

private fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navigator: cafe.adriel.voyager.navigator.Navigator,
    showWarning: () -> Unit
) {
    val patchesFile = viewModel.getCachedPatchesFile() ?: return
    val versionStatus = uiState.apkInfo?.versionStatus
    if (versionStatus != null && versionStatus != VersionStatus.EXACT_MATCH && versionStatus != VersionStatus.UNKNOWN) {
        showWarning()
    } else {
        uiState.apkInfo?.let { info ->
            navigator.push(PatchSelectionScreen(
                apkPath = info.filePath,
                apkName = info.appName,
                patchesFilePath = patchesFile.absolutePath,
                packageName = info.packageName,
                apkArchitectures = info.architectures
            ))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  HEADER BAR — Logo + patches version + status, horizontal
// ════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderBar(
    uiState: HomeUiState,
    isSmall: Boolean,
    onChangePatchesClick: () -> Unit,
    onRetry: () -> Unit
) {
    val mono = LocalMorpheFont.current
    val titleInsets = LocalTitleBarInsets.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    DraggableHeaderArea {
        Row(
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
                .padding(
                    start = 12.dp + titleInsets.start,
                    end = 12.dp,
                    top = 8.dp + titleInsets.top,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo — left-aligned, compact
            BrandingSection(isCompact = true)

            Spacer(modifier = Modifier.width(16.dp))

            // Patches version inline
            if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                PatchesVersionInline(
                    patchesVersion = uiState.patchesVersion!!,
                    isLatest = uiState.isUsingLatestPatches,
                    onChangePatchesClick = onChangePatchesClick
                )
            } else if (uiState.isLoadingPatches) {
                PatchesLoadingIndicator()
            } else if (uiState.patchLoadError != null) {
                PatchesVersionInline(
                    patchesVersion = "NOT LOADED",
                    isLatest = false,
                    onChangePatchesClick = onChangePatchesClick
                )
            }

            // Offline badge
            if (uiState.isOffline && !uiState.isLoadingPatches) {
                Spacer(modifier = Modifier.width(12.dp))
                OfflineBadge(onRetry = onRetry)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Device indicator + settings — inline in the header
            TopBarRow(allowCacheClear = true)
        }
    }
}

/**
 * Inline patches version for the header bar — compact, horizontal.
 */
@Composable
private fun PatchesVersionInline(
    patchesVersion: String,
    isLatest: Boolean,
    onChangePatchesClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MorpheColors.Blue.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
            .clickable(onClick = onChangePatchesClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PATCHES",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = patchesVersion,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = MorpheColors.Blue
        )
        if (isLatest) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(MorpheColors.Teal.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                    .border(1.dp, MorpheColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "LATEST",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MorpheColors.Teal,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun PatchesLoadingIndicator() {
    val mono = LocalMorpheFont.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MorpheColors.Blue
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Loading patches…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun OfflineBadge(onRetry: () -> Unit) {
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

// ════════════════════════════════════════════════════════════════════
//  MIDDLE CONTENT — Drop zone / APK info / Analyzing
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MiddleContent(
    uiState: HomeUiState,
    isCompact: Boolean,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    when {
        uiState.isAnalyzing -> {
            AnalyzingSection(isCompact = isCompact)
        }
        uiState.apkInfo != null -> {
            ApkSelectedSection(
                patchesLoaded = patchesLoaded,
                apkInfo = uiState.apkInfo,
                isCompact = isCompact,
                onClearClick = onClearClick,
                onChangeClick = onChangeClick,
                onContinueClick = onContinueClick
            )
        }
        else -> {
            DropPromptSection(
                isDragHovering = uiState.isDragHovering,
                isCompact = isCompact,
                onBrowseClick = onChangeClick
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DROP ZONE — Corner brackets, scanner/targeting aesthetic
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DropPromptSection(
    isDragHovering: Boolean,
    isCompact: Boolean = false,
    onBrowseClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val bracketColor = if (isDragHovering) MorpheColors.Blue.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val bracketLen = if (isCompact) 24f else 32f

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
                .aspectRatio(if (isCompact) 1.6f else 1.4f)
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
                    fontSize = if (isCompact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = if (isDragHovering) MorpheColors.Blue
                            else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                Text(
                    text = "or",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.height(if (isCompact) 38.dp else 42.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, MorpheColors.Blue.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MorpheColors.Blue)
                ) {
                    Text(
                        "BROWSE FILES",
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  APK SELECTED — Info card + action buttons
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    isCompact: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val showWarning = apkInfo.versionStatus != VersionStatus.EXACT_MATCH &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val warningColor = when (apkInfo.versionStatus) {
        VersionStatus.NEWER_VERSION -> MaterialTheme.colorScheme.error
        VersionStatus.OLDER_VERSION -> Color(0xFFFF9800)
        else -> MorpheColors.Blue
    }
    val primaryColor = if (showWarning) warningColor else MorpheColors.Blue

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 20.dp))

        if (isCompact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    ActionButtonContent(patchesLoaded, showWarning, mono)
                }
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
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
            }
        } else {
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
}

@Composable
private fun ActionButtonContent(
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
                imageVector = Icons.Default.Warning,
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
private fun AnalyzingSection(isCompact: Boolean = false) {
    val mono = LocalMorpheFont.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isCompact) 16.dp else 32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (isCompact) 28.dp else 32.dp),
            color = MorpheColors.Blue,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

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

// ════════════════════════════════════════════════════════════════════
//  SUPPORTED APPS — Bottom section, horizontal scrolling cards
// ════════════════════════════════════════════════════════════════════

/**
 * Bottom section — horizontal scrolling cards.
 */
@Composable
private fun SupportedAppsSection(
    isCompact: Boolean = false,
    maxWidth: Dp = 800.dp,
    isLoading: Boolean = false,
    isDefaultSource: Boolean = true,
    supportedApps: List<SupportedApp> = emptyList(),
    loadError: String? = null,
    onRetry: () -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val useVerticalLayout = maxWidth < 400.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "SUPPORTED APPS",
            fontSize = if (isCompact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MorpheColors.Cyan.copy(alpha = 0.7f),
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isDefaultSource) "Download the exact version from APKMirror and drop it here."
                   else "Drop the APK for a supported app here.",
            fontSize = if (isCompact) 10.sp else 11.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = if (useVerticalLayout) 280.dp else 500.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MorpheColors.Cyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading patches...",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            loadError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "LOAD FAILED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = loadError,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(corners.small),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MorpheColors.Cyan
                        ),
                        border = BorderStroke(1.dp, MorpheColors.Cyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            "RETRY",
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            supportedApps.isEmpty() -> {
                Text(
                    text = "No supported apps found",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            else -> {
                val focusManager = LocalFocusManager.current
                var searchQuery by remember { mutableStateOf("") }
                val filteredApps = if (searchQuery.isBlank()) supportedApps
                else supportedApps.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (supportedApps.size > 4) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Filter apps…",
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MorpheColors.Cyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = mono,
                            fontSize = 11.sp
                        ),
                        shape = RoundedCornerShape(corners.small),
                        modifier = Modifier
                            .widthIn(max = 260.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MorpheColors.Cyan.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            cursorColor = MorpheColors.Cyan
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val cardsMinHeight = if (useVerticalLayout) 120.dp else 80.dp

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = cardsMinHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching apps",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                } else if (useVerticalLayout) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(max = 300.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { focusManager.clearFocus() }
                    ) {
                        filteredApps.forEach { app ->
                            SupportedAppCardDynamic(
                                supportedApp = app,
                                isCompact = isCompact,
                                showDownloadButton = isDefaultSource,
                                showPackageName = !isDefaultSource,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            .horizontalScroll(rememberScrollState())
                            .height(IntrinsicSize.Max)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { focusManager.clearFocus() }
                    ) {
                        filteredApps.forEach { app ->
                            SupportedAppCardDynamic(
                                supportedApp = app,
                                isCompact = isCompact,
                                showDownloadButton = isDefaultSource,
                                showPackageName = !isDefaultSource,
                                modifier = Modifier.width(190.dp).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BrandingSection(isCompact: Boolean = false) {
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

@Composable
private fun PatchesVersionCard(
    patchesVersion: String,
    isLatest: Boolean,
    onChangePatchesClick: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MorpheColors.Blue.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
            .clickable(onClick = onChangePatchesClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PATCHES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = patchesVersion,
                fontSize = if (isCompact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = MorpheColors.Blue
            )
            if (isLatest) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(MorpheColors.Teal.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                        .border(1.dp, MorpheColors.Teal.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LATEST",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MorpheColors.Teal,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionWarningDialog(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val warnColor = if (versionStatus == VersionStatus.NEWER_VERSION)
        MaterialTheme.colorScheme.error else Color(0xFFFF9800)

    val (title, message) = when (versionStatus) {
        VersionStatus.NEWER_VERSION -> Pair(
            "VERSION MISMATCH",
            "Current: v$currentVersion\nExpected: v$suggestedVersion\n\nPatching newer versions may cause failures or broken patches."
        )
        VersionStatus.OLDER_VERSION -> Pair(
            "OUTDATED VERSION",
            "Current: v$currentVersion\nLatest patches target: v$suggestedVersion\n\nYou may be missing new features and fixes."
        )
        else -> Pair("VERSION NOTICE", "Continue with v$currentVersion?")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = warnColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = mono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = warnColor),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "CONTINUE ANYWAY",
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

/**
 * Supported app card — sharp, technical, cyberdeck aesthetic.
 */
@Composable
private fun SupportedAppCardDynamic(
    supportedApp: SupportedApp,
    isCompact: Boolean = false,
    showDownloadButton: Boolean = true,
    showPackageName: Boolean = false,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    var showAllVersions by remember { mutableStateOf(false) }

    val downloadUrl = supportedApp.apkDownloadUrl
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MorpheColors.Cyan.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 12.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = supportedApp.displayName,
                fontSize = if (isCompact) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = supportedApp.packageName,
                fontSize = 9.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 10.dp))

            if (supportedApp.recommendedVersion != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.medium))
                        .background(MorpheColors.Teal.copy(alpha = 0.06f))
                        .border(
                            1.dp,
                            MorpheColors.Teal.copy(alpha = 0.15f),
                            RoundedCornerShape(corners.medium)
                        )
                        .clickable { showAllVersions = !showAllVersions }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RECOMMENDED",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MorpheColors.Teal.copy(alpha = 0.6f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v${supportedApp.recommendedVersion}",
                        fontSize = if (isCompact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MorpheColors.Teal
                    )
                    val otherVersionsCount = supportedApp.supportedVersions.count { it != supportedApp.recommendedVersion }
                    if (otherVersionsCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (showAllVersions) "hide ${otherVersionsCount} more" else "+${otherVersionsCount} compatible",
                            fontSize = 9.sp,
                            fontFamily = mono,
                            color = MorpheColors.Teal.copy(alpha = 0.4f)
                        )
                    }
                }

                val otherVersions = supportedApp.supportedVersions.filter { it != supportedApp.recommendedVersion }
                if (showAllVersions && otherVersions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(corners.medium))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(corners.medium))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ALSO SUPPORTED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            letterSpacing = 1.sp
                        )
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            otherVersions.forEach { version ->
                                Box(
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                                            RoundedCornerShape(corners.small)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v$version",
                                        fontSize = 10.sp,
                                        fontFamily = mono,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.medium))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ANY VERSION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                }
            }

            if (showDownloadButton && downloadUrl != null) {
                Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 10.dp))
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = {
                        openUrlAndFollowRedirects(downloadUrl) { urlResolved ->
                            uriHandler.openUri(urlResolved)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(corners.small),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MorpheColors.Cyan
                    ),
                    border = BorderStroke(
                        1.dp,
                        MorpheColors.Cyan.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "DOWNLOAD APK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    val mono = LocalMorpheFont.current
    val bracketColor = MorpheColors.Blue.copy(alpha = 0.6f)

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
                color = MorpheColors.Blue,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                fontSize = 11.sp,
                fontFamily = mono,
                color = MorpheColors.Blue.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    }
}

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK File", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") } }
        isVisible = true
    }

    val directory = fileDialog.directory
    val file = fileDialog.file

    return if (directory != null && file != null) {
        File(directory, file)
    } else {
        null
    }
}
