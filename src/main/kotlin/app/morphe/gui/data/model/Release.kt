package app.morphe.gui.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a GitHub release (for CLI or Patches)
 */
@Serializable
data class Release(
    val id: Long,
    @SerialName("tag_name")
    val tagName: String,
    val name: String,
    @SerialName("prerelease")
    val isPrerelease: Boolean,
    val draft: Boolean = false,
    @SerialName("published_at")
    val publishedAt: String,
    val assets: List<ReleaseAsset> = emptyList(),
    val body: String? = null
) {
    /**
     * Get the version string (removes 'v' prefix if present)
     */
    fun getVersion(): String {
        return tagName.removePrefix("v")
    }

    /**
     * Check if this is a dev/pre-release version
     */
    fun isDevRelease(): Boolean {
        return isPrerelease || tagName.contains("dev", ignoreCase = true) ||
                tagName.contains("alpha", ignoreCase = true) ||
                tagName.contains("beta", ignoreCase = true)
    }
}

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    val size: Long,
    @SerialName("content_type")
    val contentType: String
) {
    /**
     * Check if this is a JAR file
     */
    fun isJar(): Boolean = name.endsWith(".jar", ignoreCase = true)

    /**
     * Check if this is an MPP (Morphe Patches) file
     */
    fun isMpp(): Boolean = name.endsWith(".mpp", ignoreCase = true)

    /**
     * Get human-readable file size
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
