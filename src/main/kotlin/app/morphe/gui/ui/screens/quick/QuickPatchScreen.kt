package app.morphe.gui.ui.screens.quick

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import androidx.compose.foundation.isSystemInDarkTheme
import app.morphe.morphe_cli.generated.resources.Res
import app.morphe.morphe_cli.generated.resources.morphe_dark
import app.morphe.morphe_cli.generated.resources.morphe_light
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.util.PatchService
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.theme.MorpheColors
import androidx.compose.runtime.rememberCoroutineScope
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import kotlinx.coroutines.launch
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.awt.FileDialog
import java.awt.Frame

/**
 * Quick Patch Mode - Single screen simplified patching.
 */
class QuickPatchScreen : Screen {
    @Composable
    override fun Content() {
        val patchSourceManager: PatchSourceManager = koinInject()
        val patchService: PatchService = koinInject()
        val configRepository: ConfigRepository = koinInject()

        val viewModel = remember {
            QuickPatchViewModel(patchSourceManager, patchService, configRepository)
        }

        QuickPatchContent(viewModel)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickPatchContent(viewModel: QuickPatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Compose drag and drop target
    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                viewModel.setDragHover(true)
            }

            override fun onEnded(event: DragAndDropEvent) {
                viewModel.setDragHover(false)
            }

            override fun onExited(event: DragAndDropEvent) {
                viewModel.setDragHover(false)
            }

            override fun onEntered(event: DragAndDropEvent) {
                viewModel.setDragHover(true)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                viewModel.setDragHover(false)
                val transferable = event.awtTransferable
                return try {
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        val apkFile = files.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) || it.name.endsWith(".apkm", ignoreCase = true) }
                        if (apkFile != null) {
                            viewModel.onFileSelected(apkFile)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dragAndDropTarget
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Branding
                Spacer(modifier = Modifier.height(8.dp))
                val themeState = LocalThemeState.current
                val isDark = when (themeState.current) {
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                    else -> themeState.current.isDark()
                }
                Image(
                    painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
                    contentDescription = "Morphe Logo",
                    modifier = Modifier.height(48.dp)
                )
                Text(
                    text = "Quick Patch",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Offline banner
                if (uiState.isOffline && uiState.phase == QuickPatchPhase.IDLE) {
                    OfflineBanner(
                        onRetry = { viewModel.retryLoadPatches() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                // Main content based on phase
                // Remember last valid data for safe animation transitions
                val lastApkInfo = remember(uiState.apkInfo) { uiState.apkInfo }
                val lastOutputPath = remember(uiState.outputPath) { uiState.outputPath }

                AnimatedContent(
                    targetState = uiState.phase,
                    modifier = Modifier.weight(1f)
                ) { phase ->
                    when (phase) {
                        QuickPatchPhase.IDLE, QuickPatchPhase.ANALYZING -> {
                            IdleContent(
                                isAnalyzing = phase == QuickPatchPhase.ANALYZING,
                                isDragHovering = uiState.isDragHovering,
                                error = uiState.error,
                                onFileSelected = { viewModel.onFileSelected(it) },
                                onDragHover = { viewModel.setDragHover(it) },
                                onClearError = { viewModel.clearError() }
                            )
                        }
                        QuickPatchPhase.READY -> {
                            // Use current or last known apkInfo to prevent crash during animation
                            val apkInfo = uiState.apkInfo ?: lastApkInfo
                            if (apkInfo != null) {
                                ReadyContent(
                                    apkInfo = apkInfo,
                                    error = uiState.error,
                                    onPatch = { viewModel.startPatching() },
                                    onClear = { viewModel.reset() },
                                    onClearError = { viewModel.clearError() }
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
                            val apkInfo = uiState.apkInfo ?: lastApkInfo
                            val outputPath = uiState.outputPath ?: lastOutputPath
                            if (apkInfo != null && outputPath != null) {
                                CompletedContent(
                                    outputPath = outputPath,
                                    apkInfo = apkInfo,
                                    onPatchAnother = { viewModel.reset() }
                                )
                            }
                        }
                    }
                }

                // Bottom app cards (only show in IDLE phase)
                if (uiState.phase == QuickPatchPhase.IDLE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SupportedAppsRow(
                        supportedApps = uiState.supportedApps,
                        isLoading = uiState.isLoadingPatches,
                        loadError = uiState.patchLoadError,
                        patchesVersion = uiState.patchesVersion,
                        isDefaultSource = uiState.isDefaultSource,
                        onOpenUrl = { url ->
                            openUrlAndFollowRedirects(url) { urlResolved ->
                                uriHandler.openUri(urlResolved)
                            }
                        },
                        onRetry = { viewModel.retryLoadPatches() }
                    )
                }
            }

            // Top bar (device indicator + settings) in top-right corner
            TopBarRow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            )

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    isAnalyzing: Boolean,
    isDragHovering: Boolean,
    error: String?,
    onFileSelected: (File) -> Unit,
    onDragHover: (Boolean) -> Unit,
    onClearError: () -> Unit
) {
    val dropZoneColor = when {
        isDragHovering -> MorpheColors.Blue.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val borderColor = when {
        isDragHovering -> MorpheColors.Blue
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(dropZoneColor)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isAnalyzing) {
                openFilePicker()?.let { onFileSelected(it) }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MorpheColors.Blue,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing APK...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isDragHovering) MorpheColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Drop APK here",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "or click to browse",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    apkInfo: QuickApkInfo,
    error: String?,
    onPatch: () -> Unit,
    onClear: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // APK Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon: first letter of display name
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = apkInfo.displayName.first().toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorpheColors.Blue
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v${apkInfo.versionName} • ${apkInfo.formattedSize}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Checksum status
                when (apkInfo.checksumStatus) {
                    is ChecksumStatus.Verified -> {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = MorpheColors.Teal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is ChecksumStatus.Mismatch -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Checksum mismatch",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Verification status banner
        VerificationStatusBanner(
            checksumStatus = apkInfo.checksumStatus,
            isRecommendedVersion = apkInfo.isRecommendedVersion,
            currentVersion = apkInfo.versionName,
            suggestedVersion = apkInfo.recommendedVersion ?: "Unknown"
        )

        Spacer(modifier = Modifier.weight(1f))

        // Patch button
        Button(
            onClick = onPatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MorpheColors.Blue
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Patch with Defaults",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Uses latest patches with recommended settings",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PatchingContent(
    phase: QuickPatchPhase,
    statusMessage: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp,
            color = MorpheColors.Teal
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (phase) {
                QuickPatchPhase.DOWNLOADING -> "Preparing..."
                QuickPatchPhase.PATCHING -> "Patching..."
                else -> ""
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CompletedContent(
    outputPath: String,
    apkInfo: QuickApkInfo,
    onPatchAnother: () -> Unit
) {
    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val monitorState by DeviceMonitor.state.collectAsState()
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = MorpheColors.Teal,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Patching Complete!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = outputFile.name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (outputFile.exists()) {
            Text(
                text = formatFileSize(outputFile.length()),
                fontSize = 13.sp,
                color = MorpheColors.Teal
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    try {
                        val folder = outputFile.parentFile
                        if (folder != null && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(folder)
                        }
                    } catch (e: Exception) { }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Folder")
            }

            Button(
                onClick = onPatchAnother,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorpheColors.Blue
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Patch Another")
            }
        }

        if (monitorState.isAdbAvailable == true) {
            Spacer(modifier = Modifier.height(16.dp))

            val readyDevices = monitorState.devices.filter { it.isReady }
            val selectedDevice = monitorState.selectedDevice

            if (installSuccess) {
                Surface(
                    color = MorpheColors.Teal.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Installed successfully!",
                        fontSize = 13.sp,
                        color = MorpheColors.Teal,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else if (isInstalling) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MorpheColors.Blue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Installing...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (readyDevices.isNotEmpty()) {
                val device = selectedDevice ?: readyDevices.first()
                Button(
                    onClick = {
                        scope.launch {
                            isInstalling = true
                            installError = null
                            val result = adbManager.installApk(
                                apkPath = outputPath,
                                deviceId = device.id
                            )
                            result.fold(
                                onSuccess = { installSuccess = true },
                                onFailure = { installError = it.message }
                            )
                            isInstalling = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MorpheColors.Teal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install on ${device.displayName}")
                }
            } else {
                Text(
                    text = "Connect your device via USB to install with ADB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            installError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SupportedAppsRow(
    supportedApps: List<app.morphe.gui.data.model.SupportedApp>,
    isLoading: Boolean,
    loadError: String? = null,
    patchesVersion: String?,
    isDefaultSource: Boolean = true,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDefaultSource) "Download original APK" else "Supported apps",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (patchesVersion != null) {
                Text(
                    text = "Patches: $patchesVersion",
                    fontSize = 11.sp,
                    color = MorpheColors.Blue.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            // Loading state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MorpheColors.Blue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Loading supported apps...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (loadError != null || supportedApps.isEmpty()) {
            // Error or no apps loaded
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = loadError ?: "Could not load supported apps",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Retry", fontSize = 12.sp)
                }
            }
        } else {
            // Search bar for many apps
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
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
                            "Search apps...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MorpheColors.Blue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isDefaultSource) {
                // Default source: show clickable download cards with fixed width
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(IntrinsicSize.Max)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    filteredApps.forEach { app ->
                        val url = app.apkDownloadUrl
                        if (url != null) {
                            OutlinedCard(
                                onClick = { onOpenUrl(url) },
                                modifier = Modifier.width(180.dp).fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        app.recommendedVersion?.let { version ->
                                            Text(
                                                text = "v$version",
                                                fontSize = 10.sp,
                                                color = MorpheColors.Teal
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Custom source: show app names and versions in a scrollable row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(IntrinsicSize.Max)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredApps.forEach { app ->
                        OutlinedCard(
                            modifier = Modifier.width(160.dp).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = app.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                app.recommendedVersion?.let { version ->
                                    Text(
                                        text = "v$version",
                                        fontSize = 10.sp,
                                        color = MorpheColors.Teal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows verification status (version + checksum) in a compact banner.
 */
@Composable
private fun VerificationStatusBanner(
    checksumStatus: ChecksumStatus,
    isRecommendedVersion: Boolean,
    currentVersion: String,
    suggestedVersion: String
) {
    when {
        // Recommended version with verified checksum
        checksumStatus is ChecksumStatus.Verified -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MorpheColors.Teal.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MorpheColors.Teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Recommended version • Verified",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MorpheColors.Teal
                        )
                        Text(
                            text = "Checksum matches APKMirror",
                            fontSize = 11.sp,
                            color = MorpheColors.Teal.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Checksum mismatch - warning
        checksumStatus is ChecksumStatus.Mismatch -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Checksum mismatch",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "File may be corrupted. Re-download from APKMirror.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Recommended version but no checksum configured
        isRecommendedVersion && checksumStatus is ChecksumStatus.NotConfigured -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MorpheColors.Teal.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MorpheColors.Teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Using recommended version",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MorpheColors.Teal
                    )
                }
            }
        }

        // Non-recommended version (older or newer)
        !isRecommendedVersion -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF9800).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Version $currentVersion",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF9800)
                        )
                        Text(
                            text = "Recommended: v$suggestedVersion. Patching may have issues.",
                            fontSize = 11.sp,
                            color = Color(0xFFFF9800).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Checksum error
        checksumStatus is ChecksumStatus.Error -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF9800).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recommended version (checksum unavailable)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

/**
 * Open native file picker.
 */
private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") } }
        isVisible = true
    }

    val directory = fileDialog.directory
    val file = fileDialog.file

    return if (directory != null && file != null) {
        File(directory, file)
    } else null
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
