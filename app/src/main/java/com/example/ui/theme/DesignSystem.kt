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
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    
    val Display = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 48.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-1.44).sp,
        lineHeight = 52.sp,
        color = TextPrimaryColor
    )
    
    val Heading = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 34.sp,
        fontWeight = FontWeight.W800,
        letterSpacing = (-0.68).sp,
        lineHeight = 40.sp,
        color = TextPrimaryColor
    )

    val Subheading = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        lineHeight = 32.sp,
        color = TextPrimaryColor
    )

    val Title = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.W700,
        lineHeight = 28.sp,
        color = TextPrimaryColor
    )

    val Headline = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 17.sp,
        fontWeight = FontWeight.W600,
        lineHeight = 22.sp,
        color = TextPrimaryColor
    )

    val Body = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 22.sp,
        color = TextPrimaryColor
    )

    val Callout = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.W500,
        lineHeight = 20.sp,
        color = TextPrimaryColor
    )

    val Footnote = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 18.sp,
        color = TextPrimaryColor
    )

    val Caption = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.5.sp,
        lineHeight = 14.sp,
        color = TextPrimaryColor
    )

    val Micro = androidx.compose.ui.text.TextStyle(
        fontFamily = defaultFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.sp,
        lineHeight = 12.sp,
        color = TextPrimaryColor
    )
}

/**
 * A specialized text component that scales its internal typography to fit
 * within a required line count, preventing layout destruction.
 */
@Composable
fun AutoResizingText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true
) {
    var resizedTextStyle by remember(text, style) { mutableStateOf(style) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }

    val defaultColor = if (color == Color.Unspecified) style.color else color

    Text(
        text = text,
        color = defaultColor,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        style = resizedTextStyle,
        softWrap = softWrap,
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = overflow,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth || textLayoutResult.lineCount > maxLines) {
                val currentSize = resizedTextStyle.fontSize.value
                val currentSpacing = resizedTextStyle.letterSpacing.value
                val currentLineHeight = resizedTextStyle.lineHeight.value
                
                if (currentSize > 10f) {
                    // 1. Scale font size
                    resizedTextStyle = resizedTextStyle.copy(
                        fontSize = (currentSize * 0.92f).sp,
                        lineHeight = (currentLineHeight * 0.92f).sp,
                        letterSpacing = (currentSpacing * 0.95f).sp
                    )
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}

fun Modifier.bouncy(enabled: Boolean = true): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 80, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        } else {
            spring(dampingRatio = 0.71f, stiffness = 280f)
        },
        label = "bounce_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = if (isPressed) {
            tween(durationMillis = 80, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        } else {
            spring(dampingRatio = 0.71f, stiffness = 280f)
        },
        label = "bounce_alpha"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
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
    .background(Color(0xFFFFFFFF).copy(alpha = 0.05f), shape)
    .border(0.5.dp, Color(0xFFFFFFFF).copy(alpha = 0.1f), shape)
    
fun Modifier.glassRecipeRaised(shape: Shape = RoundedCornerShape(IronCorner.RadiusMd)): Modifier = this
    .background(Color(0xFFFFFFFF).copy(alpha = 0.08f), shape)
    .border(0.5.dp, Color(0xFFFFFFFF).copy(alpha = 0.15f), shape)

fun Modifier.hairlineBorder(shape: Shape = RoundedCornerShape(IronCorner.RadiusMd)): Modifier = this
    .border(0.5.dp, Color.White.copy(alpha = 0.1f), shape)

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(IronCorner.RadiusLg),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassRecipe(shape)
            .padding(IronSpacing.x20),
        content = content
    )
}

@Composable
fun StatCard(
    label: String,
    value: String,
    subValue: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassRecipe(RoundedCornerShape(IronCorner.RadiusMd))
            .padding(IronSpacing.x16)
    ) {
        Text(
            text = label.uppercase(),
            style = IronTypography.Micro.copy(color = TextTertiaryColor, letterSpacing = 1.5.sp)
        )
        Spacer(modifier = Modifier.height(IronSpacing.x4))
        AutoResizingText(
            text = value,
            style = IronTypography.Subheading.copy(fontWeight = FontWeight.Black),
            maxLines = 1
        )
        if (subValue != null) {
            Text(
                text = subValue,
                style = IronTypography.Caption.copy(color = TextSecondaryColor)
            )
        }
    }
}

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
