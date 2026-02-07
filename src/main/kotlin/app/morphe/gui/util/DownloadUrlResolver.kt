package app.morphe.gui.util

/**
 * Builds direct APKMirror release page URLs from package name + version.
 * Pattern: https://www.apkmirror.com/apk/{publisher}/{app}/{app}-{version}-release/
 */
object DownloadUrlResolver {

    private data class ApkMirrorApp(val publisher: String, val name: String)

    private val PACKAGE_MAP = mapOf(
        "com.google.android.youtube" to ApkMirrorApp("google-inc", "youtube"),
        "com.google.android.apps.youtube.music" to ApkMirrorApp("google-inc", "youtube-music"),
        "com.reddit.frontpage" to ApkMirrorApp("redditinc", "reddit")
    )

    fun buildUrl(packageName: String, version: String?): String {
        if (version == null) return fallbackUrl(packageName)

        val app = PACKAGE_MAP[packageName] ?: return fallbackUrl(packageName)
        val versionSlug = version.replace(".", "-")

        return "https://www.apkmirror.com/apk/${app.publisher}/${app.name}/${app.name}-$versionSlug-release/"
    }

    private fun fallbackUrl(packageName: String): String {
        return "${app.morphe.gui.data.constants.AppConstants.MORPHE_API_URL}/v2/web-search/$packageName"
    }
}
