/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.engine.UpdateInfo
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
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
import app.morphe.gui.util.VersionStatus
import java.io.File

class HomeViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
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

        // Background CLI update check — non-blocking, banner only.
        screenModelScope.launch {
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
            )
        }

        // Observe source changes — drop(1) to skip the initial value
        screenModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                Logger.info("HomeVM: Source changed, reloading patches...")
                patchRepository = patchSourceManager.getActiveRepositorySync()
                localPatchFilePath = patchSourceManager.getLocalFilePath()
                isDefaultSource = patchSourceManager.isDefaultSource()
                lastLoadedVersion = null
                cachedPatchesFile = null
                // Preserve update banner state across source changes.
                val carriedUpdate = _uiState.value.updateInfo
                val carriedDismissed = _uiState.value.dismissedUpdateVersion
                _uiState.value = HomeUiState(
                    isDefaultSource = isDefaultSource,
                    updateInfo = carriedUpdate,
                    dismissedUpdateVersion = carriedDismissed,
                )
                loadPatchesAndSupportedApps(forceRefresh = true)
            }
        }
    }

    /**
     * Re-run the update check. Called by Settings after the user changes the
     * update channel preference so the banner state matches the new channel
     * without waiting for a restart.
     */
    fun refreshUpdateCheck() {
        Logger.info("HomeVM: refreshUpdateCheck() called")
        screenModelScope.launch {
            updateCheckRepository.clearCache()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            Logger.info("HomeVM: refresh result — info=${info?.latestVersion}, dismissed=$dismissed")
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                updateBannerSessionDismissed = false,
            )
        }
    }

    /**
     * Hide the update banner for the rest of this app session only. The banner
     * will reappear on next startup. Cheap path for users who want to be
     * reminded but not nagged right now.
     */
    fun dismissUpdateForSession() {
        _uiState.value = _uiState.value.copy(updateBannerSessionDismissed = true)
    }

    /**
     * Hide the update banner persistently for the current available version.
     * The banner will reappear automatically when an even newer version becomes
     * available.
     */
    fun dismissUpdateForVersion() {
        val target = _uiState.value.updateInfo?.latestVersion ?: return
        _uiState.value = _uiState.value.copy(dismissedUpdateVersion = target)
        screenModelScope.launch {
            configRepository.setDismissedUpdateVersion(target)
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
                val latestDevVersion = releases.firstOrNull { it.isDevRelease() }?.tagName

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
                    latestDevPatchesVersion = latestDevVersion,
                    patchSourceName = patchSourceManager.getActiveSourceName(),
                    patchLoadError = null
                )
                reanalyzeSelectedApk()
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
            patchSourceName = patchSourceManager.getActiveSourceName(),
            patchLoadError = null
        )
        reanalyzeSelectedApk()
    }

    /**
     * Re-runs APK analysis against the freshly-loaded `supportedApps` so the info
     * card reflects the new patch file's version compatibility (e.g. a v23 file
     * marks the APK "too new", but switching to v24 should clear that warning).
     */
    private suspend fun reanalyzeSelectedApk() {
        val file = _uiState.value.selectedApk ?: return
        val refreshed = withContext(Dispatchers.IO) { parseApkManifest(file) } ?: return
        _uiState.value = _uiState.value.copy(apkInfo = refreshed)
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
                error = "Please drop a valid .apk, .apkm, .xapk, or .apks file",
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
            return ApkValidationResult(false, errorMessage = "File must have .apk, .apkm, .xapk, or .apks extension")
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
        // For split APK bundles (.apkm, .xapk, .apks), extract base.apk first
        val isBundleFormat = FileUtils.isBundleFormat(file)
        val apkToParse = if (isBundleFormat) {
            FileUtils.extractBaseApkFromBundle(file) ?: run {
                Logger.error("Failed to extract base APK from bundle: ${file.name}")
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
                    Logger.warn("Unsupported package: $packageName — no compatible patches found")
                }

                // Get app display name - prefer dynamic, fallback to hardcoded, then package name
                val appName = dynamicSupportedApp?.displayName
                    ?: SupportedApp.resolveDisplayName(packageName, meta.label)

                // Resolve the version against the supported app's stable +
                // experimental version lists.
                val versionResolution = if (dynamicSupportedApp != null) {
                    app.morphe.gui.util.resolveVersionStatus(versionName, dynamicSupportedApp)
                } else {
                    app.morphe.gui.util.VersionResolution(VersionStatus.UNKNOWN, null)
                }
                val suggestedVersion = versionResolution.suggestedVersion
                val versionStatus = versionResolution.status

                // Get supported architectures from native libraries
                // For split bundles, scan the original bundle (splits contain the native libs, not base.apk)
                val architectures = FileUtils.extractArchitectures(if (isBundleFormat) file else apkToParse)

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
                    checksumStatus = checksumStatus,
                    isUnsupportedApp = !isSupported
                )
            }
        } catch (e: Exception) {
            Logger.error("Failed to parse APK manifest", e)
            null
        } finally {
            if (isBundleFormat) apkToParse.delete()
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

    // compareVersions and VersionStatus moved to app.morphe.gui.util.VersionUtils
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
    val latestPatchesVersion: String? = null,
    val latestDevPatchesVersion: String? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    /** Session-only dismiss; cleared on next app start. Not persisted. */
    val updateBannerSessionDismissed: Boolean = false,
) {
    /**
     * Show the update banner only when an update was found AND the user hasn't
     * dismissed THAT specific version persistently AND hasn't dismissed it for
     * this session. A newer version invalidates the persistent dismissal.
     */
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed

    val isUsingLatestPatches: Boolean
        get() = patchesVersion != null &&
                (patchesVersion == latestPatchesVersion || patchesVersion == latestDevPatchesVersion)

    /**
     * Label for the LATEST badge — distinguishes stable vs dev so users can tell
     * which channel they're on at a glance. Null when the loaded version isn't
     * the newest of either channel.
     */
    val latestPatchesLabel: String?
        get() = when (patchesVersion) {
            null -> null
            latestPatchesVersion -> "LATEST STABLE"
            latestDevPatchesVersion -> "LATEST DEV"
            else -> null
        }
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
    val checksumStatus: app.morphe.gui.util.ChecksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured,
    val isUnsupportedApp: Boolean = false
)

data class ApkValidationResult(
    val isValid: Boolean,
    val apkInfo: ApkInfo? = null,
    val errorMessage: String? = null
)
