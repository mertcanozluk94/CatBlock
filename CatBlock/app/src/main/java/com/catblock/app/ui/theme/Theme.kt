package com.catblock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CatOrange = Color(0xFFF4A261)
private val CatDeep = Color(0xFFE76F51)
private val CatNight = Color(0xFF264653)

private val LightColors = lightColorScheme(
    primary = CatDeep,
    onPrimary = Color.White,
    secondary = CatOrange,
    onSecondary = Color.Black,
    background = Color(0xFFFAF5EF),
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B)
)

private val DarkColors = darkColorScheme(
    primary = CatOrange,
    onPrimary = Color.Black,
    secondary = CatDeep,
    onSecondary = Color.White,
    background = CatNight,
    onBackground = Color.White,
    surface = Color(0xFF1F3540),
    onSurface = Color.White
)

@Composable
fun CatBlockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
