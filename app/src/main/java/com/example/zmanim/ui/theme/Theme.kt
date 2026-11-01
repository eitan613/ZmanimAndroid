package com.example.zmanim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = InkBlue,
    onPrimary = Paper,
    primaryContainer = PaleBlue,
    onPrimaryContainer = DeepInk,
    background = Paper,
    onBackground = DeepInk,
    surface = Paper,
    onSurface = DeepInk,
    surfaceVariant = Mist,
    onSurfaceVariant = Slate,
    outline = Slate,
)

private val DarkColorScheme = darkColorScheme(
    primary = Sky,
    onPrimary = Night,
    primaryContainer = NightBlue,
    onPrimaryContainer = PaleBlue,
    background = Night,
    onBackground = Mist,
    surface = Night,
    onSurface = Mist,
    surfaceVariant = NightBlue,
    onSurfaceVariant = PaleBlue,
    outline = Sky,
)

@Composable
fun ZmanimTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
