/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

import app.morphe.desktop.command.MainCommand
import app.morphe.engine.BootstrapDownloader
import app.morphe.library.logging.Logger
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.logging.Logger.getLogger as JVLogger
import picocli.CommandLine

fun main(args: Array<String>) {
    val isInternalGuiSubprocess = args.contains("--internal-gui-subprocess")
    val isGuiIntent = (args.isEmpty() || isInternalGuiSubprocess) && !GraphicsEnvironment.isHeadless()

    if (isGuiIntent) {
        if (isInternalGuiSubprocess) {
            val guiArgs = args.filter { it != "--internal-gui-subprocess" }.toTypedArray()
            launchGuiReflectively(guiArgs)
            return
        } else {
            val location = object {}.javaClass.protectionDomain.codeSource?.location
            val isJar = location?.path?.endsWith(".jar") == true

            if (!isJar) {
                // Running from IDE/classpath, Skiko is likely already available
                launchGuiReflectively(args)
                return
            } else {
                val downloadedJars = BootstrapDownloader.downloadIfMissing()
                val javaHome = System.getProperty("java.home")
                val javaBin = File(javaHome, "bin/java").absolutePath
                val downloadedClassPath = downloadedJars.joinToString(File.pathSeparator) { it.absolutePath }
                val classPath = File(location.toURI()).absolutePath + File.pathSeparator + downloadedClassPath

                val processBuilder = ProcessBuilder(
                    javaBin,
                    "-cp",
                    classPath,
                    "app.morphe.MorpheLauncherKt",
                    "--internal-gui-subprocess",
                    *args
                )
                processBuilder.inheritIO()
                val process = processBuilder.start()
                process.waitFor()
                System.exit(process.exitValue())
            }
        }
    } else {
        Logger.setDefault()

        if (GraphicsEnvironment.isHeadless() && args.isEmpty()){
            val logger = JVLogger("app.morphe.MorpheLauncher")
            logger.info("Running in Headless environment, falling back to CLI mode.")
        }

        CommandLine(MainCommand)
            .execute(*args)
            .let(System::exit)
    }
}

private fun launchGuiReflectively(args: Array<String>) {
    Class.forName("app.morphe.gui.GuiMainKt")
        .getMethod("launchGui", Array<String>::class.java)
        .invoke(null, args)
}
