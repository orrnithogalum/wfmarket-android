package com.wfmarket.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.wfmarket.app.data.ThemeMode

/*
 * Without dynamic color, the app is in a soft pastel blue with a muted gold accent. Dark theme
 * uses the pastel tones themselves for its accents, and light theme a quieter mid blue that still
 * carries white text at 4.5:1. Every role was worked out in OKLCH at one hue, which keeps the blue
 * from drifting towards purple as it lightens, and each text color clears 4.5:1 on its background.
 */
private val LightFallback: ColorScheme = lightColorScheme(
    primary = Color(0xFF4666AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7FF),
    onPrimaryContainer = Color(0xFF1A3060),
    inversePrimary = Color(0xFFA7C1F6),
    secondary = Color(0xFF56637D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE7FA),
    onSecondaryContainer = Color(0xFF212B3F),
    tertiary = Color(0xFF876620),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE7C1),
    onTertiaryContainer = Color(0xFF3F2D04),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1D23),
    surface = Color(0xFFF8FAFE),
    onSurface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFFDDE3EF),
    onSurfaceVariant = Color(0xFF4A505C),
    surfaceTint = Color(0xFF4666AB),
    inverseSurface = Color(0xFF2D3037),
    inverseOnSurface = Color(0xFFEDF0F6),
    outline = Color(0xFF777D8A),
    outlineVariant = Color(0xFFC1C8D5),
    surfaceBright = Color(0xFFF8FAFE),
    surfaceDim = Color(0xFFD5D9E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5FB),
    surfaceContainer = Color(0xFFECF0F7),
    surfaceContainerHigh = Color(0xFFE6EBF3),
    surfaceContainerHighest = Color(0xFFE1E5EE),
)
private val DarkFallback: ColorScheme = darkColorScheme(
    primary = Color(0xFFACC7FE),
    onPrimary = Color(0xFF1C2E56),
    primaryContainer = Color(0xFF324978),
    onPrimaryContainer = Color(0xFFDCE8FF),
    inversePrimary = Color(0xFF4666AB),
    secondary = Color(0xFFBCC8DE),
    onSecondary = Color(0xFF293040),
    secondaryContainer = Color(0xFF3A4456),
    onSecondaryContainer = Color(0xFFE0E8F9),
    tertiary = Color(0xFFE8CD9D),
    onTertiary = Color(0xFF433210),
    tertiaryContainer = Color(0xFF624C23),
    onTertiaryContainer = Color(0xFFFAE9CA),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE1E5EB),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE1E5EB),
    surfaceVariant = Color(0xFF424853),
    onSurfaceVariant = Color(0xFFBAC1CE),
    surfaceTint = Color(0xFFACC7FE),
    inverseSurface = Color(0xFFE1E5EB),
    inverseOnSurface = Color(0xFF2D3037),
    outline = Color(0xFF868C99),
    outlineVariant = Color(0xFF424853),
    surfaceBright = Color(0xFF33373D),
    surfaceDim = Color(0xFF0F1216),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF15171C),
    surfaceContainer = Color(0xFF181B21),
    surfaceContainerHigh = Color(0xFF21252B),
    surfaceContainerHighest = Color(0xFF2A2E35),
)

/**
 * Colors whose meaning must hold under every wallpaper, so they aren't drawn from dynamic color.
 * The series values come from a palette validated for colorblind separation on both surfaces.
 */
@Immutable
data class SemanticColors(
    val positive: Color,
    val negative: Color,
    val seriesBlue: Color,
    val seriesOrange: Color,
    val seriesAqua: Color,
    val seriesViolet: Color,
    val ingame: Color,
    val online: Color,
    val offline: Color,
    val platinum: Color,
)

private val LightSemantic = SemanticColors(
    positive = Color(0xFF137A55),
    negative = Color(0xFFC63A39),
    seriesBlue = Color(0xFF2A78D6),
    seriesOrange = Color(0xFFEB6834),
    seriesAqua = Color(0xFF1BAF7A),
    seriesViolet = Color(0xFF6250D6),
    ingame = Color(0xFF7B4FD0),
    online = Color(0xFF1E9E5A),
    offline = Color(0xFF8A8F8D),
    platinum = Color(0xFF3B7FA8),
)

private val DarkSemantic = SemanticColors(
    positive = Color(0xFF4FC99A),
    negative = Color(0xFFE66767),
    seriesBlue = Color(0xFF3987E5),
    seriesOrange = Color(0xFFD95926),
    seriesAqua = Color(0xFF199E70),
    seriesViolet = Color(0xFF9085E9),
    ingame = Color(0xFFB496F2),
    online = Color(0xFF55D08C),
    offline = Color(0xFF8E9492),
    platinum = Color(0xFF8CC8EA),
)

val LocalSemanticColors = compositionLocalOf { LightSemantic }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Pure black behind everything. Cards keep part of their tint, so they still stand out from it. */
private fun ColorScheme.withBlackSurfaces() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = lerp(surfaceBright, Color.Black, 0.5f),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.7f),
    surfaceContainer = lerp(surfaceContainer, Color.Black, 0.6f),
    surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.5f),
    surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, 0.4f),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WfMarketTheme(theme: ThemeMode, dynamicColor: Boolean, amoled: Boolean, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkFallback
        else -> LightFallback
    }.let { if (dark && amoled) it.withBlackSurfaces() else it }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalSemanticColors provides if (dark) DarkSemantic else LightSemantic) {
        // Expressive motion: components move on springs, with a little overshoot.
        MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), shapes = AppShapes, content = content)
    }
}
