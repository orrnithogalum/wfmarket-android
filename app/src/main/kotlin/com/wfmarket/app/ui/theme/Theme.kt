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
 * Without dynamic color, the app is in Warframe's royal blue, the warframe.market blue behind the
 * launcher icon (#0C2F96), with a gold accent. Light theme uses that blue itself as its primary.
 * Dark theme's primary is the same hue at the deepest tone that still carries white text at 4.5:1
 * and stands out from the page at 3:1, with the icon's blue for containers. The other roles follow
 * Material's tones, worked out in OKLab, which keeps blue from drifting towards purple as it lightens.
 */
private val LightFallback: ColorScheme = lightColorScheme(
    primary = Color(0xFF0C2F96),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF01006E),
    inversePrimary = Color(0xFF3067F4),
    secondary = Color(0xFF4B5E87),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3FF),
    onSecondaryContainer = Color(0xFF0B1A3C),
    tertiary = Color(0xFF795900),
    onTertiary = Color(0xFFFFFFFE),
    tertiaryContainer = Color(0xFFFFDF9B),
    onTertiaryContainer = Color(0xFF281800),
    error = Color(0xFFB91C1E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410000),
    background = Color(0xFFF6F9FF),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF6F9FF),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFDAE3F4),
    onSurfaceVariant = Color(0xFF404755),
    surfaceTint = Color(0xFF0C2F96),
    inverseSurface = Color(0xFF2D3038),
    inverseOnSurface = Color(0xFFECF1FB),
    outline = Color(0xFF707786),
    outlineVariant = Color(0xFFBEC7D7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F9FF),
    surfaceDim = Color(0xFFD5DAE4),
    surfaceContainer = Color(0xFFE9EEF8),
    surfaceContainerHigh = Color(0xFFE3E8F2),
    surfaceContainerHighest = Color(0xFFDEE3EC),
    surfaceContainerLow = Color(0xFFEFF4FD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)
private val DarkFallback: ColorScheme = darkColorScheme(
    primary = Color(0xFF3067F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0C2F96),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = Color(0xFF0C2F96),
    secondary = Color(0xFFB0C7F5),
    onSecondary = Color(0xFF1F2F54),
    secondaryContainer = Color(0xFF34466D),
    onSecondaryContainer = Color(0xFFD5E3FF),
    tertiary = Color(0xFFE8C270),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDF9B),
    error = Color(0xFFFFB4AA),
    onError = Color(0xFF690002),
    errorContainer = Color(0xFF930008),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFDEE3EC),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFDEE3EC),
    surfaceVariant = Color(0xFF404755),
    onSurfaceVariant = Color(0xFFBEC7D7),
    surfaceTint = Color(0xFF3067F4),
    inverseSurface = Color(0xFFDEE3EC),
    inverseOnSurface = Color(0xFF2D3038),
    outline = Color(0xFF8991A1),
    outlineVariant = Color(0xFF404755),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363941),
    surfaceDim = Color(0xFF10131A),
    surfaceContainer = Color(0xFF1C2026),
    surfaceContainerHigh = Color(0xFF262A31),
    surfaceContainerHighest = Color(0xFF31353C),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainerLowest = Color(0xFF0B0E14),
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
