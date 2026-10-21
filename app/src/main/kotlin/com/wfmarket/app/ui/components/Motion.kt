@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.wfmarket.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The timing of the app's own animations. Material components keep the theme's expressive springs,
 * but the motion added around them is quieter: Material's emphasized easing, which starts quickly
 * and settles gently, with nothing that overshoots or bounces.
 */
object Motion {
    /** Material's emphasized curve, for moving between two places on screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Material's emphasized decelerate curve, for things arriving. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Something moving or growing to its place, such as a picture flying to the next screen. */
    fun <T> move(durationMillis: Int = 400): FiniteAnimationSpec<T> = tween(durationMillis, easing = Emphasized)

    /** Something arriving, such as a section settling in or a bar filling. */
    fun <T> arrive(durationMillis: Int = 300): FiniteAnimationSpec<T> = tween(durationMillis, easing = EmphasizedDecelerate)

    /** Something fading in. */
    fun <T> fadeIn(durationMillis: Int = 220): FiniteAnimationSpec<T> = tween(durationMillis, easing = EmphasizedDecelerate)

    /** Something fading out, quicker than things fade in, so the old never lingers over the new. */
    fun <T> fadeOut(durationMillis: Int = 120): FiniteAnimationSpec<T> = tween(durationMillis)
}

/**
 * A list row's way in, out, and to a new place when the list changes, in the app's quieter
 * timing. Rows arriving fade in where they land; rows leaving fade out; the rest slide over.
 */
fun LazyItemScope.listItemMotion(): Modifier =
    Modifier.animateItem(fadeInSpec = Motion.fadeIn(), placementSpec = Motion.move(350), fadeOutSpec = Motion.fadeOut())

/** The layout that shared elements fly across, around all the screens. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current screen's way in and out, which its shared elements move with. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Which card an auction was last opened from, as `list:id`. The same auction can be in several
 * lists at once, such as Recent and a search, and its picture should fly back to the one it left.
 */
@Stable
class OpenedFrom {
    var key: String? by mutableStateOf(null)
}

val LocalOpenedFrom = staticCompositionLocalOf { OpenedFrom() }

/**
 * Morphs this into whatever has the same [key] on the next screen: the bounds move while the two
 * cross-fade, so a card's picture grows into the page's.
 */
@Composable
fun Modifier.sharedAcrossScreens(key: String?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (key == null || shared == null || animated == null) return this
    return with(shared) {
        this@sharedAcrossScreens.sharedBounds(
            rememberSharedContentState(key),
            animated,
            enter = androidx.compose.animation.fadeIn(Motion.fadeIn()),
            exit = androidx.compose.animation.fadeOut(Motion.fadeOut()),
            boundsTransform = { _, _ -> Motion.move() },
        )
    }
}

/**
 * Fades this in, rising a few dp into place, when it first appears, [index] steps behind the first,
 * so content that arrives together settles in reading order.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, stepMillis: Long = 25): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * stepMillis)
        progress.animateTo(1f, Motion.arrive(300))
    }
    val lift = with(LocalDensity.current) { 8.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1 - progress.value) * lift
    }
}

/**
 * A number that changes in place, shifting a little up as it grows or down as it falls while the
 * old one fades, so a change reads as a change rather than a swap.
 */
@Composable
fun <T : Comparable<T>> RollingValue(value: T, content: @Composable (T) -> Unit) {
    AnimatedContent(
        value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(Motion.arrive(250)) { (if (up) it else -it) / 3 } + fadeIn(Motion.fadeIn(200)))
                .togetherWith(slideOutVertically(Motion.move(250)) { (if (up) -it else it) / 3 } + fadeOut(Motion.fadeOut(100)))
        },
        label = "rolling",
    ) { content(it) }
}

/** Platinum that changes in place. */
@Composable
fun RollingPlatinum(value: Double, style: TextStyle = MaterialTheme.typography.titleMedium) {
    RollingValue(value) { Platinum(it, style = style) }
}
