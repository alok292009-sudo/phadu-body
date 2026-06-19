package com.example.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox

object IronSpacing {
    val x4 = 4.dp
    val x8 = 8.dp
    val x12 = 12.dp
    val x16 = 16.dp
    val x20 = 20.dp
    val x24 = 24.dp
    val x32 = 32.dp
    val x48 = 48.dp
    val x64 = 64.dp
}

object IronCorner {
    val RadiusSm = 10.dp
    val RadiusMd = 16.dp
    val RadiusLg = 24.dp
    val RadiusFull = 999.dp
}

object IronTypography {
    private val defaultFamily = FontFamily.Default
    
    val Caption = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
        letterSpacing = 0.66.sp, // ~6% of 11px
        color = TextPrimaryColor
    )
    val Footnote = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        color = TextPrimaryColor
    )
    val Body = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.W400,
        color = TextPrimaryColor
    )
    val Callout = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.W500,
        color = TextPrimaryColor
    )
    val Headline = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 17.sp,
        fontWeight = FontWeight.W600,
        color = TextPrimaryColor
    )
    val Title3 = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        color = TextPrimaryColor
    )
    val Title2 = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        color = TextPrimaryColor
    )
    val Title1 = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 34.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.68).sp,
        color = TextPrimaryColor
    )
    val LargeTitle = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 48.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-1.44).sp,
        color = TextPrimaryColor
    )
}

fun Modifier.bouncy(enabled: Boolean = true): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (isPressed) 120 else 80, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)),
        label = "bounce_scale"
    )

    this
        .scale(scale)
        .pointerInput(enabled) {
            if (enabled) {
                while (true) {
                    awaitPointerEventScope {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                    }
                }
            }
        }
}

fun Modifier.bouncyClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    this
        .bouncy(enabled)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

fun Modifier.glassRecipe(shape: Shape = RoundedCornerShape(IronCorner.RadiusMd)): Modifier = this
    .background(Color(0xFFFFFFFF).copy(alpha = 0.07f), shape)
    .border(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.12f), shape)
    
fun Modifier.glassRecipeRaised(shape: Shape = RoundedCornerShape(IronCorner.RadiusMd)): Modifier = this
    .background(Color(0xFFFFFFFF).copy(alpha = 0.10f), shape)
    .border(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.12f), shape)

fun Modifier.skeleton(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E1E1E),
            Color(0xFF2C2C2C),
            Color(0xFF1E1E1E)
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, translateAnim)
    )

    this.background(brush)
}

@Composable
fun EmptyState(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(IronSpacing.x32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(IronSpacing.x64)
                .glassRecipe(RoundedCornerShape(IronCorner.RadiusSm)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AllInbox,
                contentDescription = null,
                tint = TextSecondaryColor,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(IronSpacing.x16))
        
        Text(
            text = message,
            style = IronTypography.Body.copy(color = TextSecondaryColor, textAlign = TextAlign.Center),
            modifier = Modifier.padding(horizontal = IronSpacing.x24)
        )
        
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(IronSpacing.x24))
            
            Box(
                modifier = Modifier
                    .bouncyClick(onClick = onActionClick)
                    .background(TextPrimaryColor, RoundedCornerShape(IronCorner.RadiusSm))
                    .padding(horizontal = IronSpacing.x24, vertical = IronSpacing.x12),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = IronTypography.Headline.copy(color = BgColor)
                )
            }
        }
    }
}
