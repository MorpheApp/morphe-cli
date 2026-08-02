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

    // Remote Dependencies
    data class RemoteDependency(val fileName: String, val url: String, val expectedHash: String)

    private val SKIKO by lazy {
        val targetName = PlatformDetector.skikoTargetName
        val skikoHash = BootstrapConstants.SKIKO_HASHES[targetName] 
            ?: error("Unsupported or unknown Skiko target: $targetName")
        
        val skikoFileName = "skiko-awt-runtime-$targetName-${BootstrapConstants.SKIKO_VERSION}.jar"
        RemoteDependency(
            fileName = skikoFileName,
            url = "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-$targetName/${BootstrapConstants.SKIKO_VERSION}/$skikoFileName",
            expectedHash = skikoHash
        )
    }

    private val MATERIAL_ICONS = RemoteDependency(
        fileName = "material-icons-extended-desktop-${BootstrapConstants.MATERIAL_ICONS_VERSION}.jar",
        url = "https://repo1.maven.org/maven2/org/jetbrains/compose/material/material-icons-extended-desktop/${BootstrapConstants.MATERIAL_ICONS_VERSION}/material-icons-extended-desktop-${BootstrapConstants.MATERIAL_ICONS_VERSION}.jar",
        expectedHash = BootstrapConstants.MATERIAL_ICONS_HASH
    )

    private val JNA = RemoteDependency(
        fileName = "jna-${BootstrapConstants.JNA_VERSION}.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/${BootstrapConstants.JNA_VERSION}/jna-${BootstrapConstants.JNA_VERSION}.jar",
        expectedHash = BootstrapConstants.JNA_HASH
    )

    private val JNA_PLATFORM = RemoteDependency(
        fileName = "jna-platform-${BootstrapConstants.JNA_VERSION}.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/${BootstrapConstants.JNA_VERSION}/jna-platform-${BootstrapConstants.JNA_VERSION}.jar",
        expectedHash = BootstrapConstants.JNA_PLATFORM_HASH
    )

    /**
     * Downloads the required GUI dependencies if missing or invalid.
     * @return the list of downloaded (or cached) dependency files.
     */
    fun downloadIfMissing(): List<File> {
        val dependencies = listOf(SKIKO, MATERIAL_ICONS, JNA, JNA_PLATFORM)
        val binDir = File(MorpheData.root, "libs").also { it.mkdirs() }
        
        val expectedFileNames = dependencies.map { it.fileName }.toSet()
        binDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in expectedFileNames) {
                logger.info("Removing obsolete dependency: ${file.name}")
                file.delete()
            }
        }
        
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
