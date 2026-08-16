/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Morphe Brand Colors
object MorpheColors {
    val Blue = Color(0xFF3B7BF7)
    val Teal = Color(0xFF00D1B2)
    val Cyan = Color(0xFF62E1FF)
    val DeepBlack = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val SurfaceLight = Color(0xFFF5F5F5)
    val TextLight = Color(0xFFE3E3E3)
    val TextDark = Color(0xFF1C1C1C)
}

// ════════════════════════════════════════════════════════════════════
//  ACCENT COLOR SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Per-theme accent colors. Components should read from LocalMorpheAccents
 * instead of using MorpheColors.Blue/Teal directly.
 */
data class MorpheAccentColors(
    val primary: Color,    // Buttons, selections, links (replaces MorpheColors.Blue)
    val secondary: Color,  // Badges, options, success states (replaces MorpheColors.Teal)
    val tertiary: Color = Color(0xFF5C6BC0), // Structural emphasis, info accents
    val warning: Color = Color(0xFFFF9800),  // Warning states (was hardcoded everywhere)
)

val LocalMorpheAccents = compositionLocalOf { MorpheAccentColors(MorpheColors.Blue, MorpheColors.Teal) }

/** Morphe Dark. Morphe's Material 3 palette on dark charcoal. */
private val DarkAccents = MorpheAccentColors(
    primary = Color(0xFFA4C9FF),   // Morphe dark primary, light blue
    secondary = Color(0xFF9CCC65), // Success green for dark surfaces
    tertiary = Color(0xFFD9BDE3),  // Morphe dark tertiary
    warning = Color(0xFFE0A030),   // Amber
)

/** Morphe Light. Morphe's Material 3 blue accent on light neutrals. */
private val LightAccents = MorpheAccentColors(
    primary = Color(0xFF005FAC),   // Morphe Material blue (buttons, links, selections)
    secondary = Color(0xFF386A20), // Success green (manager uses green for installed states)
    tertiary = Color(0xFF6D5677),  // Morphe tertiary, muted purple
    warning = Color(0xFFB26A00),   // Amber
)

// ════════════════════════════════════════════════════════════════════
//  CORNER / SHAPE STYLE SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Defines the corner radius style for the current theme.
 */
data class MorpheCornerStyle(
    val small: Dp = 2.dp,
    val medium: Dp = 2.dp,
    val large: Dp = 2.dp,
)

val LocalMorpheCorners = compositionLocalOf { MorpheCornerStyle() }

/**
 * Canonical control sizing across the app. Use these instead of hardcoded `.dp`
 * values for buttons, text fields, search bars, and dialog action rows so the
 * same dimensions apply everywhere — no per-screen drift.
 *
 * - [controlHeight]: standard interactive height (buttons, text fields, pills,
 *   search bars). Matches the height of OPEN LOGS / OPEN APP DATA action buttons.
 * - [iconInControl]: icon size used inside controlHeight-sized affordances.
 * - [controlHorizontalPadding]: standard horizontal padding inside a control.
 */
data class MorpheDimens(
    val controlHeight: Dp = 36.dp,
    val iconInControl: Dp = 14.dp,
    val controlHorizontalPadding: Dp = 12.dp,
)

val LocalMorpheDimens = compositionLocalOf { MorpheDimens() }

/** Material 3 rounding: 12dp cards, 16dp sheets, 24dp dialogs. */
private val Corners = MorpheCornerStyle(small = 12.dp, medium = 16.dp, large = 24.dp)

// ════════════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════

private val MorpheDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD9BDE3),
    onTertiary = Color(0xFF3D2946),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val MorpheAmoledColorScheme = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD9BDE3),
    onTertiary = Color(0xFF3D2946),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    onBackground = MorpheColors.TextLight,
    onSurface = MorpheColors.TextLight,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val MorpheLightColorScheme = lightColorScheme(
    primary = Color(0xFF005FAC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C39),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6D5677),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

// ════════════════════════════════════════════════════════════════════
//  THEME PREFERENCE
// ════════════════════════════════════════════════════════════════════

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM;

    /** Whether this theme uses dark color scheme (for resource qualifiers). */
    fun isDark(): Boolean = when (this) {
        DARK, AMOLED -> true
        LIGHT -> false
        SYSTEM -> false // caller should check isSystemInDarkTheme()
    }

}

// ════════════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════════════

@Composable
fun MorpheTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreference) {
        ThemePreference.DARK -> MorpheDarkColorScheme
        ThemePreference.AMOLED -> MorpheAmoledColorScheme
        ThemePreference.LIGHT -> MorpheLightColorScheme
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) MorpheDarkColorScheme else MorpheLightColorScheme
        }
    }

    val corners = Corners
    val font = Roboto
    val monoFont = RobotoMono
    val accents = when (themePreference) {
        ThemePreference.DARK -> DarkAccents
        ThemePreference.AMOLED -> DarkAccents
        ThemePreference.LIGHT -> LightAccents
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkAccents else LightAccents
    }

    CompositionLocalProvider(
        LocalMorpheCorners provides corners,
        LocalMorpheFont provides font,
        LocalMorpheMono provides monoFont,
        LocalMorpheAccents provides accents,
        LocalMorpheDimens provides MorpheDimens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
