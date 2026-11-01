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
    secondary = Slate,
    onSecondary = Paper,
    secondaryContainer = PaleBlue,
    onSecondaryContainer = DeepInk,
    tertiary = InkBlue,
    onTertiary = Paper,
    tertiaryContainer = PaleBlue,
    onTertiaryContainer = DeepInk,
    surfaceTint = InkBlue,
    surfaceContainer = Mist,
    surfaceContainerLow = Paper,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = PaleBlue,
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
    secondary = Sky,
    onSecondary = Night,
    secondaryContainer = NightBlue,
    onSecondaryContainer = PaleBlue,
    tertiary = Sky,
    onTertiary = Night,
    tertiaryContainer = NightBlue,
    onTertiaryContainer = PaleBlue,
    surfaceTint = Sky,
    surfaceContainer = NightBlue,
    surfaceContainerLow = Night,
    surfaceContainerHigh = NightBlue,
    surfaceContainerHighest = NightBlue,
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
