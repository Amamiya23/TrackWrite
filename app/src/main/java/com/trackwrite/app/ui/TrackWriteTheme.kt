package com.trackwrite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.trackwrite.app.settings.AppearanceMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E293B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
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
    background = Color(0xFFEFF2F7),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEFF2F7),
    surfaceContainerHigh = Color(0xFFE2E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BCDB8),
    onPrimary = Color(0xFF0E372E),
    primaryContainer = Color(0xFF174D41),
    onPrimaryContainer = Color(0xFFD2F2E5),
    secondary = Color(0xFFB9CBC1),
    onSecondary = Color(0xFF26352F),
    secondaryContainer = Color(0xFF3D4C45),
    onSecondaryContainer = Color(0xFFDCE8E0),
    tertiary = Color(0xFFE9BE7F),
    onTertiary = Color(0xFF4B310F),
    tertiaryContainer = Color(0xFF66471B),
    onTertiaryContainer = Color(0xFFFFE5C1),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF68120C),
    errorContainer = Color(0xFF8B2118),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF111512),
    onBackground = Color(0xFFE4E9E3),
    surface = Color(0xFF171D19),
    onSurface = Color(0xFFE4E9E3),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFC5D0C8),
    outline = Color(0xFF8E9A92),
    outlineVariant = Color(0xFF3F4943),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1E2420),
    surfaceContainerHigh = Color(0xFF283029),
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
        AppearanceMode.System -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        AppearanceMode.Light -> LightColors
        AppearanceMode.Dark -> DarkColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

val ColorScheme.successContainer: Color
    get() = primaryContainer

val ColorScheme.onSuccessContainer: Color
    get() = onPrimaryContainer
