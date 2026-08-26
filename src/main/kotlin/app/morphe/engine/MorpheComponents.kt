/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.util.Properties

/**
 * Versions of Morphe ecosystem components that this desktop build is running against.
 *
 * Prefer each library's own embedded resource when present (so composite/local
 * substitutions report the truth). Fall back to build-time resolved metadata only
 * when a library does not ship a version resource (currently: morphe-library).
 */
object MorpheComponents {

    /** morphe-patcher on the classpath (`app/morphe/patcher/version.properties`). */
    val patcherVersion: String? by lazy {
        readVersionProperty("/app/morphe/patcher/version.properties", "version")
    }

    /**
     * morphe-library on the classpath.
     * Prefers `app/morphe/library/version.properties` if the library starts shipping it;
     * otherwise the build-time resolved version in `app/morphe/cli/components.properties`.
     */
    val libraryVersion: String? by lazy {
        readVersionProperty("/app/morphe/library/version.properties", "version")
            ?: readVersionProperty("/app/morphe/cli/components.properties", "libraryVersion")
    }

    private fun readVersionProperty(resourcePath: String, key: String): String? =
        runCatching {
            MorpheComponents::class.java
                .getResourceAsStream(resourcePath)
                ?.use { Properties().apply { load(it) }.getProperty(key) }
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.startsWith($$"${") }
        }.getOrNull()
}
