/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import java.awt.Desktop
import java.net.URI

fun openUrlInBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI(url))
                return
            } catch (e: Exception) {
                Logger.warn("AWT Desktop browse failed, falling back to native commands: ${e.message}")
            }
        }

        val os = System.getProperty("os.name").lowercase()
        val runtime = Runtime.getRuntime()

        when {
            os.contains("linux") || os.contains("nix") -> runtime.exec(arrayOf("xdg-open", url))
            os.contains("mac") -> runtime.exec(arrayOf("open", url))
            os.contains("win") -> runtime.exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
            else -> Logger.error("Unsupported OS for URL fallback: $os")
        }
    } catch (e: Exception) {
        Logger.error("Failed to open URL natively: $url", e)
    }
}
