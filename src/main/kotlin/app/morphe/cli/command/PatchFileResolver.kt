package app.morphe.cli.command

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.logging.Logger


object PatchFileResolver {
    private val logger = Logger.getLogger(this::class.java.name)

    /**
     * Takes the user's provided Patch Files and resolves any URLs that might be present.
     * Returns a new Set<File> with URLs replaced by downloaded/cached .mpp files.
     */

    fun resolve(
        patchFiles: Set<File>,
        prerelease: Boolean,
        cacheDir: File
    ): Set<File> {
        // We try to download our patch file here if the user passed a link
        if (patchFiles.any {
                it.path.startsWith("http:/") ||
                        it.path.startsWith("https:/")
            }) {
            try {
                val urlEntry = patchFiles.first{
                    it.path.startsWith("http:/") || it.path.startsWith("https:/")
                }

                val url = urlEntry.path

                val urlParts = url.split("/")
                val owner = urlParts[2]
                val repo = urlParts[3]

                // Resolve the version and asset from the GitHub API, then use the helper to cache/download.
                val version: String
                val asset: JsonElement?

                if (url.contains("releases/tag/")){
                    // We have the release version in this branch.
                    version = urlParts[6] // version part of the url

                    // First we hit the GitHub api for this specific release
                    val response = java.net.URI(
                        "https://api.github.com/repos/${owner}/${repo}/releases/tags/${version}"
                    ).toURL().openStream().bufferedReader().readText()

                    // Then we find where the .mpp file is from the stream above
                    val json = Json.parseToJsonElement(response).jsonObject
                    val assetArray = json["assets"]?.jsonArray

                    asset = assetArray?.find {
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".mpp") == true
                    }

                } else if (!prerelease) {
                    // Here in this "only repo mentioned" branch, get the latest stable version.
                    val response = java.net.URI(
                        "https://api.github.com/repos/${owner}/${repo}/releases/latest"
                    ).toURL().openStream().bufferedReader().readText()

                    // Then we find where the .mpp file is from the stream above
                    val json = Json.parseToJsonElement(response).jsonObject
                    val assetArray = json["assets"]?.jsonArray

                    asset = assetArray?.find {
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".mpp") == true
                    }

                    version = json["tag_name"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException(
                            "Could not determine version from ${owner}/${repo}"
                        )

                } else {
                    // Get latest dev version here.
                    // Get latest dev version from GitHub immediately to check our local file.
                    val response = java.net.URI(
                        "https://api.github.com/repos/${owner}/${repo}/releases"
                    ).toURL().openStream().bufferedReader().readText()

                    val releases = Json.parseToJsonElement(response).jsonArray
                    val release = releases.firstOrNull {
                        it.jsonObject["prerelease"]?.jsonPrimitive?.content == "true"
                    }
                        ?: throw IllegalArgumentException(
                            "Could not get dev release from ${owner}/${repo}"
                        )

                    val assetArray = release.jsonObject["assets"]?.jsonArray

                    asset = assetArray?.find {
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".mpp") == true
                    }

                    version = release.jsonObject["tag_name"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException(
                            "Could not determine version from ${owner}/${repo}"
                        )
                }

                // Use the helper to check cache or download the .mpp file
                val resolvedFile = fetchRemotePatchFile(
                    owner,
                    repo,
                    version,
                    asset,
                    cacheDir
                )
                return patchFiles - urlEntry + resolvedFile

            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to download patches from URL: ${e.message}")
            }
        }
        return patchFiles
    }

    // This is the helper function that can be called to do the patch files downloading.
    // The caller resolves the version and asset from the GitHub API before calling this.
    private fun fetchRemotePatchFile(
        owner: String,
        repo: String,
        version: String,
        asset: JsonElement?,
        cacheDir: File
    ): File {
        val versionNumber = version.removePrefix("v")

        val repoCacheDir = cacheDir.resolve("download").resolve("${owner}-${repo}")

        val cachedFile = repoCacheDir.listFiles()?.find {
            it.name.endsWith(".mpp") && it.name.contains(versionNumber)
        }

        if (cachedFile != null){
            val relativePath = cachedFile.relativeTo(cacheDir.parentFile).path
            // If the user mentioned file with that version already exists, return that file location.
            logger.info("Using cached patch file at $relativePath")
            return cachedFile
        }
        else{
            // If it doesn't exist or some other version is present, then we come here.
            // Either way we download our version and replace whatever else is present.
            repoCacheDir.listFiles()?.filter {
                it.name.endsWith(".mpp")
            }?.forEach { it.delete() }
            repoCacheDir.mkdirs()

            // Get the .mpp file ready here
            val downloadUrl = asset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content

            // Also get the file name ready here
            val assetName = asset?.jsonObject?.get("name")?.jsonPrimitive?.content

            if (downloadUrl == null || assetName == null){
                throw IllegalArgumentException("No .mpp file found in release $version")
            }

            // We finally download and set everything here.
            logger.info("Downloading patches from ${owner}/${repo} ${versionNumber}...")
            val targetFile = File(repoCacheDir, assetName)
            java.net.URI(downloadUrl).toURL().openStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val relativePath = targetFile.relativeTo(cacheDir.parentFile).path
            logger.info("Patches mpp saved to $relativePath. This file will be used on your next run as long as it is not deleted!")

            return targetFile
        }
    }
}