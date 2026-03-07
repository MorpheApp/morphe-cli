package app.morphe.gui.data.model

import kotlinx.serialization.Serializable
import app.morphe.gui.ui.theme.ThemePreference

/**
 * Application configuration stored in config.json
 */
@Serializable
data class AppConfig(
    val themePreference: String = ThemePreference.SYSTEM.name,
    val lastCliVersion: String? = null,
    val lastPatchesVersion: String? = null,
    val preferredPatchChannel: String = PatchChannel.STABLE.name,
    val defaultOutputDirectory: String? = null,
    val autoCleanupTempFiles: Boolean = true,  // Default ON
    val useSimplifiedMode: Boolean = true      // Default to Quick/Simplified mode
) {
    fun getThemePreference(): ThemePreference {
        return try {
            ThemePreference.valueOf(themePreference)
        } catch (e: Exception) {
            ThemePreference.SYSTEM
        }
    }

    fun getPatchChannel(): PatchChannel {
        return try {
            PatchChannel.valueOf(preferredPatchChannel)
        } catch (e: Exception) {
            PatchChannel.STABLE
        }
    }
}

enum class PatchChannel {
    STABLE,
    DEV
}
