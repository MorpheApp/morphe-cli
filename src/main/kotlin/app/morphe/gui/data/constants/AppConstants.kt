package app.morphe.gui.data.constants

/**
 * Centralized configuration for supported apps.
 * This file is massively outdated. Could be used for other things in the future but kinda useless now.
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
    }

    // ==================== YOUTUBE MUSIC ====================
    object YouTubeMusic {
        const val DISPLAY_NAME = "YouTube Music"
        const val PACKAGE_NAME = "com.google.android.apps.youtube.music"
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

    // TODO: Checksum verification will be re-enabled when checksums are added to .mpp files
    // For now, checksums are not validated. See ChecksumUtils.kt for the verification logic.
}
