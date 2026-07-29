/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

/**
 * Utility to determine the host OS and architecture for resolving platform-specific dependencies.
 */
object PlatformDetector {
    /**
     * The Skiko target name string based on the current platform and architecture.
     * Examples: macos-arm64, windows-x64, linux-x64
     */
    val skikoTargetName: String by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val isMac = osName.contains("mac")
        val isWindows = osName.contains("windows")
        val isLinux = osName.contains("linux")

        val isArm = osArch == "aarch64" || osArch == "arm64"
        val isX64 = osArch == "amd64" || osArch == "x86_64"

        when {
            isMac && isArm -> "macos-arm64"
            isMac && isX64 -> "macos-x64"
            isLinux && isArm -> "linux-arm64"
            isLinux && isX64 -> "linux-x64"
            isWindows && isX64 -> "windows-x64"
            else -> error("Unsupported OS/Architecture combination: $osName / $osArch")
        }
    }
}
