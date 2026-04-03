/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.morphe.gui.ui.components.LocalFrameWindowScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.ui.components.TitleBarInsets
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import app.morphe.gui.util.FileUtils

/**
 * Main entry point.
 * The app switches between simplified and full mode dynamically via settings.
 */
fun launchGui(args: Array<String>) = application {
    // Determine initial mode from args or config
    val initialSimplifiedMode = when {
        args.contains("--quick") || args.contains("-q") -> true
        args.contains("--full") || args.contains("-f") -> false
        else -> loadConfigSync().useSimplifiedMode
    }

    val windowState = rememberWindowState(
        size = DpSize(1024.dp, 768.dp),
        position = WindowPosition(Alignment.Center)
    )

    val appIcon = remember { loadAppIcon() }

    // Set macOS dock icon
    remember {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                val stream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream("morphe_logo.png")
                    ?: ClassLoader.getSystemResourceAsStream("morphe_logo.png")
                if (stream != null) {
                    java.awt.Taskbar.getTaskbar().iconImage =
                        javax.imageio.ImageIO.read(stream)
                }
            }
        } catch (_: Exception) {
            // Taskbar not supported or icon loading failed
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Morphe",
        state = windowState,
        icon = appIcon
    ) {
        window.minimumSize = java.awt.Dimension(600, 400)

        // macOS: transparent title bar with expanded height so traffic lights
        // align with our header row content. Uses JetBrains Runtime custom title bar API.
        // Other OS: standard decorated window (no-op).
        val titleBarInsets = remember {
            val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
            val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
            var insets = TitleBarInsets()
            if (isMac) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)

                // JBR: expand the title bar so traffic lights center with our header row.
                // Height ~= header top padding (26dp) + half content height (~20dp) + buffer
                // → traffic lights center vertically with our header icons/text.
                try {
                    val decorations = com.jetbrains.JBR.getWindowDecorations()
                    val titleBar = decorations.createCustomTitleBar()
                    titleBar.height = 56f
                    titleBar.putProperty("controls.visible", true)
                    decorations.setCustomTitleBar(window, titleBar)
                    val macInsets = titleBar.toInsets(window, fallbackStartDp = 80f)
                    insets = macInsets.copy(
                        // Keep header content clear of the traffic-light cluster.
                        // JBR's inset can still land a bit tight on macOS depending on scaling.
                        start = macInsets.start + 24.dp
                    )
                } catch (_: Exception) {
                    // Not running on JBR — traffic lights stay at default position
                    insets = TitleBarInsets(start = 104.dp)
                }
            }
            if (isWindows) {
                try {
                    val decorations = com.jetbrains.JBR.getWindowDecorations()
                    val titleBar = decorations.createCustomTitleBar()
                    titleBar.height = 50f
                    titleBar.putProperty("controls.visible", true)
                    decorations.setCustomTitleBar(window, titleBar)
                    insets = titleBar.toInsets(window, fallbackEndDp = 138f)
                } catch (_: Exception) {
                    insets = TitleBarInsets(end = 138.dp)
                }
            }
            insets
        }

        CompositionLocalProvider(LocalFrameWindowScope provides this) {
            App(
                initialSimplifiedMode = initialSimplifiedMode,
                titleBarInsets = titleBarInsets
            )
        }
    }
}

/**
 * Load config synchronously (needed before app starts).
 */
private fun loadConfigSync(): AppConfig {
    return try {
        val configFile = FileUtils.getConfigFile()
        if (configFile.exists()) {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<AppConfig>(configFile.readText())
        } else {
            AppConfig() // Defaults: useSimplifiedMode = true
        }
    } catch (e: Exception) {
        AppConfig() // Defaults on error
    }
}

/**
 * Load the app icon from resources.
 * Tries multiple classloaders and paths to handle different resource packaging.
 */
private fun loadAppIcon(): BitmapPainter? {
    val possiblePaths = listOf(
        "/morphe_logo.png",
        "morphe_logo.png",
        "/composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png",
        "composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png"
    )

    // Try different classloader approaches
    val classLoaders = listOf(
        { path: String -> object {}.javaClass.getResourceAsStream(path) },
        { path: String -> Thread.currentThread().contextClassLoader.getResourceAsStream(path) },
        { path: String -> ClassLoader.getSystemResourceAsStream(path) }
    )

    for (loader in classLoaders) {
        for (path in possiblePaths) {
            try {
                val stream = loader(path)
                if (stream != null) {
                    return stream.use {
                        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
                    }
                }
            } catch (e: Exception) {
                // Try next combination
            }
        }
    }
    return null
}

private fun com.jetbrains.WindowDecorations.CustomTitleBar.toInsets(
    window: java.awt.Window,
    fallbackStartDp: Float = 0f,
    fallbackEndDp: Float = 0f
): TitleBarInsets {
    val scale = window.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val leftDp = (leftInset / scale).takeIf { it > 0f } ?: fallbackStartDp
    val rightDp = (rightInset / scale).takeIf { it > 0f } ?: fallbackEndDp
    return TitleBarInsets(
        start = leftDp.dp,
        end = rightDp.dp
    )
}
