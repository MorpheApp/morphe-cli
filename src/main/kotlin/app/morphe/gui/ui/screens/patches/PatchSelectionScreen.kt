package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import app.morphe.gui.ui.components.LocalTitleBarInsets
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
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
    val packageName: String,
    val apkArchitectures: List<String> = emptyList()
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchSelectionViewModel> {
            parametersOf(apkPath, apkName, patchesFilePath, packageName, apkArchitectures)
        }
        PatchSelectionScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchSelectionScreenContent(viewModel: PatchSelectionViewModel) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
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

    var cleanMode by remember { mutableStateOf(false) }
    var showCommandPreview by remember { mutableStateOf(false) }
    var continueOnError by remember { mutableStateOf(false) }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header bar ──
        val titleInsets = LocalTitleBarInsets.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp + titleInsets.start,
                    end = 16.dp,
                    top = 12.dp + titleInsets.top,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            val backHover = remember { MutableInteractionSource() }
            val isBackHovered by backHover.collectIsHoveredAsState()
            val backBorder by animateColorAsState(
                if (isBackHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .hoverable(backHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, backBorder, RoundedCornerShape(corners.small))
                    .clickable { navigator.pop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECT PATCHES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "${uiState.selectedCount} of ${uiState.totalCount} selected",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.3.sp
                )
            }

            // Select/Deselect all
            val selectAllHover = remember { MutableInteractionSource() }
            val isSelectAllHovered by selectAllHover.collectIsHoveredAsState()
            val allSelected = uiState.selectedPatches.size == uiState.allPatches.size
            val selectAllBorder by animateColorAsState(
                if (isSelectAllHovered) MorpheColors.Blue.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .height(34.dp)
                    .hoverable(selectAllHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, selectAllBorder, RoundedCornerShape(corners.small))
                    .clickable {
                        if (allSelected) viewModel.deselectAll() else viewModel.selectAll()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (allSelected) "DESELECT ALL" else "SELECT ALL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    color = if (isSelectAllHovered) MorpheColors.Blue
                            else MorpheColors.Blue.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Command preview toggle
            if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
                val cmdHover = remember { MutableInteractionSource() }
                val isCmdHovered by cmdHover.collectIsHoveredAsState()
                val cmdActive = showCommandPreview
                val cmdBorder by animateColorAsState(
                    when {
                        cmdActive -> MorpheColors.Teal.copy(alpha = 0.5f)
                        isCmdHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    },
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(cmdHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, cmdBorder, RoundedCornerShape(corners.small))
                        .then(
                            if (cmdActive) Modifier.background(
                                MorpheColors.Teal.copy(alpha = 0.08f),
                                RoundedCornerShape(corners.small)
                            ) else Modifier
                        )
                        .clickable { showCommandPreview = !showCommandPreview },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Command Preview",
                        tint = if (cmdActive) MorpheColors.Teal
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Continue on error toggle
                val errHover = remember { MutableInteractionSource() }
                val isErrHovered by errHover.collectIsHoveredAsState()
                val errBorder by animateColorAsState(
                    when {
                        continueOnError -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        isErrHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    },
                    animationSpec = tween(150)
                )

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                "Continue patching even if a patch fails",
                                fontFamily = mono,
                                fontSize = 11.sp
                            )
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .hoverable(errHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, errBorder, RoundedCornerShape(corners.small))
                            .then(
                                if (continueOnError) Modifier.background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                    RoundedCornerShape(corners.small)
                                ) else Modifier
                            )
                            .clickable { continueOnError = !continueOnError },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistRemove,
                            contentDescription = "Continue on error",
                            tint = if (continueOnError) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
            }

            DeviceIndicator()
            Spacer(modifier = Modifier.width(6.dp))
            SettingsButton(allowCacheClear = false)
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )

        // Command preview — collapsible
        if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
            val commandPreview = remember(uiState.selectedPatches, uiState.selectedArchitectures, cleanMode, continueOnError) {
                viewModel.getCommandPreview(cleanMode, continueOnError)
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
        PatchSearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            showOnlySelected = uiState.showOnlySelected,
            onShowOnlySelectedChange = { viewModel.setShowOnlySelected(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MorpheColors.Blue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "LOADING PATCHES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
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
                        text = if (uiState.searchQuery.isNotBlank()) "No patches match your search"
                               else "No patches found",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Architecture selector
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
                            onToggle = { viewModel.togglePatch(patch.uniqueId) },
                            getOptionValue = { optionKey, default ->
                                viewModel.getOptionValue(patch.name, optionKey, default)
                            },
                            onOptionValueChange = { optionKey, value ->
                                viewModel.setOptionValue(patch.name, optionKey, value)
                            }
                        )
                    }
                }

                // ── Bottom action bar ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = dividerColor,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(16.dp)
                ) {
                    val patchHover = remember { MutableInteractionSource() }
                    val isPatchHovered by patchHover.collectIsHoveredAsState()
                    val patchEnabled = uiState.selectedPatches.isNotEmpty()
                    val patchBg by animateColorAsState(
                        when {
                            !patchEnabled -> MorpheColors.Blue.copy(alpha = 0.1f)
                            isPatchHovered -> MorpheColors.Blue.copy(alpha = 0.9f)
                            else -> MorpheColors.Blue
                        },
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .hoverable(patchHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(patchBg, RoundedCornerShape(corners.small))
                            .then(
                                if (patchEnabled) Modifier.clickable {
                                    val config = viewModel.createPatchConfig(continueOnError)
                                    navigator.push(PatchingScreen(config))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PATCH (${uiState.selectedCount})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (patchEnabled) Color.White
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Search Bar ──

@Composable
private fun PatchSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showOnlySelected: Boolean,
    onShowOnlySelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom compact search field
        val searchFocused = remember { mutableStateOf(false) }
        val searchBorderColor by animateColorAsState(
            if (searchFocused.value) MorpheColors.Blue.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            animationSpec = tween(150)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, searchBorderColor, RoundedCornerShape(corners.small))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Search patches…",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MorpheColors.Blue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { searchFocused.value = it.isFocused }
                )
            }

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // "Selected" filter chip
        val chipHover = remember { MutableInteractionSource() }
        val isChipHovered by chipHover.collectIsHoveredAsState()
        val chipBorder by animateColorAsState(
            when {
                showOnlySelected -> MorpheColors.Blue.copy(alpha = 0.5f)
                isChipHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .height(38.dp)
                .hoverable(chipHover)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, chipBorder, RoundedCornerShape(corners.small))
                .then(
                    if (showOnlySelected) Modifier.background(
                        MorpheColors.Blue.copy(alpha = 0.08f),
                        RoundedCornerShape(corners.small)
                    ) else Modifier
                )
                .clickable { onShowOnlySelectedChange(!showOnlySelected) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showOnlySelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MorpheColors.Blue,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "SELECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    color = if (showOnlySelected) MorpheColors.Blue
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Patch List Item ──

@Composable
private fun PatchListItem(
    patch: Patch,
    isSelected: Boolean,
    onToggle: () -> Unit,
    getOptionValue: (optionKey: String, default: String?) -> String = { _, d -> d ?: "" },
    onOptionValueChange: (optionKey: String, value: String) -> Unit = { _, _ -> }
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected && isHovered -> MorpheColors.Blue.copy(alpha = 0.4f)
            isSelected -> MorpheColors.Blue.copy(alpha = 0.2f)
            isHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        },
        animationSpec = tween(150)
    )

    var showOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .then(
                if (isSelected) Modifier.background(
                    MorpheColors.Blue.copy(alpha = 0.04f),
                    RoundedCornerShape(corners.small)
                ) else Modifier
            )
            .hoverable(interactionSource)
    ) {
        // Header — clicking toggles patch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom checkbox
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.5.dp,
                        if (isSelected) MorpheColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(corners.small)
                    )
                    .then(
                        if (isSelected) Modifier.background(MorpheColors.Blue, RoundedCornerShape(corners.small))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patch.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (patch.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = patch.description,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Compatible packages
                if (patch.compatiblePackages.isNotEmpty()) {
                    val genericSegments = setOf("com", "org", "net", "android", "google", "apps", "app", "www")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        patch.compatiblePackages.take(2).forEach { pkg ->
                            val meaningful = pkg.name.split(".").filter { it !in genericSegments }
                            val displayName = meaningful.takeLast(2).joinToString(" ")
                                .replaceFirstChar { it.uppercase() }
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        RoundedCornerShape(corners.small)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    fontSize = 9.sp,
                                    fontFamily = mono,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                }

                // Options indicator
                if (patch.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${patch.options.size} option${if (patch.options.size > 1) "s" else ""} ${if (showOptions) "▲" else "▼"}",
                        fontSize = 9.sp,
                        fontFamily = mono,
                        fontWeight = FontWeight.Medium,
                        color = MorpheColors.Teal.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Options section
        if (patch.options.isNotEmpty()) {
            val optionDivider = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)

            if (!showOptions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = optionDivider,
                                start = Offset(14.dp.toPx(), 0f),
                                end = Offset(size.width - 14.dp.toPx(), 0f),
                                strokeWidth = 1f
                            )
                        }
                        .clickable { showOptions = true }
                        .background(MorpheColors.Teal.copy(alpha = 0.03f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CONFIGURE OPTIONS",
                        fontSize = 9.sp,
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        color = MorpheColors.Teal.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = showOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = optionDivider,
                                start = Offset(14.dp.toPx(), 0f),
                                end = Offset(size.width - 14.dp.toPx(), 0f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Collapse button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(corners.small))
                            .background(MorpheColors.Teal.copy(alpha = 0.04f))
                            .clickable { showOptions = false }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "HIDE OPTIONS ▲",
                            fontSize = 9.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            color = MorpheColors.Teal.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                    }

                    patch.options.forEach { option ->
                        PatchOptionEditor(
                            option = option,
                            value = getOptionValue(option.key, option.default),
                            onValueChange = { onOptionValueChange(option.key, it) }
                        )
                    }
                }
            }
        }
    }
}

// ── Patch Option Editor ──

@Composable
private fun PatchOptionEditor(
    option: app.morphe.gui.data.model.PatchOption,
    value: String,
    onValueChange: (String) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.title.ifBlank { option.key },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = mono,
                color = MorpheColors.Teal
            )
            if (option.required) {
                Text(
                    text = "*",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (option.description.isNotBlank()) {
            Text(
                text = option.description,
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (option.type) {
            app.morphe.gui.data.model.PatchOptionType.BOOLEAN -> {
                var localChecked by remember(option.key) { mutableStateOf(value.equals("true", ignoreCase = true)) }
                LaunchedEffect(value) {
                    val v = value.equals("true", ignoreCase = true)
                    if (localChecked != v) localChecked = v
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = localChecked,
                        onCheckedChange = { newChecked ->
                            localChecked = newChecked
                            onValueChange(newChecked.toString())
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MorpheColors.Teal
                        )
                    )
                    Text(
                        text = if (localChecked) "Enabled" else "Disabled",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                var localText by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localText != value) localText = value
                }

                OutlinedTextField(
                    value = localText,
                    onValueChange = { newText ->
                        localText = newText
                        onValueChange(newText)
                    },
                    placeholder = {
                        Text(
                            text = option.default ?: option.type.name.lowercase(),
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        fontFamily = mono
                    ),
                    shape = RoundedCornerShape(corners.small),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MorpheColors.Teal.copy(alpha = 0.2f),
                        focusedBorderColor = MorpheColors.Teal.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

// ── Default Disabled Info Card ──

@Composable
private fun DefaultDisabledInfoCard(
    count: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MorpheColors.Blue.copy(alpha = 0.15f),
                RoundedCornerShape(corners.small)
            )
            .background(MorpheColors.Blue.copy(alpha = 0.04f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MorpheColors.Blue.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$count patch${if (count > 1) "es are" else " is"} unselected by default as they may cause issues.",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(corners.small))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ── Command Preview ──

@Composable
private fun CommandPreview(
    command: String,
    cleanMode: Boolean,
    onToggleMode: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    val terminalGreen = MorpheColors.Teal
    val terminalText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val terminalBg = MaterialTheme.colorScheme.surface

    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                terminalGreen.copy(alpha = 0.15f),
                RoundedCornerShape(corners.small)
            )
            .background(terminalBg)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = terminalGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "COMMAND PREVIEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = terminalGreen.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy button
                val copyHover = remember { MutableInteractionSource() }
                val isCopyHovered by copyHover.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .hoverable(copyHover)
                        .clip(RoundedCornerShape(corners.small))
                        .clickable {
                            onCopy()
                            showCopied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (showCopied) terminalGreen
                                   else terminalGreen.copy(alpha = if (isCopyHovered) 0.8f else 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (showCopied) "COPIED" else "COPY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (showCopied) terminalGreen
                                   else terminalGreen.copy(alpha = if (isCopyHovered) 0.8f else 0.4f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Mode toggle
                val modeHover = remember { MutableInteractionSource() }
                val isModeHovered by modeHover.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .hoverable(modeHover)
                        .clip(RoundedCornerShape(corners.small))
                        .clickable(onClick = onToggleMode)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (cleanMode) "COMPACT" else "EXPAND",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = terminalGreen.copy(alpha = if (isModeHovered) 0.8f else 0.4f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command text
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

// ── Architecture Selector ──

@Composable
private fun ArchitectureSelectorCard(
    architectures: List<String>,
    selectedArchitectures: Set<String>,
    onToggleArchitecture: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val deviceState by DeviceMonitor.state.collectAsState()
    val deviceArch = deviceState.selectedDevice?.architecture

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MorpheColors.Teal.copy(alpha = 0.15f),
                RoundedCornerShape(corners.small)
            )
            .background(MorpheColors.Teal.copy(alpha = 0.03f))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MorpheColors.Teal, RoundedCornerShape(1.dp))
            )
            Text(
                text = "STRIP NATIVE LIBRARIES",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Uncheck architectures to remove from the output APK and reduce file size.",
            fontSize = 10.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        if (deviceArch != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Your device's CPU architecture: $deviceArch",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = mono,
                color = MorpheColors.Teal.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            architectures.forEach { arch ->
                val isSelected = selectedArchitectures.contains(arch)
                val archHover = remember { MutableInteractionSource() }
                val isArchHovered by archHover.collectIsHoveredAsState()
                val archBorder by animateColorAsState(
                    when {
                        isSelected -> MorpheColors.Teal.copy(alpha = 0.4f)
                        isArchHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    },
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .hoverable(archHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, archBorder, RoundedCornerShape(corners.small))
                        .then(
                            if (isSelected) Modifier.background(
                                MorpheColors.Teal.copy(alpha = 0.08f),
                                RoundedCornerShape(corners.small)
                            ) else Modifier
                        )
                        .clickable { onToggleArchitecture(arch) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (isSelected) MorpheColors.Teal
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        Text(
                            text = arch,
                            fontSize = 11.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MorpheColors.Teal
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
