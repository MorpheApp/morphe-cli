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
import androidx.compose.ui.graphics.luminance
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

// Morphe Preset Colors
val THEME_PRESET_COLORS = listOf(
    Color(0xFF6750A4),
    Color(0xFF386641),
    Color(0xFF0061A4),
    Color(0xFF8E24AA),
    Color(0xFFEF6C00),
    Color(0xFF00897B),
    Color(0xFFD81B60),
    Color(0xFF5C6BC0),
    Color(0xFF43A047),
    Color(0xFF1DE9B6),
    Color(0xFFFFC400),
    Color(0xFF00B8D4),
    Color(0xFFD32F2F),
    Color(0xFFAFB42B),
    Color(0xFF795548),
    Color(0xFF546E7A)
)

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

/** Morphe Dark. Brand blue and teal on dark grey. */
private val MorpheDarkAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Morphe Amoled. Brighter accents so they still pop on pure black. */
private val MorpheAmoledAccents = MorpheAccentColors(
    primary = Color(0xFF5B9AFF),   // Punchy blue for pure black
    secondary = Color(0xFF00E8C6), // Vivid teal for pure black
)

/** Morphe Light. The brand colors work as they are on light backgrounds. */
private val MorpheLightAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Manager Dark. The manager's Material 3 palette on dark charcoal. */
private val ManagerDarkAccents = MorpheAccentColors(
    primary = Color(0xFFA4C9FF),   // Morphe dark primary, light blue
    secondary = Color(0xFF9CCC65), // Success green for dark surfaces
    tertiary = Color(0xFFD9BDE3),  // Morphe dark tertiary
    warning = Color(0xFFE0A030),   // Amber
)

/** Manager Light. The manager's Material 3 blue accent on light neutrals. */
private val ManagerLightAccents = MorpheAccentColors(
    primary = Color(0xFF005FAC),   // Morphe Material blue (buttons, links, selections)
    secondary = Color(0xFF386A20), // Success green (manager uses green for installed states)
    tertiary = Color(0xFF6D5677),  // Morphe tertiary, muted purple
    warning = Color(0xFFB26A00),   // Amber
)

/** Nord. Arctic frost and aurora, taken from the native Nord palette. */
private val NordAccents = MorpheAccentColors(
    primary = Color(0xFF5EC4DB),   // Frost, saturated
    secondary = Color(0xFF8FD46E), // Aurora green, vivid
    tertiary = Color(0xFF6AA3D9),  // Frost blue, punchy
    warning = Color(0xFFE8BF5A),   // Aurora yellow
)

/** Catppuccin Mocha. Mauve and teal from the native Catppuccin palette. */
private val CatppuccinAccents = MorpheAccentColors(
    primary = Color(0xFFB47BFF),   // Mauve, saturated rather than pastel
    secondary = Color(0xFF4EECD5), // Teal, vivid
    tertiary = Color(0xFF6A9FFF),  // Blue, punchy
    warning = Color(0xFFFF9A5C),   // Peach
)

/** Sakura. Triadic cherry blossom pink, spring sage, wisteria dusk. */
private val SakuraAccents = MorpheAccentColors(
    primary = Color(0xFFD44B76),   // Cherry blossom pink
    secondary = Color(0xFF5B8A72), // Spring leaf sage, the complementary green
    tertiary = Color(0xFF8B6B99),  // Wisteria dusk, a purple structural accent
    warning = Color(0xFFD89A2B),   // Golden stamen amber
)

/** Matcha. Forest green and sage. */
private val MatchaAccents = MorpheAccentColors(
    primary = Color(0xFF4C7A35),   // Tea leaf green
    secondary = Color(0xFF4C7871), // Muted jade
    tertiary = Color(0xFF7D6A9B),  // Soft plum contrast
    warning = Color(0xFFB77833),   // Toasted ochre
)

