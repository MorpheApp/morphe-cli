/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.components.onCardGradient
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.screens.home.BundleChoice
import app.morphe.gui.ui.screens.home.ActivePatchSource
import app.morphe.gui.ui.screens.home.BundleRelease
import app.morphe.gui.ui.screens.home.BundleSupport
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.components.MorpheSwitch
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.Frame
import java.io.File

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.shiftLightness
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which list the home pane is showing: all supported apps, or only patched ("yours"). */
enum class AppListFilter { ALL, YOURS }

/**
 * Segmented filter at the top of the apps pane: ALL APPS · YOUR APPS. Replaces the
 * old static "SUPPORTED APPS" header. The "Your apps" tab carries a count badge so
 * the history is discoverable even before it's selected.
 */
@Composable
fun AppListFilterChips(
    filter: AppListFilter,
    onSelect: (AppListFilter) -> Unit,
    allCount: Int,
    yourCount: Int,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
    ) {
        FilterChip(
            label = "All apps",
            count = if (allCount > 0) allCount else null,
            selected = filter == AppListFilter.ALL,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.ALL) },
        )
        FilterChip(
            label = "Your apps",
            count = if (yourCount > 0) yourCount else null,
            selected = filter == AppListFilter.YOURS,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.YOURS) },
        )
    }
}

/**
 * On-open update notice (Phase 7 QoL, mirrors Manager). Shown above the apps list
 * when one or more patched apps have a newer app version or patch-source version
 * available. Tapping jumps to the "Your apps" list where each is badged.
 */
