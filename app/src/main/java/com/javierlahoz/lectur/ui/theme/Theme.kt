package com.javierlahoz.lectur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.javierlahoz.lectur.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE7EF),
    onPrimaryContainer = Color(0xFF06232D),
    secondary = Color(0xFF7A5C3E),
    background = Color(0xFFFBF8F4),
    onBackground = Color(0xFF1B1B1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEDE7DF),
    onSurfaceVariant = Color(0xFF4C4640),
    outline = Color(0xFFB6ADA2)
)

/** Modo oscuro real: fondo negro y texto blanco, pensado para leer de noche. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ACBDD),
    onPrimary = Color(0xFF06232D),
    primaryContainer = Color(0xFF16323C),
    onPrimaryContainer = Color(0xFFCDE7EF),
    secondary = Color(0xFFD8BE9E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFD6D6D6),
    outline = Color(0xFF4A4A4A)
)

private val LecturTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelMedium = TextStyle(fontSize = 13.sp)
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LecturTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LecturTypography,
        content = content
    )
}
