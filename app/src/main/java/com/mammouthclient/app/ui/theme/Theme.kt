package com.mammouthclient.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MammouthGreen = Color(0xFF2F5D50)
private val MammouthGreenLight = Color(0xFF8FD3BE)
private val MammouthSand = Color(0xFFB4795A)

private val LightColors = lightColorScheme(
    primary = MammouthGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EBD8),
    onPrimaryContainer = Color(0xFF002016),
    secondary = MammouthSand,
    onSecondary = Color.White,
    background = Color(0xFFFBFDF9),
    surface = Color(0xFFFBFDF9),
    surfaceVariant = Color(0xFFDCE5DF)
)

private val DarkColors = darkColorScheme(
    primary = MammouthGreenLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF14513E),
    onPrimaryContainer = Color(0xFFB8EBD8),
    secondary = Color(0xFFE8B48F),
    onSecondary = Color(0xFF44290F),
    background = Color(0xFF111412),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF3F4A44)
)

@Composable
fun MammouthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