/** Deepspace. High saturation cyan on near black, the cyberdeck look. */
private val DeepspaceAccents = MorpheAccentColors(
    primary = Color(0xFF00D9FF),   // Electric cyan
    secondary = Color(0xFF79E3A5), // Mint green, stable and success states
    tertiary = Color(0xFF7AB7FF),  // Cool blue, structural
    warning = Color(0xFFFFB347),   // Warm amber, older and warning states
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
 * same dimensions apply everywhere, with no per-screen drift.
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

/**
 * Sharp corners, the Morphe default. A theme uses these unless it opts into
 * [SoftCorners] or [ManagerCorners]. See [ThemePreference.isSoft] and
 * [ThemePreference.isManager].
 */
private val SharpCorners = MorpheCornerStyle(small = 2.dp, medium = 2.dp, large = 2.dp)

/** Soft rounded corners for the warm themes. */
private val SoftCorners = MorpheCornerStyle(small = 10.dp, medium = 14.dp, large = 18.dp)

/** Material 3 rounding from #259: 12dp cards, 16dp sheets, 24dp dialogs. */
private val ManagerCorners = MorpheCornerStyle(small = 12.dp, medium = 16.dp, large = 24.dp)

// ════════════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════

/**
 * Morphe Dark. Brand blue, teal and cyan on dark grey, the original desktop look.
 *
 * Container and outline tokens are set here but were absent from the pre-#259
 * palette, which predates the app reading them. Leaving them unset now falls
 * back to the Material baseline, which is purple and wrong for every theme here.
 */
private val MorpheDarkColorScheme = darkColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFFE3E3E3),
    primary = MorpheColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16305F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = MorpheColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF05433A),
    onSecondaryContainer = Color(0xFF9DF2E2),
    tertiary = MorpheColors.Cyan,
    onTertiary = Color.Black,
    background = Color(0xFF121212),
    onBackground = MorpheColors.TextLight,
    surface = Color(0xFF1E1E1E),
    onSurface = MorpheColors.TextLight,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF2F2F2F),
    error = Color(0xFFCF6679),
    onError = Color.Black,
)

/** Morphe Amoled. The dark palette dropped onto true black for OLED panels. */
private val MorpheAmoledColorScheme = darkColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFFE3E3E3),
    primary = MorpheColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF122A52),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = MorpheColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF04372F),
    onSecondaryContainer = Color(0xFFA8F5E7),
    tertiary = MorpheColors.Cyan,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = MorpheColors.TextLight,
    surface = Color(0xFF0A0A0A),
    onSurface = MorpheColors.TextLight,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF1F1F1F),
    error = Color(0xFFCF6679),
    onError = Color.Black,
)

/** Morphe Light. The same brand palette on light neutrals. */
private val MorpheLightColorScheme = lightColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFF1C1C1C),
    primary = MorpheColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF06214F),
    secondary = MorpheColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F2E8),
    onSecondaryContainer = Color(0xFF00312A),
    tertiary = MorpheColors.Cyan,
    onTertiary = Color.Black,
    background = Color(0xFFFAFAFA),
    onBackground = MorpheColors.TextDark,
    surface = MorpheColors.SurfaceLight,
    onSurface = MorpheColors.TextDark,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF505050),
    outline = Color(0xFF767676),
    outlineVariant = Color(0xFFD0D0D0),
    error = Color(0xFFB00020),
    onError = Color.White,
)

private val ManagerDarkColorScheme = darkColorScheme(
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

private val ManagerAmoledColorScheme = darkColorScheme(
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

private val ManagerLightColorScheme = lightColorScheme(
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

/**
 * Nord. Arctic and cool toned, after nordtheme.com.
 *
 * Container and outline tokens are set explicitly. Leaving them out falls back
 * to the Material baseline, which is purple and wrong for every theme here.
 */
private val NordColorScheme = darkColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFFECEFF4),
    primary = Color(0xFF88C0D0),          // Frost
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF3C5A66),
    onPrimaryContainer = Color(0xFFD8EEF4),
    secondary = Color(0xFFA3BE8C),        // Aurora green
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFF4A5A42),
    onSecondaryContainer = Color(0xFFE2EDD8),
    tertiary = Color(0xFF81A1C1),         // Frost blue
    onTertiary = Color(0xFF2E3440),
    background = Color(0xFF2E3440),       // Polar Night
    onBackground = Color(0xFFECEFF4),     // Snow Storm
    surface = Color(0xFF3B4252),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF7B88A1),
    outlineVariant = Color(0xFF4C566A),
    error = Color(0xFFBF616A),            // Aurora red
    onError = Color(0xFFECEFF4),
)

