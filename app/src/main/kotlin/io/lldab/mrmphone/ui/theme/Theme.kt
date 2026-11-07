package io.lldab.mrmphone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val MrmColorScheme = darkColorScheme(
    background = MrmBackground,
    surface = MrmSurface,
    surfaceVariant = MrmSurfaceVariant,
    surfaceContainer = MrmSurface,
    surfaceContainerHigh = MrmSurfaceVariant,
    surfaceContainerHighest = MrmSurfaceVariant,
    onBackground = MrmOnBackground,
    onSurface = MrmOnSurface,
    onSurfaceVariant = MrmOnSurfaceMuted,
    primary = MrmPrimary,
    onPrimary = MrmOnPrimary,
    secondaryContainer = MrmSecondaryContainer,
    onSecondaryContainer = MrmOnBackground,
    outline = MrmOnSurfaceMuted,
    outlineVariant = MrmSurfaceVariant,
    error = MrmError,
)

// Square corners everywhere; 2 dp is the most anything gets.
private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
)

@Composable
fun MrmTheme(content: @Composable () -> Unit) {
    // Dark only, never dynamicDarkColorScheme().
    MaterialTheme(
        colorScheme = MrmColorScheme,
        typography = MrmTypography,
        shapes = SquareShapes,
    ) {
        // No Surface at the root, so set the default content colour here:
        // otherwise bare Icons (back arrows) draw black on black.
        CompositionLocalProvider(LocalContentColor provides MrmOnBackground, content = content)
    }
}
