package app.morphe.gui.util

import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Strips native libraries from an APK, keeping only specified architectures.
 */
object ApkLibraryStripper {

    /**
     * Strips native libraries from an APK file, keeping only the specified architectures.
     *
     * @param apkFile The APK file to strip libraries from (modified in-place).
     * @param architecturesToKeep List of architectures to keep (e.g., ["arm64-v8a"]).
     * @param onProgress Optional callback for progress updates.
     */
    fun stripLibraries(apkFile: File, architecturesToKeep: List<String>, onProgress: (String) -> Unit = {}) {
        val keepSet = architecturesToKeep.toSet()
        val tempFile = File(apkFile.parentFile, "${apkFile.name}.tmp")

        var strippedCount = 0

        ZipFile(apkFile).use { zip ->
            ZipOutputStream(tempFile.outputStream().buffered()).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    if (shouldStripEntry(entry.name, keepSet)) {
                        strippedCount++
                        continue
                    }

                    val newEntry = ZipEntry(entry.name).apply {
                        if (entry.method == ZipEntry.STORED) {
                            method = ZipEntry.STORED
                            size = entry.size
                            compressedSize = entry.compressedSize
                            crc = entry.crc
                        }
                        entry.extra?.let { extra = it }
                    }

                    zos.putNextEntry(newEntry)
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }

        onProgress("Stripped $strippedCount native library files")

        // Replace original with stripped version
        apkFile.delete()
        tempFile.renameTo(apkFile)
    }

    /**
     * Returns true if the ZIP entry should be stripped (is a native lib for an architecture not in the keep set).
     */
    private fun shouldStripEntry(entryName: String, keepSet: Set<String>): Boolean {
        if (!entryName.startsWith("lib/")) return false

        // Entry format: lib/<arch>/libname.so
        val parts = entryName.removePrefix("lib/").split("/", limit = 2)
        if (parts.size < 2) return false

        val arch = parts[0]
        return arch !in keepSet
    }
}