@Composable
fun PatchedUpdatesBanner(count: Int, onView: () -> Unit) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val hover = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onView)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(MorpheIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(15.dp))
        Text(
            text = if (count == 1) "1 patched app has an update available"
                   else "$count patched apps have updates available",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text("View", fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    font: FontFamily,
    corner: Dp,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val border by animateColorAsState(
        when {
            selected -> accent.copy(alpha = 0.6f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        tween(150), label = "chip",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .border(1.dp, border, RoundedCornerShape(corner))
            .background(if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = font,
            color = if (selected) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count != null) {
            Text(
                text = count.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact summary row for the "Your apps" list. One per [PatchedAppRecord].
 * Tapping opens [PatchedAppDetailDialog] for the full breakdown.
 */
@Composable
fun YourAppRow(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    onClick: () -> Unit,
    onInstall: () -> Unit = {},
    installing: Boolean = false,
    appIconColorHex: String? = null,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val initial = record.displayName.firstOrNull()?.uppercase() ?: "?"

    val cardFills = LocalCardFills.current

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = corners.medium,
        appIconColorHex = appIconColorHex,
        fill = cardFills[record.packageName],
        onClick = onClick,
        onCustomise = {
            cardFills.requestEdit(record.packageName, record.displayName, appIconColorHex)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = font, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${record.apkVersion.removePrefix("v")} · ${relativeOrShortDate(record.patchedAt)}",
                    fontSize = 10.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MiniBadge("Install ready", MorpheColors.Teal, font, onGradient = true)
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state, font)
            }
        }
        deviceInfo?.let { DeviceLine(it, font, Color.White.copy(alpha = 0.5f), Color.White) }
        // Patch source + version, with "→ vNew" when a newer patch file is available.
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                font = font,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        // Support status only. Which version to move to is the APK section's job,
        // and naming one here just duplicated that list in prose.
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null) {
            Text(
                text = cardAdvice.first,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            Text(
                text = "ⓘ Newer patch may bump the app - tap Update to check",
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Already-patched APK is newer than what's on the device → offer to install
        // it directly (no re-patch needed). Streams away once the device catches up.
        if (deviceInfo?.installPending == true) {
            Text(
                text = if (deviceInfo.installed)
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready - device on v${deviceInfo.installedVersion?.removePrefix("v") ?: "?"} (no repatch needed)"
                else
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready to install (no repatch needed)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Only the one action the card cannot defer: a patched APK waiting to go
        // onto the device. Repatch and everything else live in the sheet, which
        // tapping the card opens, so the card stays a card.
        if (deviceInfo?.installPending == true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                DetailActionPill(
                    if (installing) "Installing…" else "Install",
                    MorpheIcons.Download,
                    accents.secondary, font, corners.small,
                    onGradient = true,
                    onClick = if (installing) ({}) else onInstall,
                )
            }
        }
    }
}
}

/**
 * Full recall breakdown for one patched app. Everything is already on the record
 * (date, versions, per-source snapshot, selection, options, integrity). This is a
 * read surface plus the Re-patch / Open folder / Forget actions.
 */
@Composable
fun PatchedAppDetailDialog(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: app.morphe.gui.ui.screens.home.RecallUpdateInfo?,
    /** Patch metadata for this package, for the APK version list. Null when unknown. */
    supportedApp: SupportedApp? = null,
    /** Sources a patch run will use right now. Not the record's history. */
    activeSources: List<ActivePatchSource> = emptyList(),
    /** Every configured source, on or off, so the list can show both. */
    allSources: List<PatchSource> = emptyList(),
    /** source name to the releases it can be pinned to, newest first. */
    bundleVersionsBySource: Map<String, List<BundleRelease>> = emptyMap(),
    /** Turn a source on or off by id. Global, so it affects every app. */
    onSetSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    /** Register a brand new source. Global, so it affects every app. */
    onAddSource: () -> Unit = {},
    /** Register a local `.mpp` on disk as a source. Global, like the others. */
    onAddLocalBundle: (String) -> Unit = {},
    /** versionName of an APK on disk, so a picked file can name its own version. */
    onResolveApkVersion: suspend (String) -> String? = { null },
    /** Whether a source's `.mpp` for a given tag is already cached on disk. */
    onIsBundleCached: suspend (sourceName: String, tag: String) -> Boolean = { _, _ -> true },
    /** App versions the chosen bundle set supports. See [BundleSupport]. */
    onSupportedAppFor: suspend (packageName: String, overrides: Map<String, BundleChoice>) -> BundleSupport? =
        { _, _ -> null },
    /** Fetch a bundle into the cache, reporting 0f..1f. */
    onDownloadBundle: suspend (sourceName: String, tag: String, onProgress: (Float) -> Unit) -> Result<Unit> =
        { _, _, _ -> Result.success(Unit) },
    /**
     * Non-null while a chosen bundle is downloading, as (source name, 0f..1f).
     * A version the user has never fetched is downloaded when PATCH is pressed,
     * so the sheet stays open and reports it rather than appearing to hang.
     */
    patchPrepProgress: Pair<String, Float>? = null,
    preparingPatch: Boolean = false,
    onDismiss: () -> Unit,
    onRepatch: () -> Unit,
    /**
     * Run a patch with an explicitly chosen APK and per-source bundle overrides.
     * An empty [overrides] means "resolve normally", the same thing plain
     * Repatch used to do.
     */
    onPatchWith: (apkPath: String, overrides: Map<String, BundleChoice>) -> Unit = { _, _ -> },
    onForget: () -> Unit,
    onOpenFolder: () -> Unit,
    onInstall: () -> Unit = {},
    onUninstall: () -> Unit = {},
    installing: Boolean = false,
    uninstalling: Boolean = false,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val cardFills = LocalCardFills.current
    val patchCount = record.patchSelectionByBundle.values.sumOf { it.size }
    // Keyed off patchesChanged, not sources.any { outdated }, so this matches
    // the list badge.
    val hasUpdate = updateInfo != null && (updateInfo.appOutdated || updateInfo.patchesChanged)
    val installPending = deviceInfo?.installPending == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Tap outside the card to dismiss (the card swallows its own taps below).
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
        // Grow with content, but cap at ~90% of the window height so the dialog
        // can use a tall screen like Settings does, instead of the old fixed
        // 560dp cap, while still wrapping shorter content.
        val maxDialogHeight = maxHeight * 0.9f
        // Floor keeps it usable on a small window, ceiling stops it sprawling.
        val maxDialogWidth = (maxWidth * 0.7f).coerceIn(480.dp, 860.dp)

        var selectedApkPath by remember(record.packageName) {
            mutableStateOf(record.inputApkPath)
        }
        var bundleChoices by remember(record.packageName) {
            mutableStateOf(emptyMap<String, BundleChoice>())
        }
        var apkExpanded by remember(record.packageName) { mutableStateOf(false) }
        var patchExpanded by remember(record.packageName) { mutableStateOf(false) }

        // Known outright for the recorded input, read off the manifest otherwise.
        var selectedApkVersion by remember(record.packageName) {
            mutableStateOf<String?>(record.apkVersion)
        }
        LaunchedEffect(selectedApkPath) {
            selectedApkVersion =
                if (selectedApkPath == record.inputApkPath) record.apkVersion
                else onResolveApkVersion(selectedApkPath)
        }

        val bundleParts = activeSources.map { src ->
            val label = when (val c = bundleChoices[src.name]) {
                is BundleChoice.Version -> "v${c.tag.removePrefix("v")}"
                is BundleChoice.LocalFile -> File(c.path).name
                null -> src.resolvedVersion?.let { "v${it.removePrefix("v")}" } ?: "latest"
            }
            src.name to label
        }

        // A hand-supplied file is on disk already, and an unresolved source has
        // no tag to look up. Both drop out of the cache check.
        val bundleTags = activeSources.mapNotNull { src ->
            when (val c = bundleChoices[src.name]) {
                is BundleChoice.Version -> src.name to c.tag
                is BundleChoice.LocalFile -> null
                null -> src.resolvedVersion?.let { src.name to it }
            }
        }
        // Null until the first check completes. Empty means everything is cached.
        var pendingDownloads by remember(record.packageName) { mutableStateOf<List<String>?>(null) }
        LaunchedEffect(bundleTags) {
            pendingDownloads = bundleTags
                .filterNot { (name, tag) -> onIsBundleCached(name, tag) }
                .map { (name, tag) -> "$name v${tag.removePrefix("v")}" }
        }

        // Supported APK versions belong to the bundle, so a pin invalidates them.
        val scope = rememberCoroutineScope()
        var bundleSupport by remember(record.packageName) { mutableStateOf<BundleSupport?>(null) }
        var supportEpoch by remember(record.packageName) { mutableStateOf(0) }
        var bundleDownload by remember(record.packageName) { mutableStateOf<Float?>(null) }
        // Reading a `.mpp` off disk is not instant the first time. Say so, rather
        // than leaving the previous bundle's versions up with nothing to explain them.
        var supportLoading by remember(record.packageName) { mutableStateOf(false) }
        var bundleDownloadError by remember(record.packageName) { mutableStateOf<String?>(null) }
        LaunchedEffect(bundleChoices, activeSources, supportEpoch) {
            supportLoading = true
            bundleSupport = onSupportedAppFor(record.packageName, bundleChoices)
            supportLoading = false
        }

        // What the version lists describe, versus what a run would actually use.
        val loadedLabel = activeSources.joinToString(", ") { src ->
            "${src.name} ${src.resolvedVersion?.let { "v${it.removePrefix("v")}" } ?: "latest"}"
        }.ifBlank { "the loaded bundle" }
        val chosenLabel = bundleChoices.entries
            .mapNotNull { (name, c) ->
                (c as? BundleChoice.Version)?.let { "$name v${it.tag.removePrefix("v")}" }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
        val support = bundleSupport
        val stagedVersion = selectedApkVersion
        val effectiveApp = support?.takeIf { it.missing.isEmpty() }?.app ?: supportedApp
        // Deliberately NOT gated on [BundleSupport.isCurrent]. That flag only says
        // whether `app` came from the already-loaded set or a freshly read bundle,
        // and both carry the same version lists. Skipping the check for the
        // resolved bundle meant the default selection never warned, which is the
        // one selection every user starts on.
        val versionUnsupported = support != null &&
            support.missing.isEmpty() &&
            effectiveApp != null &&
            stagedVersion != null &&
            stagedVersion.removePrefix("v") !in
            (effectiveApp.supportedVersions + effectiveApp.experimentalVersions)
                .map { it.removePrefix("v") }
        val apkNeedsAttention = support?.missing?.isNotEmpty() == true || versionUnsupported

        // The version each row is leaving behind, so a change reads as a change
        // rather than as a number that quietly differs from the record above.
        val apkPrevious = record.apkVersion
            .takeIf { stagedVersion != null && it.removePrefix("v") != stagedVersion.removePrefix("v") }
            ?.let { "v${it.removePrefix("v")}" }
        val bundlePrevious = bundleParts.singleOrNull()?.let { (name, current) ->
            record.sourcesSnapshot.firstOrNull { it.sourceName == name }
                ?.version
                ?.let { "v${it.removePrefix("v")}" }
                ?.takeIf { it != current }
        }

        val sheetScroll = rememberScrollState()
        Box {
        MorpheDialogSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .pointerInput(Unit) { detectTapGestures { } },
            contentModifier = Modifier
                .heightIn(max = maxDialogHeight)
                .verticalScroll(sheetScroll),
            horizontalAlignment = Alignment.Start,
            // Full bleed: each band paints edge to edge and pads itself.
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
                IdentityBand(record, state, deviceInfo, updateInfo, font)
                BandDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(corners.small))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                RoundedCornerShape(corners.small),
                            ),
                    ) {
                        AssemblyRow(
                            label = "Source APK",
                            primary = record.displayName,
                            version = selectedApkVersion?.let { "v${it.removePrefix("v")}" },
                            previousVersion = apkPrevious,
                            sub = File(selectedApkPath).name,
                            expanded = apkExpanded,
                            accent = accents.secondary,
                            font = font,
                            warning = apkNeedsAttention,
                            onToggle = { apkExpanded = !apkExpanded },
                        ) {
                            ApkSourceSection(
                                // The chosen bundle's list wins once it is known.
                                app = effectiveApp,
                                recordedApkPath = record.inputApkPath,
                                recordedVersion = record.apkVersion,
                                selectedApkPath = selectedApkPath,
                                selectedVersion = selectedApkVersion,
                                support = support,
                                loading = supportLoading,
                                loadedLabel = loadedLabel,
                                chosenLabel = chosenLabel,
                                versionUnsupported = versionUnsupported,
                                downloadProgress = bundleDownload,
                                onDownloadMissing = {
                                    val targets = bundleSupport?.missing.orEmpty()
                                    scope.launch {
                                        bundleDownloadError = null
                                        // One bar across the whole set, so two
                                        // bundles do not reset it to zero midway.
                                        targets.forEachIndexed { i, (name, tag) ->
                                            bundleDownload = i.toFloat() / targets.size
                                            val result = onDownloadBundle(name, tag) { pct ->
                                                bundleDownload = (i + pct) / targets.size
                                            }
                                            result.onFailure {
                                                bundleDownloadError =
                                                    "Could not download $name $tag: ${it.message}"
                                                return@forEachIndexed
                                            }
                                        }
                                        bundleDownload = null
                                        supportEpoch++
                                    }
                                },
                                downloadError = bundleDownloadError,
                                font = font,
                                corner = corners.small,
                                onApkSelected = { selectedApkPath = it },
                            )
                        }
                        RowDivider()
                        AssemblyRow(
                            label = "Patch bundle",
                            // Several sources cannot share one headline version,
                            // so the count leads and the pairs go in the sub-line.
                            primary = when (bundleParts.size) {
                                0 -> "No sources enabled"
                                1 -> bundleParts[0].first
                                else -> "${bundleParts.size} sources"
                            },
                            version = bundleParts.singleOrNull()?.second,
                            previousVersion = bundlePrevious,
                            sub = when (bundleParts.size) {
                                0 -> "enable or add one below"
                                1 -> null
                                else -> bundleParts.joinToString("  ·  ") { "${it.first} ${it.second}" }
                            },
                            expanded = patchExpanded,
                            accent = accents.primary,
                            font = font,
                            onToggle = { patchExpanded = !patchExpanded },
                        ) {
                            // Disabled sources stay listed so toggling one greys it
                            // in place rather than making it disappear.
                            allSources.forEach { src ->
                                val active = activeSources.firstOrNull { it.name == src.name }
                                PatchSourceSection(
                                    sourceName = src.name,
                                    enabled = src.enabled,
                                    resolvedVersion = active?.resolvedVersion,
                                    availableVersions = bundleVersionsBySource[src.name],
                                    choice = bundleChoices[src.name],
                                    font = font,
                                    corner = corners.small,
                                    onChoose = { c ->
                                        bundleChoices = if (c == null) bundleChoices - src.name
                                        else bundleChoices + (src.name to c)
                                    },
                                    onSetEnabled = { onSetSourceEnabled(src.id, it) },
                                )
                            }
                            AddSourceControl(
                                font = font,
                                corner = corners.small,
                                onAddSource = onAddSource,
                                onAddLocalBundle = onAddLocalBundle,
                            )
                        }
                    }

                    // A pending install outranks patching, so it takes the solid
                    // treatment and the patch bar steps back to outlined.
                    if (installPending) {
                        ActionBar(
                            label = if (installing) "Installing…" else "Install to device",
                            icon = MorpheIcons.Download,
                            color = app.morphe.gui.ui.theme.MorpheColors.Teal,
                            font = font,
                            corner = corners.small,
                            filled = true,
                            sublabels = listOf(
                                if (deviceInfo.installed)
                                    "v${record.apkVersion.removePrefix("v")} ready  ·  device on v${deviceInfo.installedVersion?.removePrefix("v") ?: "?"}"
                                else "v${record.apkVersion.removePrefix("v")} ready, no repatch needed"
                            ),
                            onClick = if (installing) ({}) else ({ onInstall() }),
                        )
                    }

                    // Held while preparing too, so the bar does not change height
                    // mid-run.
                    val downloads = pendingDownloads
                    // One bundle per line. Comma-joining them ran off the bar as
                    // soon as a second source was enabled.
                    val patchSubs = when {
                        patchPrepProgress != null -> listOf("downloading ${patchPrepProgress.first}")
                        preparingPatch -> listOf("resolving patch files")
                        // Nothing to say when there is nothing to fetch.
                        downloads.isNullOrEmpty() -> emptyList()
                        else -> downloads.map { "↓  $it" }
                    }
                    // This app is already patched, so neither label may imply a
                    // first run. Update when anything about the run differs from
                    // the record, Repatch when it is the same inputs again.
                    val inputsChanged = selectedApkPath != record.inputApkPath ||
                        bundleChoices.isNotEmpty()
                    ActionBar(
                        label = when {
                            patchPrepProgress != null ->
                                "Downloading  ${(patchPrepProgress.second * 100).toInt()}%"
                            preparingPatch -> "Preparing…"
                            hasUpdate || inputsChanged -> "Update"
                            else -> "Repatch"
                        },
                        // No icon. The label alone carries it, and a refresh glyph
                        // on an already-patched app read as "start over".
                        icon = null,
                        color = if (hasUpdate) app.morphe.gui.ui.theme.MorpheColors.Blue else accents.primary,
                        font = font,
                        corner = corners.small,
                        filled = !installPending,
                        sublabels = patchSubs,
                        progress = if (preparingPatch) (patchPrepProgress?.second ?: 0f) else null,
                        // A second press MUST NOT start a duplicate run.
                        onClick = if (preparingPatch) ({}) else ({ onPatchWith(selectedApkPath, bundleChoices) }),
                    )

                    if (deviceInfo?.installed == true) {
                        ActionBar(
                            label = if (uninstalling) "Uninstalling…" else "Uninstall",
                            icon = MorpheIcons.Delete,
                            color = Color(0xFFE0504D),
                            font = font,
                            corner = corners.small,
                            filled = false,
                            onClick = if (uninstalling) ({}) else ({ onDismiss(); onUninstall() }),
                        )
                    }
                }
                BandDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                var detailsExpanded by remember { mutableStateOf(false) }
                var patchesExpanded by remember { mutableStateOf(false) }
                var patchSearch by remember { mutableStateOf("") }
                DisclosureHeader(
                    label = "Details",
                    color = accents.secondary,
                    font = font,
                    corner = corners.small,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
                if (detailsExpanded) {
                // Inset to 18dp total, matching the bands above, while the
                // headers keep their own padding inside the hover box.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("Patched", fullDate(record.patchedAt), font)
                    StatCell(
                        "App version",
                        buildString {
                            append("v${record.apkVersion.removePrefix("v")}")
                            record.apkVersionCode?.let { append(" (${'$'}it)") }
                        },
                        font,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell("Morphe", record.patchedWithMorpheVersion, font)
                    StatCell("Output size", humanSize(record.outputApkSize), font)
                }
                record.outputApkSha256?.let { CopyableStat("SHA-256", it, font, corners.small) }
                CopyableStat("Output path", record.outputApkPath, font, corners.small)
                }
                DisclosureHeader(
                    label = "Patches applied",
                    color = accents.primary,
                    font = font,
                    corner = corners.small,
                    expanded = patchesExpanded,
                    trailing = "$patchCount",
                    onToggle = { patchesExpanded = !patchesExpanded },
                )
                if (patchesExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (patchCount > 5) {
                        PatchSearchField(patchSearch, { patchSearch = it }, font, corners.small, accents.primary)
                    }
                    record.patchSelectionByBundle.forEach { (bundle, patches) ->
                        val shown = patches
                            .map { patchDisplayName(it) }
                            .filter { patchSearch.isBlank() || it.contains(patchSearch, ignoreCase = true) }
                            .sorted()
                        if (shown.isNotEmpty()) {
                            Text(
                                text = bundle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            shown.forEach { name ->
                                Text(
                                    text = "• $name",
                                    fontSize = 10.sp,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                                )
                            }
                        }
                    }
                    if (record.patchOptionValues.isNotEmpty() && patchSearch.isBlank()) {
                        Text(
                            text = "Options",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        record.patchOptionValues.forEach { (k, v) ->
                            Text(
                                text = "• $k = $v",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                            )
                        }
                    }
                }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp),
                ) {
                    DetailActionPill(
                        "Folder", MorpheIcons.OpenInNew, accents.secondary, font, corners.small,
                        modifier = Modifier.weight(1f), onClick = onOpenFolder,
                    )
                    DetailActionPill(
                        "Customise", MorpheIcons.Palette, accents.primary, font, corners.small,
                        modifier = Modifier.weight(1f),
                    ) {
                        cardFills.requestEdit(
                            record.packageName,
                            record.displayName,
                            supportedApp?.appIconColor,
                        )
                    }
                    DetailActionPill(
                        "Forget", MorpheIcons.Delete,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), font, corners.small,
                        modifier = Modifier.weight(1f),
                    ) { onDismiss(); onForget() }
                }
                }
                }
        }
        // matchParentSize, not fillMaxHeight. A scrollbar sized off the incoming
        // constraint takes the whole window height and drags the sheet up with it.
        Box(Modifier.matchParentSize()) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(sheetScroll),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 3.dp),
                style = morpheScrollbarStyle(),
            )
        }
        }
        }
    }
}

