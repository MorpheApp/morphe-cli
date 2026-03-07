package app.morphe.gui.util

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * Platform-agnostic file utilities.
 * Handles app directories, temp files, and cross-platform path operations.
 */
object FileUtils {

    private const val APP_NAME = "morphe-gui"

    /**
     * Get the app data directory based on OS.
     * - Windows: %APPDATA%/morphe-gui
     * - macOS: ~/Library/Application Support/morphe-gui
     * - Linux: ~/.config/morphe-gui
     */
    fun getAppDataDir(): File {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        val appDataPath = when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: Paths.get(userHome, "AppData", "Roaming").toString()
                Paths.get(appData, APP_NAME)
            }
            osName.contains("mac") -> {
                Paths.get(userHome, "Library", "Application Support", APP_NAME)
            }
            else -> {
                // Linux and others
                Paths.get(userHome, ".config", APP_NAME)
            }
        }

        return appDataPath.toFile().also { it.mkdirs() }
    }

    /**
     * Get the patches cache directory.
     */
    fun getPatchesDir(): File {
        return File(getAppDataDir(), "patches").also { it.mkdirs() }
    }

    /**
     * Get the logs directory.
     */
    fun getLogsDir(): File {
        return File(getAppDataDir(), "logs").also { it.mkdirs() }
    }

    /**
     * Get the config file path.
     */
    fun getConfigFile(): File {
        return File(getAppDataDir(), "config.json")
    }

    /**
     * Get the app temp directory for patching operations.
     */
    fun getTempDir(): File {
        val systemTemp = System.getProperty("java.io.tmpdir")
        return File(systemTemp, APP_NAME).also { it.mkdirs() }
    }

    /**
     * Create a unique temp directory for a patching session.
     */
    fun createPatchingTempDir(): File {
        val timestamp = System.currentTimeMillis()
        return File(getTempDir(), "patching-$timestamp").also { it.mkdirs() }
    }

    /**
     * Clean up a temp directory.
     */
    fun cleanupTempDir(dir: File): Boolean {
        return try {
            if (dir.exists() && dir.startsWith(getTempDir())) {
                dir.deleteRecursively()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clean up all temp directories (call on app exit).
     */
    fun cleanupAllTempDirs(): Boolean {
        return try {
            getTempDir().deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the size of all temp directories.
     */
    fun getTempDirSize(): Long {
        return try {
            getTempDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if there are any temp files to clean.
     */
    fun hasTempFiles(): Boolean {
        return try {
            val tempDir = getTempDir()
            tempDir.exists() && (tempDir.listFiles()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Build a path using the system file separator.
     */
    fun buildPath(vararg parts: String): String {
        return parts.joinToString(File.separator)
    }

    /**
     * Get file extension.
     */
    fun getExtension(file: File): String {
        return file.extension.lowercase()
    }

    /**
     * Check if file is an APK or APKM.
     */
    fun isApkFile(file: File): Boolean {
        val ext = getExtension(file)
        return file.isFile && (ext == "apk" || ext == "apkm")
    }

    /**
     * Extract base.apk from an .apkm file to a temp directory.
     * Returns the extracted base.apk file, or null if extraction fails.
     * Caller is responsible for cleaning up the returned temp file.
     */
    fun extractBaseApkFromApkm(apkmFile: File): File? {
        return try {
            ZipFile(apkmFile).use { zip ->
                val baseEntry = zip.getEntry("base.apk") ?: return null
                val tempFile = File(getTempDir(), "base-${System.currentTimeMillis()}.apk")
                zip.getInputStream(baseEntry).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }
        } catch (e: Exception) {
            null
        }
    }
}
