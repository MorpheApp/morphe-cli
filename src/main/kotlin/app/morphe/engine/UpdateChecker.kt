package app.morphe.engine

import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties


object UpdateChecker {
    fun check(): String? {
        try {
            // Try to get the latest version. (TTL IS SET TO 3000)
            val currentVersion = javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
                ?.use { stream ->
                    Properties().apply { load(stream) }.getProperty("version")
                }
                ?: return null

            // Check if the user is using dev or stable release here. Then we use this to check the latest dev or stable release.
            val isDev = currentVersion.contains("dev")

            val url = if (isDev) {
                "https://raw.githubusercontent.com/MorpheApp/morphe-cli/refs/heads/dev/gradle.properties"
            } else {
                "https://raw.githubusercontent.com/MorpheApp/morphe-cli/refs/heads/main/gradle.properties"
            }

            val connection = URL(url).openConnection() as HttpURLConnection

            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            val latestVersion = Properties().apply {
                load(response.byteInputStream())
            }.getProperty("version") ?: return null

            if (latestVersion != currentVersion) {
                val currentTag = if (isDev) "[Dev]" else "[Stable]"
                val latestTag = if (latestVersion.contains("dev")) "[Dev]" else "[Stable]"
                val trackChangesMessage = if (isDev && !latestVersion.contains("dev")){
                    "\nWarning: This is a stable release. Updating will stop dev update notifications. " +
                            "To keep receiving dev updates, skip this and wait for the next dev release."
                } else ""

                return if (isDev){
                    "Update available: v$latestVersion $latestTag (current: v$currentVersion $currentTag).$trackChangesMessage\nDownload from https://github.com/MorpheApp/morphe-cli/releases/"
                } else {
                    "Update available: v$latestVersion $latestTag (current: v$currentVersion $currentTag).$trackChangesMessage\nDownload from https://github.com/MorpheApp/morphe-cli/releases/latest"
                }
            }
            return  null

        }catch (e: Exception) {
            // In case we fail anything, we silently return.
            return null
        }
    }
}