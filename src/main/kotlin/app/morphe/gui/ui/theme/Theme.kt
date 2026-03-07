package app.morphe.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM
}

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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
