package com.example.ui.workout

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.delay

@Composable
fun RestTimerBar(
    endTimeMillis: Long,
    totalDurationSeconds: Int,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(endTimeMillis, totalDurationSeconds) {
        while (true) {
            val now = System.currentTimeMillis()
            val diff = endTimeMillis - now
            if (diff <= 0) {
                remainingSeconds = 0
                progress = 0f
                onDismiss()
                break
            } else {
                remainingSeconds = (diff / 1000).toInt()
                progress = diff.toFloat() / (totalDurationSeconds * 1000f)
            }
            delay(100) // Update smoothly
        }
    }

    AnimatedVisibility(
        visible = remainingSeconds > 0,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassRecipe(androidx.compose.foundation.shape.RoundedCornerShape(IronCorner.RadiusMd))
                .height(64.dp)
                .bouncyClick { onDismiss() }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = IronSpacing.x16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Resting",
                        style = IronTypography.Callout.copy(color = TextSecondaryColor)
                    )
                    
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Text(
                        String.format("%d:%02d", mins, secs),
                        style = IronTypography.Headline,
                        color = TextPrimaryColor
                    )
                }

                // White line moving across at the bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(Color.White)
                )
            }
        }
    }
}