// ============================================================================
// DETAIL SHEET BANDS
// ============================================================================

/** Identity band: app, state, and any advice worth stating unprompted. */
@Composable
private fun IdentityBand(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    font: FontFamily,
) {
    val accents = LocalMorpheAccents.current
    val corner = LocalMorpheCorners.current.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accents.primary.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, accents.primary.copy(alpha = 0.35f), RoundedCornerShape(corner))
                    .background(accents.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = record.displayName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = accents.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = record.displayName,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Post-rename package. The original stays in the detail rows.
                Text(
                    text = record.installedPackageName,
                    fontSize = 9.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(10.dp))
                PatchedStateBadge(state, font)
            }
        }
        deviceInfo?.let {
            DeviceLine(it, font, MaterialTheme.colorScheme.onSurfaceVariant, MorpheColors.Teal)
        }
        val advice = updateInfo?.let { appAdvice(it) }
        if (advice != null) {
            UpdateHint(advice.first, font, recommended = advice.second)
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            // Bundle is newer but its app versions are not resolved yet.
            InfoNote("A newer patch bundle is available. Update to take it.", font)
        }
    }
}

/** Full-bleed hairline between the sheet's bands. */
@Composable
private fun BandDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    )
}

/** Hairline between the two rows inside the assembly card. */
@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    )
}

