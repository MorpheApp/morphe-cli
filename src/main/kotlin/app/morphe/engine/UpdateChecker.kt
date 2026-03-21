package app.morphe.engine

import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.logging.Logger

object UpdateChecker {
    fun check(logger: Logger): String? {
        try {
            // Current version of this CLI.
            val currentVersion = javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
                ?.use { stream ->
                    Properties().apply { load(stream) }.getProperty("version")
                } ?: return null

            // Check if the user is using dev or stable release.
            val isDev = currentVersion.contains("dev")

            val url = if (isDev) {
                // If on dev and a new stable release is available, then this
                // ref still is correct because after a stable release dev branch is same as main.
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
                // Warning message for when the user is to about to move from dev -> stable edge case.
                val trackChangesMessage = if (isDev && !latestVersion.contains("dev")){
                    "\nNotice: The latest CLI is a stable release. Updating to that will stop dev " +
                            "update notifications. To keep receiving dev updates, skip stable update " +
                            "and wait for the next dev release."
                } else ""

                val downloadLink = if (isDev) {
                    "https://github.com/MorpheApp/morphe-cli/releases/"
                } else {
                    "https://github.com/MorpheApp/morphe-cli/releases/latest"
                }

                return "Update available: v$latestVersion (current: v$currentVersion)" +
                        "$trackChangesMessage\nDownload from $downloadLink"
            }
            return  null

        } catch (ex: Exception) {
            logger.fine("Could not check for CLI update: $ex")
            return null
        }
    }
}