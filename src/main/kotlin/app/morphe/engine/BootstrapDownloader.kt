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
 * Handles deferred downloading of GUI dependencies (Skiko) to keep the base CLI small.
 */
object BootstrapDownloader {
    private val logger = Logger.getLogger("app.morphe.engine.BootstrapDownloader")

    private const val SKIKO_VERSION = "0.144.6"
    
    // Hardcoded expected SHA-256 hashes for Skiko 0.144.6 targets to ensure supply-chain security
    private val EXPECTED_HASHES = mapOf(
        "macos-x64" to "aacb31a5d2a70197abe18f9e81698eef15c83435791d4c487a2ffe139913762e",
        "macos-arm64" to "aec37b44e8dabf4de620068146769655748be3971bf868614e5ec6b240b2ac35",
        "linux-x64" to "3ed16be373ccbba7fbdca9acd7747ff2ed3d441764ac070aa20682c84764a671",
        "linux-arm64" to "313082bbc829dc09664f9bde09b18b668396a57522f43279e373c1a67198419f",
        "windows-x64" to "91fe81d4fa508d9a00c4596a6f23adc7e4ce193d0687afc83a486048d3f276f0"
    )

    /**
     * Downloads the Skiko JAR for the current platform if it's missing or invalid.
     * @return the downloaded (or cached) Skiko JAR file.
     */
    fun downloadIfMissing(): File {
        val targetName = PlatformDetector.skikoTargetName
        val expectedHash = EXPECTED_HASHES[targetName] 
            ?: error("Unsupported or unknown Skiko target: $targetName")

        val fileName = "skiko-awt-runtime-$targetName-$SKIKO_VERSION.jar"
        val binDir = File(MorpheData.root, "bin/skiko").also { it.mkdirs() }
        val targetFile = File(binDir, fileName)

        if (targetFile.exists()) {
            logger.info("Verifying cached GUI dependencies ($targetName)...")
            if (verifyHash(targetFile, expectedHash)) {
                logger.info("Cache valid.")
                return targetFile
            } else {
                logger.warning("Cache invalid, redownloading.")
                targetFile.delete()
            }
        }

        val url = "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-$targetName/$SKIKO_VERSION/$fileName"
        logger.info("Downloading GUI dependencies for $targetName...")

        try {
            URI(url).toURL().openStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            targetFile.delete()
            logger.severe("Failed to download GUI dependencies: ${e.message}")
            kotlin.system.exitProcess(1)
        }

        logger.info("Verifying checksums...")
        if (!verifyHash(targetFile, expectedHash)) {
            targetFile.delete()
            logger.severe("Checksum mismatch for downloaded Skiko JAR.")
            kotlin.system.exitProcess(1)
        }

        logger.info("GUI dependencies ready.")
        return targetFile
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