/** Catppuccin Mocha. Warm pastel dark, after the Mocha flavour. */
private val CatppuccinMochaColorScheme = darkColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFFCDD6F4),
    primary = Color(0xFFCBA6F7),          // Mauve
    onPrimary = Color(0xFF1E1E2E),
    primaryContainer = Color(0xFF4B3A63),
    onPrimaryContainer = Color(0xFFEBDDFC),
    secondary = Color(0xFFF5C2E7),        // Pink
    onSecondary = Color(0xFF1E1E2E),
    secondaryContainer = Color(0xFF5C3F55),
    onSecondaryContainer = Color(0xFFFBDCF2),
    tertiary = Color(0xFF89B4FA),         // Blue
    onTertiary = Color(0xFF1E1E2E),
    background = Color(0xFF1E1E2E),       // Base
    onBackground = Color(0xFFCDD6F4),     // Text
    surface = Color(0xFF313244),          // Surface0
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF45475A),   // Surface1
    onSurfaceVariant = Color(0xFFBAC2DE), // Subtext1
    outline = Color(0xFF7F849C),          // Overlay1
    outlineVariant = Color(0xFF45475A),
    error = Color(0xFFF38BA8),            // Red
    onError = Color(0xFF1E1E2E),
)

/** Sakura. Cherry blossom pink, sage and wisteria on warm petal surfaces. */
private val SakuraColorScheme = lightColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFF3D2832),
    primary = Color(0xFFD44B76),          // Cherry blossom pink
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F0819),
    secondary = Color(0xFF5B8A72),        // Spring leaf sage
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8DA),
    onSecondaryContainer = Color(0xFF10281B),
    tertiary = Color(0xFF8B6B99),         // Wisteria dusk
    onTertiary = Color.White,
    background = Color(0xFFFFF0EA),       // Warm blossom paper
    onBackground = Color(0xFF3D2832),     // Plum tinted ink, not pure black
    surface = Color(0xFFFFE4DC),          // Pink petal surface
    onSurface = Color(0xFF3D2832),
    surfaceVariant = Color(0xFFF5D5CC),   // Deeper blush for emphasis
    onSurfaceVariant = Color(0xFF7A5562), // Plum brown, a sakura bark tone
    outline = Color(0xFFA88592),
    outlineVariant = Color(0xFFEBC9C0),
    error = Color(0xFFC03048),
    onError = Color.White,
)

/** Matcha. Pista green, a fresh light theme. */
private val MatchaColorScheme = lightColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFF21321B),
    primary = Color(0xFF4C7A35),          // Tea leaf green
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDB6),
    onPrimaryContainer = Color(0xFF0F2900),
    secondary = Color(0xFF5E8554),        // Deep herb
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8CE),
    onSecondaryContainer = Color(0xFF16240F),
    tertiary = Color(0xFF92B887),         // Soft matcha
    onTertiary = Color(0xFF21321B),
    background = Color(0xFFF6F8F1),       // Green tinted white
    onBackground = Color(0xFF21321B),     // Deep forest
    surface = Color(0xFFEAF1E1),          // Pale leaf
    onSurface = Color(0xFF21321B),
    surfaceVariant = Color(0xFFD6E2C9),
    onSurfaceVariant = Color(0xFF476042),
    outline = Color(0xFF74806C),
    outlineVariant = Color(0xFFC6D2BC),
    error = Color(0xFFAA3A3A),
    onError = Color.White,
)

