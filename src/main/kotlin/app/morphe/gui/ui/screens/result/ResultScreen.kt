package app.morphe.gui.ui.screens.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.repository.ConfigRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceStatus
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop
import java.io.File

/**
 * Screen showing the result of patching.
 */
data class ResultScreen(
    val outputPath: String
) : Screen {

    @Composable
    override fun Content() {
        ResultScreenContent(outputPath = outputPath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreenContent(outputPath: String) {
    val navigator = LocalNavigator.currentOrThrow
    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    // Cleanup state
    var hasTempFiles by remember { mutableStateOf(false) }
    var tempFilesSize by remember { mutableStateOf(0L) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var autoCleanupEnabled by remember { mutableStateOf(false) }

    // Check for temp files and auto-cleanup setting
    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        autoCleanupEnabled = config.autoCleanupTempFiles
        hasTempFiles = FileUtils.hasTempFiles()
        tempFilesSize = FileUtils.getTempDirSize()

        // Auto-cleanup if enabled
        if (autoCleanupEnabled && hasTempFiles) {
            FileUtils.cleanupAllTempDirs()
            hasTempFiles = false
            tempFilesCleared = true
            Logger.info("Auto-cleaned temp files after successful patching")
        }
    }

    // Install function
    fun installViaAdb() {
        val device = monitorState.selectedDevice ?: return
        scope.launch {
            isInstalling = true
            installError = null
            installProgress = "Installing on ${device.displayName}..."

            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = device.id,
                onProgress = { installProgress = it }
            )

            result.fold(
                onSuccess = {
                    installSuccess = true
                    installProgress = "Installation successful!"
                },
                onFailure = { exception ->
                    installError = (exception as? AdbException)?.message ?: exception.message ?: "Unknown error"
                }
            )

            isInstalling = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
        val scrollState = rememberScrollState()

        // Estimate content height for dynamic spacing
        val contentHeight = 600.dp // Approximate height of all content
        val extraSpace = (maxHeight - contentHeight).coerceAtLeast(0.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(32.dp)
        ) {
            // Add top spacing to center content on large screens
            Spacer(modifier = Modifier.height(extraSpace / 2))
            // Success icon
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MorpheColors.Teal,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Patching Complete!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your patched APK is ready",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Output file info card
                Card(
                    modifier = Modifier.widthIn(max = 500.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Output File",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = outputFile.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = outputFile.parent ?: "",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (outputFile.exists()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = formatFileSize(outputFile.length()),
                                fontSize = 13.sp,
                                color = MorpheColors.Teal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ADB Install Section
                if (monitorState.isAdbAvailable == true) {
                    AdbInstallSection(
                        devices = monitorState.devices,
                        selectedDevice = monitorState.selectedDevice,
                        isLoadingDevices = false,
                        isInstalling = isInstalling,
                        installProgress = installProgress,
                        installError = installError,
                        installSuccess = installSuccess,
                        onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                        onRefreshDevices = { },
                        onInstallClick = { installViaAdb() },
                        onRetryClick = {
                            installError = null
                            installSuccess = false
                            installViaAdb()
                        },
                        onDismissError = { installError = null }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Cleanup section
                if (hasTempFiles || tempFilesCleared) {
                    CleanupSection(
                        hasTempFiles = hasTempFiles,
                        tempFilesSize = tempFilesSize,
                        tempFilesCleared = tempFilesCleared,
                        autoCleanupEnabled = autoCleanupEnabled,
                        onCleanupClick = {
                            FileUtils.cleanupAllTempDirs()
                            hasTempFiles = false
                            tempFilesCleared = true
                            Logger.info("Manually cleaned temp files after patching")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

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
                            } catch (e: Exception) {
                                // Ignore errors
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Folder")
                    }

                    Button(
                        onClick = { navigator.popUntilRoot() },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorpheColors.Blue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Patch Another", fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Help text (only show when ADB is not available)
                if (monitorState.isAdbAvailable == false) {
                    Text(
                        text = "ADB not found. Install Android SDK Platform Tools to enable direct installation.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                } else if (monitorState.isAdbAvailable == null) {
                    Text(
                        text = "Checking for ADB...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // Bottom spacing to center content on large screens
                Spacer(modifier = Modifier.height(extraSpace / 2))
            }
        }

        // Top bar (device indicator + settings) in top-right corner
        TopBarRow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp),
            allowCacheClear = false
        )
    }
}

@Composable
private fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    isLoadingDevices: Boolean,
    isInstalling: Boolean,
    installProgress: String,
    installError: String?,
    installSuccess: Boolean,
    onDeviceSelected: (AdbDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit
) {
    Card(
        modifier = Modifier.widthIn(max = 500.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                installSuccess -> MorpheColors.Teal.copy(alpha = 0.1f)
                installError != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = MorpheColors.Blue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Install via ADB",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
                // Refresh button
                IconButton(
                    onClick = onRefreshDevices,
                    enabled = !isLoadingDevices && !isInstalling
                ) {
                    if (isLoadingDevices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh devices",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                installSuccess -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MorpheColors.Teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Installed successfully on ${selectedDevice?.displayName ?: "device"}!",
                            fontWeight = FontWeight.Medium,
                            color = MorpheColors.Teal
                        )
                    }
                }

                installError != null -> {
                    Text(
                        text = installError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onDismissError) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRetryClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }

                isInstalling -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MorpheColors.Blue
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = installProgress.ifEmpty { "Installing..." },
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                else -> {
                    // Device list
                    val readyDevices = devices.filter { it.isReady }
                    val notReadyDevices = devices.filter { !it.isReady }

                    if (devices.isEmpty()) {
                        // No devices
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No devices connected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Connect your Android device via USB with USB debugging enabled",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Show device list
                        Text(
                            text = if (readyDevices.size == 1) "Connected device:" else "Select a device:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Ready devices
                        readyDevices.forEach { device ->
                            DeviceRow(
                                device = device,
                                isSelected = selectedDevice?.id == device.id,
                                onClick = { onDeviceSelected(device) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // Not ready devices (unauthorized/offline)
                        notReadyDevices.forEach { device ->
                            DeviceRow(
                                device = device,
                                isSelected = false,
                                onClick = { },
                                enabled = false
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Install button
                        Button(
                            onClick = onInstallClick,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedDevice != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorpheColors.Teal
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (selectedDevice != null)
                                    "Install on ${selectedDevice.displayName}"
                                else
                                    "Select a device to install",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupSection(
    hasTempFiles: Boolean,
    tempFilesSize: Long,
    tempFilesCleared: Boolean,
    autoCleanupEnabled: Boolean,
    onCleanupClick: () -> Unit
) {
    Card(
        modifier = Modifier.widthIn(max = 500.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tempFilesCleared)
                MorpheColors.Teal.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tempFilesCleared) "Temp files cleaned" else "Temporary files",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = if (tempFilesCleared)
                        MorpheColors.Teal
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        tempFilesCleared && autoCleanupEnabled -> "Auto-cleanup is enabled"
                        tempFilesCleared -> "Freed up ${formatFileSize(tempFilesSize)}"
                        else -> "${formatFileSize(tempFilesSize)} can be freed"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasTempFiles && !tempFilesCleared) {
                OutlinedButton(
                    onClick = onCleanupClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Clean up", fontSize = 13.sp)
                }
            } else if (tempFilesCleared) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MorpheColors.Teal,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: AdbDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> MorpheColors.Teal
                !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected)
                MorpheColors.Teal.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = when {
                    isSelected -> MorpheColors.Teal
                    device.isReady -> MorpheColors.Blue
                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                },
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
                Text(
                    text = device.id,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            // Status badge
            Surface(
                color = when (device.status) {
                    DeviceStatus.DEVICE -> MorpheColors.Teal.copy(alpha = 0.15f)
                    DeviceStatus.UNAUTHORIZED -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (device.status) {
                        DeviceStatus.DEVICE -> "Ready"
                        DeviceStatus.UNAUTHORIZED -> "Unauthorized"
                        DeviceStatus.OFFLINE -> "Offline"
                        DeviceStatus.UNKNOWN -> "Unknown"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = when (device.status) {
                        DeviceStatus.DEVICE -> MorpheColors.Teal
                        DeviceStatus.UNAUTHORIZED -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
