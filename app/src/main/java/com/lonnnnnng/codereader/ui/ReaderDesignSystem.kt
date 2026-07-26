package com.lonnnnnng.codereader.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.ReaderTheme

private val LightBaseColors = lightColorScheme(
    primary = Color(0xFF0A7057),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F3E8),
    onPrimaryContainer = Color(0xFF063C30),
    secondary = Color(0xFF315F7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF2),
    onSecondaryContainer = Color(0xFF15394D),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4AF),
    onTertiaryContainer = Color(0xFF4B3000),
    background = Color(0xFFF4F7F8),
    onBackground = Color(0xFF172024),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172024),
    surfaceVariant = Color(0xFFE6ECEE),
    onSurfaceVariant = Color(0xFF465357),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFA),
    surfaceContainer = Color(0xFFF0F4F5),
    surfaceContainerHigh = Color(0xFFE9EFF0),
    surfaceContainerHighest = Color(0xFFE1E8EA),
    outline = Color(0xFF6B787C),
    outlineVariant = Color(0xFFC8D1D4),
)

private val DarkBaseColors = darkColorScheme(
    primary = Color(0xFF79D8B8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF164F40),
    onPrimaryContainer = Color(0xFFC6F5E4),
    secondary = Color(0xFFA9CCE0),
    onSecondary = Color(0xFF113341),
    secondaryContainer = Color(0xFF304B59),
    onSecondaryContainer = Color(0xFFD5EDF8),
    tertiary = Color(0xFFE8C474),
    onTertiary = Color(0xFF3D2F00),
    tertiaryContainer = Color(0xFF58471B),
    onTertiaryContainer = Color(0xFFFFE9AF),
    background = Color(0xFF17191B),
    onBackground = Color(0xFFE3E8EA),
    surface = Color(0xFF202326),
    onSurface = Color(0xFFE3E8EA),
    surfaceVariant = Color(0xFF2B3033),
    onSurfaceVariant = Color(0xFFBEC8CC),
    surfaceContainerLowest = Color(0xFF141617),
    surfaceContainerLow = Color(0xFF1B1E20),
    surfaceContainer = Color(0xFF202427),
    surfaceContainerHigh = Color(0xFF292E31),
    surfaceContainerHighest = Color(0xFF32383B),
    outline = Color(0xFF89969A),
    outlineVariant = Color(0xFF3D484C),
)

private data class PaletteAccent(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

/** 应用配色只替换交互强调色，阅读表面和文字对比始终由统一亮暗基线兜底。 @author long */
internal fun appColorScheme(theme: ReaderTheme, palette: AppColorPalette): ColorScheme {
    val dark = theme.isDark
    val accent = when (palette) {
        AppColorPalette.EMERALD -> if (dark) {
            PaletteAccent(
                Color(0xFF79D8B8), Color(0xFF00382B), Color(0xFF164F40), Color(0xFFC6F5E4),
                Color(0xFFA9CCE0), Color(0xFF304B59), Color(0xFFD5EDF8),
            )
        } else {
            PaletteAccent(
                Color(0xFF0A7057), Color.White, Color(0xFFD7F3E8), Color(0xFF063C30),
                Color(0xFF315F7A), Color(0xFFDCEAF2), Color(0xFF15394D),
            )
        }

        AppColorPalette.OCEAN -> if (dark) {
            PaletteAccent(
                Color(0xFF8CCBFF), Color(0xFF003258), Color(0xFF174B72), Color(0xFFD5ECFF),
                Color(0xFFBCCBE9), Color(0xFF3A4964), Color(0xFFE2E9FF),
            )
        } else {
            PaletteAccent(
                Color(0xFF155EA8), Color.White, Color(0xFFDCEAF8), Color(0xFF0C365F),
                Color(0xFF4B5F8B), Color(0xFFE1E8F8), Color(0xFF26395F),
            )
        }

        AppColorPalette.AMBER -> if (dark) {
            PaletteAccent(
                Color(0xFFE8C474), Color(0xFF3D2F00), Color(0xFF58471B), Color(0xFFFFE9AF),
                Color(0xFFD6C89F), Color(0xFF514A38), Color(0xFFF3E8CA),
            )
        } else {
            PaletteAccent(
                Color(0xFF805600), Color.White, Color(0xFFFFE4AF), Color(0xFF4B3000),
                Color(0xFF665B3A), Color(0xFFEFE5C8), Color(0xFF3F3817),
            )
        }
    }
    val base = if (dark) DarkBaseColors else LightBaseColors
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
    )
}

internal fun paletteSwatch(palette: AppColorPalette, darkTheme: Boolean): Color =
    appColorScheme(if (darkTheme) ReaderTheme.DARCULA else ReaderTheme.HIGH_CONTRAST_LIGHT, palette).primary

internal val ReaderTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp),
)

internal val ReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

internal object ReaderDimens {
    val pageHorizontal = 14.dp
    val sectionGap = 20.dp
    val itemGap = 8.dp
    val iconTouchTarget = 48.dp
}
