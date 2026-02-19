package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.Release
import org.koin.core.parameter.parametersOf
import cafe.adriel.voyager.koin.koinScreenModel
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.theme.MorpheColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Screen for selecting patch version to apply.
 * This is the screen that selects the patches.mpp file
 */
data class PatchesScreen(
    val apkPath: String,
    val apkName: String
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchesViewModel> { parametersOf(apkPath, apkName) }
        PatchesScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()

    var showErrorDialog by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            currentError = error
            showErrorDialog = true
        }
    }

    // Error dialog
    if (showErrorDialog && currentError != null) {
        ErrorDialog(
            title = "Error",
            message = getFriendlyErrorMessage(currentError!!),
            errorType = getErrorType(currentError!!),
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
            onRetry = {
                showErrorDialog = false
                viewModel.clearError()
                viewModel.loadReleases()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Select Patches", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = viewModel.getApkName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    DeviceIndicator()
                    IconButton(
                        onClick = { viewModel.loadReleases() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    SettingsButton(allowCacheClear = true)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Channel selector (hidden when offline)
            if (!uiState.isOffline) {
                ChannelSelector(
                    selectedChannel = uiState.selectedChannel,
                    onChannelSelected = { viewModel.setChannel(it) },
                    stableCount = uiState.stableReleases.size,
                    devCount = uiState.devReleases.size,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Offline banner
            if (uiState.isOffline && uiState.currentReleases.isNotEmpty()) {
                OfflineBanner(
                    onRetry = { viewModel.loadReleases() },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                )
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MorpheColors.Blue)
                            Text(
                                text = "Fetching releases...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.currentReleases.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "No releases found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { viewModel.loadReleases() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    // Releases list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.currentReleases,
                            key = { it.tagName }
                        ) { release ->
                            ReleaseCard(
                                release = release,
                                isSelected = release.tagName == uiState.selectedRelease?.tagName,
                                isDownloaded = release.tagName in uiState.cachedReleaseVersions,
                                isOffline = uiState.isOffline,
                                onClick = { viewModel.selectRelease(release) }
                            )
                        }
                    }

                    // Bottom action bar
                    BottomActionBar(
                        uiState = uiState,
                        onDownloadClick = { viewModel.downloadPatches() },
                        onSelectClick = {
                            // Save the selected version to config before navigating back
                            viewModel.confirmSelection()
                            // Go back to HomeScreen - the new patches file is now cached
                            navigator.pop()
                        },
                        onExportJsonClick = {
                            val fileDialog = FileDialog(null as Frame?, "Export Options JSON", FileDialog.SAVE).apply {
                                file = "options.json"
                                isVisible = true
                            }
                            val directory = fileDialog.directory
                            val file = fileDialog.file
                            if (directory != null && file != null) {
                                viewModel.exportOptionsJson(File(directory, file))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSelector(
    selectedChannel: ReleaseChannel,
    onChannelSelected: (ReleaseChannel) -> Unit,
    stableCount: Int,
    devCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelChip(
            label = "Stable",
            count = stableCount,
            isSelected = selectedChannel == ReleaseChannel.STABLE,
            onClick = { onChannelSelected(ReleaseChannel.STABLE) },
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            label = "Dev",
            count = devCount,
            isSelected = selectedChannel == ReleaseChannel.DEV,
            onClick = { onChannelSelected(ReleaseChannel.DEV) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MorpheColors.Blue.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        MorpheColors.Blue
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MorpheColors.Blue else MaterialTheme.colorScheme.onSurface
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($count)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    onClick: () -> Unit
) {
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val accentColor = if (isSelected && isDownloaded) MorpheColors.Teal else MorpheColors.Blue
    val devBadgeColor = MorpheColors.Teal

    var isExpanded by remember { mutableStateOf(false) }
    val hasNotes = !release.body.isNullOrBlank()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardBackground = when {
        isSelected && isDownloaded -> MorpheColors.Teal.copy(alpha = if (isHovered) 0.22f else 0.15f)
        isSelected -> MorpheColors.Blue.copy(alpha = if (isHovered) 0.22f else 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isHovered) 0.7f else 0.25f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Green ribbon for downloaded (non-selected) cards
            if (isDownloaded && !isSelected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            MorpheColors.Teal,
                            RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = release.tagName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor
                        )
                        if (release.isDevRelease()) {
                            Surface(
                                color = devBadgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "DEV",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = devBadgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Show .mpp file info if available
                    release.assets.find { it.isMpp() }?.let { mppAsset ->
                        Text(
                            text = "${mppAsset.name} (${mppAsset.getFormattedSize()})",
                            fontSize = 13.sp,
                            color = subtitleColor
                        )
                    }

                    val formattedDate = formatDate(release.publishedAt)
                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text = "${if (isOffline) "Cached:" else "Published:"} $formattedDate",
                            fontSize = 12.sp,
                            color = dateColor
                        )
                    }

                    if (hasNotes) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = accentColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isExpanded = !isExpanded }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "Hide patch notes" else "Patch notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accentColor
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

            }

            // Expandable release notes
            if (isExpanded && hasNotes) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                FormattedReleaseNotes(
                    markdown = release.body.orEmpty(),
                    modifier = Modifier.padding(16.dp)
                )
            }
            }
        }
    }
}

/**
 * Renders GitHub release notes markdown as formatted Compose text.
 */
@Composable
private fun FormattedReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val lines = parseMarkdown(markdown)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            when (line) {
                is MdLine.Header -> Text(
                    text = line.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is MdLine.SubHeader -> Text(
                    text = line.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is MdLine.Bullet -> {
                    Row {
                        Text(
                            text = "\u2022  ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = line.text,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
                is MdLine.Plain -> Text(
                    text = line.text,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private sealed class MdLine {
    data class Header(val text: String) : MdLine()
    data class SubHeader(val text: String) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

private fun parseMarkdown(markdown: String): List<MdLine> {
    return markdown.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("# ")))
                trimmed.startsWith("## ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("## ")))
                trimmed.startsWith("### ") -> MdLine.SubHeader(cleanMarkdown(trimmed.removePrefix("### ")))
                trimmed.startsWith("* ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("* ")))
                trimmed.startsWith("- ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("- ")))
                else -> MdLine.Plain(cleanMarkdown(trimmed))
            }
        }
}

/**
 * Strip markdown syntax to plain readable text:
 * - **bold** → bold
 * - [text](url) → text
 * - ([hash](url)) → remove entirely (commit refs)
 */
private fun cleanMarkdown(text: String): String {
    var result = text
    // Remove commit refs like ([abc1234](https://...))
    result = result.replace(Regex("""\(\[[\da-f]{7,}]\([^)]*\)\)"""), "")
    // [text](url) → text
    result = result.replace(Regex("""\[([^\]]*?)]\([^)]*\)"""), "$1")
    // **bold** → bold
    result = result.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    // Clean up extra whitespace
    result = result.replace(Regex("""\s+"""), " ").trim()
    return result
}

@Composable
private fun BottomActionBar(
    uiState: PatchesUiState,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    onExportJsonClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Download progress
            if (uiState.isDownloading) {
                LinearProgressIndicator(
                    progress = { uiState.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MorpheColors.Blue,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloading patches...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download button
                if (uiState.downloadedPatchFile == null) {
                    Button(
                        onClick = onDownloadClick,
                        enabled = uiState.selectedRelease != null && !uiState.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorpheColors.Blue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (uiState.isDownloading) "Downloading..." else "Download Patches",
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Select button (patches downloaded)
                    Button(
                        onClick = onSelectClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorpheColors.Teal
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Select",
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Export JSON button / spinner
                    if (uiState.isExporting) {
                        Box(
                            modifier = Modifier.height(48.dp).width(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MorpheColors.Blue,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onExportJsonClick,
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MorpheColors.Blue
                            ),
                        ) {
                            Text(
                                text = "Export JSON",
                                fontWeight = FontWeight.Medium,
                                color = MorpheColors.Blue
                            )
                        }
                    }
                }
            }

        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        // Takes "2024-01-15T10:30:00Z" and returns "Jan 15, 2024 at 10:30 AM"
        val datePart = isoDate.substringBefore("T")
        val timePart = isoDate.substringAfter("T").substringBefore("Z").substringBefore("+")
        val parts = datePart.split("-")
        if (parts.size == 3) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val month = months.getOrElse(parts[1].toInt() - 1) { "???" }
            val day = parts[2].toInt()
            val year = parts[0]
            val timeParts = timePart.split(":")
            val timeStr = if (timeParts.size >= 2) {
                val hour = timeParts[0].toInt()
                val minute = timeParts[1]
                val amPm = if (hour >= 12) "PM" else "AM"
                val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                " at $hour12:$minute $amPm UTC"
            } else ""
            "$month $day, $year$timeStr"
        } else {
            datePart
        }
    } catch (e: Exception) {
        isoDate
    }
}
