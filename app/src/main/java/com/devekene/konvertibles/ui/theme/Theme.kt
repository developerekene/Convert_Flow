package com.devekene.konvertibles.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = CrimsonOnContainer,
    secondary = CharcoalSecondary,
    onSecondary = Color.White,
    secondaryContainer = CharcoalContainer,
    onSecondaryContainer = CharcoalInk,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = Color(0xFF034730),
    background = PureWhite,
    onBackground = CharcoalInk,
    surface = SoftSurface,
    onSurface = CharcoalInk,
    surfaceVariant = SoftWell,
    onSurfaceVariant = MutedSlate,
    outline = SubtleOutline,
    outlineVariant = SubtleOutline.copy(alpha = 0.7f)
)

val KonvertiblesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun KonvertiblesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = ConvertFlowTypography,
        shapes = KonvertiblesShapes,
        content = content
    )
}
