package com.example.ui.progress

import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.WorkoutSet

@Composable
fun PlateCalculatorDialog(
    initialTargetWeight: Double,
    isKgInitially: Boolean = true,
    initialBarWeight: Double = 20.0,
    onDismiss: () -> Unit
) {
    var targetWeightInput by remember { mutableStateOf(if (initialTargetWeight <= 0.0) "100" else if (initialTargetWeight % 1.0 == 0.0) "${initialTargetWeight.toInt()}" else "$initialTargetWeight") }
    var isKg by remember { mutableStateOf(isKgInitially) }
    var barbellWeight by remember { mutableStateOf(initialBarWeight) }

    // Multi-select for plate inventory (KG and LBS inventory states)
    var availableKgPlates by remember {
        mutableStateOf(mapOf(
            25.0 to true, 20.0 to true, 15.0 to true, 10.0 to true,
            5.0 to true, 2.5 to true, 1.25 to true, 0.5 to false, 0.25 to false
        ))
    }
    var availableLbsPlates by remember {
        mutableStateOf(mapOf(
            45.0 to true, 35.0 to true, 25.0 to true, 10.0 to true,
            5.0 to true, 2.5 to true
        ))
    }

    val targetWeightDouble = targetWeightInput.toDoubleOrNull() ?: 0.0
    val inventory = if (isKg) {
        availableKgPlates.filter { it.value }.keys.sortedDescending()
    } else {
        availableLbsPlates.filter { it.value }.keys.sortedDescending()
    }

    val platesResult = remember(targetWeightDouble, barbellWeight, inventory, isKg) {
        calculatePlates(targetWeightDouble, barbellWeight, inventory)
    }

    val loadedWeight = barbellWeight + 2.0 * platesResult.sumOf { it.first * it.second }
    val unresolved = targetWeightDouble - loadedWeight

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BARBELL MATH PRO",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "PRECISE PLATE LOADING DESIGN",
                            color = Color(0xFF39FF14),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // 2. PRIMARY VIEW: Large Symmetrical Barbell Preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F2C2C35)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BARBELL SYMMETRY PREVIEW",
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Symmetrical Barbell Drawing
                        SymmetricalBarbellCanvas(platesResult, isKg, modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Loaded stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TARGET WEIGHT", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                                Text("${if (targetWeightDouble % 1.0 == 0.0) targetWeightDouble.toInt() else targetWeightDouble} ${if (isKg) "KG" else "LBS"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL BAR LOADED", color = Color(0xFF39FF14), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${if (loadedWeight % 1.0 == 0.0) loadedWeight.toInt() else loadedWeight} ${if (isKg) "KG" else "LBS"}", color = Color(0xFF39FF14), fontWeight = FontWeight.Black, fontSize = 24.sp)
                            }
                            if (unresolved > 0.1) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("UNRESOLVED", color = Color(0xFFFF4136), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("${if (unresolved % 1.0 == 0.0) unresolved.toInt() else unresolved} ${if (isKg) "KG" else "LBS"}", color = Color(0xFFFF4136), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }

                // 3. TARGET WEIGHT INPUT PANEL
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F2C2C35)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TARGET WEIGHT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            // KG vs LBS toggle
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(2.dp)
                            ) {
                                listOf(true, false).forEach { value ->
                                    val checked = isKg == value
                                    val label = if (value) "KG" else "LBS"
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (checked) Color(0xFF39FF14) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                isKg = value
                                                barbellWeight = if (value) 20.0 else 45.0
                                                val current = targetWeightInput.toDoubleOrNull() ?: 100.0
                                                // Convert standard weights roughly
                                                val next = if (value) {
                                                    val lbsToKg = Math.round(current / 2.20462)
                                                    (Math.round(lbsToKg / 2.5) * 2.5)
                                                } else {
                                                    val kgToLbs = Math.round(current * 2.20462)
                                                    (Math.round(kgToLbs / 5.0) * 5.0)
                                                }
                                                targetWeightInput = if (next % 1.0 == 0.0) "${next.toInt()}" else "$next"
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (checked) Color.Black else Color.White.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = targetWeightInput,
                            onValueChange = { targetWeightInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = {
                                Text(if (isKg) "kg" else "lbs", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF39FF14),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.4f)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tap steps increments
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val listSteps = if (isKg) listOf(-10.0, -2.5, 2.5, 10.0) else listOf(-25.0, -5.0, 5.0, 25.0)
                            listSteps.forEach { step ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val current = targetWeightInput.toDoubleOrNull() ?: 100.0
                                            val nextVal = (current + step).coerceAtLeast(barbellWeight)
                                            targetWeightInput = if (nextVal % 1.0 == 0.0) "${nextVal.toInt()}" else "$nextVal"
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (step > 0) "+$step" else "$step",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. BARBELL WEIGHT SELECTOR LIST
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F2C2C35)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BARBELL BASE WEIGHT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val bars = if (isKg) listOf(20.0, 15.0, 10.0) else listOf(45.0, 35.0, 25.0)
                            bars.forEach { bar ->
                                val selected = barbellWeight == bar
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (selected) Color(0xFF39FF14) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { barbellWeight = bar }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${bar.toInt()} ${if (isKg) "kg" else "lbs"}",
                                        color = if (selected) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. WARM-UP INTEGRATION ROW (40% / 60% / 80%)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F2C2C35)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AUTOMATED WARM-UP PROGRESSION",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Physical setups computed using your active plate availability.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        listOf(0.40, 0.60, 0.80).forEach { pct ->
                            val stepTarget = targetWeightDouble * pct
                            val stepPlates = calculatePlates(stepTarget, barbellWeight, inventory)
                            val roundedTotal = barbellWeight + 2.0 * stepPlates.sumOf { it.first * it.second }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.width(76.dp)) {
                                    Text(
                                        text = "${(pct * 100).toInt()}% LOAD",
                                        color = Color(0xFF39FF14),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${if (roundedTotal % 1.0 == 0.0) roundedTotal.toInt() else roundedTotal} ${if (isKg) "kg" else "lbs"}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Small mini-bar visual
                                SymmetricalBarbellCanvas(
                                    plates = stepPlates,
                                    isKg = isKg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                val breakdownLabel = if (stepPlates.isEmpty()) "BAR ONLY" else {
                                    stepPlates.joinToString(",") { "${it.second}x${if (it.first % 1.0 == 0.0) it.first.toInt() else it.first}" }
                                }
                                Text(
                                    text = breakdownLabel,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(64.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                // 6. GYM PLAYPLATES INVENTORY SWITCH
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F2C2C35)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ACTIVE PLATE INVENTORY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Text("Deconstruct any unavailable plates in your facility.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isKg) {
                                availableKgPlates.forEach { (wt, available) ->
                                    val isSelected = available
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) Color(0xFF39FF14).copy(alpha = 0.15f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF39FF14) else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val mutable = availableKgPlates.toMutableMap()
                                                mutable[wt] = !available
                                                availableKgPlates = mutable
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${if (wt % 1.0 == 0.0) wt.toInt() else wt} kg",
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                availableLbsPlates.forEach { (wt, available) ->
                                    val isSelected = available
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) Color(0xFF39FF14).copy(alpha = 0.15f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF39FF14) else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val mutable = availableLbsPlates.toMutableMap()
                                                mutable[wt] = !available
                                                availableLbsPlates = mutable
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${if (wt % 1.0 == 0.0) wt.toInt() else wt} lbs",
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SymmetricalBarbellCanvas(
    plates: List<Pair<Double, Int>>,
    isKg: Boolean,
    modifier: Modifier = Modifier
) {
    val itemsToDraw = remember(plates) {
        val list = mutableListOf<Double>()
        plates.forEach { (denom, count) ->
            repeat(count) {
                list.add(denom)
            }
        }
        list
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            // 1. Draw central barbell steel bar shaft
            drawRect(
                color = Color(0xFF45454F),
                topLeft = Offset(width * 0.05f, centerY - 4f),
                size = Size(width * 0.9f, 8f)
            )

            // 2. Collar Stops (Inner Stops centered symmetrically at 30% and 70%)
            val leftCollarX = width * 0.35f
            val rightCollarX = width * 0.65f

            // Symmetrical Stops (Left and Right Collar sleeves)
            drawRoundRect(
                color = Color(0xFF1E1E24),
                topLeft = Offset(leftCollarX - 10f, centerY - 28f),
                size = Size(10f, 56f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            drawRoundRect(
                color = Color(0xFF1E1E24),
                topLeft = Offset(rightCollarX, centerY - 28f),
                size = Size(10f, 56f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Inner sleeve extensions outwards
            drawRect(
                color = Color(0xFF78909C),
                topLeft = Offset(width * 0.05f, centerY - 10f),
                size = Size(leftCollarX - width * 0.05f - 10f, 20f)
            )

            drawRect(
                color = Color(0xFF78909C),
                topLeft = Offset(rightCollarX + 10f, centerY - 10f),
                size = Size(width * 0.95f - rightCollarX - 10f, 20f)
            )

            // 3. Render plate loads symmetrically on both sides!
            // RIGHT SLEEVE DRAW (Draws outwards from right collar)
            var currentRightX = rightCollarX + 14f
            itemsToDraw.forEach { plate ->
                val color = getPlateColor(plate, isKg)
                val h = getPlateHeight(plate, isKg) * 0.8f
                val w = getPlateWidth(plate, isKg) * 0.7f

                drawRoundRect(
                    color = color,
                    topLeft = Offset(currentRightX, centerY - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )

                // Silver center hub
                drawCircle(
                    color = Color(0xFFB0BEC5),
                    radius = 3f,
                    center = Offset(currentRightX + w / 2f, centerY)
                )

                // Draw small label
                val label = if (plate % 1.0 == 0.0) "${plate.toInt()}" else "$plate"
                val textPaint = Paint().apply {
                    this.color = if (color == Color.White) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    textSize = 14f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    currentRightX + w / 2f,
                    centerY + 5f,
                    textPaint
                )

                currentRightX += w + 2f
            }

            // LEFT SLEEVE DRAW (Symmetrical drawing going leftwards from left collar stop)
            var currentLeftX = leftCollarX - 14f
            itemsToDraw.forEach { plate ->
                val color = getPlateColor(plate, isKg)
                val h = getPlateHeight(plate, isKg) * 0.8f
                val w = getPlateWidth(plate, isKg) * 0.7f

                // In leftward loading, subtract the width FIRST to draw correctly from collar outwards
                val startX = currentLeftX - w
                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX, centerY - h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )

                // Center hub
                drawCircle(
                    color = Color(0xFFB0BEC5),
                    radius = 3f,
                    center = Offset(startX + w / 2f, centerY)
                )

                // Label
                val label = if (plate % 1.0 == 0.0) "${plate.toInt()}" else "$plate"
                val textPaint = Paint().apply {
                    this.color = if (color == Color.White) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    textSize = 14f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                     label,
                     startX + w / 2f,
                     centerY + 5f,
                     textPaint
                )

                currentLeftX -= (w + 2f)
            }

            // Clamps (collars) on collars outermost edges if loaded
            if (itemsToDraw.isNotEmpty()) {
                // right clamp
                drawRoundRect(
                    color = Color(0xFFCFD8DC),
                    topLeft = Offset(currentRightX + 2f, centerY - 18f),
                    size = Size(6f, 36f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // left clamp
                drawRoundRect(
                    color = Color(0xFFCFD8DC),
                    topLeft = Offset(currentLeftX - 8f, centerY - 18f),
                    size = Size(6f, 36f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}
