package com.easylog.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MutedCyan,
    onPrimary = Color.White,
    primaryContainer = MutedCyanLight,
    onPrimaryContainer = Color.White,

    secondary = CyanGrey80,
    tertiary = Teal80,

    background = DarkCharcoal,
    onBackground = Color.White,

    surface = DarkCharcoal,
    onSurface = Color.White,

    surfaceVariant = MediumCharcoal,
    onSurfaceVariant = Color(0xFFB0BEC5),

    error = Color(0xFFCF6679),
    onError = Color.White
)

@Composable
fun EasyLogTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
