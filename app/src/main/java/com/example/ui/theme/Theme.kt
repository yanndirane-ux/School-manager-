package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SchoolLightColorScheme = lightColorScheme(
    primary = MidnightNavy,
    onPrimary = PureWhite,
    primaryContainer = IceBlueContainer,
    onPrimaryContainer = MidnightNavy,
    secondary = SkyBluePrimary,
    onSecondary = PureWhite,
    secondaryContainer = ColorTokens.SkyLightContainer,
    onSecondaryContainer = DeepRoyalBlue,
    tertiary = SkyBlueBright,
    onTertiary = PureWhite,
    background = SoftSlateBackground,
    onBackground = CharcoalInk,
    surface = PureWhite,
    onSurface = CharcoalInk,
    surfaceVariant = IceBlueContainer,
    onSurfaceVariant = SlateMuted,
    outline = SlateBorder,
    error = CoralError,
    onError = PureWhite,
    errorContainer = CoralContainer,
    onErrorContainer = CoralError
)

private val SchoolDarkColorScheme = darkColorScheme(
    primary = SkyBlueLight,
    onPrimary = DarkMidnightBg,
    primaryContainer = DeepRoyalBlue,
    onPrimaryContainer = PureWhite,
    secondary = SkyBlueBright,
    onSecondary = DarkMidnightBg,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = SkyBlueLight,
    onTertiary = DarkMidnightBg,
    background = DarkMidnightBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkSurfaceElevated,
    error = CoralError,
    onError = PureWhite,
    errorContainer = CoralContainer,
    onErrorContainer = CoralError
)

private object ColorTokens {
    val SkyLightContainer = androidx.compose.ui.graphics.Color(0xFFDBEAFE)
}

val SchoolShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SchoolManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SchoolDarkColorScheme else SchoolLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SchoolShapes,
        content = content
    )
}
