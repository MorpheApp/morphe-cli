/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import java.io.File
import java.util.logging.Logger
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.tools.build.apkzlib.zip.AlignmentRules
import com.android.tools.build.apkzlib.zip.StoredEntry

/**
 * Strips native libraries from an APK, keeping only specified architectures.
 */
object ApkLibraryStripper {

    // Alignment for native libraries (4KB boundary). We no longer just write it ourselves.
    private val LIBRARY_EXTENSION = ".so"
    private val LIBRARY_ALIGNMENT = 4096
    private val DEFAULT_ALIGNMENT = 4

    /*
    Alignment rules matching morphe-patcher's ApkUtils to ensure resources.arsc stays
    4-byte aligned (required by Android 11+) and .so files stay 4KB aligned.
     */
    private val zFileOptions = ZFileOptions().setAlignmentRule(
        AlignmentRules.compose(
            AlignmentRules.constantForSuffix(LIBRARY_EXTENSION, LIBRARY_ALIGNMENT),
            AlignmentRules.constant(DEFAULT_ALIGNMENT),
        )
    )

    private val VALID_ARCHITECTURES = setOf(
        "armeabi-v7a",
        "arm64-v8a",
        "x86",
        "x86_64",
        // Old obsolete architectures. Only found in Android 6.0 and earlier.
        "armeabi",
        "mips",
        "mips64",
    )

    /**
     * Validates that all requested architectures are known.
     * Throws IllegalArgumentException if any are invalid.
     */
    private fun validateArchitectures(architectures: List<String>) {
        // Error on no recognizable architectures.
        require(architectures.isNotEmpty() && architectures.any { it in VALID_ARCHITECTURES }) {
            "No valid architectures specified with --striplibs: $architectures " +
                    "Valid architectures are: $VALID_ARCHITECTURES"
        }

        // Warn on unrecognizable.
        val invalid = architectures.filter { it !in VALID_ARCHITECTURES }
        if (invalid.isNotEmpty()) {
            Logger.getLogger(this::class.java.name).warning(
                "Ignoring unrecognized --striplibs architecture: '$invalid' " +
                        "Valid architectures are: $VALID_ARCHITECTURES"
            )
        }
    }

    /**
     * Strips native libraries from an APK file, keeping only the specified architectures.
     *
     * @param apkFile The APK file to strip libraries from (modified in-place).
     * @param architecturesToKeep List of architectures to keep (e.g., ["arm64-v8a"]).
     * @param onProgress Optional callback for progress updates.
     */
    fun stripLibraries(apkFile: File, architecturesToKeep: List<String>, onProgress: (String) -> Unit = {}) {

        validateArchitectures(architecturesToKeep)

        val keepSet = architecturesToKeep.toSet()

        var strippedCount = 0

        // Open APK in-place with alignment rules, delete unwanted lib entries, and realign.
        // Need to do this to preserve 4 byte alignment.
        ZFile.openReadWrite(apkFile, zFileOptions).use { zFile ->
            zFile.entries().forEach { entry ->
                if (shouldStripEntry(entry.centralDirectoryHeader.name, keepSet)){
                    entry.delete()
                    strippedCount++
                }
            }
            zFile.realign()
        }

        onProgress("Kept $architecturesToKeep, stripped $strippedCount native library files")
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