/** One input in the patch recipe. Collapsed it states the current selection. */
@Composable
private fun AssemblyRow(
    label: String,
    primary: String,
    /** Headline value, right aligned. Null when there is no single one to show. */
    version: String?,
    /** What [version] is replacing, shown small beside it. Null when unchanged. */
    previousVersion: String? = null,
    sub: String?,
    expanded: Boolean,
    accent: Color,
    font: FontFamily,
    /** Flags the row's content as needing attention, without expanding it. */
    warning: Boolean = false,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val nameColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = if (expanded) 0.07f else if (isHovered) 0.05f else 0f))
                .hoverable(hover)
                .handCursor()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = accent.copy(alpha = 0.8f),
                    )
                    if (warning) {
                        Text(
                            text = "⚠",
                            fontSize = 11.sp,
                            fontFamily = font,
                            color = LocalMorpheAccents.current.warning,
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = nameColor, fontSize = 12.sp)) { append(primary) }
                        sub?.let {
                            withStyle(SpanStyle(color = subColor, fontSize = 10.sp)) { append("   $it") }
                        }
                    },
                    fontFamily = font,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (version != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    previousVersion?.let {
                        Text(
                            text = it,
                            fontSize = 10.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            maxLines = 1,
                        )
                        Text(
                            text = "→",
                            fontSize = 10.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        text = version,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            Chevron(expanded, accent)
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                content = content,
            )
        }
    }
}

