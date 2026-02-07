package app.morphe.gui.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import org.jetbrains.compose.resources.painterResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.screens.home.components.ApkInfoCard
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
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

    // Refresh patches when returning from PatchesScreen (in case user selected a different version)
    // Use navigator.items.size as key so this triggers when navigation stack changes (e.g., pop back)
    val navStackSize = navigator.items.size
    LaunchedEffect(navStackSize) {
        viewModel.refreshPatchesIfNeeded()
    }

    // Show error snackbar
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
        onFilesDropped = { viewModel.onFilesDropped(it) }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isCompact = maxWidth < 500.dp
            val isSmall = maxHeight < 600.dp
            val padding = if (isCompact) 16.dp else 24.dp

            // Version warning dialog state
            var showVersionWarningDialog by remember { mutableStateOf(false) }

            // Version warning dialog
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
                                patchesFilePath = patchesFile.absolutePath
                            ))
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val scrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxSize()) {
                // SpaceBetween + fillMaxSize pushes supported apps to the bottom
                // when there's room; verticalScroll kicks in when content overflows.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(padding),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top group: branding + patches version + middle content
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 16.dp))
                        BrandingSection(isCompact = isCompact)

                        // Patches version selector card - right under logo
                        if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            PatchesVersionCard(
                                patchesVersion = uiState.patchesVersion!!,
                                isLatest = uiState.isUsingLatestPatches,
                                onChangePatchesClick = {
                                    // Navigate to patches version selection screen
                                    // Pass empty apk info since user hasn't selected an APK yet
                                    navigator.push(PatchesScreen(
                                        apkPath = uiState.apkInfo?.filePath ?: "",
                                        apkName = uiState.apkInfo?.appName ?: "Select APK first"
                                    ))
                                },
                                isCompact = isCompact,
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            )
                        } else if (uiState.isLoadingPatches) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MorpheColors.Blue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Loading patches...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isSmall) 16.dp else 32.dp))

                        MiddleContent(
                            uiState = uiState,
                            isCompact = isCompact,
                            patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null,
                            onClearClick = { viewModel.clearSelection() },
                            onChangeClick = {
                                openFilePicker()?.let { file ->
                                    viewModel.onFileSelected(file)
                                }
                            },
                            onContinueClick = {
                                val patchesFile = viewModel.getCachedPatchesFile()
                                if (patchesFile == null) {
                                    // Patches not ready yet
                                    return@MiddleContent
                                }

                                val versionStatus = uiState.apkInfo?.versionStatus
                                if (versionStatus != null && versionStatus != VersionStatus.EXACT_MATCH && versionStatus != VersionStatus.UNKNOWN) {
                                    showVersionWarningDialog = true
                                } else {
                                    uiState.apkInfo?.let { info ->
                                        navigator.push(PatchSelectionScreen(
                                            apkPath = info.filePath,
                                            apkName = info.appName,
                                            patchesFilePath = patchesFile.absolutePath
                                        ))
                                    }
                                }
                            }
                        )
                    }

                    // Bottom group: supported apps section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = if (isSmall) 16.dp else 24.dp)
                    ) {
                        SupportedAppsSection(
                            isCompact = isCompact,
                            maxWidth = this@BoxWithConstraints.maxWidth,
                            isLoading = uiState.isLoadingPatches,
                            supportedApps = uiState.supportedApps,
                            loadError = uiState.patchLoadError,
                            onRetry = { viewModel.retryLoadPatches() }
                        )
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 16.dp))
                    }
                }

                // Settings button in top-right corner
                SettingsButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(padding),
                    allowCacheClear = true
                )

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

@Composable
private fun MiddleContent(
    uiState: HomeUiState,
    isCompact: Boolean,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    if (uiState.apkInfo != null) {
        ApkSelectedSection(
            patchesLoaded = patchesLoaded,
            apkInfo = uiState.apkInfo,
            isCompact = isCompact,
            onClearClick = onClearClick,
            onChangeClick = onChangeClick,
            onContinueClick = onContinueClick
        )
    } else {
        DropPromptSection(
            isDragHovering = uiState.isDragHovering,
            isCompact = isCompact,
            onBrowseClick = onChangeClick
        )
    }
}

