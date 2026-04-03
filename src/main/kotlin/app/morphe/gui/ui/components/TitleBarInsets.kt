package app.morphe.gui.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope

/**
 * Insets for the title bar region. On macOS with transparent title bar,
 * the traffic lights occupy ~80dp on the left and some space from the top.
 * Screens should apply these to their header rows so controls don't
 * overlap with native window buttons.
 */
data class TitleBarInsets(
    val start: androidx.compose.ui.unit.Dp = 0.dp,
    val top: androidx.compose.ui.unit.Dp = 0.dp,
    val end: androidx.compose.ui.unit.Dp = 0.dp
)

val LocalTitleBarInsets = compositionLocalOf { TitleBarInsets() }

/**
 * Provides FrameWindowScope so composables deep in the tree can use
 * WindowDraggableArea for native window dragging.
 */
val LocalFrameWindowScope = staticCompositionLocalOf<FrameWindowScope?> { null }
