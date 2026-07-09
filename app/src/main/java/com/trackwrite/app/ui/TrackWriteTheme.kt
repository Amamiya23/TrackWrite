package com.trackwrite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackwrite.app.settings.AppearanceMode

object TrackShape {
    val pill = RoundedCornerShape(50)
    val control = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
}

object TrackSpacing {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x7 = 28.dp
}

object TrackAlpha {
    const val disabled = 0.38f
    const val faint = 0.60f
    const val subtle = 0.80f
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

private val LightBackground = Color(0xFFF7F7F7)
private val LightCard = Color(0xFFFFFFFF)
private val LightPanel = Color(0xFFF4F5F7)
private val LightPrimary = Color(0xFF326AA8)
private val LightPrimarySoft = Color(0xFFEAF2FB)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFAFCFF),
    primaryContainer = LightPrimarySoft,
    onPrimaryContainer = Color(0xFF234A73),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF92400E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightCard,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightPanel,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceContainerLow = LightCard,
    surfaceContainer = Color(0xFFECEFF3),
    surfaceContainerHigh = LightPanel,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F0),
    onPrimary = Color(0xFF0F2748),
    primaryContainer = Color(0xFF1B3A5F),
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = Color(0xFF64748B),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFCBD5E1),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF78350F),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    surfaceContainerLow = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF273549),
    surfaceContainerHigh = Color(0xFF334155),
)

@Composable
fun TrackWriteTheme(
    appearance: AppearanceMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (appearance) {
        AppearanceMode.System -> isSystemInDarkTheme()
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    }

    val colorScheme = when (appearance) {
        AppearanceMode.System -> if (darkTheme) DarkColors else LightColors
        AppearanceMode.Light -> LightColors
        AppearanceMode.Dark -> DarkColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

val ColorScheme.successContainer: Color
    get() = primaryContainer

val ColorScheme.onSuccessContainer: Color
    get() = onPrimaryContainer
