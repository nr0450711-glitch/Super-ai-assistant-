package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = OnArcCyan,
    primaryContainer = ArcCyanContainer,
    onPrimaryContainer = Color(0xFFA8F3FF),
    secondary = VipAmber,
    onSecondary = OnVipAmber,
    secondaryContainer = VipAmberContainer,
    onSecondaryContainer = Color(0xFFFFDF94),
    tertiary = NeuralViolet,
    onTertiary = Color(0xFF1A0847),
    tertiaryContainer = NeuralVioletContainer,
    onTertiaryContainer = Color(0xFFE6DDFF),
    background = ObsidianBlack,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = HudCoral,
    onError = Color(0xFF33000B)
)

private val JarvisLightColorScheme = lightColorScheme(
    primary = LightCyan,
    onPrimary = Color.White,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = Color(0xFF002026),
    secondary = LightAmber,
    onSecondary = Color.White,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = Color(0xFF2D1900),
    tertiary = Color(0xFF5A38C4),
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightElevated,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = Color(0xFFC62845),
    onError = Color.White
)

val JarvisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisVipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> JarvisDarkColorScheme
        else -> JarvisLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
