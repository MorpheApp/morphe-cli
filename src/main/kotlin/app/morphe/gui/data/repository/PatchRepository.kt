/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.gui.data.model.Release
import app.morphe.gui.data.model.ReleaseAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.io.File

/**
 * Repository for fetching patches from GitHub releases.
 * @param repoPath GitHub repo in "owner/repo" format (e.g. "MorpheApp/morphe-patches")
 */
class PatchRepository(
    private val httpClient: HttpClient,
    private val repoPath: String = DEFAULT_REPO
) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val DEFAULT_REPO = "MorpheApp/morphe-patches"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val releasesEndpoint = "$GITHUB_API_BASE/repos/$repoPath/releases"

    // In-memory cache so multiple callers (both modes) don't re-fetch from GitHub
    private var cachedReleases: List<Release>? = null
    private var cacheTimestamp: Long = 0L

    /**
     * Fetch all releases from GitHub. Returns cached result if still fresh.
     * @param forceRefresh bypass the in-memory cache
     */
    suspend fun fetchReleases(forceRefresh: Boolean = false): Result<List<Release>> = withContext(Dispatchers.IO) {
        // Return cached releases if still fresh
        val cached = cachedReleases
        if (!forceRefresh && cached != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            Logger.info("Using cached releases (${cached.size} releases, age=${(System.currentTimeMillis() - cacheTimestamp) / 1000}s)")
            return@withContext Result.success(cached)
        }

        try {
            Logger.info("Fetching releases from $releasesEndpoint")
            val response: HttpResponse = httpClient.get(releasesEndpoint) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-GitHub-Api-Version", "2022-11-28")
                }
            }

            if (response.status.isSuccess()) {
                val releases: List<Release> = response.body()
                Logger.info("Fetched ${releases.size} releases from $releasesEndpoint")
                cachedReleases = releases
                cacheTimestamp = System.currentTimeMillis()
                Result.success(releases)
            } else {
                val error = "Failed to fetch releases: ${response.status}"
                Logger.error(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Logger.error("Error fetching releases", e)
            // If we have stale cached data, return it rather than failing
            val stale = cachedReleases
            if (stale != null) {
                Logger.info("Returning stale cached releases after fetch failure")
                Result.success(stale)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Get stable releases only (non-prerelease).
     */
    suspend fun fetchStableReleases(): Result<List<Release>> {
        return fetchReleases().map { releases ->
            releases.filter { !it.isDevRelease() }
        }
    }

    /**
     * Get dev/prerelease versions only.
     */
    suspend fun fetchDevReleases(): Result<List<Release>> {
        return fetchReleases().map { releases ->
            releases.filter { it.isDevRelease() }
        }
    }

    /**
     * Get the latest stable release.
     */
    suspend fun getLatestStableRelease(): Result<Release?> {
        return fetchStableReleases().map { it.firstOrNull() }
    }

    /**
     * Get the latest dev release.
     */
    suspend fun getLatestDevRelease(): Result<Release?> {
        return fetchDevReleases().map { it.firstOrNull() }
    }

    /**
     * Find the patch .mpp asset in a release.
     */
    fun findPatchAsset(release: Release): ReleaseAsset? {
        return release.assets.find { it.isPatchFile() }
    }

    /**
     * Download the patch .mpp file from a release.
     * Returns the path to the downloaded file.
     */
    suspend fun downloadPatches(release: Release, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val asset = findPatchAsset(release)
        if (asset == null) {
            val error = "No .mpp patch files found in release ${release.tagName}"
            Logger.error(error)
            return@withContext Result.failure(Exception(error))
        }

        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        patchesDir.mkdirs()
        val targetFile = File(patchesDir, asset.name)

        // Check if already cached
        if (targetFile.exists() && targetFile.length() == asset.size) {
            Logger.info("Using cached patches: ${targetFile.absolutePath}")
            onProgress(1f)
            return@withContext Result.success(targetFile)
        }

        try {
            Logger.info("Downloading patches from ${asset.downloadUrl}")

            val response: HttpResponse = httpClient.get(asset.downloadUrl) {
                headers {
                    append(HttpHeaders.Accept, "application/octet-stream")
                }
            }

            if (!response.status.isSuccess()) {
                val error = "Failed to download patches: ${response.status}"
                Logger.error(error)
                return@withContext Result.failure(Exception(error))
            }

            val bytes = response.readRawBytes()
            targetFile.writeBytes(bytes)
            onProgress(1f)

            Logger.info("Patches downloaded to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            Logger.error("Error downloading patches", e)
            // Clean up partial download
            if (targetFile.exists()) {
                targetFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Get cached patch file for a specific version.
     */
    fun getCachedPatches(version: String): File? {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.find {
            it.name.contains(version) && isPatchFileName(it.name)
        }
    }

    private fun isPatchFileName(name: String): Boolean {
        return name.endsWith(".mpp", ignoreCase = true)
    }

    /**
     * List all cached patch versions.
     */
    fun listCachedPatches(): List<File> {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.filter { isPatchFileName(it.name) } ?: emptyList()
    }

    /**
     * Get the per-source cache directory for this repository.
     */
    fun getCacheDir(): File {
        val dir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        dir.mkdirs()
        return dir
    }

    /**
     * Delete cached patches.
     */
    fun clearCache(): Boolean {
        cachedReleases = null
        cacheTimestamp = 0L
        return try {
            val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
            var failedCount = 0
            patchesDir.listFiles()?.forEach { file ->
                try {
                    if (!file.deleteRecursively()) throw Exception("Could not delete")
                } catch (e: Exception) {
                    failedCount++
                    Logger.error("Failed to delete ${file.name}: ${e.message}")
                }
            }
            if (failedCount > 0) {
                Logger.error("Patches cache clear incomplete: $failedCount file(s) locked")
                false
            } else {
                Logger.info("Patches cache cleared for $repoPath")
                true
            }
        } catch (e: Exception) {
            Logger.error("Failed to clear patches cache", e)
            false
        }
    }
}
