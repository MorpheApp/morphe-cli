/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.model

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils.ANDROID_ARCHITECTURES
import kotlinx.serialization.Serializable

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
    val keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    val keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    // User's global keep-list for strip libs. Defaults to all common modern arches
    // (equivalent to no stripping). Stripping is only applied when the APK contains
    // an arch NOT in this set. See PatchSelectionViewModel.computeStripLibsStatus.
    val keepArchitectures: Set<String> = ANDROID_ARCHITECTURES,
    // Persisted expand/collapse state for each section in the Settings dialog.
    // Keyed by section title (e.g. "STRIP LIBS"). Missing key = section starts collapsed.
    val collapsibleSectionStates: Map<String, Boolean> = emptyMap()
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
