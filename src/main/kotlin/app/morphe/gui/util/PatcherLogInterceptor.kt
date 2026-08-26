/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Utility to intercept internal `java.util.logging` output from the underlying 
 * patching library. This captures granular progress messages (e.g., "Writing classes")
 * which are not emitted through standard progress callbacks.
 */
object PatcherLogInterceptor {
    
    private var activeLogger: Logger? = null
    
    /**
     * Attaches a handler to the patcher logger and returns it so it can be detached later.
     */
    fun attach(onLog: (String) -> Unit): Handler {
        val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                val prefix = when (record.level) {
                    Level.SEVERE -> "ERROR: "
                    Level.WARNING -> "WARNING: "
                    else -> ""
                }
                record.message?.takeIf { it.isNotBlank() }?.let { onLog("$prefix$it") }
            }
            
            override fun flush() {}
            
            override fun close() {}
        }
        val logger = Logger.getLogger("app.morphe.patcher")
        activeLogger = logger
        logger.addHandler(handler)
        return handler
    }

    /**
     * Detaches the given handler.
     */
    fun detach(handler: Handler) {
        activeLogger?.removeHandler(handler)
        activeLogger = null
    }
}
