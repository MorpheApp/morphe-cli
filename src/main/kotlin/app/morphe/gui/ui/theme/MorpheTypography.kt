/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/**
 * Roboto - the standard sans-serif font used for the main UI.
 */
val Roboto: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/Roboto-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/Roboto-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/Roboto-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/Roboto-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Roboto-Bold.ttf", weight = FontWeight.Bold),
    )

/**
 * Roboto Mono - the monospace face for all technical data:
 * versions, package names, architectures, checksums, console output.
 */
val RobotoMono: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/RobotoMono-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/RobotoMono-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/RobotoMono-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/RobotoMono-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/RobotoMono-Bold.ttf", weight = FontWeight.Bold),
    )

/**
 * JetBrains Mono, the face the sharp Morphe themes are built around. Serves as
 * both the UI font and the technical font for those themes: versions, package
 * names, architectures, checksums, console output.
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

/**
 * Nunito, the soft rounded sans for the warm themes (Sakura, Matcha). Generous
 * x-height and fully rounded terminals, which is the whole point of those two.
 */
val Nunito: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/Nunito-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/Nunito-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/Nunito-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/Nunito-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Nunito-Bold.ttf", weight = FontWeight.Bold),
    )

/**
 * Theme-aware font provider. The Manager themes take Roboto, the soft themes take
 * [Nunito], every other theme takes [JetBrainsMono]. See [ThemePreference.isManager]
 * and [ThemePreference.isSoft].
 */
val LocalMorpheFont = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalMorpheMono = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * Material's type scale rebased onto [font].
 *
 * Without this the app's font reaches a `Text` only where the call site sets
 * `fontFamily` by hand, and every one that forgets silently falls back to
 * Material's default. Passing a typography makes the theme's font the default
 * everywhere, so an omission inherits the right face instead of Roboto.
 */
fun morpheTypography(font: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font),
        displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font),
        headlineMedium = base.headlineMedium.copy(fontFamily = font),
        headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font),
        titleMedium = base.titleMedium.copy(fontFamily = font),
        titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font),
        bodyMedium = base.bodyMedium.copy(fontFamily = font),
        bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font),
        labelMedium = base.labelMedium.copy(fontFamily = font),
        labelSmall = base.labelSmall.copy(fontFamily = font),
    )
}
