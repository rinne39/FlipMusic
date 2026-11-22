package com.youflip.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFFF5A6E)

/** The red of a liked song's heart. */
val LikeRed = Color(0xFFFF2D46)
val AccentSoft = Color(0xFFFF8A98)
val Ink = Color(0xFF0B0B0F)
val Surface1 = Color(0xFF15151B)
val Surface2 = Color(0xFF1E1E26)
val TextMuted = Color(0xFFA3A3B2)

private val darkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A1621),
    onPrimaryContainer = AccentSoft,
    secondary = Color(0xFFB9B9FF),
    background = Ink,
    onBackground = Color(0xFFF2F2F5),
    surface = Ink,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Color(0xFF2A2A34),
    outline = Color(0xFF3A3A46),
)

/** Apple-style light palette: white pages, grouped-gray containers, the same accent a touch deeper. */
private val lightScheme = lightColorScheme(
    primary = Color(0xFFF0384F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDE1),
    onPrimaryContainer = Color(0xFF7A1020),
    secondary = Color(0xFF5A5AD6),
    background = Color.White,
    onBackground = Color(0xFF0B0B0F),
    surface = Color.White,
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = Color(0xFFEDEDF2),
    onSurfaceVariant = Color(0xFF6C6C74),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFECECF1),
    surfaceContainerHighest = Color(0xFFE4E4EA),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFE5E5EA),
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun YouFlipTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = type, content = content)
}

/** While [active], status/navigation bar icons are light (for the dark player over a light theme). */
@Composable
fun DarkSystemBars(active: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(active) {
        val window = (view.context as? android.app.Activity)?.window
        val c = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val status = c?.isAppearanceLightStatusBars
        val nav = c?.isAppearanceLightNavigationBars
        if (active && c != null) { c.isAppearanceLightStatusBars = false; c.isAppearanceLightNavigationBars = false }
        onDispose { if (active && c != null) { c.isAppearanceLightStatusBars = status == true; c.isAppearanceLightNavigationBars = nav == true } }
    }
}

/** The players sit on darkened album art, so they stay dark in light mode too (like Apple Music). */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkScheme, typography = type) {
        CompositionLocalProvider(LocalContentColor provides darkScheme.onSurface, content = content)
    }
}
