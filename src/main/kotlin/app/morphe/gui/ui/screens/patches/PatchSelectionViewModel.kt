package app.morphe.gui.ui.screens.patches

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.data.repository.PatchRepository
import java.io.File

class PatchSelectionViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchesFilePath: String,
    private val apkArchitectures: List<String>,
    private val patchService: PatchService,
    private val patchRepository: PatchRepository
) : ScreenModel {

    // Actual path to use - may differ from patchesFilePath if we had to re-download
    private var actualPatchesFilePath: String = patchesFilePath

    private val _uiState = MutableStateFlow(PatchSelectionUiState(
        apkArchitectures = apkArchitectures,
        selectedArchitectures = apkArchitectures.toSet()
    ))
    val uiState: StateFlow<PatchSelectionUiState> = _uiState.asStateFlow()

    init {
        loadPatches()
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

            val packageName = getPackageNameFromApk()

            // Load patches using PatchService (direct library call)
            val patchesResult = patchService.listPatches(actualPatchesFilePath, packageName)

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

    fun toggleArchitecture(arch: String) {
        val current = _uiState.value.selectedArchitectures
        // Don't allow deselecting all architectures
        if (current.contains(arch) && current.size <= 1) return
        val newSelection = if (current.contains(arch)) {
            current - arch
        } else {
            current + arch
        }
        _uiState.value = _uiState.value.copy(selectedArchitectures = newSelection)
    }

    /**
     * Count of patches that are disabled by default (from .mpp metadata).
     */
    fun getDefaultDisabledCount(): Int {
        return _uiState.value.allPatches.count { !it.isEnabled }
    }

    fun createPatchConfig(): PatchConfig {
        // Create app folder in the same location as the input APK
        val inputFile = File(apkPath)
        val appFolderName = apkName.replace(" ", "-")
        val outputDir = File(inputFile.parentFile, appFolderName)
        outputDir.mkdirs()

        // Extract version from APK filename for output name
        val version = extractVersionFromFilename(inputFile.name) ?: "patched"
        val outputFileName = "${appFolderName}-${version}-patched.apk"
        val outputPath = File(outputDir, outputFileName).absolutePath

        // Convert unique IDs back to patch names for CLI
        val selectedPatchNames = _uiState.value.allPatches
            .filter { _uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        val disabledPatchNames = _uiState.value.allPatches
            .filter { !_uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        // Only set riplibs if user deselected any architecture (keeps = selected ones)
        val riplibs = if (_uiState.value.selectedArchitectures.size < apkArchitectures.size && apkArchitectures.size > 1) {
            _uiState.value.selectedArchitectures.toList()
        } else {
            emptyList()
        }

        return PatchConfig(
            inputApkPath = apkPath,
            outputApkPath = outputPath,
            patchesFilePath = actualPatchesFilePath,
            enabledPatches = selectedPatchNames,
            disabledPatches = disabledPatchNames,
            useExclusiveMode = true,
            riplibs = riplibs
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

    fun getApkName(): String = apkName

    /**
     * Generate a preview of the CLI command that will be executed.
     * @param cleanMode If true, formats with newlines for readability. If false, compact single-line format.
     */
    fun getCommandPreview(cleanMode: Boolean = false): String {
        val inputFile = File(apkPath)
        val patchesFile = File(actualPatchesFilePath)
        val appFolderName = apkName.replace(" ", "-")
        val version = extractVersionFromFilename(inputFile.name) ?: "patched"
        val outputFileName = "${appFolderName}-${version}-patched.apk"

        val selectedPatchNames = _uiState.value.allPatches
            .filter { _uiState.value.selectedPatches.contains(it.uniqueId) }
            .map { it.name }

        // riplibs flag: only when user deselected at least one architecture
        val riplibsArg = if (_uiState.value.selectedArchitectures.size < apkArchitectures.size && apkArchitectures.size > 1) {
            _uiState.value.selectedArchitectures.joinToString(",")
        } else {
            null
        }

        return if (cleanMode) {
            val sb = StringBuilder()
            sb.append("java -jar morphe-cli.jar patch \\\n")
            sb.append("  -p ${patchesFile.name} \\\n")
            sb.append("  -o ${outputFileName} \\\n")
            sb.append("  --exclusive \\\n")

            if (riplibsArg != null) {
                sb.append("  --riplibs $riplibsArg \\\n")
            }

            selectedPatchNames.forEachIndexed { index, patch ->
                val isLast = index == selectedPatchNames.lastIndex
                sb.append("  -e \"$patch\"")
                if (!isLast) {
                    sb.append(" \\")
                }
                sb.append("\n")
            }

            sb.append("  ${inputFile.name}")
            sb.toString()
        } else {
            // Compact mode - single line that wraps naturally
            val patches = selectedPatchNames.joinToString(" ") { "-e \"$it\"" }
            val riplibsPart = if (riplibsArg != null) " --riplibs $riplibsArg" else ""
            "java -jar morphe-cli.jar patch -p ${patchesFile.name} -o $outputFileName --exclusive$riplibsPart $patches ${inputFile.name}"
        }
    }

    /**
     * Download patches file if it's missing (e.g., after cache clear).
     * Tries to find a release matching the expected filename, or falls back to latest stable.
     */
    private suspend fun downloadMissingPatches(expectedFilename: String): Result<File> {
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

    private fun getPackageNameFromApk(): String {
        // Extract package name from APK filename (APKMirror format)
        val fileName = File(apkPath).name
        return when {
            fileName.startsWith("com.google.android.youtube_") -> "com.google.android.youtube"
            fileName.startsWith("com.google.android.apps.youtube.music_") -> "com.google.android.apps.youtube.music"
            fileName.startsWith("com.reddit.frontpage_") -> "com.reddit.frontpage"
            else -> ""
        }
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
    val selectedArchitectures: Set<String> = emptySet()
) {
    val selectedCount: Int get() = selectedPatches.size
    val totalCount: Int get() = allPatches.size
}
