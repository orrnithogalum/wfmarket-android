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

private val LightFallback: ColorScheme = lightColorScheme()
private val DarkFallback: ColorScheme = darkColorScheme()

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

