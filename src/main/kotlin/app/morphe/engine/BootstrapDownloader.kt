/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.logging.Logger

/**
 * Handles deferred downloading of GUI dependencies to keep the base CLI as small as possible.
 */
object BootstrapDownloader {
    private val logger = Logger.getLogger("app.morphe.engine.BootstrapDownloader")

    // Versions
    private const val SKIKO_VERSION = "0.144.6"
    private const val MATERIAL_ICONS_VERSION = "1.7.3"
    private const val JNA_VERSION = "5.19.1"

    // Hashes (SHA-256 supply-chain security)
    private const val MATERIAL_ICONS_HASH = "dc55d383dca8279e353917e5c5a93d192a95e7ca4f926ee55ee0b4627f99d860"
    private const val JNA_HASH = "4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae"
    private const val JNA_PLATFORM_HASH = "3b3864f5b449e9c3c24b16861524b622b086563f44e0cd8384c8efc5a6052f82"
    private val SKIKO_HASHES = mapOf(
        "macos-x64" to "aacb31a5d2a70197abe18f9e81698eef15c83435791d4c487a2ffe139913762e",
        "macos-arm64" to "aec37b44e8dabf4de620068146769655748be3971bf868614e5ec6b240b2ac35",
        "linux-x64" to "3ed16be373ccbba7fbdca9acd7747ff2ed3d441764ac070aa20682c84764a671",
        "linux-arm64" to "313082bbc829dc09664f9bde09b18b668396a57522f43279e373c1a67198419f",
        "windows-x64" to "91fe81d4fa508d9a00c4596a6f23adc7e4ce193d0687afc83a486048d3f276f0"
    )

    // Remote Dependencies
    data class RemoteDependency(val fileName: String, val url: String, val expectedHash: String)

    private val SKIKO by lazy {
        val targetName = PlatformDetector.skikoTargetName
        val skikoHash = SKIKO_HASHES[targetName] 
            ?: error("Unsupported or unknown Skiko target: $targetName")
        
        val skikoFileName = "skiko-awt-runtime-$targetName-$SKIKO_VERSION.jar"
        RemoteDependency(
            fileName = skikoFileName,
            url = "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-$targetName/$SKIKO_VERSION/$skikoFileName",
            expectedHash = skikoHash
        )
    }

    private val MATERIAL_ICONS = RemoteDependency(
        fileName = "material-icons-extended-desktop-$MATERIAL_ICONS_VERSION.jar",
        url = "https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-extended-desktop/$MATERIAL_ICONS_VERSION/material-icons-extended-desktop-$MATERIAL_ICONS_VERSION.jar",
        expectedHash = MATERIAL_ICONS_HASH
    )

    private val JNA = RemoteDependency(
        fileName = "jna-$JNA_VERSION.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/$JNA_VERSION/jna-$JNA_VERSION.jar",
        expectedHash = JNA_HASH
    )

    private val JNA_PLATFORM = RemoteDependency(
        fileName = "jna-platform-$JNA_VERSION.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/$JNA_VERSION/jna-platform-$JNA_VERSION.jar",
        expectedHash = JNA_PLATFORM_HASH
    )

    /**
     * Downloads the required GUI dependencies if missing or invalid.
     * @return the list of downloaded (or cached) dependency files.
     */
    fun downloadIfMissing(): List<File> {
        val dependencies = listOf(SKIKO, MATERIAL_ICONS, JNA, JNA_PLATFORM)
        val binDir = File(MorpheData.root, "libs").also { it.mkdirs() }
        val downloadedFiles = mutableListOf<File>()

        for (dep in dependencies) {
            val targetFile = File(binDir, dep.fileName)

            if (targetFile.exists()) {
                if (verifyHash(targetFile, dep.expectedHash)) {
                    downloadedFiles.add(targetFile)
                    continue
                } else {
                    logger.warning("Cache invalid for ${dep.fileName}, redownloading.")
                    targetFile.delete()
                }
            }

            logger.info("Downloading ${dep.fileName}...")
            try {
                URI(dep.url).toURL().openStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                targetFile.delete()
                logger.severe("Failed to download GUI dependency ${dep.fileName}: ${e.message}")
                kotlin.system.exitProcess(1)
            }

            if (!verifyHash(targetFile, dep.expectedHash)) {
                targetFile.delete()
                logger.severe("Checksum mismatch for downloaded dependency ${dep.fileName}.")
                kotlin.system.exitProcess(1)
            }

            downloadedFiles.add(targetFile)
        }

        logger.info("All GUI dependencies ready.")
        return downloadedFiles
    }

    private fun verifyHash(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
