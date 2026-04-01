/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

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
            // Well-known package name mappings
            val knownNames = mapOf(
                "com.google.android.youtube" to "YouTube",
                "com.google.android.apps.youtube.music" to "YouTube Music",
                "com.reddit.frontpage" to "Reddit",
                "com.duolingo" to "Duolingo",
                "com.myfitnesspal.android" to "MyFitnessPal",
                "com.pandora.android" to "Pandora",
                "ch.protonvpn.android" to "ProtonVPN",
                "com.amazon.avod.thirdpartyclient" to "Prime Video",
                "com.getmimo" to "Mimo",
                "com.zombodroid.MemeGenerator" to "Meme Generator",
                "com.sofascore.results" to "SofaScore",
                "pl.solidexplorer2" to "Solid Explorer",
                "com.bambuna.podcastaddict" to "Podcast Addict",
                "com.wallpaperscraft.wallpaper" to "WallpapersCraft",
                "cn.wps.moffice_eng" to "WPS Office",
                "com.merriamwebster" to "Merriam-Webster",
                "com.busuu.android.enc" to "Busuu",
                "jp.ne.ibis.ibispaintx.app" to "ibisPaint X",
                "com.laurencedawson.reddit_sync" to "Sync for Reddit",
                "com.laurencedawson.reddit_sync.pro" to "Sync for Reddit Pro",
                "com.laurencedawson.reddit_sync.dev" to "Sync for Reddit Dev",
                "com.andrewshu.android.reddit" to "Reddit is Fun",
                "free.reddit.news" to "Relay for Reddit",
                "reddit.news" to "Relay for Reddit Pro",
                "com.rubenmayayo.reddit" to "Boost for Reddit",
                "o.o.joey" to "Joey for Reddit",
                "o.o.joey.pro" to "Joey for Reddit Pro",
                "o.o.joey.dev" to "Joey for Reddit Dev",
                "com.onelouder.baconreader" to "BaconReader",
                "com.onelouder.baconreader.premium" to "BaconReader Premium",
                "me.edgan.redditslide" to "Slide for Reddit",
                "io.syncapps.lemmy_sync" to "Sync for Lemmy",
                "org.cygnusx1.continuum" to "Continuum for Reddit",
            )
            knownNames[packageName]?.let { return it }

            // Smart fallback: use the most meaningful part of the package name
            val parts = packageName.split(".")
            // Skip common prefixes: com, org, net, android, app, etc.
            val skipParts = setOf("com", "org", "net", "io", "me", "app", "android", "apps", "free")
            val meaningful = parts.filter { it.lowercase() !in skipParts && it.length > 1 }
            // Use the last meaningful part, or the full last segment
            val name = meaningful.lastOrNull() ?: parts.last()
            // Split camelCase and underscores, capitalize
            return name
                .replace("_", " ")
                .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
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