/** The sheet's disclosure marker. Every one of them MUST use this. */
@Composable
private fun Chevron(expanded: Boolean, color: Color, alpha: Float = 0.7f) {
    Text(
        text = if (expanded) "▾" else "▸",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = LocalMorpheFont.current,
        color = color.copy(alpha = alpha),
    )
}

/**
 * A committing action. [progress] paints the fill itself, so a running action
 * needs no separate indicator.
 */
@Composable
private fun ActionBar(
    label: String,
    /** Null for a label-only bar. */
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color,
    font: FontFamily,
    corner: Dp,
    filled: Boolean,
    /** Centred lines under the label, one per line, stating what this will cost. */
    sublabels: List<String> = emptyList(),
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(corner)
    val running = progress != null
    // Picked off the fill's luminance, not the theme surface. A bright accent
    // (Deepspace cyan) needs dark text, a mid one (brand blue) needs white, and
    // a fixed surface colour got one of the two wrong on every theme.
    val contentColor = when {
        running -> MaterialTheme.colorScheme.onSurface
        filled -> if (color.luminance() > 0.45f) Color(0xFF10141A) else Color.White
        else -> color
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A minimum, not a fixed height. The bar grows with however many
            // bundles it has to name.
            .heightIn(min = if (filled) 46.dp else 38.dp)
            .clip(shape)
            .then(
                when {
                    running -> Modifier.background(color.copy(alpha = 0.14f))
                    filled -> Modifier.background(color.copy(alpha = if (isHovered) 1f else 0.9f))
                    else -> Modifier
                        .border(1.dp, color.copy(alpha = if (isHovered) 0.55f else 0.3f), shape)
                        .background(color.copy(alpha = if (isHovered) 0.12f else 0.06f))
                }
            )
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.38f)),
                )
            }
        }
        val title = @Composable {
            Text(
                text = label,
                fontSize = if (filled) 13.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = contentColor,
            )
        }
        val lines = @Composable {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                title()
                sublabels.forEach {
                    Text(
                        text = it,
                        fontSize = 9.sp,
                        fontFamily = font,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            icon == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { lines() }

            sublabels.isEmpty() -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (filled) 16.dp else 13.dp),
                )
                title()
            }

            // The icon sits beside the text and spans every line. The trailing
            // spacer matches icon plus gap, so the text block still lands on the
            // bar's centre line instead of being pushed right by the icon.
            else -> {
                val iconSize = 26.dp
                val iconGap = 10.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(Modifier.width(iconGap))
                    Box(Modifier.weight(1f, fill = false)) { lines() }
                    Spacer(Modifier.width(iconSize + iconGap))
                }
            }
        }
    }
}

/** Collapsible section header in the details band. */
@Composable
private fun DisclosureHeader(
    label: String,
    color: Color,
    font: FontFamily,
    corner: Dp,
    expanded: Boolean,
    trailing: String? = null,
    onToggle: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = if (expanded || isHovered) 0.07f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        SectionHeader(label, color, font)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Chevron(expanded, color, alpha = 1f)
    }
}

/**
 * A long value shown in full, wrapped, with click-to-copy. Truncating a hash or
 * a path leaves the user no way to reach the rest of it.
 */
@Composable
private fun CopyableStat(label: String, value: String, font: FontFamily, corner: Dp) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val accents = LocalMorpheAccents.current
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(accents.primary.copy(alpha = if (isHovered) 0.07f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(value), null)
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            if (isHovered) {
                Text(
                    text = "Click to copy",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = accents.primary.copy(alpha = 0.8f),
                )
            }
        }
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            lineHeight = 14.sp,
        )
    }
}