/** Deepspace. Electric cyan and mint on near black blue, the cyberdeck look. */
private val DeepspaceColorScheme = darkColorScheme(
    // Neutral, so elevation lifts a surface without tinting it. Material
    // defaults surfaceTint to primary, which is why every elevated surface in
    // the app was washed with the accent. The Manager schemes keep the default
    // on purpose, since that tint is part of the look #259 designed.
    surfaceTint = Color(0xFFD6DEEB),
    primary = Color(0xFF00D9FF),          // Electric cyan
    onPrimary = Color(0xFF001A22),
    primaryContainer = Color(0xFF00404F),
    onPrimaryContainer = Color(0xFFB4EEFF),
    secondary = Color(0xFF79E3A5),        // Mint green
    onSecondary = Color(0xFF0A2317),
    secondaryContainer = Color(0xFF12402A),
    onSecondaryContainer = Color(0xFFA6F3C6),
    tertiary = Color(0xFF7AB7FF),         // Cool blue
    onTertiary = Color(0xFF051628),
    background = Color(0xFF0D1117),       // Near black blue
    onBackground = Color(0xFFD6DEEB),
    surface = Color(0xFF14191F),          // Slightly raised
    onSurface = Color(0xFFD6DEEB),
    surfaceVariant = Color(0xFF1B2128),   // Card surfaces
    onSurfaceVariant = Color(0xFF8E97A6), // Muted text
    outline = Color(0xFF5A6675),
    outlineVariant = Color(0xFF2A323C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1E0707),
)

// ════════════════════════════════════════════════════════════════════
//  THEME PREFERENCE
// ════════════════════════════════════════════════════════════════════

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    MANAGER_LIGHT,
    MANAGER_DARK,
    MANAGER_AMOLED,
    NORD,
    CATPPUCCIN,
    SAKURA,
    MATCHA,
    DEEPSPACE,
    SYSTEM;

    /** Whether this theme uses dark color scheme (for resource qualifiers). */
    fun isDark(): Boolean = when (this) {
        DARK, AMOLED, MANAGER_DARK, MANAGER_AMOLED, NORD, CATPPUCCIN, DEEPSPACE -> true
        LIGHT, MANAGER_LIGHT, SAKURA, MATCHA -> false
        SYSTEM -> false // caller should check isSystemInDarkTheme()
    }

    /** Whether this theme swaps the sharp default for [SoftCorners]. */
    fun isSoft(): Boolean = when (this) {
        SAKURA, MATCHA -> true
        else -> false
    }

    /**
     * Whether this theme is one of the morphe-manager look-alikes added by #259.
     * These keep the manager's Material 3 palette, [ManagerCorners] and Roboto.
     */
    fun isManager(): Boolean = when (this) {
        MANAGER_LIGHT, MANAGER_DARK, MANAGER_AMOLED -> true
        else -> false
    }
}

// ════════════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════════════

