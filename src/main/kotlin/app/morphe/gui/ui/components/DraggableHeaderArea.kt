/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps content in a WindowDraggableArea on macOS so the header row
 * can be used to drag the window. Interactive children (buttons, etc.)
 * still receive clicks normally — only drags on empty space move the window.
 * On non-macOS or when FrameWindowScope is unavailable, renders content directly.
 */
@Composable
fun DraggableHeaderArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val frameScope = LocalFrameWindowScope.current
    if (frameScope != null) {
        with(frameScope) {
            WindowDraggableArea(modifier = modifier) {
                content()
            }
        }
    } else {
        content()
    }
}
