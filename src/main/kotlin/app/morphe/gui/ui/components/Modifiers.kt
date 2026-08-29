/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * Turns the desktop cursor into a hand over an interactive surface.
 *
 * Compose leaves the arrow in place on its own, including on `clickable` and on
 * the Material buttons, so every affordance MUST opt in for the pointer to read
 * as clickable. Apply it next to the `clickable` it belongs to.
 */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)
