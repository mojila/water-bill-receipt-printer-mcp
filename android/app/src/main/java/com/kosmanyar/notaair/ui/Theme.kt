package com.kosmanyar.notaair.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2563EB)
private val BrandDark = Color(0xFF1E3A8A)
private val Accent = Color(0xFF0EA5E9)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = BrandDark,
    secondary = Accent,
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF0B1220),
    primaryContainer = BrandDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Brand,
    background = Color(0xFF0B1220),
    surface = Color(0xFF111C2E),
    onSurface = Color(0xFFE2E8F0)
)

@Composable
fun NotaAirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
