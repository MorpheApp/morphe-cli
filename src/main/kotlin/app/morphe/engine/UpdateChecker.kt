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
                return if (isDev){
                    "Update available: v$latestVersion (current: v$currentVersion). Download from https://github.com/MorpheApp/morphe-cli/releases/"
                } else {
                    "Update available: v$latestVersion (current: v$currentVersion). Download from https://github.com/MorpheApp/morphe-cli/releases/latest"
                }
            }
            return  null

        }catch (e: Exception) {
            // In case we fail anything, we silently return.
            return null
        }
    }
}