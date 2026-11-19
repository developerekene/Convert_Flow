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

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltPrimaryContainer,
    onPrimaryContainer = CobaltOnPrimaryContainer,
    secondary = MintSecondary,
    onSecondary = Color.White,
    secondaryContainer = MintContainer,
    onSecondaryContainer = MintOnContainer,
    tertiary = CoralAccent,
    onTertiary = Color.White,
    tertiaryContainer = CoralContainer,
    onTertiaryContainer = CoralOnContainer,
    background = StudioPaper,
    onBackground = StudioInk,
    surface = StudioSurface,
    onSurface = StudioInk,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = StudioMutedInk,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.55f)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCobalt,
    onPrimary = Color(0xFF071340),
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = Color(0xFFE2E8FF),
    secondary = DarkMint,
    onSecondary = Color(0xFF022B23),
    secondaryContainer = DarkMintContainer,
    onSecondaryContainer = Color(0xFFBDF8EB),
    tertiary = DarkCoral,
    onTertiary = Color(0xFF3E1202),
    background = DarkObsidian,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkMutedInk,
    outline = DarkBorder,
    outlineVariant = DarkBorder.copy(alpha = 0.55f)
)

val ConvertFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ConvertFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConvertFlowTypography,
        shapes = ConvertFlowShapes,
        content = content
    )
}
