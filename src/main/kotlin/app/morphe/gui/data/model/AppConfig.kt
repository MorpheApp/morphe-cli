/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.model

import kotlinx.serialization.Serializable
import app.morphe.gui.ui.theme.ThemePreference

/**
 * Application configuration stored in config.json
 */

val DEFAULT_PATCH_SOURCE = PatchSource(
    id = "morphe-default",
    name = "Morphe Patches",
    type = PatchSourceType.DEFAULT,
    url = "https://github.com/MorpheApp/morphe-patches",
    deletable = false
)

@Serializable
data class AppConfig(
    val themePreference: String = ThemePreference.SYSTEM.name,
    val lastCliVersion: String? = null,
    val lastPatchesVersion: String? = null,
    val preferredPatchChannel: String = PatchChannel.STABLE.name,
    val defaultOutputDirectory: String? = null,
    val autoCleanupTempFiles: Boolean = true,  // Default ON
    val useSimplifiedMode: Boolean = true, // Default to Quick/Simplified mode
    val patchSource: List<PatchSource> = listOf(DEFAULT_PATCH_SOURCE),
    val activePatchSourceId: String = "morphe-default",
    val keystorePath: String? = null,
    val keystorePassword: String? = null,
    val keystoreAlias: String = "Morphe",
    val keystoreEntryPassword: String = "Morphe"
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

@Serializable
data class PatchSource (
    val id: String,
    val name: String,
    val type: PatchSourceType,
    val url: String? = null, // For DEFAULT (morphe) and GITHUB (other source) type
    val filePath: String? = null, // For local files
    val deletable: Boolean = true
)

@Serializable
enum class PatchSourceType{
    DEFAULT, GITHUB, LOCAL
}

enum class PatchChannel {
    STABLE,
    DEV
}
