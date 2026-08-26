/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

/** One version entry from a CHANGELOG.md file. */
data class ChangelogEntry(
    val version: String,
    val date: String?,
    val content: String,
    val scopedBullets: Map<String, List<String>> = emptyMap(),
)

/** True when the version carries a pre-release suffix (e.g. `1.2.3-dev.4`). */
val ChangelogEntry.isPrerelease: Boolean get() = version.contains('-')

/**
 * Parses the conventional-changelog CHANGELOG.md that Morphe patch repositories
 * emit. Ported from morphe-manager.
 *
 * Unrecognised headings are skipped, not errors. Another template yields no
 * entries, and the caller MUST fall back to showing the update.
 */
object ChangelogParser {

    /**
     * `# [1.2.3](url) (2026-01-01)`, `# app [1.2.3](url) (…)`, `# 1.2.3 (…)`.
     * Group 1 bracketed version, group 2 bare version, exactly one per match.
     * Group 3 date.
     */
    private val VERSION_HEADING = Regex(
        """^#{1,3}\s+(?:\S+\s+)?(?:\[([^]]+)]\([^)]*\)|([^\s\[(]+))\s+\((\d{4}-\d{2}-\d{2})\)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * `* **YouTube - Hide ads:** text` gives group 1 `YouTube - Hide ads`. The
     * colon sits inside the bold span. Unscoped bullets MUST be ignored, since
     * attributing a global bullet to every app is what produces false badges.
     */
    private val BULLET_SCOPE_RE = Regex("""^\* \*\*(.+?):\*\*""")

    /**
     * Emitted for every new experimental target even when no patch logic
     * changed. Alone it MUST NOT count as a change (manager #622).
     */
    private val EXPERIMENTAL_VERSION_ADDITION_RE = Regex(
        """^Add(?:ed)?\s+experimental\s+support\s+for\b""",
        RegexOption.IGNORE_CASE,
    )

    private val COMMIT_LINK_REGEX = Regex("""\s*\(\[([0-9a-f]{7,})]\([^)]+/commit/[^)]+\)\)""")

    private fun String.sanitizeContent(): String = replace(COMMIT_LINK_REGEX, "").trimEnd()

    private fun resolveScopedBullets(content: String): Map<String, List<String>> {
        val scoped = mutableMapOf<String, MutableList<String>>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            val match = BULLET_SCOPE_RE.find(line) ?: continue
            val scope = match.groupValues[1]
            val body = line.substring(match.value.length).trim()
            scoped.getOrPut(scope) { mutableListOf() }.add(body)
        }
        return scoped
    }

    /** Entries in file order, which is newest first. */
    fun parse(markdown: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()

        var currentVersion: String? = null
        var currentDate: String? = null
        val currentContent = StringBuilder()

        fun flush() {
            val v = currentVersion ?: return
            val raw = currentContent.toString()
            entries += ChangelogEntry(
                version = v,
                date = currentDate,
                content = raw.sanitizeContent(),
                scopedBullets = resolveScopedBullets(raw),
            )
        }

        for (line in markdown.lines()) {
            val match = VERSION_HEADING.find(line)
            if (match != null) {
                flush()
                currentVersion = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
                currentDate = match.groupValues[3]
                currentContent.clear()
            } else if (currentVersion != null) {
                currentContent.appendLine(line)
            }
        }
        flush()

        return entries
    }

    /** Strictly newer than [installedVersion]. Null returns everything. */
    fun entriesNewerThan(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
    ): List<ChangelogEntry> {
        if (installedVersion == null) return entries
        val installedDate = findVersion(entries, installedVersion)?.date
        return entries.filter { entry ->
            isNewerVersion(entry.version, installedVersion) &&
                (installedDate == null || entry.date == null || entry.date >= installedDate)
        }
    }

    /**
     * True when a newer entry attributes a substantive bullet to this app.
     * [appNames] takes several candidates because sources name the same app
     * differently. Any one matching is enough.
     */
    fun hasChangesFor(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
        appNames: Collection<String>,
    ): Boolean {
        if (appNames.isEmpty()) return false
        val newerEntries = entriesNewerThan(entries, installedVersion)
        if (newerEntries.isEmpty()) return false
        return newerEntries.any { entry ->
            entry.scopedBullets.any { (scope, bullets) ->
                val scopeMatches = appNames.any { name ->
                    scope.equals(name, ignoreCase = true) ||
                        scope.startsWith("$name - ", ignoreCase = true)
                }
                scopeMatches && bullets.any { !EXPERIMENTAL_VERSION_ADDITION_RE.containsMatchIn(it) }
            }
        }
    }

    /** Exact [version] match, ignoring a leading `v`. */
    fun findVersion(entries: List<ChangelogEntry>, version: String): ChangelogEntry? {
        val normalized = version.normalizeVersion()
        return entries.firstOrNull { it.version.normalizeVersion() == normalized }
    }
}
