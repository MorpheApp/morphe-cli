/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.color

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.morphe.engine.MorpheData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * User-saved colours, shared by every colour control in the app. Backed by a
 * snapshot list so the UI recomposes when a colour is added or removed, and
 * capped at [MAX] slots.
 *
 * Still persisted to `morphe-data/icons/swatches.json` even though this is no
 * longer Icon Studio code. The path is deliberate: moving it would orphan the
 * swatches every existing user has already saved.
 */
/** The built-in colours offered ahead of the user's own saved ones. */
val MORPHE_SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0033.toInt(), 0xFF00E5FF.toInt(),
    0xFF1DE9B6.toInt(), 0xFFFFC400.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF6D00.toInt(),
)

object CustomSwatches {
    const val MAX = 12

    private val file by lazy { File(MorpheData.iconsDir, "swatches.json") }
    private val json = Json { ignoreUnknownKeys = true }

    val colors: SnapshotStateList<Int> = mutableStateListOf<Int>().also { list ->
        runCatching { if (file.exists()) list.addAll(json.decodeFromString<List<Int>>(file.readText())) }
    }

    val isFull: Boolean get() = colors.size >= MAX

    fun add(argb: Int) {
        if (argb !in colors && colors.size < MAX) { colors.add(argb); save() }
    }

    fun remove(argb: Int) {
        if (colors.remove(argb)) save()
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(colors.toList())) }
    }
}
