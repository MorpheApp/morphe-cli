package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.Patch
import org.koin.core.parameter.parametersOf
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DeviceMonitor
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Screen for selecting which patches to apply.
 * This screen is the one that selects which patch options need to be applied. Eg: Custom Branding, Spoof App Version, etc.
 */
data class PatchSelectionScreen(
    val apkPath: String,
    val apkName: String,
    val patchesFilePath: String,
    val apkArchitectures: List<String> = emptyList()
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchSelectionViewModel> {
            parametersOf(apkPath, apkName, patchesFilePath, apkArchitectures)
        }
        PatchSelectionScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchSelectionScreenContent(viewModel: PatchSelectionViewModel) {
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
            title = "Error Loading Patches",
            message = getFriendlyErrorMessage(currentError!!),
            errorType = getErrorType(currentError!!),
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
            onRetry = {
                showErrorDialog = false
                viewModel.clearError()
                viewModel.loadPatches()
            }
        )
    }

    // State for command preview
    var cleanMode by remember { mutableStateOf(false) }
    var showCommandPreview by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Select Patches", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${uiState.selectedCount} of ${uiState.totalCount} selected",
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
                    // Select all / Deselect all
                    TextButton(
                        onClick = {
                        if (uiState.selectedPatches.size == uiState.allPatches.size) {
                            viewModel.deselectAll()
                        } else {
                            viewModel.selectAll()
                        }
                    },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (uiState.selectedPatches.size == uiState.allPatches.size) "Deselect All" else "Select All",
                            color = MorpheColors.Blue
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Command preview toggle
                    if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
                        val isActive = showCommandPreview
                        Surface(
                            onClick = { showCommandPreview = !showCommandPreview },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isActive) MorpheColors.Teal.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isActive) MorpheColors.Teal.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Command Preview",
                                tint = if (isActive) MorpheColors.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    TopBarRow(allowCacheClear = false)

                    Spacer(Modifier.width(12.dp))
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
            // Command preview - collapsible via top bar button
            if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
                val commandPreview = remember(uiState.selectedPatches, uiState.selectedArchitectures, cleanMode) {
                    viewModel.getCommandPreview(cleanMode)
                }
                AnimatedVisibility(
                    visible = showCommandPreview,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    CommandPreview(
                        command = commandPreview,
                        cleanMode = cleanMode,
                        onToggleMode = { cleanMode = !cleanMode },
                        onCopy = {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(commandPreview), null)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                showOnlySelected = uiState.showOnlySelected,
                onShowOnlySelectedChange = { viewModel.setShowOnlySelected(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Info card about default-disabled patches
            val defaultDisabledCount = remember(uiState.allPatches) {
                viewModel.getDefaultDisabledCount()
            }
            var infoDismissed by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = defaultDisabledCount > 0 && !infoDismissed && !uiState.isLoading,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DefaultDisabledInfoCard(
                    count = defaultDisabledCount,
                    onDismiss = { infoDismissed = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                                text = "Loading patches...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.filteredPatches.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No patches match your search" else "No patches found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    // Patch list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Architecture selector at the top of the list
                        // Disabled for .apkm files until properly tested with merged APKs
                        val isApkm = viewModel.getApkPath().endsWith(".apkm", ignoreCase = true)
                        val showArchSelector = !isApkm &&
                                uiState.apkArchitectures.size > 1 &&
                                !(uiState.apkArchitectures.size == 1 && uiState.apkArchitectures[0] == "universal")
                        if (showArchSelector) {
                            item(key = "arch_selector") {
                                ArchitectureSelectorCard(
                                    architectures = uiState.apkArchitectures,
                                    selectedArchitectures = uiState.selectedArchitectures,
                                    onToggleArchitecture = { viewModel.toggleArchitecture(it) }
                                )
                            }
                        }

                        items(
                            items = uiState.filteredPatches,
                            key = { it.uniqueId }
                        ) { patch ->
                            PatchListItem(
                                patch = patch,
                                isSelected = uiState.selectedPatches.contains(patch.uniqueId),
                                onToggle = { viewModel.togglePatch(patch.uniqueId) }
                            )
                        }
                    }

                    // Bottom action bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val config = viewModel.createPatchConfig()
                                    navigator.push(PatchingScreen(config))
                                },
                                enabled = uiState.selectedPatches.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MorpheColors.Blue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Patch (${uiState.selectedCount})",
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

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showOnlySelected: Boolean,
    onShowOnlySelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search patches...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MorpheColors.Blue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        FilterChip(
            selected = showOnlySelected,
            onClick = { onShowOnlySelectedChange(!showOnlySelected) },
            label = { Text("Selected") },
            leadingIcon = if (showOnlySelected) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )
    }
}

@Composable
private fun PatchListItem(
    patch: Patch,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MorpheColors.Blue.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MorpheColors.Blue,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patch.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (patch.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = patch.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Show compatible packages if any
                if (patch.compatiblePackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        patch.compatiblePackages.take(2).forEach { pkg ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = pkg.name.substringAfterLast("."),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Show options if patch has any
                if (patch.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        patch.options.forEach { option ->
                            Surface(
                                color = MorpheColors.Teal.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = option.title.ifBlank { option.key },
                                    fontSize = 10.sp,
                                    color = MorpheColors.Teal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultDisabledInfoCard(
    count: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MorpheColors.Blue.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MorpheColors.Blue,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "$count patch${if (count > 1) "es are" else " is"} unselected by default as they may cause issues or are not recommended by the patches team.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Terminal-style command preview showing the CLI command that will be executed.
 */
@Composable
private fun CommandPreview(
    command: String,
    cleanMode: Boolean,
    onToggleMode: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terminalBackground = Color(0xFF1E1E1E)
    val terminalGreen = Color(0xFF6A9955)
    val terminalText = Color(0xFFD4D4D4)
    val terminalDim = Color(0xFF6A9955)

    var showCopied by remember { mutableStateOf(false) }

    // Reset "Copied!" message after a delay
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = terminalBackground),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header with terminal icon and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side - icon and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = terminalGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Command Preview",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = terminalGreen
                    )
                }

                // Right side - controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy button
                    Surface(
                        onClick = {
                            onCopy()
                            showCopied = true
                        },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = if (showCopied) terminalGreen else terminalDim,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (showCopied) "Copied!" else "Copy",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showCopied) terminalGreen else terminalDim
                            )
                        }
                    }

                    // Mode toggle
                    Surface(
                        onClick = onToggleMode,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (cleanMode) "Compact" else "Expand",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = terminalDim,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vertically scrollable command text with max height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = command,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = terminalText,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ArchitectureSelectorCard(
    architectures: List<String>,
    selectedArchitectures: Set<String>,
    onToggleArchitecture: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Get connected device architecture for hint
    val deviceState by DeviceMonitor.state.collectAsState()
    val deviceArch = deviceState.selectedDevice?.architecture

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MorpheColors.Teal.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MorpheColors.Teal,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Strip native libraries",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Uncheck architectures to remove from the output APK and reduce file size.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (deviceArch != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Your device: $deviceArch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MorpheColors.Teal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                architectures.forEach { arch ->
                    val isSelected = selectedArchitectures.contains(arch)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleArchitecture(arch) },
                        label = {
                            Text(
                                text = arch,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MorpheColors.Teal.copy(alpha = 0.2f),
                            selectedLabelColor = MorpheColors.Teal
                        )
                    )
                }
            }
        }
    }
}
