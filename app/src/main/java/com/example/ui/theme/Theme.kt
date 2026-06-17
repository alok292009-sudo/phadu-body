package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

private val DarkColorScheme =
  darkColorScheme(
    primary = PureWhite,
    onPrimary = TrueBlack,
    secondary = GrayLight,
    onSecondary = TrueBlack,
    background = TrueBlack,
    surface = TrueBlack,
    surfaceVariant = GrayDark,
    onBackground = PureWhite,
    onSurface = PureWhite,
    onSurfaceVariant = GrayMedium,
    outline = GlassBorderLight,
    error = ErrorColor
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
      colorScheme = DarkColorScheme, 
      typography = Typography, 
      content = content
  )
}

// Reusable spring physics bounce modifier to give Apple Liquid UI feel on every tap!
fun Modifier.bounceClick(onClick: () -> Unit) = this.composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f, // subtle overshoot and settle
            stiffness = 300f
        ),
        label = "bounce"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        tryAwaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = {
                    onClick()
                }
            )
        }
}

