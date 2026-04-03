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
import androidx.compose.material.icons.filled.Settings
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
import app.morphe.gui.ui.components.DraggableHeaderArea
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
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

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
        DraggableHeaderArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f
                    )
                }
                .padding(
                    start = 12.dp + titleInsets.start,
                    end = 12.dp + titleInsets.end,
                    top = 8.dp + titleInsets.top,
                    bottom = 8.dp
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "SELECT PATCHES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = "${uiState.selectedCount} of ${uiState.totalCount} selected",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.3.sp,
                    lineHeight = 8.sp
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
        }

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
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Architecture selector — disabled for split APK bundles
                        val isBundleFormat = viewModel.getApkPath().lowercase().let { it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") }
                        val showArchSelector = !isBundleFormat &&
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

                    androidx.compose.foundation.VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(lazyListState)
                    )
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
    val hasOptions = patch.options.isNotEmpty()

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
                // Name + app chips on same line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = patch.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (patch.compatiblePackages.isNotEmpty()) {
                        val genericSegments = setOf("com", "org", "net", "android", "google", "apps", "app", "www")
                        patch.compatiblePackages.take(2).forEach { pkg ->
                            val displayName = pkg.displayName?.takeIf { it.isNotBlank() } ?: run {
                                val meaningful = pkg.name.split(".").filter { it !in genericSegments }
                                meaningful.takeLast(2).joinToString(" ")
                                    .replaceFirstChar { it.uppercase() }
                            }
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
            }

            // Gear button for options
            if (hasOptions) {
                val gearHover = remember { MutableInteractionSource() }
                val isGearHovered by gearHover.collectIsHoveredAsState()
                val gearBorder by animateColorAsState(
                    when {
                        showOptions -> MorpheColors.Teal.copy(alpha = 0.5f)
                        isGearHovered -> MorpheColors.Teal.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                    animationSpec = tween(150)
                )
                val gearBg by animateColorAsState(
                    if (showOptions) MorpheColors.Teal.copy(alpha = 0.08f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )

                // Wrapper box — no clip, allows badge to overflow
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gear button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .hoverable(gearHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, gearBorder, RoundedCornerShape(corners.small))
                            .background(gearBg, RoundedCornerShape(corners.small))
                            .clickable { showOptions = !showOptions },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure options",
                            tint = when {
                                showOptions -> MorpheColors.Teal
                                isGearHovered -> MorpheColors.Teal.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Options count badge — outside clip
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(18.dp)
                            .background(MorpheColors.Teal, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${patch.options.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = Color.White,
                            lineHeight = 9.sp
                        )
                    }
                }
            }
        }

        // Expandable options section
        if (hasOptions) {
            val optionDivider = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)

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
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
            app.morphe.gui.data.model.PatchOptionType.FILE -> {
                var localPath by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localPath != value) localPath = value
                }

                // Detect if this is an image file option from key/title
                val keyLower = option.key.lowercase() + " " + option.title.lowercase()
                val isImage = keyLower.contains("icon") || keyLower.contains("image") ||
                    keyLower.contains("logo") || keyLower.contains("banner") ||
                    keyLower.contains("png") || keyLower.contains("jpg")
                val fileFilterDesc = if (isImage) "Image files" else "All files"
                val fileExtensions = if (isImage) "png,jpg,jpeg,webp" else "*"

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    if (fieldFocused.value) MorpheColors.Teal.copy(alpha = 0.6f)
                    else MorpheColors.Teal.copy(alpha = 0.2f),
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Path text field
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (localPath.isEmpty()) {
                                Text(
                                    text = if (isImage) "Select image…" else "Select file…",
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = localPath,
                                onValueChange = { newPath ->
                                    localPath = newPath
                                    onValueChange(newPath)
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MorpheColors.Teal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { fieldFocused.value = it.isFocused }
                            )
                        }
                    }

                    // Browse button
                    val browseHover = remember { MutableInteractionSource() }
                    val isBrowseHovered by browseHover.collectIsHoveredAsState()
                    val browseBorder by animateColorAsState(
                        if (isBrowseHovered) MorpheColors.Teal.copy(alpha = 0.5f)
                        else MorpheColors.Teal.copy(alpha = 0.2f),
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .hoverable(browseHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, browseBorder, RoundedCornerShape(corners.small))
                            .clickable {
                                val dialog = FileDialog(null as java.awt.Frame?, fileFilterDesc, FileDialog.LOAD)
                                if (isImage) {
                                    // setFile pattern works on macOS; setFilenameFilter works on Linux/Windows
                                    dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                                    dialog.setFilenameFilter { _, name ->
                                        val lower = name.lowercase()
                                        lower.endsWith(".png") || lower.endsWith(".jpg") ||
                                            lower.endsWith(".jpeg") || lower.endsWith(".webp")
                                    }
                                }
                                dialog.isVisible = true
                                val selected = dialog.file
                                if (selected != null) {
                                    val fullPath = File(dialog.directory, selected).absolutePath
                                    localPath = fullPath
                                    onValueChange(fullPath)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BROWSE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (isBrowseHovered) MorpheColors.Teal else MorpheColors.Teal.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            else -> {
                var localText by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localText != value) localText = value
                }

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    if (fieldFocused.value) MorpheColors.Teal.copy(alpha = 0.6f)
                    else MorpheColors.Teal.copy(alpha = 0.2f),
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (localText.isEmpty()) {
                            Text(
                                text = option.default ?: option.type.name.lowercase(),
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = localText,
                            onValueChange = { newText ->
                                localText = newText
                                onValueChange(newText)
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MorpheColors.Teal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { fieldFocused.value = it.isFocused }
                        )
                    }
                }
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