/** One label-over-value pair in the details grid. */
@Composable
private fun RowScope.StatCell(label: String, value: String, font: FontFamily) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Slim search field for filtering the applied-patches list. */
@Composable
private fun PatchSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    font: FontFamily,
    corner: Dp,
    accent: Color,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(accent),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Fixed height + centered content so the field doesn't grow/shift
                    // when typing, and the placeholder/cursor sit at the same spot.
                    .height(32.dp)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(corner))
                    .padding(horizontal = 8.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Search patches…",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** "↑ …" advice line. recommended = amber (take it), optional = blue (your call). */
@Composable
private fun UpdateHint(text: String, font: FontFamily, recommended: Boolean = false) {
    Text(
        text = "↑ $text",
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = if (recommended) Color(0xFFE0A030) else MorpheColors.Blue,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

/**
 * App-version advice for a patched app, or null if current. Returns (message,
 * recommended): recommended=true (amber) when the version is unsupported or a newer
 * stable is out. False (blue) for an optional experimental bump.
 */
/**
 * The one app-version fact worth stating unprompted. Which version to move to is
 * the APK section's job.
 */
private fun appAdvice(u: app.morphe.gui.ui.screens.home.RecallUpdateInfo): Pair<String, Boolean>? {
    if (u.appUsedSupported) return null
    return "v${u.appUsedVersion.removePrefix("v")} is no longer supported by the latest patches" to true
}

/**
 * "label vOld → vNew" with distinct colors: muted label/old/arrow, highlighted new.
 * Reads far better than a single flat accent. [newColor] signals tone (blue = optional,
 * amber = recommended). When [newVersion] is null, just shows "label vOld".
 */
@Composable
private fun VersionBumpText(
    label: String,
    oldVersion: String,
    newVersion: String?,
    font: FontFamily,
    suffix: String? = null,
) {
    val labelColor = Color.White
    val muted = Color.White.copy(alpha = 0.6f)
    val arrow = Color.White.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(label) }
        withStyle(SpanStyle(color = muted)) { append("v${oldVersion.removePrefix("v")}") }
        if (newVersion != null) {
            withStyle(SpanStyle(color = arrow)) { append("  →  ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("v${newVersion.removePrefix("v")}") }
        }
        if (suffix != null) withStyle(SpanStyle(color = muted)) { append(suffix) }
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Small pill badge (matches PatchedStateBadge styling) for ad-hoc states. */
@Composable
private fun MiniBadge(
    label: String,
    color: Color,
    font: FontFamily,
    /** See [Pill]. True keeps the badge white for a coloured card behind it. */
    onGradient: Boolean = false,
) {
    val corner = LocalMorpheCorners.current.small
    // Same rule as [Pill]: keep the accent, adjusting it only when it is too
    // close to the card to read. See [onCardGradient].
    val badgeColor = if (onGradient) color.onCardGradient() else color
    val badgeInk = if (onGradient) badgeColor.contrastingForeground() else badgeColor
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(if (onGradient) badgeColor else badgeColor.copy(alpha = 0.2f))
            .then(if (onGradient) Modifier else Modifier.border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(corner)))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = badgeInk)
    }
}

/** Muted informational note (ⓘ). Full width, wraps. */
@Composable
private fun InfoNote(text: String, font: FontFamily) {
    Text(
        text = "ⓘ  $text",
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
private fun SectionHeader(text: String, color: Color, font: FontFamily) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontFamily = font,
        fontWeight = FontWeight.SemiBold,
        color = color.copy(alpha = 0.85f),
    )
}

@Composable
private fun DetailActionPill(
    label: String,
    /** Null for a label-only pill. */
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color,
    font: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    /** Pass a weight to share a row equally with its siblings. */
    modifier: Modifier = Modifier,
    /** 0f..1f while work runs. Paints the fill, matching the PATCH bar. */
    progress: Float? = null,
    /** See [Pill]. True for pills on an app card, where white stays legible. */
    onGradient: Boolean = false,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(corner)
    // Each action carries its own colour, and on a card the fill is solid. A
    // translucent fill has to reckon with the gradient underneath, which differs
    // at every position, so the pill never settles. An opaque fill is its own
    // surface and reads the same wherever it lands.
    val pillColor = if (onGradient) color.onCardGradient() else color
    val pillInk = pillColor.contrastingForeground()
    Box(
        modifier = modifier
            .clip(shape)
            // Solid on both surfaces. On a sheet this used to be an 8 percent wash
            // behind a 35 percent edge, which left the accent barely visible and the
            // action looking disabled.
            .background(if (isHovered) pillColor.shiftLightness(0.08f) else pillColor)
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick),
    ) {
        if (progress != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.3f)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = pillInk, modifier = Modifier.size(13.dp))
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = font, color = pillInk)
        }
    }
}

// ============================================================================
// APK SOURCE PICKER
// ============================================================================

/**
 * Picks the APK a repatch runs against. Uses the same Stable and Experimental
 * split as the supported-apps list, and is the action behind
 * [PatchedAppState.NEW_APP_VERSION].
 */
