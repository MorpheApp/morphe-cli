package app.morphe.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
    val Blue = Color(0xFF2D62DD)
    val Teal = Color(0xFF00A797)
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
    val warning: Color = Color(0xFFFF9800),  // Warning states (was hardcoded everywhere)
)

val LocalMorpheAccents = compositionLocalOf { MorpheAccentColors(MorpheColors.Blue, MorpheColors.Teal) }

/** Morphe Dark — brand blue + teal on dark gray. */
private val DarkAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Amoled — slightly brighter accents to pop on pure black. */
private val AmoledAccents = MorpheAccentColors(
    primary = Color(0xFF4A7FFF),   // Brighter blue for pure black
    secondary = Color(0xFF00BFA5), // Brighter teal
)

/** Morphe Light — brand colors work fine on light backgrounds. */
private val LightAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Nord — native Nord palette. Arctic frost + aurora. */
private val NordAccents = MorpheAccentColors(
    primary = Color(0xFF88C0D0),   // Nord Frost
    secondary = Color(0xFFA3BE8C), // Nord Aurora Green
    warning = Color(0xFFEBCB8B),   // Nord Aurora Yellow
)

/** Catppuccin Mocha — native Catppuccin palette. Mauve + teal. */
private val CatppuccinAccents = MorpheAccentColors(
    primary = Color(0xFFCBA6F7),   // Mauve
    secondary = Color(0xFF94E2D5), // Teal
    warning = Color(0xFFFAB387),   // Peach
)

/** Sakura — warm rose + dusty lavender. */
private val SakuraAccents = MorpheAccentColors(
    primary = Color(0xFFD4567A),   // Deep rose
    secondary = Color(0xFF9A6DAF), // Dusty lavender
    warning = Color(0xFFE8874A),   // Warm amber
)

/** Matcha — forest green + sage. */
private val MatchaAccents = MorpheAccentColors(
    primary = Color(0xFF5A9A4E),   // Forest green
    secondary = Color(0xFF7AADAF), // Sage teal
    warning = Color(0xFFD4944A),   // Warm ochre
)

// ════════════════════════════════════════════════════════════════════
//  CORNER / SHAPE STYLE SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Defines the corner radius style for the current theme.
 * Sharp themes use 2dp, soft/cute themes use larger radii.
 */
data class MorpheCornerStyle(
    val small: Dp = 2.dp,
    val medium: Dp = 2.dp,
    val large: Dp = 2.dp,
)

val LocalMorpheCorners = compositionLocalOf { MorpheCornerStyle() }

/** Sharp corners for cyberdeck/dev themes. */
private val SharpCorners = MorpheCornerStyle(small = 2.dp, medium = 2.dp, large = 2.dp)

/** Soft rounded corners for cute/warm themes. */
private val SoftCorners = MorpheCornerStyle(small = 10.dp, medium = 14.dp, large = 18.dp)

// ════════════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════

private val MorpheDarkColorScheme = darkColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextLight,
    onSurface = MorpheColors.TextLight,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val MorpheAmoledColorScheme = darkColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextLight,
    onSurface = MorpheColors.TextLight,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val MorpheLightColorScheme = lightColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color(0xFFFAFAFA),
    surface = MorpheColors.SurfaceLight,
    surfaceVariant = Color(0xFFE8E8E8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextDark,
    onSurface = MorpheColors.TextDark,
    onSurfaceVariant = Color(0xFF505050),
    error = Color(0xFFB00020),
    onError = Color.White
)

// ── Nord ──
// Arctic, cool-toned dark theme inspired by nordtheme.com
private val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0),       // Frost
    secondary = Color(0xFFA3BE8C),     // Aurora Green
    tertiary = Color(0xFF81A1C1),      // Frost Blue
    background = Color(0xFF2E3440),    // Polar Night
    surface = Color(0xFF3B4252),       // Polar Night lighter
    surfaceVariant = Color(0xFF434C5E),
    onPrimary = Color(0xFF2E3440),
    onSecondary = Color(0xFF2E3440),
    onTertiary = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4), // Snow Storm
    onSurface = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFFD8DEE9),
    error = Color(0xFFBF616A),         // Aurora Red
    onError = Color(0xFFECEFF4)
)

