package com.trackwrite.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trackwrite.app.settings.AppearanceMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D6B57),
    onPrimary = Color(0xFFF7FBF7),
    primaryContainer = Color(0xFFDDEEE6),
    onPrimaryContainer = Color(0xFF123E34),
    secondary = Color(0xFF596E64),
    onSecondary = Color(0xFFF7FBF7),
    secondaryContainer = Color(0xFFE4EBE6),
    onSecondaryContainer = Color(0xFF25352F),
    tertiary = Color(0xFF8A5B23),
    onTertiary = Color(0xFFFFFBF4),
    tertiaryContainer = Color(0xFFFFE5C1),
    onTertiaryContainer = Color(0xFF4B310F),
    error = Color(0xFFA93226),
    onError = Color(0xFFFFFBF8),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF5E120C),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF18201C),
    surface = Color(0xFFFBFCF8),
    onSurface = Color(0xFF18201C),
    surfaceVariant = Color(0xFFE7ECE7),
    onSurfaceVariant = Color(0xFF4F5D56),
    outline = Color(0xFFC8D1CA),
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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

val ColorScheme.successContainer: Color
    get() = primaryContainer

val ColorScheme.onSuccessContainer: Color
    get() = onPrimaryContainer
