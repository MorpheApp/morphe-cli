package app.morphe.gui.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * JetBrains Mono — the monospace face for all technical data:
 * versions, package names, architectures, checksums, console output.
 */
val JetBrainsMono: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/JetBrainsMono-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/JetBrainsMono-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/JetBrainsMono-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/JetBrainsMono-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/JetBrainsMono-Bold.ttf", weight = FontWeight.Bold),
    )
