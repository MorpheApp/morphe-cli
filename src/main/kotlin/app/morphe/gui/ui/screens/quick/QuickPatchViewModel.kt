package app.morphe.gui.ui.screens.quick

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.ChecksumUtils
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import java.io.File

/**
 * ViewModel for Quick Patch mode - handles the entire flow in one screen.
 */
class QuickPatchViewModel(
    private val patchRepository: PatchRepository,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(QuickPatchUiState())
    val uiState: StateFlow<QuickPatchUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null

    // Cached dynamic data from patches
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedSupportedApps: List<SupportedApp> = emptyList()
    private var cachedPatchesFile: File? = null

    init {
        // Load patches on startup to get dynamic app info
        loadPatchesAndSupportedApps()
    }

    /**
     * Load patches from GitHub and extract supported apps dynamically.
     */
    private fun loadPatchesAndSupportedApps() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            try {
                // Fetch releases
                val releasesResult = patchRepository.fetchReleases()
                val releases = releasesResult.getOrNull()

                if (releases.isNullOrEmpty()) {
                    Logger.warn("Quick mode: Could not fetch releases")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not fetch releases. Check your internet connection.")
                    return@launch
                }

                // Quick mode always uses the latest stable release
                val release = releases.firstOrNull { !it.isDevRelease() }

                if (release == null) {
                    Logger.warn("Quick mode: No suitable release found")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "No suitable release found")
                    return@launch
                }

                // Download patches
                val patchFileResult = patchRepository.downloadPatches(release)
                val patchFile = patchFileResult.getOrNull()

                if (patchFile == null) {
                    Logger.warn("Quick mode: Could not download patches")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not download patches")
                    return@launch
                }

                cachedPatchesFile = patchFile

                // Load patches using PatchService (direct library call)
                val patchesResult = patchService.listPatches(patchFile.absolutePath)
                val patches = patchesResult.getOrNull()

                if (patches.isNullOrEmpty()) {
                    Logger.warn("Quick mode: Could not load patches: ${patchesResult.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not load patches")
                    return@launch
                }

                cachedPatches = patches

                // Extract supported apps dynamically
                val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
                cachedSupportedApps = supportedApps

                Logger.info("Quick mode: Loaded ${supportedApps.size} supported apps: ${supportedApps.map { "${it.displayName} (${it.recommendedVersion})" }}")

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    supportedApps = supportedApps,
                    patchesVersion = release.tagName,
                    patchLoadError = null
                )
            } catch (e: Exception) {
                Logger.error("Quick mode: Failed to load patches", e)
                _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Failed to load patches: ${e.message}")
            }
        }
    }

    /**
     * Retry loading patches after a failure.
     */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps()
    }

    /**
     * Handle file drop or selection.
     */
    fun onFileSelected(file: File) {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.ANALYZING,
                error = null
            )

            val result = analyzeApk(file)
            if (result != null) {
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.READY,
                    apkFile = file,
                    apkInfo = result
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.IDLE,
                    error = _uiState.value.error ?: "Failed to analyze APK"
                )
            }
        }
    }

    /**
     * Analyze the APK file using dynamic data from patches.
     */
    private suspend fun analyzeApk(file: File): QuickApkInfo? {
        if (!file.exists() || !(file.name.endsWith(".apk", ignoreCase = true) || file.name.endsWith(".apkm", ignoreCase = true))) {
            _uiState.value = _uiState.value.copy(error = "Please drop a valid .apk or .apkm file")
            return null
        }

        // For .apkm files, extract base.apk first
        val isApkm = file.extension.equals("apkm", ignoreCase = true)
        val apkToParse = if (isApkm) {
            FileUtils.extractBaseApkFromApkm(file) ?: run {
                _uiState.value = _uiState.value.copy(error = "Failed to extract base.apk from APKM bundle")
                return null
            }
        } else {
            file
        }

        return try {
            ApkFile(apkToParse).use { apk ->
                val meta = apk.apkMeta
                val packageName = meta.packageName
                val versionName = meta.versionName ?: "Unknown"

                // Check if supported using dynamic data
                val dynamicAppInfo = cachedSupportedApps.find { it.packageName == packageName }

                if (dynamicAppInfo == null) {
                    // Fallback to hardcoded check if patches not loaded yet
                    val supportedPackages = if (cachedSupportedApps.isEmpty()) {
                        listOf(
                            AppConstants.YouTube.PACKAGE_NAME,
                            AppConstants.YouTubeMusic.PACKAGE_NAME,
                            AppConstants.Reddit.PACKAGE_NAME
                        )
                    } else {
                        cachedSupportedApps.map { it.packageName }
                    }

                    if (packageName !in supportedPackages) {
                        _uiState.value = _uiState.value.copy(
                            error = "Unsupported app: $packageName\n\nSupported apps: ${cachedSupportedApps.map { it.displayName }.ifEmpty { listOf("YouTube", "YouTube Music", "Reddit") }.joinToString(", ")}"
                        )
                        return null
                    }
                }

                // Get display name and recommended version from dynamic data, fallback to constants
                val displayName = dynamicAppInfo?.displayName
                    ?: SupportedApp.getDisplayName(packageName)

                val recommendedVersion = dynamicAppInfo?.recommendedVersion
                    ?: AppConstants.getSuggestedVersion(packageName)

                // Version check
                val isRecommendedVersion = recommendedVersion != null && versionName == recommendedVersion
                val versionWarning = if (!isRecommendedVersion && recommendedVersion != null) {
                    "Version $versionName may have compatibility issues. Recommended: $recommendedVersion"
                } else null

                // Checksum verification (still uses AppConstants - checksums are manually maintained)
                val checksumStatus = verifyChecksum(file, packageName, versionName, recommendedVersion)

                Logger.info("Quick mode: Analyzed $displayName v$versionName (recommended: $recommendedVersion)")

                QuickApkInfo(
                    fileName = file.name,
                    packageName = packageName,
                    versionName = versionName,
                    fileSize = file.length(),
                    displayName = displayName,
                    recommendedVersion = recommendedVersion,
                    isRecommendedVersion = isRecommendedVersion,
                    versionWarning = versionWarning,
                    checksumStatus = checksumStatus
                )
            }
        } catch (e: Exception) {
            Logger.error("Quick mode: Failed to analyze APK", e)
            _uiState.value = _uiState.value.copy(error = "Failed to read APK: ${e.message}")
            null
        } finally {
            if (isApkm) apkToParse.delete()
        }
    }

    /**
     * Verify checksum against known values.
     */
    private fun verifyChecksum(file: File, packageName: String, version: String, recommendedVersion: String?): ChecksumStatus {
        // Check if this is a non-recommended version (use dynamic recommended version)
        if (recommendedVersion != null && version != recommendedVersion) {
            return ChecksumStatus.NonRecommendedVersion
        }

        val expectedChecksum = AppConstants.getChecksum(packageName, version, emptyList())
            ?: return ChecksumStatus.NotConfigured

        return try {
            val actualChecksum = ChecksumUtils.calculateSha256(file)
            if (actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                ChecksumStatus.Verified
            } else {
                ChecksumStatus.Mismatch(expectedChecksum, actualChecksum)
            }
        } catch (e: Exception) {
            ChecksumStatus.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Start the patching process with defaults.
     */
    fun startPatching() {
        val apkFile = _uiState.value.apkFile ?: return
        val apkInfo = _uiState.value.apkInfo ?: return

        patchingJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.DOWNLOADING,
                progress = 0f,
                statusMessage = "Preparing patches..."
            )

            // Use cached patches file if available, otherwise download
            val patchFile = if (cachedPatchesFile?.exists() == true) {
                _uiState.value = _uiState.value.copy(progress = 0.3f)
                cachedPatchesFile!!
            } else {
                // Download patches
                val patchesResult = patchRepository.getLatestStableRelease()
                val patchRelease = patchesResult.getOrNull()
                if (patchRelease == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Failed to fetch patches. Check your internet connection."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Downloading patches ${patchRelease.tagName}..."
                )

                val patchFileResult = patchRepository.downloadPatches(patchRelease) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress * 0.3f)
                }

                val downloadedFile = patchFileResult.getOrNull()
                if (downloadedFile == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Failed to download patches: ${patchFileResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
                cachedPatchesFile = downloadedFile
                downloadedFile
            }

            // 2. Start patching
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.PATCHING,
                statusMessage = "Patching...",
                progress = 0.4f
            )

            // Generate output path
            val outputDir = apkFile.parentFile ?: File(System.getProperty("user.home"))
            val baseName = apkInfo.displayName.replace(" ", "-")
            val outputFileName = "$baseName-Morphe-${apkInfo.versionName}.apk"
            val outputPath = File(outputDir, outputFileName).absolutePath

            // Use PatchService for direct library patching (no CLI subprocess)
            // exclusiveMode = false means the library's patch.use field determines defaults
            val patchResult = patchService.patch(
                patchesFilePath = patchFile.absolutePath,
                inputApkPath = apkFile.absolutePath,
                outputApkPath = outputPath,
                enabledPatches = emptyList(),
                disabledPatches = emptyList(),
                options = emptyMap(),
                exclusiveMode = false,
                onProgress = { message ->
                    // Update status with current operation
                    if (message.contains("patch", ignoreCase = true) ||
                        message.contains("applying", ignoreCase = true) ||
                        message.contains("Applied", ignoreCase = true)) {
                        _uiState.value = _uiState.value.copy(statusMessage = message.take(60))
                    }
                    // Parse progress
                    parseProgress(message)
                }
            )

            patchResult.fold(
                onSuccess = { result ->
                    if (result.success) {
                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.COMPLETED,
                            outputPath = outputPath,
                            progress = 1f,
                            statusMessage = "Patching complete! Applied ${result.appliedPatches.size} patches."
                        )
                        Logger.info("Quick mode: Patching completed - $outputPath (${result.appliedPatches.size} patches)")
                    } else {
                        val errorMsg = if (result.failedPatches.isNotEmpty()) {
                            "Patching had failures: ${result.failedPatches.joinToString(", ")}"
                        } else {
                            "Patching failed. Please try the full mode for more details."
                        }
                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.READY,
                            error = errorMsg
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Error: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * Parse progress from CLI output.
     */
    private fun parseProgress(line: String) {
        // Pattern: "Executing patch X of Y"
        val executingPattern = Regex("""(?:Executing|Applying)\s+patch\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)
        val match = executingPattern.find(line)
        if (match != null) {
            val current = match.groupValues[1].toIntOrNull() ?: 0
            val total = match.groupValues[2].toIntOrNull() ?: 1
            val patchProgress = current.toFloat() / total.toFloat()
            // Patching is 50-100% of total progress
            _uiState.value = _uiState.value.copy(
                progress = 0.5f + patchProgress * 0.5f
            )
        }
    }

    /**
     * Cancel patching.
     */
    fun cancelPatching() {
        patchingJob?.cancel()
        patchingJob = null
        _uiState.value = _uiState.value.copy(
            phase = QuickPatchPhase.READY,
            statusMessage = "Cancelled"
        )
    }

    /**
     * Reset to start over.
     */
    fun reset() {
        patchingJob?.cancel()
        patchingJob = null
        _uiState.value = QuickPatchUiState(
            // Preserve already-loaded patches data
            isLoadingPatches = false,
            supportedApps = cachedSupportedApps,
            patchesVersion = _uiState.value.patchesVersion
        )
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }
}

/**
 * Phases of the quick patch flow.
 */
enum class QuickPatchPhase {
    IDLE,           // Waiting for APK
    ANALYZING,      // Reading APK info
    READY,          // APK validated, ready to patch
    DOWNLOADING,    // Downloading patches/CLI
    PATCHING,       // Running patch command
    COMPLETED       // Done!
}

/**
 * Simplified APK info for quick mode.
 * Uses dynamic data from patches instead of hardcoded values.
 */
data class QuickApkInfo(
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val fileSize: Long,
    val displayName: String,
    val recommendedVersion: String?,
    val isRecommendedVersion: Boolean,
    val versionWarning: String?,
    val checksumStatus: ChecksumStatus
) {
    val formattedSize: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "%.1f KB".format(fileSize / 1024.0)
            fileSize < 1024 * 1024 * 1024 -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
            else -> "%.2f GB".format(fileSize / (1024.0 * 1024.0 * 1024.0))
        }
}

/**
 * UI state for quick patch mode.
 */
data class QuickPatchUiState(
    val phase: QuickPatchPhase = QuickPatchPhase.IDLE,
    val apkFile: File? = null,
    val apkInfo: QuickApkInfo? = null,
    val error: String? = null,
    val isDragHovering: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val outputPath: String? = null,
    // Dynamic data from patches
    val isLoadingPatches: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val patchLoadError: String? = null
)
