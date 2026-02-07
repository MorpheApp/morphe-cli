package app.morphe.gui.ui.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import java.io.File

class HomeViewModel(
    private val patchRepository: PatchRepository,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cached patches and supported apps
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedPatchesFile: File? = null

    init {
        // Auto-fetch patches on startup
        loadPatchesAndSupportedApps()
    }

    // Track the last loaded version to avoid reloading unnecessarily
    private var lastLoadedVersion: String? = null

    /**
     * Load patches from GitHub and extract supported apps.
     * If a saved version exists in config, load that version instead of latest.
     */
    private fun loadPatchesAndSupportedApps(forceRefresh: Boolean = false) {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            try {
                // Check if there's a saved patches version in config
                val config = configRepository.loadConfig()
                val savedVersion = config.lastPatchesVersion

                // 1. Fetch all releases to find the right one
                val releasesResult = patchRepository.fetchReleases()
                val releases = releasesResult.getOrNull()

                if (releases.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "Could not fetch patches: ${releasesResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                // Find the latest stable release for reference
                val latestStable = releases.firstOrNull { !it.isDevRelease() }
                val latestVersion = latestStable?.tagName

                // 2. Find the release to use - prefer saved version, fallback to latest stable
                val release = if (savedVersion != null) {
                    releases.find { it.tagName == savedVersion }
                        ?: latestStable  // Fallback to latest stable
                } else {
                    latestStable  // Latest stable
                }

                if (release == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "No suitable release found"
                    )
                    return@launch
                }

                // Skip reload if we've already loaded this version (unless forced)
                if (!forceRefresh && lastLoadedVersion == release.tagName && cachedPatchesFile?.exists() == true) {
                    Logger.info("Skipping reload - already loaded version ${release.tagName}")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false)
                    return@launch
                }

                Logger.info("Loading patches version: ${release.tagName} (saved=$savedVersion)")

                // 3. Download patches
                val patchFileResult = patchRepository.downloadPatches(release)
                val patchFile = patchFileResult.getOrNull()

                if (patchFile == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "Could not download patches: ${patchFileResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                cachedPatchesFile = patchFile
                lastLoadedVersion = release.tagName

                // 3. Load patches using PatchService (direct library call)
                val patchesResult = patchService.listPatches(patchFile.absolutePath)
                val patches = patchesResult.getOrNull()

                if (patches == null || patches.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "Could not load patches: ${patchesResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                cachedPatches = patches

                // 5. Extract supported apps
                val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
                Logger.info("Loaded ${supportedApps.size} supported apps from patches: ${supportedApps.map { "${it.displayName} (${it.recommendedVersion})" }}")

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    supportedApps = supportedApps,
                    patchesVersion = release.tagName,
                    latestPatchesVersion = latestVersion,
                    patchLoadError = null
                )
            } catch (e: Exception) {
                Logger.error("Failed to load patches and supported apps", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Retry loading patches.
     */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps(forceRefresh = true)
    }

    /**
     * Refresh patches if a different version was selected.
     * Called when returning to HomeScreen from PatchesScreen.
     */
    fun refreshPatchesIfNeeded() {
        screenModelScope.launch {
            val config = configRepository.loadConfig()
            val savedVersion = config.lastPatchesVersion

            // If saved version differs from currently loaded version, reload
            if (savedVersion != null && savedVersion != lastLoadedVersion) {
                Logger.info("Patches version changed: $lastLoadedVersion -> $savedVersion, reloading...")
                loadPatchesAndSupportedApps(forceRefresh = true)
            }
        }
    }

    /**
     * Get the cached patches file path for navigation to next screen.
     */
    fun getCachedPatchesFile(): File? = cachedPatchesFile

    /**
     * Get recommended version for a package from loaded patches.
     */
    fun getRecommendedVersion(packageName: String): String? {
        return SupportedAppExtractor.getRecommendedVersion(cachedPatches, packageName)
    }

    fun onFileSelected(file: File) {
        screenModelScope.launch {
            Logger.info("File selected: ${file.absolutePath}")

            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            val validationResult = withContext(Dispatchers.IO) {
                validateAndAnalyzeApk(file)
            }

            if (validationResult.isValid) {
                _uiState.value = _uiState.value.copy(
                    selectedApk = file,
                    apkInfo = validationResult.apkInfo,
                    error = null,
                    isReady = true,
                    isAnalyzing = false
                )
                Logger.info("APK analyzed successfully: ${validationResult.apkInfo?.appName ?: file.name}")
            } else {
                _uiState.value = _uiState.value.copy(
                    selectedApk = null,
                    apkInfo = null,
                    error = validationResult.errorMessage,
                    isReady = false,
                    isAnalyzing = false
                )
                Logger.warn("APK validation failed: ${validationResult.errorMessage}")
            }
        }
    }

    fun onFilesDropped(files: List<File>) {
        val apkFile = files.firstOrNull { FileUtils.isApkFile(it) }
        if (apkFile != null) {
            onFileSelected(apkFile)
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Please drop a valid .apk or .apkm file",
                isReady = false
            )
        }
    }

    fun clearSelection() {
        // Preserve loaded patches state when clearing APK selection
        _uiState.value = _uiState.value.copy(
            selectedApk = null,
            apkInfo = null,
            error = null,
            isDragHovering = false,
            isReady = false,
            isAnalyzing = false
        )
        Logger.info("APK selection cleared")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }

    private fun validateAndAnalyzeApk(file: File): ApkValidationResult {
        if (!file.exists()) {
            return ApkValidationResult(false, errorMessage = "File does not exist")
        }

        if (!file.isFile) {
            return ApkValidationResult(false, errorMessage = "Selected item is not a file")
        }

        if (!FileUtils.isApkFile(file)) {
            return ApkValidationResult(false, errorMessage = "File must have .apk or .apkm extension")
        }

        if (file.length() < 1024) {
            return ApkValidationResult(false, errorMessage = "File is too small to be a valid APK")
        }

        // Parse APK info from AndroidManifest.xml using apk-parser
        val apkInfo = parseApkManifest(file)

        return if (apkInfo != null) {
            ApkValidationResult(true, apkInfo = apkInfo)
        } else {
            ApkValidationResult(false, errorMessage = "Could not parse APK. The file may be corrupted or not a valid APK.")
        }
    }

    /**
     * Parse APK metadata directly from AndroidManifest.xml using apk-parser library.
     * This works with APKs from any source, not just APKMirror.
     */
    private fun parseApkManifest(file: File): ApkInfo? {
        // For .apkm files, extract base.apk first
        val isApkm = file.extension.equals("apkm", ignoreCase = true)
        val apkToParse = if (isApkm) {
            FileUtils.extractBaseApkFromApkm(file) ?: run {
                Logger.error("Failed to extract base.apk from APKM: ${file.name}")
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
                val minSdk = meta.minSdkVersion?.toIntOrNull()

                // Check if package is supported - first check dynamic, then fallback to hardcoded
                val dynamicSupportedApp = _uiState.value.supportedApps.find { it.packageName == packageName }
                val isSupported = dynamicSupportedApp != null ||
                    packageName in listOf(
                        app.morphe.gui.data.constants.AppConstants.YouTube.PACKAGE_NAME,
                        app.morphe.gui.data.constants.AppConstants.YouTubeMusic.PACKAGE_NAME
                    )

                if (!isSupported) {
                    Logger.warn("Unsupported package: $packageName")
                    return null
                }

                // Get app display name - prefer dynamic, fallback to hardcoded
                val appName = dynamicSupportedApp?.displayName
                    ?: SupportedApp.getDisplayName(packageName)

                // Get recommended version - prefer dynamic, fallback to hardcoded
                val suggestedVersion = dynamicSupportedApp?.recommendedVersion
                    ?: app.morphe.gui.data.constants.AppConstants.getSuggestedVersion(packageName)

                // Determine AppType for backward compatibility (still used in some places)
                val appType = when (packageName) {
                    app.morphe.gui.data.constants.AppConstants.YouTube.PACKAGE_NAME -> AppType.YOUTUBE
                    app.morphe.gui.data.constants.AppConstants.YouTubeMusic.PACKAGE_NAME -> AppType.YOUTUBE_MUSIC
                    else -> null
                }

                // Compare versions if we have a suggested version
                val versionStatus = if (suggestedVersion != null) {
                    compareVersions(versionName, suggestedVersion)
                } else {
                    VersionStatus.UNKNOWN
                }

                // Get supported architectures from native libraries in the APK
                val architectures = extractArchitectures(apkToParse)

                // Verify checksum (still uses AppConstants for now)
                val checksumStatus = verifyChecksum(file, packageName, versionName, architectures, suggestedVersion)

                Logger.info("Parsed APK: $packageName v$versionName (recommended=$suggestedVersion, minSdk=$minSdk, archs=$architectures)")

                ApkInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    formattedSize = formatFileSize(file.length()),
                    appName = appName,
                    appType = appType,
                    packageName = packageName,
                    versionName = versionName,
                    architectures = architectures,
                    minSdk = minSdk,
                    suggestedVersion = suggestedVersion,
                    versionStatus = versionStatus,
                    checksumStatus = checksumStatus
                )
            }
        } catch (e: Exception) {
            Logger.error("Failed to parse APK manifest", e)
            null
        } finally {
            if (isApkm) apkToParse.delete()
        }
    }

    /**
     * Extract supported CPU architectures from native libraries in the APK.
     * Uses ZipFile to scan for lib/<arch>/ directories.
     */
    private fun extractArchitectures(file: File): List<String> {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val archDirs = zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") }
                    .mapNotNull { path ->
                        val parts = path.split("/")
                        if (parts.size >= 2) parts[1] else null
                    }
                    .distinct()
                    .toList()

                archDirs.ifEmpty {
                    // No native libs - likely a universal APK
                    listOf("universal")
                }
            }
        } catch (e: Exception) {
            Logger.warn("Could not extract architectures: ${e.message}")
            emptyList()
        }
    }

    /**
     * Verify the APK checksum against expected values.
     */
    private fun verifyChecksum(
        file: File,
        packageName: String,
        version: String,
        architectures: List<String>,
        recommendedVersion: String?
    ): app.morphe.gui.util.ChecksumStatus {
        // Check if this is a non-recommended version (use dynamic recommended version)
        if (recommendedVersion != null && version != recommendedVersion) {
            return app.morphe.gui.util.ChecksumStatus.NonRecommendedVersion
        }

        // Get expected checksum (still from AppConstants - checksums are manually maintained)
        val expectedChecksum = app.morphe.gui.data.constants.AppConstants.getChecksum(packageName, version, architectures)
        if (expectedChecksum == null) {
            return app.morphe.gui.util.ChecksumStatus.NotConfigured
        }

        // Calculate actual checksum
        return try {
            val actualChecksum = app.morphe.gui.util.ChecksumUtils.calculateSha256(file)
            Logger.info("Checksum verification - Expected: $expectedChecksum, Actual: $actualChecksum")

            if (actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                app.morphe.gui.util.ChecksumStatus.Verified
            } else {
                app.morphe.gui.util.ChecksumStatus.Mismatch(expectedChecksum, actualChecksum)
            }
        } catch (e: Exception) {
            Logger.error("Checksum calculation failed", e)
            app.morphe.gui.util.ChecksumStatus.Error(e.message ?: "Unknown error")
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

    /**
     * Compares two version strings (e.g., "19.16.39" vs "20.40.45")
     * Returns the version status of the current version relative to suggested.
     */
    private fun compareVersions(current: String, suggested: String): VersionStatus {
        return try {
            val currentParts = current.split(".").map { it.toInt() }
            val suggestedParts = suggested.split(".").map { it.toInt() }

            // Compare each part
            for (i in 0 until maxOf(currentParts.size, suggestedParts.size)) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val suggestedPart = suggestedParts.getOrElse(i) { 0 }

                when {
                    currentPart > suggestedPart -> return VersionStatus.NEWER_VERSION
                    currentPart < suggestedPart -> return VersionStatus.OLDER_VERSION
                }
            }
            VersionStatus.EXACT_MATCH
        } catch (e: Exception) {
            Logger.warn("Failed to compare versions: $current vs $suggested")
            VersionStatus.UNKNOWN
        }
    }
}