@Composable
fun MorpheTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    customAccentColorArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when (themePreference) {
        ThemePreference.DARK -> MorpheDarkColorScheme
        ThemePreference.AMOLED -> MorpheAmoledColorScheme
        ThemePreference.LIGHT -> MorpheLightColorScheme
        ThemePreference.MANAGER_DARK -> ManagerDarkColorScheme
        ThemePreference.MANAGER_AMOLED -> ManagerAmoledColorScheme
        ThemePreference.MANAGER_LIGHT -> ManagerLightColorScheme
        ThemePreference.NORD -> NordColorScheme
        ThemePreference.CATPPUCCIN -> CatppuccinMochaColorScheme
        ThemePreference.SAKURA -> SakuraColorScheme
        ThemePreference.MATCHA -> MatchaColorScheme
        ThemePreference.DEEPSPACE -> DeepspaceColorScheme
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) MorpheDarkColorScheme else MorpheLightColorScheme
        }
    }

    val customPrimary = customAccentColorArgb?.let { Color(it) }

    val colorScheme = if (customPrimary != null) {
        val isDark = baseColorScheme.background.luminance() < 0.5f
        val secondary = customPrimary.shiftLightness(if (isDark) 0.15f else -0.15f)
        val tertiary = customPrimary.shiftLightness(if (isDark) -0.10f else 0.10f)
        val primaryContainer = customPrimary.shiftLightness(if (isDark) 0.25f else -0.25f)
        val secondaryContainer = customPrimary.shiftLightness(if (isDark) 0.35f else -0.35f)
        
        baseColorScheme.copy(
            primary = customPrimary,
            onPrimary = customPrimary.contrastingForeground(),
            secondary = secondary,
            onSecondary = secondary.contrastingForeground(),
            tertiary = tertiary,
            onTertiary = tertiary.contrastingForeground(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryContainer.contrastingForeground(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = secondaryContainer.contrastingForeground(),
            surfaceTint = customPrimary
        )
    } else {
        baseColorScheme
    }

    val corners = when {
        themePreference.isManager() -> ManagerCorners
        themePreference.isSoft() -> SoftCorners
        else -> SharpCorners
    }
    // Each family belongs to its theme group. The soft themes are built on
    // Nunito's rounded terminals, the Manager themes ship Roboto like the app
    // they mimic, and everything else is built around JetBrains Mono. Only the
    // sharp themes double up, since JetBrains Mono is already a monospace face.
    val font = when {
        themePreference.isManager() -> Roboto
        themePreference.isSoft() -> Nunito
        else -> JetBrainsMono
    }
    val monoFont = if (themePreference.isManager() || themePreference.isSoft()) {
        RobotoMono
    } else {
        JetBrainsMono
    }
    val baseAccents = when (themePreference) {
        ThemePreference.DARK -> MorpheDarkAccents
        ThemePreference.AMOLED -> MorpheAmoledAccents
        ThemePreference.LIGHT -> MorpheLightAccents
        ThemePreference.MANAGER_DARK -> ManagerDarkAccents
        ThemePreference.MANAGER_AMOLED -> ManagerDarkAccents
        ThemePreference.MANAGER_LIGHT -> ManagerLightAccents
        ThemePreference.NORD -> NordAccents
        ThemePreference.CATPPUCCIN -> CatppuccinAccents
        ThemePreference.SAKURA -> SakuraAccents
        ThemePreference.MATCHA -> MatchaAccents
        ThemePreference.DEEPSPACE -> DeepspaceAccents
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) MorpheDarkAccents else MorpheLightAccents
    }

    val accents = if (customPrimary != null) {
        val isDark = baseColorScheme.background.luminance() < 0.5f
        val secondary = customPrimary.shiftLightness(if (isDark) 0.15f else -0.15f)
        val tertiary = customPrimary.shiftLightness(if (isDark) -0.10f else 0.10f)
        baseAccents.copy(
            primary = customPrimary,
            secondary = secondary,
            tertiary = tertiary
        )
    } else {
        baseAccents
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
            typography = morpheTypography(font),
            content = content
        )
    }
}

/**
 * An accent on our own themes, and [managerNeutral] on the Manager ones.
 *
 * #259 replaced a number of accent colours with neutral `colorScheme` tokens,
 * which is right for the Manager themes it was designed around and drains the
 * colour out of every other theme. This keeps both: the Manager themes render
 * exactly as they do today, and the rest get their accent back.
 */
@Composable
fun themedAccent(accent: Color, managerNeutral: Color): Color =
    if (LocalThemeState.current.current.isManager()) managerNeutral else accent

fun Color.shiftLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    colorToHSL(this, hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return hslToColor(hsl)
}

fun Color.contrastingForeground(): Color {
    return if (this.luminance() > 0.5f) Color.Black else Color.White
}

private fun colorToHSL(color: Color, hsl: FloatArray) {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    var h = 0f
    var s = 0f
    val l = (max + min) / 2f
    if (max != min) {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            b -> (r - g) / d + 4f
            else -> 0f
        }
        h /= 6f
    }
    hsl[0] = h * 360f
    hsl[1] = s
    hsl[2] = l
}

private fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0] / 360f
    val s = hsl[1]
    val l = hsl[2]
    var r = l
    var g = l
    var b = l
    if (s != 0f) {
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        r = hueToRGB(p, q, h + 1f / 3f)
        g = hueToRGB(p, q, h)
        b = hueToRGB(p, q, h - 1f / 3f)
    }
    return Color(r, g, b)
}

private fun hueToRGB(p: Float, q: Float, t: Float): Float {
    var t0 = t
    if (t0 < 0f) t0 += 1f
    if (t0 > 1f) t0 -= 1f
    if (t0 < 1f / 6f) return p + (q - p) * 6f * t0
    if (t0 < 1f / 2f) return q
    if (t0 < 2f / 3f) return p + (q - p) * (2f / 3f - t0) * 6f
    return p
}
