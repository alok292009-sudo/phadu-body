package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

object IronAnimations {
    // Springs
    // Standard: tension 280, friction 24
    fun <T> springStandard(): SpringSpec<T> = spring(dampingRatio = 0.71f, stiffness = 280f)
    // Bouncy: tension 200, friction 18
    fun <T> springBouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 200f)
    // Gentle: tension 150, friction 22
    fun <T> springGentle(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 150f)
    
    // Durations
    const val durationFast = 150
    const val durationMedium = 300
    const val durationSlow = 500
    
    // Easings
    val easingStandard = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val easingOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

fun Modifier.staggeredEntry(
    index: Int,
    baseDelayMs: Int = 60,
    translationYOffsetDp: Float = 20f,
    springSpec: SpringSpec<Float> = IronAnimations.springStandard()
): Modifier = composed {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * baseDelayMs).toLong())
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = IronAnimations.durationMedium, easing = IronAnimations.easingOut),
        label = "stagger_alpha_$index"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0f else translationYOffsetDp,
        animationSpec = springSpec,
        label = "stagger_translate_$index"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translateY * density
    }
}