data class HomeUiState(
    val selectedApk: File? = null,
    val apkInfo: ApkInfo? = null,
    val error: String? = null,
    val isDragHovering: Boolean = false,
    val isReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    // Dynamic patches data
    val isLoadingPatches: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val latestPatchesVersion: String? = null,  // Track the latest available version
    val patchLoadError: String? = null
) {
    val isUsingLatestPatches: Boolean
        get() = patchesVersion != null && patchesVersion == latestPatchesVersion
}

enum class AppType(
    val displayName: String,
    val packageName: String,
    val suggestedVersion: String
) {
    YOUTUBE(
        displayName = app.morphe.gui.data.constants.AppConstants.YouTube.DISPLAY_NAME,
        packageName = app.morphe.gui.data.constants.AppConstants.YouTube.PACKAGE_NAME,
        suggestedVersion = app.morphe.gui.data.constants.AppConstants.YouTube.SUGGESTED_VERSION
    ),
    YOUTUBE_MUSIC(
        displayName = app.morphe.gui.data.constants.AppConstants.YouTubeMusic.DISPLAY_NAME,
        packageName = app.morphe.gui.data.constants.AppConstants.YouTubeMusic.PACKAGE_NAME,
        suggestedVersion = app.morphe.gui.data.constants.AppConstants.YouTubeMusic.SUGGESTED_VERSION
    )
}

data class ApkInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val formattedSize: String,
    val appName: String,
    val appType: AppType?, // Nullable for dynamically supported apps not in the enum
    val packageName: String,
    val versionName: String,
    val architectures: List<String> = emptyList(),
    val minSdk: Int? = null,
    val suggestedVersion: String? = null,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val checksumStatus: app.morphe.gui.util.ChecksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured
)

enum class VersionStatus {
    EXACT_MATCH,      // Using the suggested version
    OLDER_VERSION,    // Using an older version (newer patches available)
    NEWER_VERSION,    // Using a newer version (might have issues)
    UNKNOWN           // Could not determine
}

data class ApkValidationResult(
    val isValid: Boolean,
    val apkInfo: ApkInfo? = null,
    val errorMessage: String? = null
)
