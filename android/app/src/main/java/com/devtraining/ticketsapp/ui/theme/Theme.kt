package com.devtraining.ticketsapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = OnGreenContainer,
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = Red,
    onError = Color.White,
    errorContainer = RedContainer,
    onErrorContainer = OnRedContainer,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = LineSoft,
    onSurfaceVariant = InkMuted,
    // Cards use surfaceContainerHighest, dialogs surfaceContainerHigh.
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = CardWhite,
    surfaceContainerHighest = CardWhite,
    outline = Line,
    outlineVariant = LineSoft,
)

private val DarkColors = darkColorScheme(
    primary = NavyDark,
    onPrimary = OnNavyDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    secondary = GreenDark,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = OnGreenContainerDark,
    tertiary = AmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = RedDark,
    errorContainer = RedContainerDark,
    onErrorContainer = OnRedContainerDark,
    background = Night,
    onBackground = InkDark,
    surface = Night,
    onSurface = InkDark,
    surfaceVariant = LineSoftDark,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = Night,
    surfaceContainerLow = CardNight,
    surfaceContainer = CardNight,
    surfaceContainerHigh = DialogNight,
    surfaceContainerHighest = CardNight,
    outline = LineDark,
    outlineVariant = LineSoftDark,
)

@Composable
fun TicketsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
