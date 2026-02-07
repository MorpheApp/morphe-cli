package app.morphe.gui.util

import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp

/**
 * Extracts supported apps from parsed patch data.
 * This allows the app to dynamically determine which apps are supported
 * based on the .mpp file contents rather than hardcoding.
 */
object SupportedAppExtractor {

    /**
     * Extract all supported apps from a list of patches.
     * Groups patches by package name and collects all supported versions.
     */
    fun extractSupportedApps(patches: List<Patch>): List<SupportedApp> {
        // Collect all package names and their versions from all patches
        val packageVersionsMap = mutableMapOf<String, MutableSet<String>>()

        for (patch in patches) {
            for (compatiblePackage in patch.compatiblePackages) {
                val packageName = compatiblePackage.name
                val versions = compatiblePackage.versions

                if (packageName.isNotBlank()) {
                    val existingVersions = packageVersionsMap.getOrPut(packageName) { mutableSetOf() }
                    existingVersions.addAll(versions)
                }
            }
        }

        // Convert to SupportedApp list
        return packageVersionsMap.map { (packageName, versions) ->
            val versionList = versions.toList().sortedDescending()
            val recommendedVersion = SupportedApp.getRecommendedVersion(versionList)
            SupportedApp(
                packageName = packageName,
                displayName = SupportedApp.getDisplayName(packageName),
                supportedVersions = versionList,
                recommendedVersion = recommendedVersion,
                apkMirrorUrl = SupportedApp.getDownloadUrl(packageName, recommendedVersion)
            )
        }.sortedBy { it.displayName }
    }

    /**
     * Get supported app by package name.
     */
    fun getSupportedApp(patches: List<Patch>, packageName: String): SupportedApp? {
        return extractSupportedApps(patches).find { it.packageName == packageName }
    }

    /**
     * Check if a package is supported by the patches.
     */
    fun isPackageSupported(patches: List<Patch>, packageName: String): Boolean {
        return patches.any { patch ->
            patch.compatiblePackages.any { it.name == packageName }
        }
    }

    /**
     * Get recommended version for a package from patches.
     */
    fun getRecommendedVersion(patches: List<Patch>, packageName: String): String? {
        return getSupportedApp(patches, packageName)?.recommendedVersion
    }
}