// ── Catppuccin Mocha ──
// Warm, soothing pastel dark theme
private val CatppuccinMochaColorScheme = darkColorScheme(
    primary = Color(0xFFCBA6F7),       // Mauve
    secondary = Color(0xFFF5C2E7),     // Pink
    tertiary = Color(0xFF89B4FA),      // Blue
    background = Color(0xFF1E1E2E),    // Base
    surface = Color(0xFF313244),       // Surface0
    surfaceVariant = Color(0xFF45475A), // Surface1
    onPrimary = Color(0xFF1E1E2E),
    onSecondary = Color(0xFF1E1E2E),
    onTertiary = Color(0xFF1E1E2E),
    onBackground = Color(0xFFCDD6F4), // Text
    onSurface = Color(0xFFCDD6F4),
    onSurfaceVariant = Color(0xFFBAC2DE), // Subtext1
    error = Color(0xFFF38BA8),         // Red
    onError = Color(0xFF1E1E2E)
)

// ── Sakura ──
// Soft pink, cute aesthetic — light theme with warm blush tones
private val SakuraColorScheme = lightColorScheme(
    primary = Color(0xFFE8729A),       // Rose pink
    secondary = Color(0xFFC75088),     // Deeper rose
    tertiary = Color(0xFFF5A0C0),      // Soft pink
    background = Color(0xFFFFF5F7),    // Blush white
    surface = Color(0xFFFFE8EE),       // Petal
    surfaceVariant = Color(0xFFFFD6E0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF5A1A30),
    onBackground = Color(0xFF4A2030),  // Deep plum
    onSurface = Color(0xFF4A2030),
    onSurfaceVariant = Color(0xFF8A506A),
    error = Color(0xFFD03050),
    onError = Color.White
)

// ── Matcha ──
// Pista green, cute aesthetic — light theme with fresh green tones
private val MatchaColorScheme = lightColorScheme(
    primary = Color(0xFF6DAF5C),       // Pista green
    secondary = Color(0xFF8BC77E),     // Fresh green
    tertiary = Color(0xFFA3D99B),      // Light mint
    background = Color(0xFFF4F9F0),    // Green-tinted white
    surface = Color(0xFFE5F0DC),       // Soft green
    surfaceVariant = Color(0xFFD4E5C8),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1E3318),
    onTertiary = Color(0xFF1E3318),
    onBackground = Color(0xFF1E3318),  // Deep forest
    onSurface = Color(0xFF1E3318),
    onSurfaceVariant = Color(0xFF4A6B3D),
    error = Color(0xFFC04040),
    onError = Color.White
)

// ════════════════════════════════════════════════════════════════════
//  THEME PREFERENCE
// ════════════════════════════════════════════════════════════════════

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    NORD,
    CATPPUCCIN,
    SAKURA,
    MATCHA,
    SYSTEM;

    /** Whether this theme uses dark color scheme (for resource qualifiers). */
    fun isDark(): Boolean = when (this) {
        DARK, AMOLED, NORD, CATPPUCCIN -> true
        LIGHT, SAKURA, MATCHA -> false
        SYSTEM -> false // caller should check isSystemInDarkTheme()
    }

    /** Whether this theme uses soft/rounded corners. */
    fun isSoft(): Boolean = when (this) {
        SAKURA, MATCHA -> true
        else -> false
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
        ThemePreference.NORD -> NordColorScheme
        ThemePreference.CATPPUCCIN -> CatppuccinMochaColorScheme
        ThemePreference.SAKURA -> SakuraColorScheme
        ThemePreference.MATCHA -> MatchaColorScheme
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) MorpheDarkColorScheme else MorpheLightColorScheme
        }
    }

    val corners = if (themePreference.isSoft()) SoftCorners else SharpCorners
    val font = if (themePreference.isSoft()) Nunito else JetBrainsMono
    val accents = when (themePreference) {
        ThemePreference.DARK -> DarkAccents
        ThemePreference.AMOLED -> AmoledAccents
        ThemePreference.LIGHT -> LightAccents
        ThemePreference.NORD -> NordAccents
        ThemePreference.CATPPUCCIN -> CatppuccinAccents
        ThemePreference.SAKURA -> SakuraAccents
        ThemePreference.MATCHA -> MatchaAccents
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkAccents else LightAccents
    }

    CompositionLocalProvider(
        LocalMorpheCorners provides corners,
        LocalMorpheFont provides font,
        LocalMorpheAccents provides accents
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
