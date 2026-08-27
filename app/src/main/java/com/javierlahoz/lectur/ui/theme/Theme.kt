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

/** Papel crema y tinta marron, como un libro de bolsillo. */
private val SepiaColors = lightColorScheme(
    primary = Color(0xFF7A5C3E),
    onPrimary = Color(0xFFFDF6E7),
    primaryContainer = Color(0xFFE8D9BC),
    onPrimaryContainer = Color(0xFF3B2F2F),
    secondary = Color(0xFF1F4E5F),
    background = Color(0xFFF5ECD7),
    onBackground = Color(0xFF3B2F2F),
    surface = Color(0xFFFBF3E2),
    onSurface = Color(0xFF3B2F2F),
    surfaceVariant = Color(0xFFEADFC4),
    onSurfaceVariant = Color(0xFF5B4B3A),
    outline = Color(0xFFB9A585)
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

/** Como hay que tenir las paginas del PDF para que peguen con el tema. */
enum class PageTint {
    /** Papel blanco, la pagina tal cual viene. */
    NONE,

    /** Papel crema y tinta marron. */
    SEPIA,

    /** Colores invertidos: fondo negro y letras blancas. */
    INVERT
}

/** Resuelve el modo "automatico" al tema que toca ahora mismo. */
@Composable
fun resolveTheme(mode: ThemeMode): ThemeMode = when (mode) {
    ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
    else -> mode
}

fun pageTintOf(resolved: ThemeMode): PageTint = when (resolved) {
    ThemeMode.DARK -> PageTint.INVERT
    ThemeMode.SEPIA -> PageTint.SEPIA
    else -> PageTint.NONE
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = resolveTheme(mode) == ThemeMode.DARK

@Composable
fun LecturTheme(theme: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) {
            ThemeMode.DARK -> DarkColors
            ThemeMode.SEPIA -> SepiaColors
            else -> LightColors
        },
        typography = LecturTypography,
        content = content
    )
}
