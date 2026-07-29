/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

/**
 * Progress callbacks for [BootstrapDownloader.downloadIfMissing]. Every method is
 * invoked on the calling (download) thread, so a UI implementation must marshal
 * onto its own toolkit thread.
 */
interface BootstrapProgressListener {
    /**
     * Invoked once before the first download, with the number of components that
     * actually need fetching (already-cached ones are excluded). Not called at all
     * when everything is already present, so the fast path stays UI-free.
     */
    fun onStart(componentCount: Int)

    /**
     * Progress for the component at [index] (0-based within the to-download set).
     * [totalBytes] is negative when the server does not send a Content-Length.
     */
    fun onProgress(index: Int, componentCount: Int, displayName: String, bytesDownloaded: Long, totalBytes: Long)

    /** Invoked once after every component has been downloaded and verified. */
    fun onComplete()

    /** Invoked on a fatal download or checksum error, right before the exception is thrown. */
    fun onError(message: String)
}

/**
 * Thrown by [BootstrapDownloader.downloadIfMissing] when a component cannot be
 * downloaded or fails checksum verification, so the caller can decide how to
 * surface it instead of the process dying silently.
 */
class BootstrapException(message: String, cause: Throwable? = null) : Exception(message, cause)