@Composable
private fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    isCompact: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val showWarning = apkInfo.versionStatus != VersionStatus.EXACT_MATCH &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val warningColor = when (apkInfo.versionStatus) {
        VersionStatus.NEWER_VERSION -> MaterialTheme.colorScheme.error
        VersionStatus.OLDER_VERSION -> Color(0xFFFF9800)
        else -> MorpheColors.Blue
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 24.dp))

        // Action buttons - stack vertically on compact
        if (isCompact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showWarning) warningColor else MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (!patchesLoaded) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Loading patches...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        if (showWarning) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "Change APK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "Change APK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier
                        .widthIn(min = 160.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showWarning) warningColor else MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (!patchesLoaded) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Loading...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        if (showWarning) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            "Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
    val (title, message) = when (versionStatus) {
        VersionStatus.NEWER_VERSION -> Pair(
            "Version Too New",
            "You're using v$currentVersion, but the recommended version is v$suggestedVersion.\n\n" +
                    "Patching newer versions may cause issues or some patches might not work correctly.\n\n" +
                    "Do you want to continue anyway?"
        )
        VersionStatus.OLDER_VERSION -> Pair(
            "Older Version Detected",
            "You're using v$currentVersion, but newer patches are available for v$suggestedVersion.\n\n" +
                    "You may be missing out on new features and bug fixes.\n\n" +
                    "Do you want to continue with this version?"
        )
        else -> Pair("Version Notice", "Continue with v$currentVersion?")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (versionStatus == VersionStatus.NEWER_VERSION)
                    MaterialTheme.colorScheme.error
                else
                    Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (versionStatus == VersionStatus.NEWER_VERSION)
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFFFF9800)
                )
            ) {
                Text("Continue Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BrandingSection(isCompact: Boolean = false) {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(if (isCompact) 48.dp else 60.dp)
    )
}

