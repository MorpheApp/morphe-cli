package app.morphe.gui.ui.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import java.io.File

class HomeViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository
) : ScreenModel {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(HomeUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cached patches and supported apps
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedPatchesFile: File? = null
    private var loadJob: Job? = null

    init {
        // Auto-fetch patches on startup
        loadPatchesAndSupportedApps()

        // Observe source changes — drop(1) to skip the initial value
        screenModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                Logger.info("HomeVM: Source changed, reloading patches...")
                patchRepository = patchSourceManager.getActiveRepositorySync()
                localPatchFilePath = patchSourceManager.getLocalFilePath()
                isDefaultSource = patchSourceManager.isDefaultSource()
                lastLoadedVersion = null
                cachedPatchesFile = null
                _uiState.value = HomeUiState(isDefaultSource = isDefaultSource)
                loadPatchesAndSupportedApps(forceRefresh = true)
            }
        }
    }

    // Track the last loaded version to avoid reloading unnecessarily
    private var lastLoadedVersion: String? = null

    /**
     * Load patches from GitHub and extract supported apps.
     * If a saved version exists in config, load that version instead of latest.
     */
    private fun loadPatchesAndSupportedApps(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            // LOCAL source: skip GitHub entirely, load directly from the .mpp file
            if (localPatchFilePath != null) {
                val localFile = File(localPatchFilePath)
                if (localFile.exists()) {
                    loadPatchesFromFile(localFile, localFile.nameWithoutExtension, latestVersion = null, isOffline = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "Local patch file not found: ${localFile.name}"
                    )
                }
                return@launch
            }

            try {
                // Check if there's a saved patches version in config
                val config = configRepository.loadConfig()
                val savedVersion = config.lastPatchesVersion

                // 1. Fetch all releases to find the right one
                val releasesResult = patchRepository.fetchReleases()
                val releases = releasesResult.getOrNull()

                if (releases.isNullOrEmpty()) {
                    // Try to fall back to cached .mpp file when offline
                    val offlinePatchFile = findCachedPatchFile(savedVersion)
                    if (offlinePatchFile != null) {
                        loadPatchesFromFile(offlinePatchFile, versionFromFilename(offlinePatchFile), latestVersion = null)
                        return@launch
                    }
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
                    val rawError = patchesResult.exceptionOrNull()?.message ?: "Unknown error"
                    val friendlyError = if (rawError.contains("zip", ignoreCase = true) || rawError.contains("END header", ignoreCase = true)) {
                        "Patch file is missing or corrupted. Clear cache and re-download."
                    } else {
                        "Could not load patches: $rawError"
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = friendlyError
                    )
                    return@launch
                }

                cachedPatches = patches

                // 5. Extract supported apps
                val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
                Logger.info("Loaded ${supportedApps.size} supported apps from patches: ${supportedApps.map { "${it.displayName} (${it.recommendedVersion})" }}")

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    isOffline = false,
                    supportedApps = supportedApps,
                    patchesVersion = release.tagName,
                    latestPatchesVersion = latestVersion,
                    patchLoadError = null
                )
            } catch (e: Exception) {
                Logger.error("Failed to load patches and supported apps", e)
                // Try to fall back to cached .mpp file
                val config = configRepository.loadConfig()
                val offlinePatchFile = findCachedPatchFile(config.lastPatchesVersion)
                if (offlinePatchFile != null) {
                    try {
                        loadPatchesFromFile(offlinePatchFile, versionFromFilename(offlinePatchFile), latestVersion = null)
                        return@launch
                    } catch (inner: Exception) {
                        Logger.error("Failed to load cached patches fallback", inner)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Find any cached .mpp file when offline.
     * Prefers the file matching savedVersion from config.
     * Searches the per-source cache directory.
     */
    private fun findCachedPatchFile(savedVersion: String?): File? {
        val patchesDir = patchRepository.getCacheDir()
        val patchFiles = patchesDir.listFiles { file ->
            val ext = file.extension.lowercase()
            ext == "mpp" || ext == "jar"
        }?.filter { it.length() > 0 } ?: return null

        if (patchFiles.isEmpty()) return null

        return if (savedVersion != null) {
            // Strip "v" prefix — savedVersion is "v1.13.0" but filenames are "patches-1.13.0.mpp"
            val versionNumber = savedVersion.removePrefix("v")
            patchFiles.firstOrNull { it.name.contains(versionNumber, ignoreCase = true) }
                ?: patchFiles.maxByOrNull { it.lastModified() }
        } else {
            patchFiles.maxByOrNull { it.lastModified() }
        }
    }

    /**
     * Extract a version string from an .mpp filename (e.g. "morphe-patches-1.3.0.mpp" -> "v1.3.0").
     */
    private fun versionFromFilename(file: File): String {
        val name = file.nameWithoutExtension
        // Try to find a version pattern like 1.2.3 or v1.2.3
        val match = Regex("""v?(\d+\.\d+\.\d+[^\s]*)""").find(name)
        return match?.value ?: name
    }

    /**
     * Load patches from a local .mpp file and update UI state.
     * Used as fallback when offline with cached patches.
     */
    private suspend fun loadPatchesFromFile(patchFile: File, version: String, latestVersion: String?, isOffline: Boolean = true) {
        cachedPatchesFile = patchFile
        lastLoadedVersion = version

        val patchesResult = patchService.listPatches(patchFile.absolutePath)
        val patches = patchesResult.getOrNull()

        if (patches == null || patches.isEmpty()) {
            val rawError = patchesResult.exceptionOrNull()?.message ?: "Unknown error"
            val friendlyError = if (rawError.contains("zip", ignoreCase = true) || rawError.contains("END header", ignoreCase = true)) {
                "Patch file is missing or corrupted. Clear cache and re-download."
            } else {
                "Could not load patches: $rawError"
            }
            _uiState.value = _uiState.value.copy(
                isLoadingPatches = false,
                patchLoadError = friendlyError
            )
            return
        }

        cachedPatches = patches
        val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
        Logger.info("Loaded ${supportedApps.size} supported apps from ${if (isOffline) "cached" else "local"} patches: ${patchFile.name}")

        _uiState.value = _uiState.value.copy(
            isLoadingPatches = false,
            isOffline = isOffline,
            supportedApps = supportedApps,
            patchesVersion = version,
            latestPatchesVersion = latestVersion,
            patchLoadError = null
        )
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

                // Get recommended version from dynamic patches data (no hardcoded fallback)
                val suggestedVersion = dynamicSupportedApp?.recommendedVersion

                // Compare versions if we have a suggested version
                val versionStatus = if (suggestedVersion != null) {
                    compareVersions(versionName, suggestedVersion)
                } else {
                    VersionStatus.UNKNOWN
                }

                // Get supported architectures from native libraries
                // For .apkm files, scan the original bundle (splits contain the native libs, not base.apk)
                val architectures = extractArchitectures(if (isApkm) file else apkToParse)

                // TODO: Re-enable when checksums are provided via .mpp files
                val checksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured

                Logger.info("Parsed APK: $packageName v$versionName (recommended=$suggestedVersion, minSdk=$minSdk, archs=$architectures)")

                ApkInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    formattedSize = formatFileSize(file.length()),
                    appName = appName,
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
                val archDirs = mutableSetOf<String>()

                // Scan for lib/<arch>/ entries directly (regular APK or merged APK)
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") }
                    .mapNotNull { path ->
                        val parts = path.split("/")
                        if (parts.size >= 2) parts[1] else null
                    }
                    .forEach { archDirs.add(it) }

                // For .apkm bundles: also detect arch from split APK names
                // e.g. split_config.arm64_v8a.apk -> arm64-v8a
                if (archDirs.isEmpty()) {
                    val knownArchs = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".apk") }
                        .forEach { name ->
                            // Convert split_config.arm64_v8a.apk format to arm64-v8a
                            val normalized = name.replace("_", "-")
                            knownArchs.filter { arch -> normalized.contains(arch) }
                                .forEach { archDirs.add(it) }
                        }
                }

                archDirs.toList().ifEmpty {
                    listOf("universal")
                }
            }
        } catch (e: Exception) {
            Logger.warn("Could not extract architectures: ${e.message}")
            emptyList()
        }
    }

    // TODO: Re-enable checksum verification when checksums are provided via .mpp files
    // private fun verifyChecksum(
    //     file: File, packageName: String, version: String,
    //     architectures: List<String>, recommendedVersion: String?
    // ): app.morphe.gui.util.ChecksumStatus { ... }

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
    val isOffline: Boolean = false,
    val isDefaultSource: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val latestPatchesVersion: String? = null,  // Track the latest available version
    val patchLoadError: String? = null
) {
    val isUsingLatestPatches: Boolean
        get() = patchesVersion != null && patchesVersion == latestPatchesVersion
}

data class ApkInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val formattedSize: String,
    val appName: String,
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
