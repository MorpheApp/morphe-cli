package app.morphe.gui.util

enum class VersionStatus {
    EXACT_MATCH,      // Using the suggested version
    OLDER_VERSION,    // Using an older version (newer patches available)
    NEWER_VERSION,    // Using a newer version (might have issues)
    UNKNOWN           // Could not determine
}

/**
 * Compares two version strings (e.g., "19.16.39" vs "20.40.45")
 * Returns the version status of the current version relative to suggested.
 */
fun compareVersions(current: String, suggested: String): VersionStatus {
    return try {
        val currentParts = current.split(".").map { it.toInt() }
        val suggestedParts = suggested.split(".").map { it.toInt() }

        for (i in 0 until maxOf(currentParts.size, suggestedParts.size)) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val suggestedPart = suggestedParts.getOrElse(i) { 0 }

            when {
                currentPart > suggestedPart -> return VersionStatus.NEWER_VERSION
                currentPart < suggestedPart -> return VersionStatus.OLDER_VERSION
            }
        }
        VersionStatus.EXACT_MATCH
    } catch (e: Exception) {
        Logger.warn("Failed to compare versions: $current vs $suggested")
        VersionStatus.UNKNOWN
    }
}
