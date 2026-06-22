package com.example.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.WorkoutSet
import com.example.ui.components.StepperChip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SetRow(
    set: WorkoutSet,
    setIdx: Int,
    isWarmup: Boolean = false,
    effectiveWeight: Double,
    displayWeightStr: String?,
    onWeightClick: () -> Unit,
    onWeightChange: (Double) -> Unit,
    onRepsClick: () -> Unit,
    onRepsChange: (Double) -> Unit,
    onDoneToggle: () -> Unit
) {
    val isSetCompleted = set.completedAt != null
    val scaleAnim = remember { Animatable(1.0f) }
    
    LaunchedEffect(isSetCompleted) {
        if (isSetCompleted) {
            scaleAnim.animateTo(1.04f, animationSpec = tween(140, easing = IronAnimations.easingOut))
            scaleAnim.animateTo(1.0f, animationSpec = IronAnimations.springBouncy())
        } else {
            scaleAnim.animateTo(1.0f, animationSpec = tween(100))
        }
    }

    val rowAlpha by animateFloatAsState(
        targetValue = if (isSetCompleted && isWarmup) 0.4f else 1f,
        animationSpec = tween(IronAnimations.durationMedium)
    )

    val rowBgColor by animateColorAsState(
        targetValue = if (isSetCompleted && !isWarmup) {
            Color.White.copy(alpha = 0.08f) // Lighter glass
        } else {
            Color.Transparent
        },
        animationSpec = tween(IronAnimations.durationMedium)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = IronSpacing.x16)
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                alpha = rowAlpha
            }
            .background(rowBgColor, RoundedCornerShape(IronCorner.RadiusMd))
            .padding(if (isSetCompleted && !isWarmup) 8.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isWarmup) {
                Text(
                    text = "${set.setNumber}", 
                    style = IronTypography.Subheading.copy(fontWeight = FontWeight.Black), 
                    modifier = Modifier.width(24.dp)
                )
            } else {
                Text("W", style = IronTypography.Subheading.copy(fontWeight = FontWeight.Black, color = TextSecondaryColor), modifier = Modifier.width(24.dp))
            }
            
            StepperChip(
                value = effectiveWeight,
                displayValueOverride = displayWeightStr,
                unit = "KG",
                onValueChange = onWeightChange,
                onClick = onWeightClick,
                modifier = Modifier.weight(1.3f)
            )

            StepperChip(
                value = set.reps.toDouble(),
                unit = "REPS",
                onValueChange = onRepsChange,
                onClick = onRepsClick,
                modifier = Modifier.weight(1.2f),
                step = 1.0
            )

            if (isWarmup) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .padding(horizontal = 16.dp)
                        .background(if (isSetCompleted) TextPrimaryColor else Color.White.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusFull))
                        .border(
                            1.dp,
                            if (isSetCompleted) Color.Transparent else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(IronCorner.RadiusFull)
                        )
                        .bouncyClick { onDoneToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Done", style = IronTypography.Footnote, color = if (isSetCompleted) BgColor else TextPrimaryColor)
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (isSetCompleted) TextPrimaryColor else Color.White.copy(alpha = 0.05f), RoundedCornerShape(IronCorner.RadiusMd))
                        .border(
                            1.dp,
                            if (isSetCompleted) Color.Transparent else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(IronCorner.RadiusMd)
                        )
                        .bouncyClick { onDoneToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Done", tint = if (isSetCompleted) BgColor else TextPrimaryColor, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (set.targetRpe != null || !set.notes.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 32.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (set.targetRpe != null) {
                    Text("Target RPE: ${set.targetRpe}", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    if (!set.notes.isNullOrBlank()) {
                        Text(" • ", style = IronTypography.Caption.copy(color = TextSecondaryColor))
                    }
                }
                if (!set.notes.isNullOrBlank()) {
                    Text(set.notes, style = IronTypography.Caption.copy(color = TextSecondaryColor))
                }
            }
        }
    }
}