@Composable
private fun ApkSourceSection(
    app: SupportedApp?,
    recordedApkPath: String,
    recordedVersion: String,
    selectedApkPath: String,
    /** Version of the staged APK, for the unsupported-by-this-bundle check. */
    selectedVersion: String?,
    /**
     * Which app versions the CHOSEN bundle supports. Null while it is being
     * worked out. The lists below come from the loaded bundle until this says
     * otherwise, so a pinned bundle never silently mislabels them.
     */
    support: BundleSupport?,
    /** True while [support] is being worked out. */
    loading: Boolean,
    /** "name vX" per source, as loaded right now. */
    loadedLabel: String,
    /** "name vX" per pinned source, or null when nothing is pinned. */
    chosenLabel: String?,
    /** True when the staged APK version is absent from the chosen bundle's lists. */
    versionUnsupported: Boolean,
    /** Non-null while a bundle is being fetched, as 0f..1f. */
    downloadProgress: Float?,
    onDownloadMissing: () -> Unit,
    /** Non-null when the last fetch failed. */
    downloadError: String?,
    font: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    onApkSelected: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val accents2 = LocalMorpheAccents.current
    val recordedExists = remember(recordedApkPath) { File(recordedApkPath).exists() }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Using",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Text(
            text = File(selectedApkPath).name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = accents2.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // The recorded input is the only outright-selectable alternative.
    if (recordedExists && selectedApkPath != recordedApkPath) {
        ChoiceRow(
            label = "v${recordedVersion.removePrefix("v")}  ·  ${File(recordedApkPath).name}",
            sub = "the APK this app was patched from",
            selected = false,
            font = font,
            corner = corner,
            onClick = { onApkSelected(recordedApkPath) },
        )
    }
    // A pinned bundle that is not on disk cannot be read, so the lists below still
    // describe the loaded one. Say so, and offer to fetch it.
    val missing = support?.missing.orEmpty()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    if (loading) {
        Text(
            text = "Reading the chosen bundle…",
            fontSize = 10.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    } else if (missing.isNotEmpty()) {
        val chosen = missing.joinToString(", ") { (name, tag) -> "$name v${tag.removePrefix("v")}" }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                        append("⚠  ")
                    }
                    withStyle(SpanStyle(color = muted)) { append("Versions below are what ") }
                    withStyle(SpanStyle(color = accents.secondary, fontWeight = FontWeight.Bold)) {
                        append(loadedLabel)
                    }
                    withStyle(SpanStyle(color = muted)) { append(" supports. You picked ") }
                    withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                        append(chosen)
                    }
                    withStyle(SpanStyle(color = muted)) {
                        append(", which is not downloaded, so what it supports is unknown.")
                    }
                },
                fontSize = 10.sp,
                fontFamily = font,
                lineHeight = 15.sp,
            )
            DetailActionPill(
                if (downloadProgress != null) "Downloading  ${(downloadProgress * 100).toInt()}%"
                else if (missing.size > 1) "Download ${missing.size} bundles" else "Download bundle",
                MorpheIcons.Download, accents.warning, font, corner,
                progress = downloadProgress,
                onClick = if (downloadProgress != null) ({}) else onDownloadMissing,
            )
            downloadError?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 14.sp,
                )
            }
        }
    } else if (versionUnsupported) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                    append("⚠  ")
                }
                withStyle(SpanStyle(color = accents.primary, fontWeight = FontWeight.Bold)) {
                    // Name whichever bundle is actually in force. Requiring a pinned
                    // label here was the second reason the default never warned.
                    append(chosenLabel ?: loadedLabel)
                }
                withStyle(SpanStyle(color = muted)) { append(" does not support ") }
                withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                    append("v${selectedVersion?.removePrefix("v")}")
                }
                withStyle(SpanStyle(color = muted)) { append(". Pick one of these instead.") }
            },
            fontSize = 10.sp,
            fontFamily = font,
            lineHeight = 15.sp,
        )
    } else if (chosenLabel != null) {
        // The list changed under the user, so name what it now reflects.
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = muted)) { append("Versions below are what ") }
                withStyle(SpanStyle(color = accents.primary, fontWeight = FontWeight.Bold)) {
                    append(chosenLabel)
                }
                withStyle(SpanStyle(color = muted)) { append(" supports.") }
            },
            fontSize = 10.sp,
            fontFamily = font,
            lineHeight = 15.sp,
        )
    }

    if (app != null) {
        // Uncapped, unlike the list row. Hiding versions would defeat the picker.
        if (app.supportedVersions.isNotEmpty()) {
            SectionLabel(text = "Stable", font = font, color = accents.secondary)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.supportedVersions.forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.secondary,
                        font = font,
                        cornerSmall = corner,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
            }
        }
        if (app.experimentalVersions.isNotEmpty()) {
            SectionLabel(text = "Experimental", font = font, color = accents.warning)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.experimentalVersions.forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.warning,
                        font = font,
                        cornerSmall = corner,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
            }
        }
    }

    DetailActionPill(
        if (recordedExists) "Choose a different APK…" else "Choose an APK…",
        MorpheIcons.FolderOpen, accents.secondary, font, corner,
    ) {
        val fd = FileDialog(null as Frame?, "Select an APK to patch", FileDialog.LOAD)
        fd.isVisible = true
        val picked = fd.file?.let { File(fd.directory, it) }
        if (picked != null && picked.exists()) onApkSelected(picked.absolutePath)
    }
}

// ============================================================================
// PATCH SOURCE PICKER
// ============================================================================

/**
 * Registers a new source. Global by design: a per-app "use just this once" would
 * leave the app-wide source list disagreeing with what actually ran.
 */
@Composable
private fun AddSourceControl(
    font: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    onAddSource: () -> Unit,
    onAddLocalBundle: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        DetailActionPill("New source…", MorpheIcons.Add, accents.primary, font, corner) {
            onAddSource()
        }
        // A local bundle is just another source.
        DetailActionPill("Local .mpp…", MorpheIcons.FolderOpen, accents.secondary, font, corner) {
            val fd = FileDialog(null as Frame?, "Select a patch bundle", FileDialog.LOAD)
            fd.isVisible = true
            val picked = fd.file?.let { File(fd.directory, it) }
            if (picked != null && picked.exists()) onAddLocalBundle(picked.absolutePath)
        }
    }
}

