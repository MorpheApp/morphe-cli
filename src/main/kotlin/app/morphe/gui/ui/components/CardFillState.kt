/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.morphe.gui.data.model.MorpheFill

/**
 * The user's per-app card colours, plus the two actions a card needs.
 *
 * Provided as a CompositionLocal for the same reason as the theme: app cards are
 * rendered from three different screens, and a purely cosmetic override should
 * not have to be threaded through every view model and pane on the way there.
 */
data class CardFillState(
    val fills: Map<String, MorpheFill> = emptyMap(),
    /** Persist an override, or clear it by passing null. */
    val onChange: (packageName: String, fill: MorpheFill?) -> Unit = { _, _ -> },
    /** Open the editor for one app. Backed by [CardFillHost]. */
    val requestEdit: (packageName: String, appName: String, appIconColorHex: String?) -> Unit =
        { _, _, _ -> },
) {
    /** The override for [packageName], or null when the user has not set one. */
    operator fun get(packageName: String): MorpheFill? = fills[packageName]
}

val LocalCardFills = compositionLocalOf { CardFillState() }

/** One app's identity while its card is being edited. */
private data class EditTarget(val packageName: String, val appName: String, val appIconColorHex: String?)

/**
 * Provides [LocalCardFills] to [content] and owns the editor dialog.
 *
 * Hosting the dialog here means a card only has to ask for it by package name.
 * Three screens render cards, and none of them should each carry dialog state for
 * something this incidental.
 */
@Composable
fun CardFillHost(
    fills: Map<String, MorpheFill>,
    onChange: (packageName: String, fill: MorpheFill?) -> Unit,
    content: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val state = CardFillState(
        fills = fills,
        onChange = onChange,
        requestEdit = { pkg, name, hex -> editing = EditTarget(pkg, name, hex) },
    )

    CompositionLocalProvider(LocalCardFills provides state) {
        content()
        editing?.let { target ->
            AppCardFillDialog(
                appName = target.appName,
                appIconColorHex = target.appIconColorHex,
                initialFill = fills[target.packageName],
                onDismiss = { editing = null },
                onSave = { fill ->
                    onChange(target.packageName, fill)
                    editing = null
                },
            )
        }
    }
}
