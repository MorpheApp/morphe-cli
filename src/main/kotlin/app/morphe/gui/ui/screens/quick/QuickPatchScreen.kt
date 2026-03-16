package app.morphe.gui.ui.screens.quick

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import app.morphe.morphe_cli.generated.resources.Res
import app.morphe.morphe_cli.generated.resources.morphe_dark
import app.morphe.morphe_cli.generated.resources.morphe_light
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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

@Composable
fun QuickPatchContent(viewModel: QuickPatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { files ->
            files.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) ||
                it.name.endsWith(".apkm", ignoreCase = true)
            }?.let { viewModel.onFileSelected(it) }
        },
        enabled = uiState.phase != QuickPatchPhase.ANALYZING
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Branding ──
                Spacer(modifier = Modifier.height(8.dp))
                BrandingHeader(patchesVersion = uiState.patchesVersion, isLoading = uiState.isLoadingPatches)

                Spacer(modifier = Modifier.height(16.dp))

                // Offline banner
                if (uiState.isOffline && uiState.phase == QuickPatchPhase.IDLE) {
                    OfflineBanner(
                        onRetry = { viewModel.retryLoadPatches() },
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

            // Top-right: device indicator + settings
            TopBarRow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            )

            // Drag overlay
            if (uiState.isDragHovering) {
                DragOverlay()
            }

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
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(LocalMorpheCorners.current.small)
                ) {
                    Text(error)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  BRANDING — Logo + patches version badge
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BrandingHeader(patchesVersion: String?, isLoading: Boolean) {
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

    Spacer(modifier = Modifier.height(4.dp))

    if (isLoading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MorpheColors.Blue
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Loading patches…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    } else if (patchesVersion != null) {
        Text(
            text = "Patches $patchesVersion",
            fontSize = 12.sp,
            color = MorpheColors.Blue.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
    } else {
        Text(
            text = "Quick Patch",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  IDLE — Simple drop zone
// ════════════════════════════════════════════════════════════════════

@Composable
private fun IdleContent(
    isAnalyzing: Boolean,
    isDragHovering: Boolean,
    onBrowse: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val bracketColor = if (isDragHovering) MorpheColors.Blue.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = !isAnalyzing) { onBrowse() }
            .drawBehind {
                val strokeWidth = 2f
                val len = 32.dp.toPx()
                val inset = 0f

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
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MorpheColors.Blue,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing APK…",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = if (isDragHovering) MorpheColors.Blue
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Drop APK here",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDragHovering) MorpheColors.Blue
                           else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "or click to browse",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ".apk  ·  .apkm",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  READY — Compact APK card + patch button
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ReadyContent(
    apkInfo: QuickApkInfo,
    onPatch: () -> Unit,
    onClear: () -> Unit
) {
    val corners = LocalMorpheCorners.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Simple APK info card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(corners.medium),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App initial
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(MorpheColors.Blue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = apkInfo.displayName.first().uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorpheColors.Blue
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v${apkInfo.versionName} · ${apkInfo.formattedSize}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Checksum badge
                when (apkInfo.checksumStatus) {
                    is ChecksumStatus.Verified -> {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = MorpheColors.Teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    is ChecksumStatus.Mismatch -> {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Mismatch",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Version status (only if noteworthy)
        val statusText = when {
            apkInfo.checksumStatus is ChecksumStatus.Verified ->
                "Recommended version · Verified"
            apkInfo.checksumStatus is ChecksumStatus.Mismatch ->
                "Checksum mismatch — re-download from APKMirror"
            !apkInfo.isRecommendedVersion && apkInfo.recommendedVersion != null ->
                "Recommended: v${apkInfo.recommendedVersion}"
            apkInfo.isRecommendedVersion ->
                "Recommended version"
            else -> null
        }
        val statusColor = when {
            apkInfo.checksumStatus is ChecksumStatus.Verified -> MorpheColors.Teal
            apkInfo.checksumStatus is ChecksumStatus.Mismatch -> MaterialTheme.colorScheme.error
            !apkInfo.isRecommendedVersion && apkInfo.recommendedVersion != null -> Color(0xFFFF9800)
            else -> MorpheColors.Teal
        }

        if (statusText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Patch button
        Button(
            onClick = onPatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MorpheColors.Blue),
            shape = RoundedCornerShape(corners.medium)
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Patch with Defaults",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Uses latest patches with recommended settings",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

// ════════════════════════════════════════════════════════════════════
//  PATCHING — Progress
// ════════════════════════════════════════════════════════════════════

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
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.dp,
            color = MorpheColors.Teal
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (phase) {
                QuickPatchPhase.DOWNLOADING -> "Preparing…"
                QuickPatchPhase.PATCHING -> "Patching…"
                else -> ""
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel", color = MaterialTheme.colorScheme.error)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  COMPLETED — Success
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CompletedContent(
    outputPath: String,
    apkInfo: QuickApkInfo,
    onPatchAnother: () -> Unit
) {
    val corners = LocalMorpheCorners.current
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
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Patching Complete!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = outputFile.name,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (outputFile.exists()) {
            Text(
                text = formatFileSize(outputFile.length()),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MorpheColors.Teal
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    try {
                        val folder = outputFile.parentFile
                        if (folder != null && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(folder)
                        }
                    } catch (_: Exception) {}
                },
                shape = RoundedCornerShape(corners.small)
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Folder")
            }

            Button(
                onClick = onPatchAnother,
                colors = ButtonDefaults.buttonColors(containerColor = MorpheColors.Blue),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text("Patch Another")
            }
        }

        // ADB install
        if (monitorState.isAdbAvailable == true) {
            Spacer(modifier = Modifier.height(16.dp))

            val readyDevices = monitorState.devices.filter { it.isReady }
            val selectedDevice = monitorState.selectedDevice

            if (installSuccess) {
                Surface(
                    color = MorpheColors.Teal.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(corners.small)
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
                        text = "Installing…",
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
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Install on ${device.displayName}")
                }
            } else {
                Text(
                    text = "Connect your device via USB to install with ADB",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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

// ════════════════════════════════════════════════════════════════════
//  SUPPORTED APPS — Simple row at the bottom
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SupportedAppsRow(
    supportedApps: List<SupportedApp>,
    isLoading: Boolean,
    loadError: String? = null,
    isDefaultSource: Boolean = true,
    onRetry: () -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDefaultSource) "Download original APK" else "Supported apps",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
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
                        text = "Loading supported apps…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            loadError != null || supportedApps.isEmpty() -> {
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
                        shape = RoundedCornerShape(corners.small),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Retry", fontSize = 12.sp)
                    }
                }
            }
            else -> {
                // Search bar for many apps
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
                            Text("Search apps…", style = MaterialTheme.typography.bodySmall)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear, "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(corners.small),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MorpheColors.Blue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Horizontal scrolling cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .height(IntrinsicSize.Max)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredApps.forEach { app ->
                        val url = app.apkDownloadUrl
                        val hoverInteraction = remember { MutableInteractionSource() }
                        val isHovered by hoverInteraction.collectIsHoveredAsState()
                        val borderColor by animateColorAsState(
                            if (isHovered) MorpheColors.Blue.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            animationSpec = tween(200)
                        )

                        Surface(
                            modifier = Modifier
                                .width(170.dp)
                                .fillMaxHeight()
                                .hoverable(hoverInteraction)
                                .then(
                                    if (isDefaultSource && url != null) {
                                        Modifier.clickable {
                                            openUrlAndFollowRedirects(url) { resolved ->
                                                uriHandler.openUri(resolved)
                                            }
                                        }
                                    } else Modifier
                                ),
                            shape = RoundedCornerShape(corners.small),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = app.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (app.recommendedVersion != null) {
                                    Text(
                                        text = "v${app.recommendedVersion}",
                                        fontSize = 11.sp,
                                        color = MorpheColors.Teal,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "Any version",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                if (isDefaultSource && url != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Download ↗",
                                        fontSize = 10.sp,
                                        color = MorpheColors.Blue.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Medium
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

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(
                width = 2.dp,
                color = MorpheColors.Blue.copy(alpha = 0.5f),
                shape = RoundedCornerShape(0.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MorpheColors.Blue
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Drop APK here",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MorpheColors.Blue
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
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") } }
        isVisible = true
    }
    val directory = fileDialog.directory
    val file = fileDialog.file
    return if (directory != null && file != null) File(directory, file) else null
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