@Composable
private fun PatchSourceSection(
    sourceName: String,
    enabled: Boolean,
    resolvedVersion: String?,
    /** Null while the release list is still being fetched. */
    availableVersions: List<BundleRelease>?,
    choice: BundleChoice?,
    font: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    onChoose: (BundleChoice?) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val dim = if (enabled) 1f else 0.38f
    // Collapsed by default. The USING line is the answer most of the time.
    var expanded by remember(sourceName) { mutableStateOf(false) }

    // The whole card is the hit target. The switch and pills inside consume
    // their own clicks, so neither also toggles expansion.
    val cardHover = remember { MutableInteractionSource() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.22f else 0.10f),
                RoundedCornerShape(corner),
            )
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.03f else 0f))
            .then(
                if (enabled) Modifier
                    .hoverable(cardHover)
                    .handCursor()
                    .clickable { expanded = !expanded }
                else Modifier
            )
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (enabled) Chevron(expanded, accents.secondary)
            Text(
                text = sourceName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = accents.secondary.copy(alpha = dim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MorpheSwitch(
                checked = enabled,
                onCheckedChange = onSetEnabled,
                accentColor = accents.primary,
            )
        }

        if (!enabled) return@Column

        val using = (choice as? BundleChoice.Version)?.tag ?: resolvedVersion
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Using",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            Text(
                text = using?.let { "v${it.removePrefix("v")}" } ?: "latest available",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = accents.primary,
            )
            if (choice != null) {
                val clearHover = remember { MutableInteractionSource() }
                val clearHovered by clearHover.collectIsHoveredAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(corner))
                        .border(
                            1.dp,
                            accents.warning.copy(alpha = if (clearHovered) 0.6f else 0.3f),
                            RoundedCornerShape(corner),
                        )
                        .background(accents.warning.copy(alpha = if (clearHovered) 0.14f else 0.06f))
                        .hoverable(clearHover)
                        .handCursor()
                        .clickable { onChoose(null) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Icon(
                        MorpheIcons.Clear,
                        contentDescription = null,
                        tint = accents.warning,
                        modifier = Modifier.size(9.dp),
                    )
                    Text(
                        text = "Pinned",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                        color = accents.warning,
                    )
                }
            }
        }

        if (!expanded) return@Column

        when {
            availableVersions == null -> Text(
                text = "Loading versions…",
                fontSize = 10.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            availableVersions.isEmpty() -> Text(
                text = "No other versions available",
                fontSize = 10.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            else -> {
                listOf(
                    "Stable" to accents.secondary,
                    "Dev" to accents.warning,
                ).forEach { (label, color) ->
                    val group = availableVersions.filter { it.isDev == (label == "Dev") }
                    if (group.isEmpty()) return@forEach
                    SectionLabel(text = label, font = font, color = color)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        group.forEach { release ->
                            val isUsing = using == release.tag
                            Pill(
                                text = release.tag,
                                color = if (isUsing) accents.primary else color,
                                font = font,
                                cornerSmall = corner,
                                backgroundAlpha = if (isUsing) 0.20f else 0.06f,
                                onClick = {
                                    // Re-picking the resolved version clears the
                                    // override rather than pinning it.
                                    onChoose(
                                        if (release.tag == resolvedVersion) null
                                        else BundleChoice.Version(release.tag)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A selectable line in a chooser. Deliberately not a Pill: pills in this sheet
 * are download links, and these MUST NOT look like one.
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    font: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val tint = if (selected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .border(
                1.dp,
                tint.copy(alpha = if (selected) 0.45f else if (isHovered) 0.3f else 0.12f),
                RoundedCornerShape(corner),
            )
            .background(tint.copy(alpha = if (selected) 0.10f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (selected) "●" else "○",
            fontSize = 10.sp,
            fontFamily = font,
            color = tint.copy(alpha = if (selected) 1f else 0.5f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(
                    text = it,
                    fontSize = 9.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/** Shared device-install line (mirrors the supported-row variant). */
@Composable
private fun DeviceLine(info: DeviceAppInfo, font: FontFamily, mutedColor: Color, activeColor: Color) {
    val version = info.installedVersion?.let { " · v${it.removePrefix("v")}" } ?: ""
    val (text, color) = when {
        !info.installed -> "Not on this device" to mutedColor
        info.signedByMorphe == false -> "On device$version · not Morphe-signed" to Color(0xFFE0504D)
        else -> "On device$version" to activeColor
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = color
    )
}

/**
 * The readable half of a patch's uniqueId, which is `name|packages|descHash`.
 * The package repeats on every row of one app's record and the hash is an
 * internal dedup key, so neither belongs in a list of applied patches.
 *
 * Trims from the right, so a name containing `|` survives. A record written
 * before uniqueIds carried the suffixes is returned unchanged.
 */
private fun patchDisplayName(uniqueId: String): String =
    uniqueId.substringBeforeLast('|').substringBeforeLast('|')

private fun fullDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(millis))

/** "today / yesterday / 3d ago / MMM d". Compact for the list row. */
private fun relativeOrShortDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

// ============================================================================
// YOUR APPS LIST BODY
// ============================================================================

/**
 * "Your apps" list body. The patched-app history (Phase 7). Same scroll/scrollbar
 * treatment as the supported-apps list, but rows are [YourAppRow]s sourced from the
 * records (not the supported-apps list), so apps patched via a since-removed source
 * still appear. Tapping a row opens the detail dialog.
 */
@Composable
internal fun YourAppsListBody(
    patchedRecords: List<PatchedAppRecord>,
    filteredRecords: List<PatchedAppRecord>,
    searchQuery: String,
    patchedStates: Map<String, PatchedAppState>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    appIconColorByPackage: Map<String, String>,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onInstall: (String) -> Unit,
    installingPackage: String?,
    paneMaxHeight: Dp,
    showSearch: Boolean,
) {
    val font = LocalMorpheFont.current
    when {
        patchedRecords.isEmpty() -> YourAppsEmptyHint(
            title = "No patched apps yet",
            subtitle = "Patch an app and it shows up here",
            font = font,
        )
        filteredRecords.isEmpty() -> YourAppsEmptyHint(
            title = "No matches",
            subtitle = "Nothing matches \"$searchQuery\"",
            font = font,
        )
        else -> {
            val listState = rememberLazyListState()
            val headerSearchAllowance = if (showSearch) 80.dp else 34.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (paneMaxHeight - headerSearchAllowance).coerceAtLeast(120.dp)),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = filteredRecords, key = { it.packageName }) { record ->
                        YourAppRow(
                            record = record,
                            state = patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
                            deviceInfo = deviceAppInfo[record.packageName],
                            updateInfo = updateInfoByPackage[record.packageName],
                            onClick = { onShowDetail(record) },
                            onInstall = { onInstall(record.packageName) },
                            installing = installingPackage == record.packageName,
                            appIconColorHex = appIconColorByPackage[record.packageName],
                        )
                    }
                }
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    VerticalScrollbar(
                        modifier = Modifier.fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                        style = morpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun YourAppsEmptyHint(title: String, subtitle: String, font: FontFamily) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
