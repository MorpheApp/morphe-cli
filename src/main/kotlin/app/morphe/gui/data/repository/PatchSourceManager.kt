/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.Logger
import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages PatchRepository instances for different patch sources.
 * Creates and caches a PatchRepository per GitHub-based source.
 * Emits [sourceVersion] whenever the active source changes so the UI can react.
 */
class PatchSourceManager(
    private val httpClient: HttpClient,
    private val configRepository: ConfigRepository
) {
    private val repositories = mutableMapOf<String, PatchRepository>()

    // Cached active state for synchronous access
    private var cachedActiveRepo: PatchRepository? = null
    private var cachedActiveSource: PatchSource? = null

    // Incremented on every source switch so Compose can key on it
    private val _sourceVersion = MutableStateFlow(0)
    val sourceVersion: StateFlow<Int> = _sourceVersion.asStateFlow()

    /**
     * Load the active source from config and cache its PatchRepository.
     * Call once at app startup (from a LaunchedEffect).
     */
    suspend fun initialize() {
        val source = configRepository.getActivePatchSource()
        cachedActiveSource = source
        cachedActiveRepo = getRepositoryForSource(source)
        Logger.info("PatchSourceManager initialized with source '${source.name}' (type=${source.type})")
    }

    /**
     * Switch the active source, persist it, and signal the UI.
     */
    suspend fun switchSource(id: String) {
        configRepository.setActivePatchSource(id)
        val source = configRepository.getActivePatchSource()
        cachedActiveSource = source
        cachedActiveRepo = getRepositoryForSource(source)
        _sourceVersion.value++
        Logger.info("Switched active patch source to '${source.name}' (type=${source.type})")
    }

    /**
     * Whether the current active source is a local .mpp file.
     */
    fun isLocalSource(): Boolean {
        return cachedActiveSource?.type == PatchSourceType.LOCAL
    }

    /**
     * Get the local .mpp file path if the active source is LOCAL, null otherwise.
     */
    fun getLocalFilePath(): String? {
        val source = cachedActiveSource ?: return null
        return if (source.type == PatchSourceType.LOCAL) source.filePath else null
    }

    /**
     * Get the display name of the active source.
     */
    fun getActiveSourceName(): String {
        return cachedActiveSource?.name ?: "Morphe Patches"
    }

    /**
     * Whether the active source is the built-in Morphe default.
     */
    fun isDefaultSource(): Boolean {
        return cachedActiveSource?.type == PatchSourceType.DEFAULT
    }

    /**
     * Get the cached active PatchRepository synchronously.
     * Returns null for LOCAL sources (no GitHub API needed).
     * Falls back to default repo if not yet initialized and source is not LOCAL.
     */
    fun getActiveRepositorySync(): PatchRepository {
        return cachedActiveRepo ?: PatchRepository(httpClient).also {
            if (!isLocalSource()) cachedActiveRepo = it
        }
    }

    /**
     * Get the PatchRepository for the currently active source (suspend version).
     * For LOCAL sources, returns null (caller should use the file path directly).
     */
    suspend fun getActiveRepository(): PatchRepository? {
        val source = configRepository.getActivePatchSource()
        return getRepositoryForSource(source)
    }

    /**
     * Get the PatchRepository for a specific source.
     * Returns null for LOCAL sources (no GitHub API needed).
     */
    fun getRepositoryForSource(source: PatchSource): PatchRepository? {
        if (source.type == PatchSourceType.LOCAL) return null

        return repositories.getOrPut(source.id) {
            val repoPath = extractRepoPath(source)
            Logger.info("Creating PatchRepository for source '${source.name}' (repo=$repoPath)")
            PatchRepository(httpClient, repoPath)
        }
    }

    /**
     * Get the active patch source config.
     */
    suspend fun getActiveSource(): PatchSource {
        return configRepository.getActivePatchSource()
    }

    /**
     * Extract "owner/repo" from a PatchSource's URL.
     * e.g. "https://github.com/MorpheApp/morphe-patches" -> "MorpheApp/morphe-patches"
     */
    private fun extractRepoPath(source: PatchSource): String {
        val url = source.url ?: return "MorpheApp/morphe-patches"
        return url
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix("/")
            .removeSuffix(".git")
    }

    /**
     * Clear all cached repository instances (e.g. after source list changes).
     */
    fun clearAll() {
        repositories.clear()
    }

    /**
     * Notify that cached patch files were deleted (e.g. via "Clear Cache" in settings).
     * Clears cached repo state and bumps [sourceVersion] so ViewModels reload.
     */
    fun notifyCacheCleared() {
        cachedActiveRepo?.clearCache()
        _sourceVersion.value++
    }
}
