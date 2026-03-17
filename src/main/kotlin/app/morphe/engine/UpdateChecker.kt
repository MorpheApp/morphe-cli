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

            val connection = URL("https://api.github.com/repos/MorpheApp/morphe-cli/releases/latest")
                .openConnection() as HttpURLConnection

            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            //
            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            val latestVersion = Regex(""""tag_name"\s*:\s*"v?([^"]+)"""").find(response)
                ?.groupValues?.get(1) ?: return null

            if (latestVersion != currentVersion) {
                return "Update available: v$latestVersion (current: v$currentVersion). Download from https://github.com/MorpheApp/morphe-cli/releases/latest"
            }
            return  null

        }catch (e: Exception) {
            // In case we fail anything, we silently return.
            return null
        }
    }
}