/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Muted body/label text color shared across the home UI components. */
@Composable
internal fun homeMutedTextColor(alpha: Float): Color =
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
