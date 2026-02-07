package app.morphe.gui.data.constants

/**
 * Centralized configuration for supported apps.
 * Update version, URL, and checksum here - changes will reflect throughout the app.
 */
object AppConstants {

    // ==================== APP INFO ====================
    const val APP_NAME = "Morphe GUI"
    const val APP_VERSION = "1.4.0" // Keep in sync with the release version numbers

    // ==================== API ====================
    const val MORPHE_API_URL = "https://api.morphe.software"

    // ==================== YOUTUBE ====================
    object YouTube {
        const val DISPLAY_NAME = "YouTube"
        const val PACKAGE_NAME = "com.google.android.youtube"
        const val SUGGESTED_VERSION = "20.40.45"

        // SHA-256 checksum from APKMirror (leave null if not verified)
        // You can find this on the APKMirror download page under "File SHA-256"
        val SHA256_CHECKSUM: String? = "b7659da492a1ebd8bd7cea2909be4ee1f58e00a2586d65a1c91b2e1e5ec6acd1"
    }

    // ==================== YOUTUBE MUSIC ====================
    object YouTubeMusic {
        const val DISPLAY_NAME = "YouTube Music"
        const val PACKAGE_NAME = "com.google.android.apps.youtube.music"
        const val SUGGESTED_VERSION = "8.40.54"
        val SHA256_CHECKSUMS: Map<String, String> = mapOf(
            "arm64-v8a" to "d5b44919a5cd5648b01e392115fe68b9569b1c7847f3cdf65b1ace1302d005d2",
            "armeabi-v7a" to "6f5181e8aaa2595af6c421b86ffffcc1c7a4e97968d7be89d04b46776392eaec",
            "x86" to "03b1eb6993d43b1de6a9416828df7864be975ca6dd3a82468c431e3c193f3a80",
            "x86_64" to "eab4cd51220b28c7108343cdb95a063251029f9a137d052a519d007a9321c848"
        )
    }

    // ==================== REDDIT ====================
    object Reddit {
        const val DISPLAY_NAME = "Reddit"
        const val PACKAGE_NAME = "com.reddit.frontpage"
    }

    /**
     * List of all supported package names for quick lookup.
     */
    val SUPPORTED_PACKAGES = listOf(
        YouTube.PACKAGE_NAME,
        YouTubeMusic.PACKAGE_NAME,
        Reddit.PACKAGE_NAME
    )

    /**
     * Get suggested version for a package name.
     */
    fun getSuggestedVersion(packageName: String): String? {
        return when (packageName) {
            YouTube.PACKAGE_NAME -> YouTube.SUGGESTED_VERSION
            YouTubeMusic.PACKAGE_NAME -> YouTubeMusic.SUGGESTED_VERSION
            else -> null
        }
    }

    /**
     * Get checksum for a package name, version, and architecture.
     * @param packageName The app's package name
     * @param version The app version
     * @param architectures List of architectures in the APK (from lib/ folder)
     * @return The expected checksum, or null if not configured/version mismatch
     */
    fun getChecksum(packageName: String, version: String, architectures: List<String> = emptyList()): String? {
        return when (packageName) {
            YouTube.PACKAGE_NAME -> {
                // YouTube has a universal APK with single checksum
                if (version == YouTube.SUGGESTED_VERSION) YouTube.SHA256_CHECKSUM else null
            }
            YouTubeMusic.PACKAGE_NAME -> {
                if (version != YouTubeMusic.SUGGESTED_VERSION) return null
                if (YouTubeMusic.SHA256_CHECKSUMS.isEmpty()) return null

                // Try to find matching architecture checksum
                // Check for universal first, then specific architectures
                YouTubeMusic.SHA256_CHECKSUMS["universal"]
                    ?: architectures.firstNotNullOfOrNull { arch ->
                        YouTubeMusic.SHA256_CHECKSUMS[arch]
                    }
            }
            else -> null
        }
    }

    /**
     * Check if we have any checksum configured for this package/version/architecture combo.
     */
    fun hasChecksumConfigured(packageName: String, version: String, architectures: List<String> = emptyList()): Boolean {
        return getChecksum(packageName, version, architectures) != null
    }

    /**
     * Check if this is the recommended version.
     */
    fun isRecommendedVersion(packageName: String, version: String): Boolean {
        return getSuggestedVersion(packageName) == version
    }

    // ==================== PATCH RECOMMENDATIONS ====================

    /**
     * Patches that are commonly disabled by users.
     * These patches change default behavior in ways that some users may not want.
     * The key is a partial match (case-insensitive) against patch names.
     */
    object PatchRecommendations {
        /**
         * Patches commonly disabled for YouTube.
         * Pair of (patch name pattern, reason for commonly disabling)
         */
        val YOUTUBE_COMMONLY_DISABLED: List<Pair<String, String>> = listOf(
            "Custom Branding" to "Keeps the original name and logo for the app",
//            "Hide ads" to "Some users prefer keeping ads to support creators",
//            "Premium heading" to "Changes the YouTube logo/heading appearance",
//            "Navigation buttons" to "Modifies bottom navigation bar layout",
//            "Spoof client" to "May cause playback issues on some devices",
//            "Disable auto captions" to "Some users rely on auto-generated captions"
        )

        /**
         * Patches commonly disabled for YouTube Music.
         */
        val YOUTUBE_MUSIC_COMMONLY_DISABLED: List<Pair<String, String>> = listOf(
            "Custom Branding" to "Keeps the original name and logo for the app",
//            "Spoof client" to "May cause playback issues on some devices"
        )

        /**
         * Patches commonly disabled for Reddit.
         */
        val REDDIT_COMMONLY_DISABLED: List<Pair<String, String>> = listOf(
            "Change package name" to "Doesn't work for reddit",
            "Spoof signature" to "May cause issues on some devices"
        )

        /**
         * Get commonly disabled patches for a package.
         */
        fun getCommonlyDisabled(packageName: String): List<Pair<String, String>> {
            return when (packageName) {
                YouTube.PACKAGE_NAME -> YOUTUBE_COMMONLY_DISABLED
                YouTubeMusic.PACKAGE_NAME -> YOUTUBE_MUSIC_COMMONLY_DISABLED
                Reddit.PACKAGE_NAME -> REDDIT_COMMONLY_DISABLED
                else -> emptyList()
            }
        }
    }
}
