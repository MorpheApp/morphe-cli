package app.morphe.gui.ui.screens.patching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.PatchConfig
import org.koin.core.parameter.parametersOf
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.screens.result.ResultScreen
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop

/**
 * Screen showing patching progress with real-time logs.
 */
data class PatchingScreen(
    val config: PatchConfig
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchingViewModel> { parametersOf(config) }
        PatchingScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchingScreenContent(viewModel: PatchingViewModel) {
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()

    // Auto-start patching when screen loads
    LaunchedEffect(Unit) {
        viewModel.startPatching()
    }

    // Auto-scroll to bottom of logs
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    // Auto-navigate to result screen on successful completion
    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && uiState.outputPath != null) {
            // Small delay to let user see the success message
            kotlinx.coroutines.delay(1500)
            navigator.push(ResultScreen(outputPath = uiState.outputPath!!))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Patching", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = getStatusText(uiState.status),
                            style = MaterialTheme.typography.bodySmall,
                            color = getStatusColor(uiState.status)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        enabled = !uiState.isInProgress
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.canCancel) {
                        TextButton(
                            onClick = { viewModel.cancelPatching() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    TopBarRow(allowCacheClear = false)
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress indicator
            if (uiState.isInProgress) {
                Column {
                    if (uiState.hasProgress) {
                        // Show determinate progress when we have progress info
                        LinearProgressIndicator(
                            progress = { uiState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MorpheColors.Blue,
                        )
                        // Show progress text
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.currentPatch ?: "Applying patches...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${uiState.patchedCount}/${uiState.totalPatches}",
                                fontSize = 11.sp,
                                color = MorpheColors.Blue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Show indeterminate progress when we don't have progress info
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MorpheColors.Blue
                        )
                    }
                }
            }

            // Log output
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(uiState.logs, key = { it.id }) { entry ->
                    LogEntryRow(entry)
                }
            }

            // Bottom action bar (only for failed/cancelled - success auto-navigates)
            when (uiState.status) {
                PatchingStatus.COMPLETED -> {
                    // Show brief success message while auto-navigating
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MorpheColors.Teal
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Patching completed! Loading result...",
                                color = MorpheColors.Teal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                PatchingStatus.FAILED, PatchingStatus.CANCELLED -> {
                    FailureBottomBar(
                        status = uiState.status,
                        error = uiState.error,
                        onStartOver = { navigator.popUntilRoot() },
                        onGoBack = { navigator.pop() }
                    )
                }

                else -> {
                    // Show nothing for in-progress states
                }
            }
        }
    }
}

@Composable
private fun FailureBottomBar(
    status: PatchingStatus,
    error: String?,
    onStartOver: () -> Unit,
    onGoBack: () -> Unit
) {
    var tempFilesCleared by remember { mutableStateOf(false) }
    val hasTempFiles = remember { FileUtils.hasTempFiles() }
    val tempFilesSize = remember { FileUtils.getTempDirSize() }
    val logFile = remember { Logger.getLogFile() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Error message
            Text(
                text = if (status == PatchingStatus.CANCELLED)
                    "Patching was cancelled"
                else
                    error ?: "Patching failed",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Log file location
            if (logFile != null && logFile.exists()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Log file",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = logFile.absolutePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(logFile.parentFile)
                                }
                            } catch (e: Exception) {
                                Logger.error("Failed to open logs folder", e)
                            }
                        }
                    ) {
                        Text("Open", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Cleanup option
            if (hasTempFiles && !tempFilesCleared) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temporary files",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatFileSize(tempFilesSize)} can be freed",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(
                        onClick = {
                            FileUtils.cleanupAllTempDirs()
                            tempFilesCleared = true
                            Logger.info("Cleaned temp files after failed patching")
                        }
                    ) {
                        Text("Clean up", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            } else if (tempFilesCleared) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MorpheColors.Teal.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temp files cleaned",
                        fontSize = 12.sp,
                        color = MorpheColors.Teal
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onStartOver,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start Over")
                }
                Button(
                    onClick = onGoBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorpheColors.Blue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go Back", fontWeight = FontWeight.Medium)
                }
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

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.SUCCESS -> MorpheColors.Teal
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> Color(0xFFFF9800)
        LogLevel.PROGRESS -> MorpheColors.Blue
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val prefix = when (entry.level) {
        LogLevel.SUCCESS -> "[OK]"
        LogLevel.ERROR -> "[ERR]"
        LogLevel.WARNING -> "[WARN]"
        LogLevel.PROGRESS -> "[...]"
        LogLevel.INFO -> "[i]"
    }

    Text(
        text = "$prefix ${entry.message}",
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = color,
        lineHeight = 18.sp
    )
}

private fun getStatusText(status: PatchingStatus): String {
    return when (status) {
        PatchingStatus.IDLE -> "Ready"
        PatchingStatus.PREPARING -> "Preparing..."
        PatchingStatus.PATCHING -> "Patching in progress..."
        PatchingStatus.COMPLETED -> "Completed"
        PatchingStatus.FAILED -> "Failed"
        PatchingStatus.CANCELLED -> "Cancelled"
    }
}

@Composable
private fun getStatusColor(status: PatchingStatus): Color {
    return when (status) {
        PatchingStatus.COMPLETED -> MorpheColors.Teal
        PatchingStatus.FAILED -> MaterialTheme.colorScheme.error
        PatchingStatus.CANCELLED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