@Composable
private fun DropPromptSection(
    isDragHovering: Boolean,
    isCompact: Boolean = false,
    onBrowseClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isCompact) 16.dp else 32.dp)
    ) {
        Text(
            text = if (isDragHovering) "Release to drop" else "Drop your APK here",
            fontSize = if (isCompact) 18.sp else 22.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDragHovering)
                MorpheColors.Blue
            else
                MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

        Text(
            text = "or",
            fontSize = if (isCompact) 12.sp else 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

        OutlinedButton(
            onClick = onBrowseClick,
            modifier = Modifier.height(if (isCompact) 44.dp else 48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MorpheColors.Blue
            )
        ) {
            Text(
                "Browse Files",
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        Text(
            text = "Supported: .apk and .apkm files",
            fontSize = if (isCompact) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SupportedAppsSection(
    isCompact: Boolean = false,
    maxWidth: Dp = 800.dp,
    isLoading: Boolean = false,
    supportedApps: List<SupportedApp> = emptyList(),
    loadError: String? = null,
    onRetry: () -> Unit = {}
) {
    // Stack vertically if very narrow
    val useVerticalLayout = maxWidth < 400.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "SUPPORTED APPS",
            fontSize = if (isCompact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

        // Important notice about APK handling
        Text(
            text = "Download the exact version from APKMirror and drop it here directly. Do not rename or modify the file.",
            fontSize = if (isCompact) 10.sp else 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = if (useVerticalLayout) 280.dp else 500.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        when {
            isLoading -> {
                // Loading state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MorpheColors.Blue,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading patches...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            loadError != null -> {
                // Error state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Could not load supported apps",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = loadError,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
            supportedApps.isEmpty() -> {
                // Empty state (shouldn't happen normally)
                Text(
                    text = "No supported apps found",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                // Display supported apps dynamically
                if (useVerticalLayout) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(max = 300.dp)
                    ) {
                        supportedApps.forEach { app ->
                            SupportedAppCardDynamic(
                                supportedApp = app,
                                isCompact = isCompact,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            .widthIn(max = 600.dp)
                    ) {
                        supportedApps.forEach { app ->
                            SupportedAppCardDynamic(
                                supportedApp = app,
                                isCompact = isCompact,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing current patches version with option to change.
 */
@Composable
private fun PatchesVersionCard(
    patchesVersion: String,
    isLatest: Boolean,
    onChangePatchesClick: () -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onChangePatchesClick),
        colors = CardDefaults.cardColors(
            containerColor = MorpheColors.Blue.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isCompact) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Using patches",
                fontSize = if (isCompact) 12.sp else 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = MorpheColors.Blue.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = patchesVersion,
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MorpheColors.Blue,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (isLatest) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MorpheColors.Teal.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Latest",
                        fontSize = if (isCompact) 9.sp else 10.sp,
                        color = MorpheColors.Teal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dynamic supported app card that uses SupportedApp data from patches.
 */
@Composable
private fun SupportedAppCardDynamic(
    supportedApp: SupportedApp,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showAllVersions by remember { mutableStateOf(false) }

    val cardPadding = if (isCompact) 12.dp else 16.dp

    val downloadUrl = supportedApp.apkDownloadUrl

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App name
            Text(
                text = supportedApp.displayName,
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))

            // Recommended version badge (dynamic from patches)
            if (supportedApp.recommendedVersion != null) {
                val cornerRadius = if (isCompact) 6.dp else 8.dp
                Surface(
                    color = MorpheColors.Teal.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(cornerRadius),
                    modifier = Modifier
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable { showAllVersions = !showAllVersions }
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 10.dp else 12.dp,
                            vertical = if (isCompact) 6.dp else 8.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Recommended",
                            fontSize = if (isCompact) 9.sp else 10.sp,
                            color = MorpheColors.Teal.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "v${supportedApp.recommendedVersion}",
                            fontSize = if (isCompact) 12.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MorpheColors.Teal
                        )
                        // Show version count if more than 1 (excluding recommended)
                        val otherVersionsCount = supportedApp.supportedVersions.count { it != supportedApp.recommendedVersion }
                        if (otherVersionsCount > 0) {
                            Text(
                                text = if (showAllVersions) "▲ Hide versions" else "▼ +$otherVersionsCount more",
                                fontSize = if (isCompact) 9.sp else 10.sp,
                                color = MorpheColors.Teal.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Expandable versions list (excluding recommended version)
                val otherVersions = supportedApp.supportedVersions.filter { it != supportedApp.recommendedVersion }
                if (showAllVersions && otherVersions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Other supported versions:",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Show versions in a compact grid-like format
                            val versionsText = otherVersions.joinToString(", ") { "v$it" }
                            Text(
                                text = versionsText,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            } else {
                // No specific version recommended
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp)
                ) {
                    Text(
                        text = "Any version",
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 10.dp else 12.dp,
                            vertical = if (isCompact) 6.dp else 8.dp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

            // Download from APKMirror button (only if URL is configured)
            if (downloadUrl != null) {
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = {
                        openUrlAndFollowRedirects(downloadUrl) { urlResolved ->
                            uriHandler.openUri(urlResolved)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp),
                    contentPadding = PaddingValues(
                        horizontal = if (isCompact) 8.dp else 12.dp,
                        vertical = if (isCompact) 6.dp else 8.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MorpheColors.Blue
                    )
                ) {
                    Text(
                        text = "Download original APK",
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))
            }

            // Package name
            Text(
                text = supportedApp.packageName,
                fontSize = if (isCompact) 9.sp else 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SupportedAppCard(
    appType: AppType,
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardPadding = if (isCompact) 12.dp else 16.dp
    val iconSize = if (isCompact) 48.dp else 56.dp
    val iconInnerSize = if (isCompact) 32.dp else 40.dp

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(if (isCompact) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(if (isCompact) 10.dp else 12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = "${appType.displayName} icon",
                    modifier = Modifier.size(iconInnerSize)
                )
            }

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

            // App name
            Text(
                text = appType.displayName,
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))

            // Suggested version badge
            Surface(
                color = MorpheColors.Teal.copy(alpha = 0.15f),
                shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 10.dp else 12.dp,
                        vertical = if (isCompact) 6.dp else 8.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recommended",
                        fontSize = if (isCompact) 9.sp else 10.sp,
                        color = MorpheColors.Teal.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "v${appType.suggestedVersion}",
                        fontSize = if (isCompact) 12.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MorpheColors.Teal
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

            // Download from APKMirror button
            val downloadUrl = SupportedApp.getDownloadUrl(appType.packageName, appType.suggestedVersion)
            if (downloadUrl != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI(downloadUrl))
                        } catch (e: Exception) {
                            // Ignore errors
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp),
                    contentPadding = PaddingValues(
                        horizontal = if (isCompact) 8.dp else 12.dp,
                        vertical = if (isCompact) 6.dp else 8.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MorpheColors.Blue
                    )
                ) {
                    Text(
                        text = if (isCompact) "APKMirror" else "Get from APKMirror",
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 8.dp))
            }

            // Package name
            Text(
                text = appType.packageName,
                fontSize = if (isCompact) 9.sp else 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DragOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MorpheColors.Blue.copy(alpha = 0.15f),
                        MorpheColors.Blue.copy(alpha = 0.05f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Drop APK here",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MorpheColors.Blue
                )
            }
        }
    }
}

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK File", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") } }
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
