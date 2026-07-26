/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.PatchBundleIncompatibleException
import app.morphe.engine.PatchSourceLoadException
import app.morphe.engine.readableMessage
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Map a load failure exception to a short, user-readable line.
 *
 * The raw `Exception.message` is hostile when the underlying cause is a
 * coroutine/Ktor internal. Users see "StandaloneCoroutine was cancelled"
 * and assume the app crashed. This translates the common network/IO failures
 * into plain English and falls back to the **full cause-chain message** for
 * anything we don't recognize (never a blank "Failed to load").
 *
 * Intentionally does NOT handle CancellationException. That should never
 * reach the UI. Callers must re-throw it from their catch blocks instead of
 * surfacing it as an error.
 */
fun humanizePatchLoadError(e: Throwable): String = when (e) {
    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException -> "Network timeout — check your connection and try again"

    is UnknownHostException -> "Couldn't reach the patch server — check your connection"

    is IOException -> {
        val msg = e.message.orEmpty()
        when {
            msg.contains("rate limit", ignoreCase = true) ->
                "GitHub rate limit hit — wait a few minutes and try again"
            msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("connection closed", ignoreCase = true) ->
                "Connection dropped while downloading — try again"
            else -> msg.ifBlank { "Network error while loading patches" }
        }
    }

    // Already expanded by MultiSourceLoader / PatcherCompatibility
    is PatchBundleIncompatibleException,
    is PatchSourceLoadException -> e.message?.takeIf { it.isNotBlank() } ?: e.readableMessage()

    // A bundle built against a newer patcher fails at link time with java.lang.Error
    // subclasses (NoSuchMethodError / NoClassDefFoundError / AbstractMethodError, all
    // LinkageError). Prefer the concrete type/message chain so the user (and logs) see
    // *which* symbol is missing, not a generic "update Morphe".
    is LinkageError -> e.readableMessage()

    // ExceptionInInitializerError and friends often have null .message.
    else -> e.readableMessage()
}
