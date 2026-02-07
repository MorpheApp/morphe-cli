package app.morphe.gui.data.model

import app.morphe.gui.util.DownloadUrlResolver

/**
 * Represents a supported app extracted dynamically from patch metadata.
 * This is populated by parsing the .mpp file's compatible packages.
 */
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val supportedVersions: List<String>,
    val recommendedVersion: String?,
    val apkDownloadUrl: String? = null
) {
    companion object {
        /**
         * Derive display name from package name.
         */
        fun getDisplayName(packageName: String): String {
            return when (packageName) {
                "com.google.android.youtube" -> "YouTube"
                "com.google.android.apps.youtube.music" -> "YouTube Music"
                "com.reddit.frontpage" -> "Reddit"
                else -> {
                    // Fallback: Extract last part of package name and capitalize
                    packageName.substringAfterLast(".")
                        .replaceFirstChar { it.uppercase() }
                }
            }
        }

        /**
         * Get a web download URL for a package name and version.
         */
        fun getDownloadUrl(packageName: String, version: String?): String? {
            if (version == null) return null
            return DownloadUrlResolver.getWebSearchDownloadLink(packageName, version)
        }

        /**
         * Get the recommended version from a list of supported versions.
         * Returns the highest version number.
         */
        fun getRecommendedVersion(versions: List<String>): String? {
            if (versions.isEmpty()) return null

            return versions.sortedWith { v1, v2 ->
                compareVersions(v2, v1) // Descending order
            }.firstOrNull()
        }

        /**
         * Compare two version strings.
         * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
         */
        private fun compareVersions(v1: String, v2: String): Int {
            val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }

            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 != p2) return p1.compareTo(p2)
            }
            return 0
        }
    }
}
