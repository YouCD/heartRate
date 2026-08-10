package online.youcd.heartrate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = WahooAccent,
    onPrimary = WahooTextPrimary,
    secondary = ZoneWarmUp,
    onSecondary = WahooTextPrimary,
    background = WahooBlack,
    onBackground = WahooTextPrimary,
    surface = WahooSurface,
    onSurface = WahooTextPrimary,
    surfaceVariant = WahooCard,
    onSurfaceVariant = WahooTextSecondary,
    outline = WahooDivider,
    error = ZoneMax,
    onError = WahooTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = WahooAccent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = ZoneWarmUp,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    error = ZoneMax,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun HeartRateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = HeartRateTypography,
        content = content
    )
}
