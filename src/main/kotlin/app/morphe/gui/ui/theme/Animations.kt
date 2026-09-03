/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

val desktopScreenEnter = fadeIn(
    animationSpec = tween(400, easing = LinearOutSlowInEasing)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = tween(400, easing = FastOutSlowInEasing)
)

val desktopScreenExit = fadeOut(
    animationSpec = tween(300, easing = LinearOutSlowInEasing)
) + scaleOut(
    targetScale = 0.95f,
    animationSpec = tween(300, easing = LinearOutSlowInEasing)
)
