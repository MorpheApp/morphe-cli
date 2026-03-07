package app.morphe.gui.data.repository

import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.data.model.PatchChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger

/**
 * Repository for managing app configuration (config.json)
 */
class ConfigRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cachedConfig: AppConfig? = null

    /**
     * Load config from file, or return default if not exists.
     */
    suspend fun loadConfig(): AppConfig = withContext(Dispatchers.IO) {
        cachedConfig?.let { return@withContext it }

        val configFile = FileUtils.getConfigFile()

        try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val config = json.decodeFromString<AppConfig>(content)
                cachedConfig = config
                Logger.info("Config loaded from ${configFile.absolutePath}")
                config
            } else {
                Logger.info("No config file found, using defaults")
                val default = AppConfig()
                saveConfig(default)
                default
            }
        } catch (e: Exception) {
            Logger.error("Failed to load config, using defaults", e)
            AppConfig()
        }
    }

    /**
     * Save config to file.
     */
    suspend fun saveConfig(config: AppConfig) = withContext(Dispatchers.IO) {
        try {
            val configFile = FileUtils.getConfigFile()
            val content = json.encodeToString(AppConfig.serializer(), config)
            configFile.writeText(content)
            cachedConfig = config
            Logger.info("Config saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            Logger.error("Failed to save config", e)
        }
    }

    /**
     * Update theme preference.
     */
    suspend fun setThemePreference(theme: ThemePreference) {
        val current = loadConfig()
        saveConfig(current.copy(themePreference = theme.name))
    }

    /**
     * Update patch channel preference.
     */
    suspend fun setPatchChannel(channel: PatchChannel) {
        val current = loadConfig()
        saveConfig(current.copy(preferredPatchChannel = channel.name))
    }

    /**
     * Update last used CLI version.
     */
    suspend fun setLastCliVersion(version: String) {
        val current = loadConfig()
        saveConfig(current.copy(lastCliVersion = version))
    }

    /**
     * Update last used patches version.
     */
    suspend fun setLastPatchesVersion(version: String) {
        val current = loadConfig()
        saveConfig(current.copy(lastPatchesVersion = version))
    }

    /**
     * Update default output directory.
     */
    suspend fun setDefaultOutputDirectory(path: String?) {
        val current = loadConfig()
        saveConfig(current.copy(defaultOutputDirectory = path))
    }

    /**
     * Update auto-cleanup temp files setting.
     */
    suspend fun setAutoCleanupTempFiles(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(autoCleanupTempFiles = enabled))
    }

    /**
     * Update simplified mode setting.
     */
    suspend fun setUseSimplifiedMode(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(useSimplifiedMode = enabled))
    }

    /**
     * Clear cached config (for testing).
     */
    fun clearCache() {
        cachedConfig = null
    }
}
