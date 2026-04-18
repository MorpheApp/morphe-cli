/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patches

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.DEFAULT_KEEP_ARCHITECTURES
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.repository.ConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.patcher.resource.CpuArchitecture
import java.io.File

class PatchSelectionViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchesFilePath: String,
    private val packageName: String,
    private val apkArchitectures: List<String>,
    private val patchService: PatchService,
    private val patchRepository: PatchRepository,
    private val configRepository: ConfigRepository,
    private val localPatchFilePath: String? = null
) : ScreenModel {

    // Actual path to use - may differ from patchesFilePath if we had to re-download
    private var actualPatchesFilePath: String = patchesFilePath

    private val _uiState = MutableStateFlow(PatchSelectionUiState(
        apkArchitectures = apkArchitectures,
        stripLibsStatus = computeStripLibsStatus(apkArchitectures, DEFAULT_KEEP_ARCHITECTURES)
    ))
    val uiState: StateFlow<PatchSelectionUiState> = _uiState.asStateFlow()

    init {
        loadPatches()
        loadStripLibsPreference()
    }

    private fun loadStripLibsPreference() {
        screenModelScope.launch {
            val config = configRepository.loadConfig()
            _uiState.value = _uiState.value.copy(
                stripLibsStatus = computeStripLibsStatus(apkArchitectures, config.keepArchitectures)
            )
        }
    }

    fun getApkPath(): String = apkPath
    fun getPatchesFilePath(): String = actualPatchesFilePath

    fun loadPatches() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // First, ensure the patches file exists - download if missing
            val patchesFile = File(patchesFilePath)
            if (!patchesFile.exists()) {
                Logger.info("Patches file not found at $patchesFilePath, attempting to download...")

                // Try to extract version from the filename and find a matching release
                // Filename format: morphe-patches-x.x.x.mpp or similar
                val downloadResult = downloadMissingPatches(patchesFile.name)
                if (downloadResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Patches file missing and could not be downloaded: ${downloadResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
                actualPatchesFilePath = downloadResult.getOrNull()!!.absolutePath
            }

            // Load patches using PatchService (direct library call)
            val patchesResult = patchService.listPatches(actualPatchesFilePath, packageName.ifEmpty { null })

            patchesResult.fold(
                onSuccess = { patches ->
                    // Deduplicate by uniqueId in case of true duplicates
                    val deduplicatedPatches = patches.distinctBy { it.uniqueId }

                    Logger.info("Loaded ${deduplicatedPatches.size} patches for $packageName")

                    // Only select patches that are enabled by default in the .mpp file
                    val defaultSelected = deduplicatedPatches
                        .filter { it.isEnabled }
                        .map { it.uniqueId }
                        .toSet()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allPatches = deduplicatedPatches,
                        filteredPatches = deduplicatedPatches,
                        selectedPatches = defaultSelected
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to list patches: ${e.message}"
                    )
                    Logger.error("Failed to list patches", e)
                }
            )
        }
    }

    fun togglePatch(patchId: String) {
        val current = _uiState.value.selectedPatches
        val newSelection = if (current.contains(patchId)) {
            current - patchId
        } else {
            current + patchId
        }
        _uiState.value = _uiState.value.copy(selectedPatches = newSelection)
    }

    fun selectAll() {
        val allIds = _uiState.value.filteredPatches.map { it.uniqueId }.toSet()
        _uiState.value = _uiState.value.copy(selectedPatches = allIds)
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedPatches = emptySet())
    }

    fun setSearchQuery(query: String) {
        val filtered = if (query.isBlank()) {
            _uiState.value.allPatches
        } else {
            _uiState.value.allPatches.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredPatches = filtered
        )
    }

    fun setShowOnlySelected(show: Boolean) {
        val filtered = if (show) {
            _uiState.value.allPatches.filter { _uiState.value.selectedPatches.contains(it.uniqueId) }
        } else if (_uiState.value.searchQuery.isNotBlank()) {
            _uiState.value.allPatches.filter {
                it.name.contains(_uiState.value.searchQuery, ignoreCase = true) ||
                it.description.contains(_uiState.value.searchQuery, ignoreCase = true)
            }
        } else {
            _uiState.value.allPatches
        }
        _uiState.value = _uiState.value.copy(
            showOnlySelected = show,
            filteredPatches = filtered
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Recompute strip-libs status from the latest settings. Called when the user
     * closes the Settings dialog so the banner stays in sync with preference edits.
     */
    fun refreshStripLibsStatus() {
        loadStripLibsPreference()
    }

    /**
     * Set a patch option value. Key format: "patchName.optionKey"
     */
    fun setOptionValue(patchName: String, optionKey: String, value: String) {
        val key = "$patchName.$optionKey"
        val current = _uiState.value.patchOptionValues.toMutableMap()
        if (value.isBlank()) {
            current.remove(key)
        } else {
            current[key] = value
        }
        _uiState.value = _uiState.value.copy(patchOptionValues = current)
    }

    /**
     * Get a patch option value. Returns the user-set value, or the default if not set.
     */
    fun getOptionValue(patchName: String, optionKey: String, default: String?): String {
        val key = "$patchName.$optionKey"
        return _uiState.value.patchOptionValues[key] ?: default ?: ""
    }

    /**
     * Count of patches that are disabled by default (from .mpp metadata).
     */
    fun getDefaultDisabledCount(): Int {
        return _uiState.value.allPatches.count { !it.isEnabled }
    }

    fun createPatchConfig(continueOnError: Boolean = false): PatchConfig {
        // Create app folder in the same location as the input APK
        val inputFile = File(apkPath)
        val appFolderName = apkName.replace(" ", "-")
        val outputDir = File(inputFile.parentFile, appFolderName)
        outputDir.mkdirs()

        // Extract version from APK filename and patches version for output name
        val version = extractVersionFromFilename(inputFile.name) ?: "patched"
        val patchesVersion = extractPatchesVersion(File(actualPatchesFilePath).name)
        val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
        val outputFileName = "${appFolderName}-Morphe-${version}${patchesSuffix}.apk"
        val outputPath = File(outputDir, outputFileName).absolutePath

        // Convert unique IDs back to patch names for CLI
        val selectedPatchNames = _uiState.value.allPatches
            .filter { _uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        val disabledPatchNames = _uiState.value.allPatches
            .filter { !_uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        // Only ship a non-empty keepArchitectures set when the current status actually
        // prescribes stripping. All other states (no native libs, universal, keep-all,
        // fallback) → empty set → patcher leaves native libs untouched.
        val keepArches = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping
            ?.mapNotNull { CpuArchitecture.valueOfOrNull(it) }
            ?.toSet()
            ?: emptySet()

        return PatchConfig(
            inputApkPath = apkPath,
            outputApkPath = outputPath,
            patchesFilePath = actualPatchesFilePath,
            enabledPatches = selectedPatchNames,
            disabledPatches = disabledPatchNames,
            patchOptions = _uiState.value.patchOptionValues,
            useExclusiveMode = true,
            keepArchitectures = keepArches,
            continueOnError = continueOnError
        )
    }

    private fun extractVersionFromFilename(fileName: String): String? {
        // Extract version from APKMirror format: com.google.android.youtube_20.40.45-xxx
        return try {
            val afterPackage = fileName.substringAfter("_")
            afterPackage.substringBefore("-").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPatchesVersion(patchesFileName: String): String? {
        // Extract version from patches filename: morphe-patches-1.13.0-dev.11.mpp -> 1.13.0-dev.11
        val regex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")
        return regex.find(patchesFileName)?.groupValues?.get(1)
    }

    fun getApkName(): String = apkName

    /**
     * Generate a preview of the CLI command that will be executed.
     * @param cleanMode If true, formats with newlines for readability. If false, compact single-line format.
     */
    fun getCommandPreview(
        cleanMode: Boolean = false,
        continueOnError: Boolean = false,
        keystorePath: String? = null,
        keystorePassword: String? = null,
        keystoreAlias: String? = null,
        keystoreEntryPassword: String? = null
    ): String {
        val inputFile = File(apkPath)
        val patchesFile = File(actualPatchesFilePath)
        val appFolderName = apkName.replace(" ", "-")
        val version = extractVersionFromFilename(inputFile.name) ?: "patched"
        val patchesVersion = extractPatchesVersion(patchesFile.name)
        val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
        val outputFileName = "${appFolderName}-Morphe-${version}${patchesSuffix}.apk"

        val selectedPatchNames = _uiState.value.allPatches
            .filter { _uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        val disabledPatchNames = _uiState.value.allPatches
            .filter { !_uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        // Use whichever produces fewer flags
        val useExclusive = selectedPatchNames.size <= disabledPatchNames.size

        // striplibs flag: only when the computed status prescribes actual stripping
        val striplibsArg = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping
            ?.joinToString(",")

        // Keystore flags (only if custom keystore is set)
        val hasCustomKeystore = keystorePath != null

        return if (cleanMode) {
            buildString {
                appendLine(
                    """
                        java -jar morphe-cli.jar patch \
                          -p ${patchesFile.name} \
                          -o $outputFileName \
                          --force \
                    """.trimIndent()
                )

                if (continueOnError) {
                    appendLine("  --continue-on-error \\")
                }

                if (useExclusive) {
                    appendLine("  --exclusive \\")
                }

                striplibsArg?.let {
                    appendLine("  --striplibs $it \\")
                }

                if (hasCustomKeystore) {
                    appendLine("  --keystore \"$keystorePath\" \\")
                    keystorePassword?.let {
                        appendLine("  --keystore-password \"$it\" \\")
                    }
                    if (keystoreAlias != null && keystoreAlias != DEFAULT_KEYSTORE_ALIAS) {
                        appendLine("  --keystore-entry-alias \"$keystoreAlias\" \\")
                    }
                    if (keystoreEntryPassword != null && keystoreEntryPassword != DEFAULT_KEYSTORE_PASSWORD) {
                        appendLine("  --keystore-entry-password \"$keystoreEntryPassword\" \\")
                    }
                }

                val flagPatches = if (useExclusive) selectedPatchNames else disabledPatchNames
                val flag = if (useExclusive) "-e" else "-d"

                flagPatches.forEachIndexed { index, patch ->
                    val suffix = if (index == flagPatches.lastIndex) "" else " \\"
                    appendLine("  $flag \"$patch\"$suffix")
                }

                append("  ${inputFile.name}")
            }
        } else {
            val flagPatches = if (useExclusive) selectedPatchNames else disabledPatchNames
            val flag = if (useExclusive) "-e" else "-d"
            val patches = flagPatches.joinToString(" ") { "$flag \"$it\"" }
            val exclusivePart = if (useExclusive) " --exclusive" else ""
            val striplibsPart = if (striplibsArg != null) " --striplibs $striplibsArg" else ""
            val continueOnErrorPart = if (continueOnError) " --continue-on-error" else ""
            val keystorePart = if (hasCustomKeystore) {
                val parts = mutableListOf(" --keystore \"$keystorePath\"")
                if (keystorePassword != null) parts.add("--keystore-password \"$keystorePassword\"")
                if (keystoreAlias != null && keystoreAlias != "Morphe") parts.add("--keystore-entry-alias \"$keystoreAlias\"")
                if (keystoreEntryPassword != null && keystoreEntryPassword != "Morphe") parts.add("--keystore-entry-password \"$keystoreEntryPassword\"")
                parts.joinToString(" ")
            } else ""
            "java -jar morphe-cli.jar patch -p ${patchesFile.name} -o $outputFileName --force$continueOnErrorPart$exclusivePart$striplibsPart$keystorePart $patches ${inputFile.name}"
        }
    }

    /**
     * Download patches file if it's missing (e.g., after cache clear).
     * For LOCAL sources, uses the local file directly.
     * Tries to find a release matching the expected filename, or falls back to latest stable.
     */
    private suspend fun downloadMissingPatches(expectedFilename: String): Result<File> {
        // LOCAL source: use the local file directly instead of downloading
        if (localPatchFilePath != null) {
            val localFile = File(localPatchFilePath)
            return if (localFile.exists()) {
                Result.success(localFile)
            } else {
                Result.failure(Exception("Local patch file not found: ${localFile.name}"))
            }
        }

        // Try to extract version from filename (e.g., "morphe-patches-1.9.0.mpp" -> "1.9.0")
        val versionRegex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")
        val versionMatch = versionRegex.find(expectedFilename)
        val expectedVersion = versionMatch?.groupValues?.get(1)

        Logger.info("Looking for patches version: ${expectedVersion ?: "latest"}")

        // Fetch releases
        val releasesResult = patchRepository.fetchReleases()
        if (releasesResult.isFailure) {
            return Result.failure(releasesResult.exceptionOrNull()
                ?: Exception("Failed to fetch releases"))
        }

        val releases = releasesResult.getOrNull() ?: emptyList()
        if (releases.isEmpty()) {
            return Result.failure(Exception("No releases found"))
        }

        // Find matching release by version, or use latest stable
        val targetRelease = if (expectedVersion != null) {
            releases.find { it.tagName.contains(expectedVersion) }
                ?: releases.firstOrNull { !it.isDevRelease() }  // Fallback to latest stable
        } else {
            releases.firstOrNull { !it.isDevRelease() }  // Latest stable
        }

        if (targetRelease == null) {
            return Result.failure(Exception("No suitable release found"))
        }

        Logger.info("Downloading patches from release: ${targetRelease.tagName}")

        // Download the patches
        return patchRepository.downloadPatches(targetRelease)
    }

}

data class PatchSelectionUiState(
    val isLoading: Boolean = false,
    val allPatches: List<Patch> = emptyList(),
    val filteredPatches: List<Patch> = emptyList(),
    val selectedPatches: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showOnlySelected: Boolean = false,
    val error: String? = null,
    val apkArchitectures: List<String> = emptyList(),
    val stripLibsStatus: StripLibsStatus = StripLibsStatus.NoNativeLibs,
    val patchOptionValues: Map<String, String> = emptyMap()
) {
    val selectedCount: Int get() = selectedPatches.size
    val totalCount: Int get() = allPatches.size
}

/**
 * What the strip-libs feature will do for the currently loaded APK given the
 * user's global keep-list preference. Computed by `computeStripLibsStatus`.
 */
sealed class StripLibsStatus {
    /** APK ships no native libraries — stripping is meaningless. */
    data object NoNativeLibs : StripLibsStatus()

    /** APK ships a single `universal` native lib folder — stripping does not apply. */
    data object Universal : StripLibsStatus()

    /**
     * User's keep-list covers every arch in the APK — nothing to strip. `notInApk`
     * holds any extra arches in the user's keep list that don't appear in the APK,
     * so the banner can surface "your preference for X has no effect here".
     */
    data class KeepAll(val notInApk: List<String>) : StripLibsStatus()

    /** User's keep-list doesn't overlap with the APK's arches — skip stripping as a safety fallback. */
    data class Fallback(val apkArches: List<String>) : StripLibsStatus()

    /**
     * Partial overlap — patcher will keep `keeping` and strip `stripping`. `notInApk`
     * lists arches the user selected that this APK doesn't ship, so the banner can
     * tell the user which of their preferences actually affect this APK.
     */
    data class WillStrip(
        val keeping: List<String>,
        val stripping: List<String>,
        val notInApk: List<String>
    ) : StripLibsStatus()
}

/**
 * Decide what strip-libs should do given the APK's native arches and the user's
 * global keep-list preference. Pure function — no I/O, no side effects — so the
 * same inputs always produce the same output. Used by both the informational
 * banner in PatchSelectionScreen and by createPatchConfig when dispatching to
 * the patcher, guaranteeing UI and behavior stay in sync.
 */
internal fun computeStripLibsStatus(
    apkArches: List<String>,
    userKeep: Set<String>
): StripLibsStatus {
    if (apkArches.isEmpty()) return StripLibsStatus.NoNativeLibs
    if (apkArches.size == 1 && apkArches[0].equals("universal", ignoreCase = true)) {
        return StripLibsStatus.Universal
    }

    val apkSet = apkArches.toSet()
    val overlap = apkSet.intersect(userKeep)
    val notInApk = userKeep.filter { it !in apkSet }

    return when {
        overlap.isEmpty() -> StripLibsStatus.Fallback(apkArches)
        overlap == apkSet -> StripLibsStatus.KeepAll(notInApk = notInApk)
        else -> StripLibsStatus.WillStrip(
            keeping = apkArches.filter { it in overlap },
            stripping = apkArches.filter { it !in overlap },
            notInApk = notInApk
        )
    }
}
