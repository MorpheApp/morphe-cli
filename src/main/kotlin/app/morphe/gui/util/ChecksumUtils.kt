package app.morphe.gui.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utility for calculating and verifying file checksums.
 */
object ChecksumUtils {

    /**
     * Calculate SHA-256 checksum of a file.
     * @return Lowercase hex string of the checksum
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)

        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a file's checksum against expected value.
     * @return true if checksums match (case-insensitive comparison)
     */
    fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        val actualChecksum = calculateSha256(file)
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }
}

/**
 * Result of checksum verification.
 */
sealed class ChecksumStatus {
    /** Checksum matches the expected value - file is verified */
    data object Verified : ChecksumStatus()

    /** Checksum doesn't match - file may be corrupted or modified */
    data class Mismatch(val expected: String, val actual: String) : ChecksumStatus()

    /** No checksum configured for this version - cannot verify */
    data object NotConfigured : ChecksumStatus()

    /** Non-recommended version - checksum verification not applicable */
    data object NonRecommendedVersion : ChecksumStatus()

    /** Checksum calculation failed */
    data class Error(val message: String) : ChecksumStatus()
}
